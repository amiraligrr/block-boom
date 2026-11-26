package ir.amiraligrr.blockboom.data

import android.content.Context
import android.util.Log
import ir.amiraligrr.blockboom.BlockBoomApp
import ir.amiraligrr.blockboom.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * What the server says about this device and this build.
 *
 * The client cannot decide any of this for itself, which is the whole point:
 * a patched APK that ignores the answer still cannot post a score, because
 * submit_score.php and start_run.php re-check the ban independently.
 */
sealed class AccessState {
    /** Still deciding, or we have no answer yet. Play is allowed. */
    data object Unknown : AccessState()

    data object Allowed : AccessState()

    data class Banned(val reason: String) : AccessState()

    data class VersionBlocked(val reason: String, val message: String, val storeUrl: String) : AccessState()
}

object AppConfigRepository {

    private const val TAG = "AppConfig"
    private const val TIMEOUT_MS = 6000L

    /** Re-check at most this often, so a resume storm does not hammer the API. */
    private const val MIN_RECHECK_MS = 20_000L

    private val _state = MutableStateFlow<AccessState>(AccessState.Unknown)
    val state: StateFlow<AccessState> = _state.asStateFlow()

    /**
     * The store link the operator configured, or "" when there is none.
     *
     * Read by the rating prompt. Kept out of [AccessState] on purpose: that type
     * answers "may this device play", and a missing link is not an answer to
     * that question — folding it in would mean a device with no store link
     * configured somehow gets a different *access* decision, which is exactly
     * the kind of coupling that turns "we forgot to set a URL" into "the app
     * locked everyone out".
     *
     * Blank by default, and blank is what the prompt checks for: with no link
     * there is nothing to send the player to, and asking them to rate the app
     * with nowhere to do it is worse than not asking.
     */
    private val _rateUrl = MutableStateFlow("")
    val rateUrl: StateFlow<String> = _rateUrl.asStateFlow()

    private val mutex = Mutex()
    private var lastCheckAt = 0L

    /** How long a positive answer is trusted before re-checking. */
    private var validUntil = 0L

    fun reset() {
        lastCheckAt = 0L
        validUntil = 0L
        _state.value = AccessState.Unknown
    }
    fun check(force: Boolean = false) {
        val ctx = appContext ?: return
        val now = System.currentTimeMillis()
        if (!force && now < validUntil) return
        if (!force && now - lastCheckAt < MIN_RECHECK_MS) return

        lastCheckAt = now
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                val result = fetch(ctx)
                if (result == null) {
                    // ── Fail open, *unless* this device already has a ban ─────
                    // A timeout, a 500, or a dead server must never stop someone
                    // playing. If we cannot reach the gate we let them in.
                    //
                    // The exception is a device that has already been told it is
                    // banned and has written that down ([LocalBanStore]). That
                    // record is the server's own decision, cached, and consulting
                    // it only here is what closes the hole "fail open" otherwise
                    // leaves: a banned player who simply never reconnects would
                    // otherwise play forever, which makes the ban a suggestion.
                    //
                    // Nothing is invented — this is not the client deciding
                    // anyone is banned. It is a previously-received answer being
                    // honoured while the network is unavailable.
                    val localBan = LocalBanStore.load(ctx)
                    if (localBan != null) {
                        Log.w(TAG, "config unreachable, but a local ban record exists")
                        _state.value = AccessState.Banned(localBan.reason)
                    } else {
                        Log.w(TAG, "config unreachable, allowing play")
                        _state.value = AccessState.Allowed
                    }
                    return@withLock
                }
                validUntil = System.currentTimeMillis() + 60_000L
                // An allowed answer from the server lifts the local record, so
                // lifting a ban through the admin panel takes effect on the
                // player's next launch without any special "unban" round trip.
                if (result is AccessState.Allowed) {
                    LocalBanStore.clear(ctx)
                }
                _state.value = result
            }
        }
    }

    private suspend fun fetch(ctx: Context): AccessState? = withContext(Dispatchers.IO) {
        val url = "${LeaderboardManager.apiBase()}/app_config.php" +
            "?device_id=${LeaderboardManager.getDeviceId(ctx)}" +
            "&version_code=${BuildConfig.VERSION_CODE}"

        val body = withTimeoutOrNull(TIMEOUT_MS) {
            HttpJson.getJson(url, connectMs = 4000, readMs = 4000)?.second
        } ?: return@withContext null

        parse(ctx, body)
    }

    private fun parse(ctx: Context, json: JSONObject): AccessState {
        applyServerPremium(json)
        // The rating destination, read on every check. It rides along on a
        // request that already happens, so it costs nothing and is always current.
        _rateUrl.value = json.optString("rate_url").ifBlank {
            json.optString("store_url")
        }.trim()
        // A ban outranks a version gate: if the device is banned there is no
        // point telling them to update.
        if (json.optBoolean("banned", false)) {
            val reason = json.optString("ban_reason", "").ifBlank {
                "با توجه به فعالیت‌های غیرمجاز اخیر، حساب شما مسدود شده است."
            }
            // Written down before it is acted on, so the block survives this
            // process, an offline stretch, a reinstall or a cleared app-data.
            // See LocalBanStore for why this is not simply another flag.
            LocalBanStore.record(ctx, reason)
            return AccessState.Banned(reason)
        }
        if (json.optBoolean("blocked", false)) {
            return AccessState.VersionBlocked(
                reason = json.optString("block_reason", "version_disabled"),
                message = json.optString("block_message", "").ifBlank {
                    "برای ادامهٔ بازی باید به آخرین نسخه بروید."
                },
                storeUrl = json.optString("store_url", "")
            )
        }
        return AccessState.Allowed
    }

    /**
     * Applies the operator's premium decision, which rides along on the same
     * request that already gates bans and versions.
     *
     * ── Why the null check is load-bearing ───────────────────────────────
     * The server sends `premium: null` when it has no opinion — which is what
     * it does when `premium_grants` does not exist on the database (the
     * migration has not been run). Reading that as `false` would be a
     * disaster: the client's response to "not premium" is to CLEAR the stored
     * grant, so an un-migrated database would silently strip premium from
     * everyone the moment they opened the app. Missing data must never be
     * treated as a negative answer.
     *
     * ── Why it runs even for a blocked build ────────────────────────────
     * Applied before the ban/version checks, so a revoked gift is not left
     * sitting in local storage for as long as the player is locked out.
     */
    private fun applyServerPremium(json: JSONObject) {
        if (json.isNull("premium")) return
        // appContext is the Application itself (it is assigned from
        // context.applicationContext), so this cast is how everything else in
        // the data layer reaches SaveManager.
        val save = (appContext as? BlockBoomApp)?.saveManager ?: return
        val active = json.optBoolean("premium", false)
        // Server speaks unix seconds; storage is millis.
        val untilMs = json.optLong("premium_until", 0L) * 1000L
        save.applyServerPremium(active, untilMs)
    }

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        // Apply any ban already on disk *before* the first network answer, so
        // an offline launch of a banned device is blocked from the very first
        // frame rather than after a connect timeout.
        //
        // This reads the local record only. It cannot invent a ban — the record
        // is written solely in response to a server answer — so doing it here
        // costs nothing in false positives and closes the window where the
        // NavHost is composed for a banned device.
        LocalBanStore.load(app)?.let {
            _state.value = AccessState.Banned(it.reason)
        }
    }
}
