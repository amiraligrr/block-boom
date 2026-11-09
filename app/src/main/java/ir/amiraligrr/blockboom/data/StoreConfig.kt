package ir.amiraligrr.blockboom.data

import kotlin.math.roundToInt

/** Adivery ad placements (from the Adivery publisher dashboard). */
object AdiveryConfig {
    const val APP_ID = "a9d23a84-8f06-4dcd-a5fb-674cbcd0524a"
    const val BANNER_PLACEMENT_ID = "94cf59ab-0dd8-4ebc-abeb-f632af1db761"
    const val REWARDED_PLACEMENT_ID = "cbdc6667-3e38-478a-9f1a-537f046432e9"

    /**
     * Interstitial placement.
     *
     * Left blank on purpose: it has to be created in the Adivery dashboard
     * first, and guessing an id would just produce silent no-fills. Until it is
     * filled in, [AdManager] treats every interstitial as unavailable and the
     * game behaves exactly as before.
     */
    const val INTERSTITIAL_PLACEMENT_ID = ""
}

/** Myket in-app billing products (from the Myket developer panel). */
object MyketConfig {
    const val SKU_PREMIUM = "blockboom_premium_59"

    const val SKU_COINS_SMALL = "blockboom_coins_small"
    const val SKU_COINS_MEDIUM = "blockboom_coins_medium"
    const val SKU_COINS_LARGE = "blockboom_coins_large"
    const val SKU_COINS_VERY_LARGE = "blockboom_coins_very_larg" // panel typo is intentional
    const val SKU_OFFER = "offer" // پیشنهاد ویژه ۱۴0۵/۰۷/۰۳ — ۲۹۰,۰۰۰ ریال

    // One step down from the launch ladder. The Myket panel is the source of
    // truth for what the store actually charges; these constants are what the
    // app puts on the card (and what the purchase ledger records), so the panel
    // and this file must be changed together. The SKU names still say _59 for
    // premium because changing a product id would orphan every purchase that
    // already points at it.
    const val PREMIUM_PRICE_RIAL = 389_000L
    const val OFFER_PRICE_RIAL = 189_000L

    // 18,000, unchanged. See the ladder note below for why this one is the fixed
    // point the other four are built around.
    const val OFFER_COINS = 18_000L

    data class CoinPack(val sku: String, val coins: Long, val priceRial: Long) {
        /** Savings compared to buying the same amount via the small pack. */
        fun savingsPercent(): Int {
            val small = COIN_PACKS.first()
            if (small.coins <= 0 || small.priceRial <= 0) return 0
            val basePerCoin = small.priceRial.toDouble() / small.coins
            val minePerCoin = priceRial.toDouble() / coins
            return ((1 - minePerCoin / basePerCoin) * 100).roundToInt()
        }
    }

    /**
     * The coin ladder.
     *
     * The Myket panel is the real source of truth for what a purchase costs. The
     * prices below are the display/ledger copy and must match the panel; the
     * coin amounts are the other half of the ladder and live only here.
     *
     * ── What was wrong ─────────────────────────────────────────────────────
     * Measured coins per 10,000 rial spent:
     *
     *     small    263      large   362
     *     medium   321      xl      404
     *     offer    621
     *
     * The featured "offer" was worth **2.4x** the entry pack. That is not a good
     * deal to feature, it is a signal that the small pack is a mistake — and the
     * only rational player response is to never buy it, which means the cheapest
     * entry point to the game costs 18,000 coins instead of 5,000. Everything
     * below the offer was decoration.
     *
     * ── How the ladder is cut now ──────────────────────────────────────────
     * The offer's rate is held as the anchor and the four graded packs are lifted
     * toward it, so savings against the small pack run 0 / 10 / 23 / 31 / 41
     * percent instead of 0 / 18 / 27 / 35 / 58. The biggest purchase should be
     * meaningfully the best value, and the offer should be genuinely worth
     * redirecting to — but no pack should be a trap.
     *
     * No existing pack was reduced. Raising the value of the dominated packs
     * fixes the ladder without taking coins away from anyone who has already
     * paid, which is the only safe direction for an IAP change.
     *
     * Sized against the 1.8x upgrade pricing: a full six-track build costs about
     * 290,000 coins, so the largest pack covers roughly 18% of one, or about 42
     * levels of play at the current late-game income of ~1,275 coins per level.
     */
    val COIN_PACKS = listOf(
        CoinPack(SKU_COINS_SMALL, 7_000L, 129_000L),
        CoinPack(SKU_COINS_MEDIUM, 16_000L, 249_000L),
        CoinPack(SKU_COINS_LARGE, 33_000L, 449_000L),
        CoinPack(SKU_COINS_VERY_LARGE, 53_000L, 649_000L),
    )

    val OFFER_PACK = CoinPack(SKU_OFFER, OFFER_COINS, OFFER_PRICE_RIAL)

    val ALL_PRODUCT_SKUS: List<String> =
        listOf(SKU_PREMIUM, SKU_OFFER) + COIN_PACKS.map { it.sku }

    fun coinPackFor(sku: String): CoinPack? =
        COIN_PACKS.firstOrNull { it.sku == sku } ?: if (sku == SKU_OFFER) OFFER_PACK else null
}