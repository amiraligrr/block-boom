package ir.amiraligrr.blockboom.data

import kotlin.math.roundToInt

/**
 * Store-independent product facts: what the player gets and what the card says.
 *
 * The SKU strings live in the flavor-specific [StoreConfig] objects. That keeps
 * one shared price/coin ladder while still letting Cafe Bazaar and Myket use
 * different product identifiers if their panels ever diverge.
 */
enum class ProductKey {
    PREMIUM,
    OFFER,
    COINS_SMALL,
    COINS_MEDIUM,
    COINS_LARGE,
    COINS_VERY_LARGE,
}

data class ProductSpec(
    val key: ProductKey,
    val coins: Long,
    val isPremium: Boolean,
    val priceRial: Long,
)

object ProductCatalog {
    val PREMIUM = ProductSpec(ProductKey.PREMIUM, 0L, true, 389_000L)
    val OFFER = ProductSpec(ProductKey.OFFER, 18_000L, false, 189_000L)
    val SMALL = ProductSpec(ProductKey.COINS_SMALL, 7_000L, false, 129_000L)
    val MEDIUM = ProductSpec(ProductKey.COINS_MEDIUM, 16_000L, false, 249_000L)
    val LARGE = ProductSpec(ProductKey.COINS_LARGE, 33_000L, false, 449_000L)
    val VERY_LARGE = ProductSpec(ProductKey.COINS_VERY_LARGE, 53_000L, false, 649_000L)

    val ALL: List<ProductSpec> = listOf(PREMIUM, OFFER, SMALL, MEDIUM, LARGE, VERY_LARGE)
}

/** A product as this store sells it: shared facts plus the store's SKU. */
data class StoreProduct(
    val key: ProductKey,
    val sku: String,
    val spec: ProductSpec,
) {
    val coins: Long get() = spec.coins
    val priceRial: Long get() = spec.priceRial
    val isPremium: Boolean get() = spec.isPremium

    /** Savings against the small pack, for the shop's value chips. */
    fun savingsPercent(): Int {
        val small = ProductCatalog.SMALL
        if (small.coins <= 0L || small.priceRial <= 0L) return 0
        val basePerCoin = small.priceRial.toDouble() / small.coins
        val minePerCoin = priceRial.toDouble() / coins.coerceAtLeast(1L)
        return ((1 - minePerCoin / basePerCoin) * 100).roundToInt()
    }
}
