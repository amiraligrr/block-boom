package ir.amiraligrr.blockboom

import ir.amiraligrr.blockboom.data.BillingProvider
import ir.amiraligrr.blockboom.data.BillingManager
import ir.amiraligrr.blockboom.data.ProductCatalog
import ir.amiraligrr.blockboom.data.ProductKey
import ir.amiraligrr.blockboom.data.StoreConfig
import ir.amiraligrr.blockboom.data.StoreProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Store-catalog invariants that must hold in BOTH flavor builds.
 *
 * This test source set is shared, so Gradle runs it once with the Myket
 * StoreConfig and once with the Bazaar StoreConfig. That is the cheapest way
 * to catch a flavor accidentally shipping another store's SKU map.
 */
class StoreCatalogTest {

    @Test
    fun `store id matches the generated build config`() {
        assertEquals(
            "StoreConfig and BuildConfig disagree about the selected store",
            BuildConfig.STORE_ID,
            StoreConfig.STORE_ID,
        )
    }

    @Test
    fun `the selected billing manager implements the shared contract`() {
        assertTrue(
            "BillingManager must implement BillingProvider",
            BillingProvider::class.java.isAssignableFrom(BillingManager::class.java),
        )
    }

    @Test
    fun `every product has a non-empty unique sku`() {
        val skus = StoreConfig.ALL_PRODUCTS.map { it.sku }
        assertEquals("all products must be present", 6, skus.size)
        assertTrue("no SKU may be blank", skus.all { it.isNotBlank() })
        assertEquals("SKUs must be unique", skus.size, skus.toSet().size)
    }

    @Test
    fun `premium is a subscription and is not a coin pack`() {
        val premium = StoreConfig.PREMIUM
        assertTrue(premium.isPremium)
        assertEquals(ProductKey.PREMIUM, premium.key)
        assertEquals(0L, premium.coins)
        assertEquals(389_000L, premium.priceRial)
        assertFalse("premium must not be treated as a consumable", StoreConfig.coinPackFor(premium.sku)?.isPremium ?: false)
    }

    @Test
    fun `coin packs keep their configured amounts`() {
        assertEquals(7_000L, StoreConfig.COIN_PACKS.first { it.key == ProductKey.COINS_SMALL }.coins)
        assertEquals(16_000L, StoreConfig.COIN_PACKS.first { it.key == ProductKey.COINS_MEDIUM }.coins)
        assertEquals(33_000L, StoreConfig.COIN_PACKS.first { it.key == ProductKey.COINS_LARGE }.coins)
        assertEquals(53_000L, StoreConfig.COIN_PACKS.first { it.key == ProductKey.COINS_VERY_LARGE }.coins)
        assertEquals(18_000L, StoreConfig.OFFER_PACK.coins)
    }

    @Test
    fun `product lookup is exact and never substitutes a sku`() {
        assertEquals(StoreConfig.SKU_PREMIUM, StoreConfig.productFor(StoreConfig.SKU_PREMIUM)?.sku)
        assertEquals(null, StoreConfig.productFor("not-a-real-sku"))
        assertEquals(
            "unknown SKUs must not be silently mapped to the offer",
            null,
            StoreConfig.coinPackFor("not-a-real-sku"),
        )
    }

    @Test
    fun `shared catalog has one spec per product key`() {
        val keys = ProductCatalog.ALL.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(ProductKey.entries.toSet(), keys.toSet())
        assertEquals(6, ProductCatalog.ALL.size)
    }
}
