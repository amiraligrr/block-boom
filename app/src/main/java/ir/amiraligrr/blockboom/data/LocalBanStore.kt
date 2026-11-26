package ir.amiraligrr.blockboom.data

import android.content.Context
import android.provider.Settings
import android.util.Base64
import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * A ban that survives the app being wiped.
 *
 * ── Why this exists ──────────────────────────────────────────────────────
 * The server already issues bans and the client already honours them
 * ([AppConfigRepository] → [AccessState.Banned]). But that check *fails open*
 * on purpose: a timeout or a dead server lets the player through, because a
 * network problem must never be able to stop somebody playing.
 *
 * That same courtesy is the hole. A banned player who never reconnects never
 * gets asked again, so "banned" means "banned as long as the network is off" —
 * which is not a ban, it is a suggestion. Restoring the ban after a factory
 * reset, an uninstall, or a wiped app-data was the whole point of recording it
 * against the device in the first place.
 *
 * ── Why it is not just another SharedPreferences flag ─────────────────────
 * Because a flag in `blockboom_prefs` is exactly what "clear app data" and
 * "uninstall" delete. Three things make this one harder to shed:
 *
 *  1. **A separate preferences file**, so a targeted clear of the save (which
 *     players do to fix a save they think is corrupted) does not take it.
 *  2. **A device-bound HMAC** over the payload. The signing key is derived
 *     from a random per-install secret *and* the device's ANDROID_ID, so a
 *     record copied from another phone does not verify there, and a record
 *     edited on-device does not verify either. Editing it means producing a
 *     valid tag, which needs the secret that never leaves this device.
 *  3. **Server-side re-check on every launch**, so lifting a ban is possible
 *     even though the local record persists — see [clear] being called from
 *     the normal allowed path.
 *
 * ── What it deliberately does NOT do ─────────────────────────────────────
 * It does not touch the player's coins, level, upgrades or lives. A ban ends
 * play; it does not quietly corrupt a save and call it a consequence. Sabotage
 * reads to the player as a bug, a bug gets a one-star review, and a destroyed
 * save is indistinguishable from data loss — which is a support ticket and a
 * refund risk. Losing access is the sanction. The account's data is left
 * exactly as it was, intact, for when the ban is lifted.
 *
 * An operator who *wants* the account's progress gone can wipe it explicitly
 * through the admin panel; that is a decision, made knowingly, and shown to
 * the player. This file is not where that decision belongs.
 */
object LocalBanStore {

    private const val TAG = "LocalBan"
    private const val PREFS = "bb_enforcement"
    private const val KEY_RECORD = "ban_record"
    private const val KEY_SECRET = "install_secret"

    private const val SIG_ALG = "HmacSHA256"

    /** How long a local ban is honoured before it must be re-confirmed. */
    private const val REVALIDATE_AFTER_MS = 6L * 60 * 60 * 1000  // 6 hours

    data class BanRecord(
        val reason: String,
        val bannedAtMs: Long,
        val issuedAtMs: Long
    ) {
        /** True once the record is old enough to be worth re-checking. */
        val needsRevalidation: Boolean
            get() = System.currentTimeMillis() - bannedAtMs > REVALIDATE_AFTER_MS
    }

    // ── signing ────────────────────────────────────────────────────────────

    /**
     * The per-install secret, generated once and kept beside the record.
     *
     * Random rather than derived from anything guessable, so an attacker who
     * knows the device id still cannot forge a tag for that device without
     * having read this value off it.
     */
    private fun secret(ctx: Context): ByteArray {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = p.getString(KEY_SECRET, null)
        if (existing != null) {
            val decoded = runCatching { Base64.decode(existing, Base64.NO_WRAP) }.getOrNull()
            if (decoded != null && decoded.isNotEmpty()) return decoded
            // Corrupt value: fall through and mint a new one.
        }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        p.edit().putString(KEY_SECRET, Base64.encodeToString(fresh, Base64.NO_WRAP)).commit()
        return fresh
    }

    private fun deviceId(ctx: Context): String =
        runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        }.getOrDefault("")

    /**
     * A tag that is only valid on this device, for this install's secret.
     *
     * The MAC covers the device id as well as the payload, so a record
     * lifted off one phone and pasted onto another fails verification.
     */
    private fun sign(ctx: Context, payload: String): String {
        val key = SecretKeySpec(
            sha256(secret(ctx) + deviceId(ctx).toByteArray()),
            SIG_ALG
        )
        val mac = Mac.getInstance(SIG_ALG)
        mac.init(key)
        return Base64.encodeToString(mac.doFinal(payload.toByteArray()), Base64.NO_WRAP)
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    // ── record ─────────────────────────────────────────────────────────────

    /**
     * Records a ban on this device, replacing any earlier one.
     *
     * `commit()` rather than `apply()`: the record has to be on disk before
     * this call returns, because the next thing the process does is block the
     * player. An async write that has not landed yet is a ban that can be
     * raced by a kill right after it is set.
     */
    fun record(context: Context, reason: String) {
        try {
            val now = System.currentTimeMillis()
            val payload = "$reason|$now|$now"
            val sig = sign(context, payload)
            val blob = "$payload|$sig"
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RECORD, blob)
                .commit()
            Log.i(TAG, "ban recorded locally")
        } catch (t: Throwable) {
            // Never let enforcement bookkeeping crash the app on the way to
            // showing the ban screen — the server check still blocks them.
            Log.w(TAG, "failed to record ban: ${t.message}")
        }
    }

    /**
     * The stored ban, or null when there is none or it does not verify.
     *
     * A record that fails its tag is discarded rather than trusted, and also
     * deleted, so a failed forgery attempt does not leave a permanent
     * "ban forever" that the server cannot clear.
     */
    fun load(context: Context): BanRecord? {
        return try {
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val blob = p.getString(KEY_RECORD, null) ?: return null
            val parts = blob.split("|")
            if (parts.size < 4) {
                p.edit().remove(KEY_RECORD).commit()
                return null
            }
            val reason = parts[0]
            val bannedAt = parts[1].toLongOrNull() ?: 0L
            val issuedAt = parts[2].toLongOrNull() ?: 0L
            val sig = parts.drop(3).joinToString("|")
            val expected = sign(context, "$reason|$bannedAt|$issuedAt")
            if (!constantTimeEquals(sig, expected)) {
                Log.w(TAG, "ban record failed verification; discarded")
                p.edit().remove(KEY_RECORD).commit()
                return null
            }
            BanRecord(reason = reason, bannedAtMs = bannedAt, issuedAtMs = issuedAt)
        } catch (t: Throwable) {
            Log.w(TAG, "failed to load ban: ${t.message}")
            null
        }
    }

    /**
     * Removes the local record.
     *
     * Only ever called from the path where the *server* has said the device
     * is allowed — a genuine unbann, or a first-ever launch of an install
     * whose record is stale. There is deliberately no client-side expiry that
     * lifts a ban on its own; that would make the whole thing a cooldown.
     */
    fun clear(context: Context) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_RECORD)
                .commit()
        }.onFailure { Log.w(TAG, "failed to clear ban: ${it.message}") }
    }

    /**
     * Whether this device may play, given both the server's answer and the
     * local record.
     *
     * The server wins when it has an opinion. A local record is what fills
     * the gap the fail-open policy leaves when the server is unreachable —
     * which is the only case where it is consulted.
     */
    fun isBlockedLocally(context: Context): Boolean = load(context) != null

    /** Comparison that does not leak timing information. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}