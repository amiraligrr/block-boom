package ir.amiraligrr.blockboom.ui

/**
 * Thousands-separated integers, grouped by 3 from the right.
 *
 * Deliberately not `String.format("%,d")`: that applies the *default* locale's
 * grouping, which on Android is not guaranteed to be the app's locale. The app
 * ships a Persian and an English UI, so the same call site would group one way
 * in Persian and another in English — and the separator itself changes shape
 * (٬ vs ,), which is exactly the sort of inconsistency that reads as
 * "unfinished" in a shop where the player is about to spend money.
 *
 * Hand-rolled so the output is identical in every locale. Pure ASCII grouping
 * on purpose: the Persian thousands separator is a legitimate design choice,
 * but silently swapping it per-locale on a price is worse than one stable form.
 */
fun formatNumber(value: Long): String {
    val s = if (value < 0) 0L else value
    val str = s.toString()
    if (str.length <= 3) return str
    return str.reversed().chunked(3).joinToString(",").reversed()
}

fun formatNumber(value: Int): String = formatNumber(value.toLong())

/** Rial to the toman the price is actually shown in. */
fun rialToToman(rial: Long): String = formatNumber(rial / 10)
