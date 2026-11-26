package ir.amiraligrr.blockboom.data

/**
 * Cafe Bazaar store configuration.
 *
 * This is a separate application id from Myket, so it can have its own catalog
 * without touching the published Myket package. The product ids below are the
 * IDs supplied for this release; if the Bazaar panel uses different ids, change
 * only this file.
 *
 * The RSA key is the Bazaar purchase-verification key. It is public by design.
 * Do not append the HTML whitespace/entity that may come from a copied web page.
 */
object StoreConfig {
    const val STORE_ID = "bazaar"
    const val STORE_NAME = "کافه بازار"

    const val IAB_PUBLIC_KEY = "MIHNMA0GCSqGSIb3DQEBAQUAA4G7ADCBtwKBrwDDi7WBC3nngz/VxP6/gmSk5VPFuzIWXUmsEL2HEUtu4a2IA3dnMZInlPgEXkEiE25sgmMY17fvkCsY8b9isVxghGw8B2wvoVcb2s7WKNrwixWYpt5O3/fK8j37fpgIhsqmsIZPaOxXB71fCt/X0prtRT8UyJN8+7qS/Vvuz3vL7XyQNopixnjNAbMCxHD3tjBPCxl9nRDnKJsiB4FU131Mi6Pr5cXCq5hwUDKcsNMCAwEAAQ=="

    const val SKU_PREMIUM = "blockboom_premium_59"
    const val SKU_OFFER = "offer"
    const val SKU_COINS_SMALL = "blockboom_coins_small"
    const val SKU_COINS_MEDIUM = "blockboom_coins_medium"
    const val SKU_COINS_LARGE = "blockboom_coins_large"
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
