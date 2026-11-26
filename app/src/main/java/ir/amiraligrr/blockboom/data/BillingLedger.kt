package ir.amiraligrr.blockboom.data

import android.content.Context

/**
 * Local idempotency ledger for store purchase tokens.
 *
 * The store inventory can replay a purchase after a crash or a reconnect. The
 * token is the one value that stays stable across that replay, so it is the
 * right key for "this transaction has already been delivered". The server-side
 * ledger is still authoritative for revenue; this only prevents the local
 * currency from being paid twice while the purchase is being consumed.
 */
object BillingLedger {
    private const val PREFS = "bb_billing_ledger"
    private const val KEY = "delivered_tokens"
    private const val MAX_TOKENS = 200

    @Synchronized
    fun isDelivered(context: Context, token: String): Boolean {
        if (token.isBlank()) return false
        return prefs(context).getStringSet(KEY, emptySet()).orEmpty().contains(token)
    }

    /** Returns true only when this call added the token (not a replay). */
    @Synchronized
    fun markDelivered(context: Context, token: String): Boolean {
        if (token.isBlank()) return false
        val prefs = prefs(context)
        val current = prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        if (!current.add(token)) return false
        if (current.size > MAX_TOKENS) {
            // Keep the newest tokens. There is no timestamp in a StringSet, so
            // this is a bounded set rather than a perfect LRU; 200 transactions
            // is far more than a player can generate in a session.
            val trimmed = current.toList().takeLast(MAX_TOKENS).toSet()
            prefs.edit().putStringSet(KEY, trimmed).apply()
        } else {
            prefs.edit().putStringSet(KEY, current).apply()
        }
        return true
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
