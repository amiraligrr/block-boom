package ir.amiraligrr.blockboom.data

import android.app.Activity

/**
 * The store-neutral billing contract.
 *
 * The actual implementation is supplied by the selected product flavor:
 * [BillingManager] in src/myket wraps the existing Myket IabHelper, and
 * [BillingManager] in src/bazaar wraps the Cafe Bazaar billing client.
 */
interface BillingProvider {
    fun connect()

    fun getProducts(): List<StoreProduct>

    fun purchase(
        activity: Activity,
        sku: String,
        onSuccess: () -> Unit = {},
        onCancelled: () -> Unit = {},
        onError: (String) -> Unit = {},
    )

    /** Consume a consumable by its store token. */
    fun consumePurchase(token: String, onResult: (Boolean) -> Unit)

    /**
     * Acknowledge a subscription/entitlement where the store SDK supports it.
     *
     * The Myket IabHelper used by this project has no separate acknowledge call;
     * its non-consumable entitlement is already owned. The Bazaar SDK likewise
     * delivers the purchase as owned. Returning true there is honest, not a
     * simulated success.
     */
    fun acknowledgePurchase(token: String, onResult: (Boolean) -> Unit)

    fun restorePurchases(onResult: (String) -> Unit)

    fun disconnect()
}
