package ir.amiraligrr.blockboom.data

/**
 * Myket store configuration.
 *
 * This flavor deliberately keeps the published package `ir.amiraligrr.blockboom`
 * and the existing product ids. Changing either would orphan installed players
 * and purchases.
 */
object StoreConfig {
    const val STORE_ID = "myket"
    const val STORE_NAME = "مایکت"

    /** Myket RSA public key (public by design; never a private signing key). */
    const val IAB_PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCVj8/wpiSGLgw42AuGT+Yy5COPxwHalPDgSgaCMSOd8PBXz7jDHLbltY/fZcCshG6io7nSVG/rwcpuO+Qh3jC7hmjOo9AZ1z5K4o/67003Ni8GftNOGmQZ5nAJDVCN70EtOiX8RCNs0KSgjMC1n98szGFXJ3YwKW93/7Ui++OmzQIDAQAB"

    const val SKU_PREMIUM = "blockboom_premium_59"
    const val SKU_OFFER = "offer"
    const val SKU_COINS_SMALL = "blockboom_coins_small"
    const val SKU_COINS_MEDIUM = "blockboom_coins_medium"
    const val SKU_COINS_LARGE = "blockboom_coins_large"
    // The Myket panel id really does end with "larg" (without the final e).
    const val SKU_COINS_VERY_LARGE = "blockboom_coins_very_larg"

    val PREMIUM = StoreProduct(ProductKey.PREMIUM, SKU_PREMIUM, ProductCatalog.PREMIUM)
    val OFFER_PACK = StoreProduct(ProductKey.OFFER, SKU_OFFER, ProductCatalog.OFFER)
    val COIN_PACKS = listOf(
        StoreProduct(ProductKey.COINS_SMALL, SKU_COINS_SMALL, ProductCatalog.SMALL),
        StoreProduct(ProductKey.COINS_MEDIUM, SKU_COINS_MEDIUM, ProductCatalog.MEDIUM),
        StoreProduct(ProductKey.COINS_LARGE, SKU_COINS_LARGE, ProductCatalog.LARGE),
        StoreProduct(ProductKey.COINS_VERY_LARGE, SKU_COINS_VERY_LARGE, ProductCatalog.VERY_LARGE),
    )
    val ALL_PRODUCTS: List<StoreProduct> = listOf(PREMIUM) + COIN_PACKS + OFFER_PACK
    val ALL_PRODUCT_SKUS: List<String> = ALL_PRODUCTS.map { it.sku }
    val PREMIUM_PRICE_RIAL: Long = PREMIUM.priceRial

    fun productFor(sku: String): StoreProduct? = ALL_PRODUCTS.firstOrNull { it.sku == sku }

    fun coinPackFor(sku: String): StoreProduct? =
        COIN_PACKS.firstOrNull { it.sku == sku } ?: if (sku == SKU_OFFER) OFFER_PACK else null
}
