package ir.amiraligrr.blockboom.data

import android.content.Context
import android.util.Log
import ir.amiraligrr.blockboom.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Gameplay telemetry — what the monitoring panel in admin/monitor.php reads.
 *
 * ── Why this is not one HTTP call per event ────────────────────────────────
 * A minute of play produces hundreds of blocks destroyed. One request each
 * would mean hundreds of requests per player per minute against a *shared*
 * host with a request-rate cap, which is how a telemetry feature gets the
 * whole account suspended and takes the game offline with it.
 *
 * So this does two different things with two different kinds of signal:
 *
 *  - **Counters** (blocks, coins, shots, combos, play time) are accumulated
 *    locally and sent as one small set of integers per flush. Exact totals,
 *    one row per device per hour on the server.
 *  - **Milestone events** (run start/end, level complete, a combo reaching 5,
 *    10, 20 …, a power-up) are appended to a bounded queue and sent as small
 *    rows. These are the ones an operator actually reads, so they are kept
 *    individually rather than averaged away.
 *
 * ── Rules this obeys, in priority order ────────────────────────────────────
 *  1. **Never affect the game.** Every entry point is a synchronized integer
 *     add or a list append. Nothing here allocates on the game loop in a way
 *     that can throw, and nothing here can block on IO.
 *  2. **Never retry.** A failed flush is dropped. Retrying a telemetry packet
 *     on a phone with no signal would queue work that outlives the session and
 *     then dumps it on the network the instant it comes back — usually the
 *     worst possible moment.
 *  3. **Never trust itself for anything.** Nothing here is authentication.
 *     The client is free to lie about all of it; the panel reads it as
 *     directional, exactly like ad_events. Score authenticity stays with the
 *     server-signed run token (see RunSession.kt).
 */
object Telemetry {

    private const val TAG = "Telemetry"
    private const val ENDPOINT = "telemetry.php"

    /** Flush once the queue reaches this, so a long session still reports live. */
    private const val FLUSH_AT_EVENTS = 25

    /** …or this long after the first pending event, whichever comes first. */
    private const val FLUSH_INTERVAL_MS = 12_000L

    /** Hard cap on the in-memory queue. Drops the oldest, never grows without bound. */
    private const val MAX_QUEUED_EVENTS = 200

    /** Combo values worth a row of their own: the chain lengths players chase. */
    private val COMBO_MILESTONES = intArrayOf(5, 10, 15, 20, 30, 50)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushLock = Mutex()
    private val inFlight = AtomicBoolean(false)

    @Volatile private var started = false

    /** Counters for the current flush window. Guarded by the Mutex. */
    private class Window {
        var runs = 0; internal set
        var levels = 0; internal set
        var blocks = 0; internal set
        var coins = 0; internal set
        var shots = 0; internal set
        var combos = 0; internal set
        var maxCombo = 0; internal set
        var activeMs = 0; internal set

        fun clear() {
            runs = 0; levels = 0; blocks = 0; coins = 0
            shots = 0; combos = 0; maxCombo = 0; activeMs = 0
        }

        val isEmpty: Boolean
            get() = runs == 0 && levels == 0 && blocks == 0 && coins == 0 &&
                    shots == 0 && combos == 0 && activeMs == 0
    }

    private val window = Window()
    private val queue = ArrayList<JSONObject>(MAX_QUEUED_EVENTS)
    private val pendingFlush = AtomicBoolean(false)

    private var appContext: Context? = null
    private var deviceId: String = ""
    private val startedAt = AtomicLong(System.currentTimeMillis())

    /**
     * Starts the reporting loop. Idempotent, and safe to call from anywhere.
     *
     * Called once from BlockBoomApp.onCreate so telemetry exists for the whole
     * process lifetime, including runs started before any screen composes.
     */
    fun init(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            appContext = context.applicationContext
            deviceId = LeaderboardManager.getDeviceId(context)
            started = true
        }
        scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flush("timer")
            }
        }
    }

    // ── Discrete events ─────────────────────────────────────────────────────

    /**
     * Queues one milestone event.
     *
     * @param level   the level the player was on, 0 when not applicable.
     * @param value   the headline number (score, combo size), 0 when not applicable.
     * @param durationMs how long the thing took, 0 when not applicable.
     * @param meta    short free text, capped by the server column.
     */
    fun track(
        type: String,
        level: Int = 0,
        value: Int = 0,
        durationMs: Int = 0,
        meta: String = ""
    ) {
        if (!started) return
        synchronized(queue) { enqueue(type, level, value, durationMs, meta) }
        if (pendingEventCount >= FLUSH_AT_EVENTS) requestFlush()
    }

    // ── Counters ────────────────────────────────────────────────────────────

    fun addRun() { bump { it.runs++ } }
    fun addLevelComplete() { bump { it.levels++ } }
    fun addBlocks(n: Int) { if (n > 0) bump { it.blocks += n } }
    fun addCoins(n: Int) { if (n > 0) bump { it.coins += n } }
    fun addShots(n: Int) { if (n > 0) bump { it.shots += n } }
    fun addCombo(n: Int = 1) { bump { it.combos += n } }
    fun addActiveMs(ms: Long) { if (ms > 0) bump { it.activeMs += ms.coerceAtMost(60_000L).toInt() } }

    /**
     * Records the longest chain of the run, and emits a row the first time
     * each milestone is crossed.
     *
     * This is the hook the monitoring panel's combo chart is built from, so it
     * is called on *every* combo change rather than only on kills.
     */
    fun noteCombo(combo: Int) {
        if (combo < 2) return
        addCombo()
        // The aggregate high-water mark, which is what the 24h "best combo"
        // tile reads. It has to be recorded here, not only when a milestone is
        // crossed — a run whose best chain was x7 would otherwise report 0.
        bump { if (combo > it.maxCombo) it.maxCombo = combo }

        synchronized(queue) {
            // Only a new high-water mark can cross a milestone. Rebuilding x20
            // five times in a run must not produce five rows.
            if (combo <= maxComboSeen) return
            maxComboSeen = combo
            if (COMBO_MILESTONES.contains(combo) && milestonesHit.add(combo)) {
                enqueue("combo", level = 0, value = combo, meta = "")
            }
        }
    }

    /** Highest combo seen since the last flush. Guarded by `queue`. */
    private var maxComboSeen: Int = 0

    /** Milestones already reported since the last flush. Guarded by `queue`. */
    private val milestonesHit = HashSet<Int>()

    private inline fun bump(block: (Window) -> Unit) {
        if (!started) return
        synchronized(this) { block(window) }
    }

    /**
     * Appends an event row. Caller must hold `queue`.
     *
     * The private twin of [track], so the combo path and the public path cannot
     * drift apart on the JSON shape or the size cap.
     */
    private fun enqueue(type: String, level: Int, value: Int, durationMs: Int = 0, meta: String) {
        if (queue.size >= MAX_QUEUED_EVENTS) queue.removeAt(0)
        queue.add(
            JSONObject()
                .put("t", type)
                .put("l", level)
                .put("v", value)
                .put("d", durationMs)
                .put("m", meta.take(180))
                .put("ts", System.currentTimeMillis())
        )
    }

    private val pendingEventCount: Int
        get() = synchronized(queue) { queue.size }

    private fun requestFlush() {
        if (pendingFlush.compareAndSet(false, true)) {
            scope.launch { flush("threshold") }
        }
    }

    /**
     * Sends everything buffered. Safe to call from any thread; concurrent
     * callers collapse into one in-flight request.
     *
     * This is also the presence heartbeat: the server treats every accepted
     * packet as "this device is playing right now", which is what the panel's
     * online-now tile is computed from.
     */
    fun flush(reason: String = "manual") {
        if (!started) return
        if (!inFlight.compareAndSet(false, true)) return
        scope.launch {
            try {
                doFlush(reason)
            } catch (t: Throwable) {
                // Telemetry must never take the app down with it.
                Log.w(TAG, "flush failed (${t.javaClass.simpleName}): ${t.message}")
            } finally {
                inFlight.set(false)
                pendingFlush.set(false)
            }
        }
    }

    private suspend fun doFlush(reason: String) {
        val ctx = appContext ?: return
        val id = deviceId
        if (id.isBlank()) return

        // ── Locking ───────────────────────────────────────────────────────────
        // Two locks, always taken in this order, never the other way round:
        //
        //     flushLock  →  synchronized(this)  →  synchronized(queue)
        //
        // `this` guards the counter window; `queue` guards the event list and
        // the combo milestone bookkeeping. Both were previously guarded
        // inconsistently — the counters were written under `this` but *read and
        // cleared* by the flush under `flushLock`, so a block destroyed while a
        // flush was serialising was silently dropped, and `milestonesHit` (a
        // plain HashSet) was written under `this` and cleared under
        // `flushLock`, which is a data race waiting for the right interleaving.
        //
        // Everything is snapshotted under the locks and then released before
        // any IO, so a tracking call on the game thread never waits on the
        // network.
        val events: JSONArray
        val body: JSONObject
        flushLock.withLock {
            synchronized(this@Telemetry) {
                if (window.isEmpty && queue.isEmpty()) return

                events = JSONArray()
                synchronized(queue) {
                    for (o in queue) events.put(o)
                    queue.clear()
                    milestonesHit.clear()
                    maxComboSeen = 0
                }

                body = JSONObject()
                    .put("device_id", id)
                    .put("app_version", BuildConfig.VERSION_NAME)
                    .put("version_code", BuildConfig.VERSION_CODE)
                    .put("reason", reason)
                    .put("session_ms", (System.currentTimeMillis() - startedAt.get()).coerceAtMost(86_400_000L))
                    .put("agg", JSONObject()
                        .put("runs", window.runs)
                        .put("levels", window.levels)
                        .put("blocks", window.blocks)
                        .put("coins", window.coins)
                        .put("shots", window.shots)
                        .put("combos", window.combos)
                        .put("max_combo", window.maxCombo)
                        .put("active_ms", window.activeMs)
                    )
                    .put("events", events)

                window.clear()
            }
        }

        val res = HttpJson.postJson(
            "${LeaderboardManager.apiBase()}/$ENDPOINT",
            body,
            connectMs = 6_000,
            readMs = 6_000
        )
        if (res == null) {
            // Dropped on the floor on purpose. See rule 2 in the class docs.
            Log.d(TAG, "telemetry packet dropped ($reason)")
        }
    }

    /** Test/diagnostic helper: how much is waiting to go out. */
    fun pendingCount(): Int = pendingEventCount
}
