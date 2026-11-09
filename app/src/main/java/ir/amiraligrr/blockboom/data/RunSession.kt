package ir.amiraligrr.blockboom.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest

/**
 * One play session, tracked as proof the run actually happened.
 *
 * The server signs a token when a run starts and will only accept a score that
 * is backed by that token. Two things here are deliberately *not* client-trust
 * boundaries — they exist to make casual tampering and mass automation
 * inconvenient, and the real judgement happens on the server:
 *
 *  - [events] and [durationMs] are self-reported. The server measures the run's
 *    wall-clock duration itself from the token's start time, so a client that
 *    inflates these gains nothing it could not get by playing.
 *  - [chain] is a rolling SHA-256 over the run's score events. It binds the
 *    event count to the final score, so simply editing the score in a
 *    save file no longer matches the digest the server already holds.
 */
class PlaySession private constructor(
    val runToken: String,
    val startedAtMs: Long
) {
    /** Sum of every score event in this run. */
    var scoreAccumulated: Int = 0
        private set

    var durationMs: Long = 0L
        private set

    private var events: Int = 0
    private var chain: String = ""
    private var closed: Boolean = false
    private var lastEventAtMs: Long = 0L

    /**
     * Fold a score delta into the run. Cheap enough to call on every block
     * destroyed, and a single SHA-256 of a short string is microseconds.
     */
    @Synchronized
    fun onScore(delta: Int, atMs: Long) {
        if (closed || delta <= 0) return
        scoreAccumulated += delta
        events++
        chain = sha256("$chain|$events|$delta|$atMs")
        if (atMs > lastEventAtMs) lastEventAtMs = atMs
    }

    /** Freeze the run so a second submit cannot reuse it. */
    @Synchronized
    fun snapshot(nowMs: Long): Snapshot {
        if (!closed) {
            val end = if (lastEventAtMs > startedAtMs) lastEventAtMs else nowMs
            durationMs = (end - startedAtMs).coerceAtLeast(0L)
            closed = true
        }
        return Snapshot(runToken, durationMs, events, chain)
    }

    data class Snapshot(
        val runToken: String,
        val durationMs: Long,
        val events: Int,
        val chain: String
    )

    companion object {
        private const val SEED = "bb-run"

        fun sha256(input: String): String = try {
            val md = MessageDigest.getInstance("SHA-256")
            md.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        } catch (t: Throwable) {
            Log.w("PlaySession", "sha256 failed", t)
            ""
        }

        fun new(runToken: String, startedAtMs: Long) = PlaySession(runToken, startedAtMs).apply {
            chain = sha256("$SEED|$runToken|$startedAtMs")
        }
    }
}

/**
 * Owns the [PlaySession] for the current run and talks to the run endpoints.
 *
 * Everything is best-effort: a player with no network, an old server that does
 * not have start_run.php, or a device on a captive portal still plays fine and
 * still submits through the legacy path. Anti-cheat must never be able to stop
 * someone playing.
 */
object RunSessionManager {

    private const val TAG = "RunSession"
    private const val START_TIMEOUT_MS = 5000L

    /**
     * How long [close] will wait for an in-flight [start] to land.
     *
     * This is the fix for "I finished the level and nothing was registered".
     *
     * The token request is fired off asynchronously when a run starts, and the
     * run can end before it comes back. `close()` used to read `current`, find
     * it null because the request was still in flight, return null, and the
     * submission then went out with no token at all. The server had no choice
     * but to reject it, so the run was silently lost — and because the request
     * is genuinely asynchronous, it hit whoever had a slow connection, which on
     * the networks these players are on is a lot of people.
     *
     * So `close` now waits for the pending request, briefly. A few seconds of
     * waiting at the end of a run is invisible; losing the run is not.
     */
    private const val CLOSE_WAIT_MS = 4000L

    private val mutex = Mutex()

    /**
     * Written under [mutex] on an IO dispatcher, read by [active] from whatever
     * thread the game loop is on — without `volatile` the reader is entitled to
     * a cached value indefinitely, so a freshly opened session could stay
     * invisible to the score-hash chain for the whole run.
     */
    @Volatile
    private var current: PlaySession? = null

    /**
     * The in-flight `start_run` request, if any.
     *
     * A [CompletableDeferred] rather than a Job so [close] can await the
     * *result* rather than the coroutine's completion, and is released by
     * whoever starts it so a failure cannot leave waiters hanging.
     */
    @Volatile
    private var pendingStart: CompletableDeferred<PlaySession?>? = null

    /** The session for the run in progress, if the server issued one. */
    fun active(): PlaySession? = current

    /**
     * Forgets the current run without submitting it.
     *
     * Needed whenever the player walks away from a run that is still open —
     * backing out to the home screen, or the activity being destroyed. Leaving
     * `current` set means the *next* run inherits this one's token, and the
     * server consumes a token on first use and only then checks its age, so that
     * run's submission comes back rejected and the score is lost on its first
     * attempt.
     */
    fun abandon() {
        pendingStart = null
        // Not under [mutex], which is suspending and this is called from the UI
        // thread. Both fields are `@Volatile` and the operation is idempotent, so
        // there is no invariant to protect — the only thing that must not happen
        // is leaving a session behind.
        current = null
    }

    /**
     * Opens a verified run for [level]. Returns null when the server is
     * unreachable or not yet deployed — callers must not block on this.
     */
    fun start(context: Context, level: Int, onReady: (PlaySession?) -> Unit) {
        val app = context.applicationContext
        val deferred = CompletableDeferred<PlaySession?>()
        // Replace any earlier in-flight request. Only one run is ever active,
        // and a stale deferred would be awaited by a close() belonging to a
        // different run.
        pendingStart = deferred
        CoroutineScope(Dispatchers.IO).launch {
            val session = requestRun(app, level)
            deferred.complete(session)
            // A new run replaces whatever session was open, unconditionally.
            //
            // This used to keep the previous one when `session` came back null —
            // "if there is no current session, or we got a fresh one, use it" —
            // which quietly carried an abandoned run's token into the next one.
            // The server consumes the token first and checks staleness second, so
            // that run's score was rejected outright on its first attempt. If no
            // token was issued this run, the correct answer is that there is no
            // session, not that there is an old one.
            mutex.withLock { current = session }
            withContext(Dispatchers.Main) { onReady(session) }
        }
    }

    private suspend fun requestRun(context: Context, level: Int): PlaySession? {
        return try {
            val deviceId = LeaderboardManager.getDeviceId(context)
            val body = JSONObject().apply {
                put("device_id", deviceId)
                put("level", level)
            }
            val resp: JSONObject? = withTimeoutOrNull(START_TIMEOUT_MS) {
                HttpJson.postJson("${LeaderboardManager.apiBase()}/start_run.php", body)
            }
            if (resp == null || !resp.optBoolean("ok", false)) {
                Log.i(TAG, "run token unavailable (${resp?.optString("error") ?: "timeout"}) - unverified path")
                return null
            }
            val token = resp.optString("run_token", "")
            if (token.isBlank()) return null
            PlaySession.new(token, System.currentTimeMillis())
        } catch (t: Throwable) {
            Log.i(TAG, "start_run failed, submitting unverified: ${t.message}")
            null
        }
    }

    /**
     * Ends the current run and returns its submission fields.
     *
     * Waits for an outstanding `start_run` first — see [CLOSE_WAIT_MS]. If the
     * token never arrives, this returns null and the score is submitted on the
     * unverified path, which the server accepts rather than rejecting.
     */
    suspend fun close(): PlaySession.Snapshot? {
        // Outside the lock: awaiting a network call while holding the mutex
        // would block a concurrent start() for the whole timeout.
        val pending = pendingStart
        if (pending != null) {
            val session = withTimeoutOrNull(CLOSE_WAIT_MS) { pending.await() }
            if (session != null) mutex.withLock { if (current == null) current = session }
            // Whether or not it produced a token, it is no longer outstanding.
            pendingStart = null
        }
        return mutex.withLock {
            val s = current ?: return null
            current = null
            s.snapshot(System.currentTimeMillis())
        }
    }
}

/** Shared HTTP helpers so every endpoint behaves the same. */
object HttpJson {

    fun postJson(url: String, body: JSONObject, connectMs: Int = 8000, readMs: Int = 8000): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = connectMs
                readTimeout = readMs
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            readJson(conn)
        } catch (e: SocketTimeoutException) {
            Log.w("HttpJson", "timeout: $url")
            null
        } catch (e: IOException) {
            Log.w("HttpJson", "io error: $url (${e.message})")
            null
        } catch (t: Throwable) {
            Log.w("HttpJson", "unexpected: $url", t)
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun getJson(url: String, connectMs: Int = 7000, readMs: Int = 7000): Pair<Int, JSONObject>? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                connectTimeout = connectMs
                readTimeout = readMs
            }
            readJson(conn)?.let { conn.responseCode to it }
        } catch (e: SocketTimeoutException) {
            Log.w("HttpJson", "timeout: $url")
            null
        } catch (e: IOException) {
            Log.w("HttpJson", "io error: $url (${e.message})")
            null
        } catch (t: Throwable) {
            Log.w("HttpJson", "unexpected: $url", t)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun readJson(conn: HttpURLConnection): JSONObject? {
        val code = conn.responseCode
        val text = try {
            (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
        } catch (t: Throwable) {
            ""
        }
        if (text.isBlank()) return null
        return try {
            JSONObject(text)
        } catch (t: Throwable) {
            Log.w("HttpJson", "bad json (HTTP $code): ${text.take(160)}")
            null
        }
    }
}
