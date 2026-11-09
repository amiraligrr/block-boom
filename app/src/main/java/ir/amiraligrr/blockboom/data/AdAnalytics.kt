package ir.amiraligrr.blockboom.data

import android.content.Context
import android.util.Log
import ir.amiraligrr.blockboom.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fire-and-forget analytics for ad impressions / clicks / rewards.
 * Batches are dropped if the network is unavailable — never blocks gameplay.
 *
 * These events are NOT authenticated, and the panel's ad numbers should be read
 * with that in mind. They used to carry an HMAC whose key was compiled into the
 * APK, which meant anyone who unzipped it could mint impressions at will — the
 * appearance of a security control with none behind it. It has been removed
 * rather than left in place to look reassuring.
 *
 * What actually protects the table now is server-side rate limiting per device
 * and per IP, plus a client-side cooldown so a bug cannot write unbounded rows.
 * Neither prevents a determined forger; it bounds the damage.
 *
 * Genuine anti-cheat lives in the leaderboard path, where the signing key never
 * leaves the server (see RunSession / start_run.php).
 */
object AdAnalytics {

    private const val TAG = "AdAnalytics"
    private const val ENDPOINT = "ad_event.php"
    private val scope = CoroutineScope(Dispatchers.IO)

    /** Minimum gap between two events of the same (type, placement). */
    private const val MIN_GAP_MS = 1_000L
    private val lastSent = HashMap<String, Long>()

    /**
     * Whether an event was actually put on the wire, for diagnostics.
     *
     * The panel showing "no banner impressions" is indistinguishable from
     * three different faults: nothing was sent, the send failed, or the panel
     * is reading a different window. Without a local counter there is no way
     * to tell them apart, and the first two are the app's fault while the
     * third is not. Readable from a bug report; never shown in the UI.
     */
    @Volatile private var bannerImpressionsSent = 0
    val bannerImpressions: Int get() = bannerImpressionsSent

    @Volatile private var bannerClicksSent = 0
    val bannerClicks: Int get() = bannerClicksSent

    /**
     * Records a delivery failure, so "no rows on the panel" has a second cause
     * that can be read off the device instead of guessed at.
     */
    @Volatile private var lastError: String? = null
    val lastSendError: String? get() = lastError

    fun log(context: Context, eventType: String, placement: String) {
        val key = "$eventType|$placement"
        val now = System.currentTimeMillis()
        synchronized(lastSent) {
            val last = lastSent[key] ?: 0L
            if (now - last < MIN_GAP_MS) return
            lastSent[key] = now
        }

        val appContext = context.applicationContext
        scope.launch {
            try {
                val deviceId = LeaderboardManager.getDeviceId(appContext)
                val timestamp = System.currentTimeMillis() / 1000
                val json = JSONObject().apply {
                    put("event_type", eventType)
                    put("placement", placement)
                    put("device_id", deviceId)
                    put("timestamp", timestamp)
                }
                val conn = URL("${apiBase()}/$ENDPOINT").openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.doOutput = true
                    conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        lastError = "HTTP $code"
                        Log.w(TAG, "ad_event rejected: $code")
                        // Let the next event through immediately rather than
                        // waiting out the cooldown on a rejected request.
                        synchronized(lastSent) { lastSent.remove(key) }
                    } else {
                        lastError = null
                        // Counted only on a confirmed 2xx, so the counter
                        // reflects rows that exist rather than attempts.
                        when {
                            eventType == "impression" && placement == "banner" ->
                                bannerImpressionsSent++
                            eventType == "click" && placement == "banner" ->
                                bannerClicksSent++
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                lastError = e.message
                Log.w(TAG, "ad_event failed: ${e.message}")
                synchronized(lastSent) { lastSent.remove(key) }
            }
        }
    }

    private fun apiBase(): String = try {
        val url = BuildConfig.BLOCKBOOM_API_URL
        if (url.isNotBlank()) url else "https://blockboomapi.amiraligrr.ir"
    } catch (_: Throwable) {
        "https://blockboomapi.amiraligrr.ir"
    }

}
