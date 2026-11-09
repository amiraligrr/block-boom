package ir.amiraligrr.blockboom.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Server-side rewards: coin grants, referral codes, announcements.
 *
 * ── Why this file exists at all ────────────────────────────────────────────
 * The server has always had a complete reward pipeline —
 * `coin_grants` + `claim_coins.php` for anything the server decided a player
 * earned (a verified Myket coin purchase, a redeemed referral code, a manual
 * admin grant), plus `referral.php` and `fetch_notifications.php`. None of it
 * was ever called by the app. The result was the worst kind of bug this project
 * could have: a player paid 190,000 Rial for 5,000 coins, an operator verified
 * the receipt in the panel, `verify_purchase.php` wrote the `coin_grants` row
 * and marked the sale complete in the revenue report — and the player received
 * nothing, silently, forever. Real money taken, nothing delivered.
 *
 * Coins are a client-local currency (the server holds no balance, only claims),
 * so this class is the delivery mechanism that closes that loop.
 *
 * Everything here is best-effort and offline-tolerant. Coins are a bonus, never
 * a gate: [RewardService.claimPendingCoins] failing means the grant stays
 * unclaimed in the database and is retried next launch, which is exactly the
 * behaviour the transactional `claim_coins.php` is built for.
 */
object RewardService {

    private const val TAG = "RewardService"

    // claim_coins does a row lock and pays up to 50 grants in one transaction.
    // Long enough for a shared host on a mobile connection, short enough that a
    // hung socket does not stall the app's startup work.
    private const val CLAIM_TIMEOUT_MS = 8000L

    // ── Coins ──────────────────────────────────────────────────────────────

    /**
     * Collects every coin grant the server is holding for this device.
     *
     * @return the number of coins actually credited, or 0 when there was
     *         nothing to collect or the request could not complete. Callers must
     *         treat 0 as "no news" rather than "failed": there is genuinely no
     *         way to tell those apart, and neither is worth interrupting a
     *         player for.
     */
    suspend fun claimPendingCoins(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("device_id", LeaderboardManager.getDeviceId(context))
            }
            val resp = HttpJson.postJson(
                "${LeaderboardManager.apiBase()}/claim_coins.php",
                body,
                connectMs = CLAIM_TIMEOUT_MS.toInt(),
                readMs = CLAIM_TIMEOUT_MS.toInt()
            ) ?: return@withContext 0

            if (!resp.optBoolean("ok", false)) {
                Log.i(TAG, "claim_coins rejected: ${resp.optString("error")}")
                return@withContext 0
            }
            val coins = resp.optInt("coins", 0)
            if (coins > 0) {
                Log.i(TAG, "collected $coins coins from ${resp.optInt("count")} grants")
            }
            coins
        } catch (t: Throwable) {
            Log.i(TAG, "claim_coins failed, grants stay pending: ${t.message}")
            0
        }
    }

    /**
     * Coins the server is holding for this device that have not been collected
     * yet. Safe to call before [claimPendingCoins] to show a "you have a
     * pending reward" hint, but it is a second request, so only call it when
     * the player is actually looking at something that would show the hint.
     */
    suspend fun pendingCoins(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            val resp = referralCall(context, "status") ?: return@withContext 0
            resp.optInt("pending_coins", 0)
        } catch (t: Throwable) {
            Log.i(TAG, "pendingCoins failed: ${t.message}")
            0
        }
    }

    // ── Referral ───────────────────────────────────────────────────────────

    data class ReferralStatus(
        val code: String,
        val coinsRedeemedToday: Int,
        val pendingCoins: Int,
        val pendingCount: Int,
        val totalCashedOut: Int
    )

    /**
     * Fetches (or lazily mints) this device's referral code and reports what is
     * still uncollected. The server reuses one code per device forever, so
     * calling this is also how a code comes into existence.
     */
    suspend fun referralStatus(context: Context): ReferralStatus? = withContext(Dispatchers.IO) {
        try {
            val status = referralCall(context, "status") ?: return@withContext null
            val codes = status.optJSONArray("codes") ?: JSONArray()
            // Which of this device's codes is still worth sharing.
            //
            // The server keeps every code this device has touched, and the
            // `used` flag on each is always true — `used_by` is written both
            // when a code is *minted* for you and when one is *redeemed*, so
            // "used" alone cannot tell your own shareable code from one you
            // have already cashed in. Reading `shareable` (which the server
            // derives from `coins_awarded`) is the honest signal; falling back
            // to the first code keeps a pre-fix server usable rather than
            // showing the player an empty screen.
            var code = ""
            for (i in 0 until codes.length()) {
                val c = codes.optJSONObject(i) ?: continue
                val shareable =
                    c.optBoolean("shareable", c.optBoolean("used", false).not())
                if (shareable) {
                    code = c.optString("code", "")
                    if (code.isNotBlank()) break
                }
            }
            if (code.isBlank()) {
                code = referralCall(context, "generate")?.optString("code", "").orEmpty()
            }
            if (code.isBlank()) return@withContext null

            ReferralStatus(
                code = code,
                coinsRedeemedToday = status.optInt("redeemed_today", 0),
                pendingCoins = status.optInt("pending_coins", 0),
                pendingCount = status.optInt("pending_count", 0),
                totalCashedOut = status.optInt("claimed_total", 0)
            )
        } catch (t: Throwable) {
            Log.i(TAG, "referralStatus failed: ${t.message}")
            null
        }
    }

    /**
     * Redeems a friend's code. Returns the coins awarded on success, or null
     * when the code was rejected or the request did not complete — the server
     * distinguishes those with distinct error strings, so the UI can explain
     * "already used" rather than shrugging.
     */
    suspend fun redeemReferral(context: Context, code: String): Result<Int> =
        withContext(Dispatchers.IO) {
            val clean = code.uppercase().filter { it.isLetterOrDigit() }
            if (clean.length != 8) {
                return@withContext Result.failure(IllegalArgumentException("bad_format"))
            }
            try {
                val resp = referralCall(context, "use", mapOf("code" to clean))
                    ?: return@withContext Result.failure(Exception("offline"))
                if (!resp.optBoolean("ok", false)) {
                    return@withContext Result.failure(Exception(
                        resp.optString("error", "rejected").lowercase()
                    ))
                }
                Result.success(resp.optInt("coins", 0))
            } catch (t: Throwable) {
                Log.i(TAG, "referral redeem failed: ${t.message}")
                Result.failure(Exception("offline"))
            }
        }

    private suspend fun referralCall(
        context: Context,
        action: String,
        extra: Map<String, String> = emptyMap()
    ): JSONObject? {
        val body = JSONObject().apply {
            put("device_id", LeaderboardManager.getDeviceId(context))
            put("action", action)
            extra.forEach { (k, v) -> put(k, v) }
        }
        return HttpJson.postJson(
            "${LeaderboardManager.apiBase()}/referral.php",
            body,
            connectMs = 7000,
            readMs = 7000
        )
    }

    // ── Notifications ──────────────────────────────────────────────────────

    data class Announcement(
        val id: Int,
        val title: String,
        val message: String,
        val createdAt: String
    )

    /**
     * Fetches announcements for this device, optionally acknowledging them.
     *
     * Acknowledging writes a per-device receipt (`notification_reads`), so an
     * announcement addressed to everyone is still delivered to every player
     * instead of being consumed by whoever happened to open the app first.
     */
    suspend fun fetchAnnouncements(
        context: Context,
        markRead: Boolean = false
    ): List<Announcement> = withContext(Dispatchers.IO) {
        try {
            val did = LeaderboardManager.getDeviceId(context)
            val url = "${LeaderboardManager.apiBase()}/fetch_notifications.php" +
                "?device_id=$did&mark_read=${if (markRead) 1 else 0}"
            val (_, resp) = HttpJson.getJson(url) ?: return@withContext emptyList()
            val arr = resp.optJSONArray("notifications") ?: JSONArray()
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        Announcement(
                            id = o.optInt("id"),
                            title = o.optString("title", ""),
                            message = o.optString("message", ""),
                            createdAt = o.optString("created_at", "")
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            Log.i(TAG, "fetchAnnouncements failed: ${t.message}")
            emptyList()
        }
    }
}
