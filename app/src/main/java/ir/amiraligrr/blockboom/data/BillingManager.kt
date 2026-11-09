package ir.amiraligrr.blockboom.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.amiraligrr.blockboom.BuildConfig
import ir.myket.billingclient.IabHelper
import ir.myket.billingclient.util.IabResult
import ir.myket.billingclient.util.Inventory
import ir.myket.billingclient.util.Purchase

/**
 * Myket in-app billing (IabHelper from the myket-billing-client library).
 *
 * - Connects on startup and immediately [queryInventoryAsync] so a non-consumed
 *   coin pack is consumed+delivered and premium is restored every launch.
 * - Coin packs are consumable (consume first, then credit coins).
 * - Premium is non-consumable and never consumed.
 */
class BillingManager(
    private val context: Context,
    private val save: SaveManager,
    private val livesManager: LivesManager,
) {
    companion object {
        private const val TAG = "BlockBoomBilling"

        /** One month (30 days) — premium auto-expires; read via SaveManager.isPremium. */
        const val PREMIUM_DURATION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }

    private var helper: IabHelper? = null

    var isReady by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    /**
     * Whether premium is active *right now*.
     *
     * ── Why this reads through instead of caching ─────────────────────────
     * It used to be `mutableStateOf(save.isPremium)` — a snapshot taken when the
     * object was created and only ever assigned `true` when a purchase landed.
     * Nothing ever set it back to false.
     *
     * `SaveManager.isPremium` is a *time comparison* against
     * `premiumUntilMillis`, so it becomes false on its own when the 30 days run
     * out. The cached copy did not. The shop gates its buy button on this value,
     * so the practical effect was: buy premium once, and the purchase button
     * never came back — for the rest of the session at minimum, and on any
     * screen that was built before the expiry. Premium became a one-off rather
     * than a renewal, which is the opposite of what it is sold as.
     *
     * Reading `save.isPremium` on each access keeps the UI honest at any moment
     * it happens to recompose, which is all this is used for. [ProgressState] is
     * still pushed on purchase so anything already composing refreshes too.
     */
    val isPremium: Boolean get() = save.isPremium

    /** Whether the buy button should be offered right now. */
    val canBuyPremium: Boolean get() = !save.isPremium

    private var isPurchasing by mutableStateOf(false)
        private set

    fun init() {
        val h = IabHelper(context, BuildConfig.IAB_PUBLIC_KEY)
        helper = h
        h.enableDebugLogging(BuildConfig.DEBUG)
        h.startSetup { result -> onSetupFinished(result) }
    }

    private fun onSetupFinished(result: IabResult) {
        val h = helper ?: return
        if (!result.isSuccess) {
            Log.w(TAG, "Setup failed: ${result.message}")
            errorMessage = result.message
            return
        }
        h.queryInventoryAsync(true, MyketConfig.ALL_PRODUCT_SKUS) { r, inventory ->
            if (!r.isSuccess) {
                Log.w(TAG, "Inventory query failed: ${r.message}")
                errorMessage = r.message
                isReady = true
                return@queryInventoryAsync
            }
            restoreExistingPurchases(inventory)
            isReady = true
        }
    }

    /** Delivers anything the user owned but never received (crash, lost connection…). */
    private fun restoreExistingPurchases(inventory: Inventory) {
        val owned = inventory.allPurchases ?: emptyList<Purchase>()
        for (purchase in owned) {
            when (purchase.sku) {
                MyketConfig.SKU_PREMIUM -> restorePremium(purchase)
                else -> {
                    val pack = MyketConfig.coinPackFor(purchase.sku) ?: continue
                    Log.d(TAG, "Restoring un-consumed ${purchase.sku} (${pack.coins} coins)")
                    consumeAndCredit(purchase)
                }
            }
        }
    }

    /**
     * Delivers a premium purchase that was paid for but never applied.
     *
     * ── Why this is keyed on the purchase token ───────────────────────────
     * This used to be a bare `grantPremium()`, and it ran on *every* billing
     * connection — which is every app launch, and every reconnect.
     *
     * Myket reports a non-consumable subscription as owned for as long as it is
     * entitled, and premium runs for a fixed 30 days and then expires. So the
     * sequence was: buy → 30 days → expire → next launch restores the same
     * purchase → another free 30 days → forever.
     *
     * Two things were wrong with that, and only one of them is about money. It
     * meant premium could never be sold twice to the same player, which is the
     * opposite of the intended model. And it meant a player's premium state was
     * not something they could ever lose, which quietly made the upgrade track
     * pointless — a reward you cannot forfeit is not a reward.
     *
     * The fix keys delivery on the *purchase token*, which is unique per
     * transaction:
     *
     *   - first purchase      → new token → grant (this is the one that matters)
     *   - launch after expiry → same token → already delivered → do nothing
     *   - buy again later     → a different token → grant again
     *
     * So premium is granted exactly once per payment, is genuinely lost on
     * expiry, and can be bought again afterwards. It also cannot stack: a
     * restore never calls [grantPremium], so it can never extend an active
     * subscription.
     */
    private fun restorePremium(purchase: Purchase) {
        val token = runCatching { purchase.token }.getOrNull()
        if (token.isNullOrBlank()) {
            Log.w(TAG, "premium purchase has no token; not restoring")
            return
        }
        if (save.deliveredPremiumPurchaseToken == token) {
            Log.d(TAG, "premium already delivered for this purchase; not re-granting")
            return
        }
        grantPremium(token)
        Log.i(TAG, "premium restored from a previously unpaid purchase")
    }

    fun purchase(
        activity: Activity,
        sku: String,
        onSuccess: () -> Unit = {},
        onCancelled: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        val h = helper
        if (h == null) {
            val message = "اتصال به فروشگاه برقرار نشد"
            errorMessage = message
            onError(message)
            return
        }
        if (!isReady) {
            val message = "فروشگاه در حال آماده‌سازی است، کمی بعد تلاش کنید"
            errorMessage = message
            onError(message)
            return
        }
        if (isPurchasing) {
            val message = "در حال پردازش خرید قبلی، لطفاً صبر کنید"
            errorMessage = message
            onError(message)
            return
        }
        isPurchasing = true
        val payload = "bb-" + System.currentTimeMillis() + "-" + (Math.random() * 1_000_000).toInt()
        h.launchPurchaseFlow(activity, sku, { result, purchase ->
            isPurchasing = false
            if (result.isFailure || purchase == null) {
                Log.d(TAG, "Purchase failed/cancelled: ${result.message}")
                onCancelled()
                return@launchPurchaseFlow
            }
            when (purchase.sku) {
                MyketConfig.SKU_PREMIUM -> {
                    grantPremium(runCatching { purchase.token }.getOrNull())
                    onSuccess()
                }
                else -> consumeAndCredit(purchase, onSuccess, onError)
            }
        }, payload)
    }

    /** Credits coins only after Myket confirms the consumable was consumed. */
    private fun consumeAndCredit(
        purchase: Purchase,
        onConsumed: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        val pack = MyketConfig.coinPackFor(purchase.sku)
        if (pack == null) {
            val message = "محصول ناشناخته: ${purchase.sku}"
            Log.w(TAG, message)
            errorMessage = message
            onError(message)
            isPurchasing = false
            return
        }
        val h = helper
        if (h == null) {
            val message = "اتصال به فروشگاه برقرار نشد"
            errorMessage = message
            onError(message)
            isPurchasing = false
            return
        }
        h.consumeAsync(purchase) { _, result ->
            if (result.isSuccess) {
                Log.d(TAG, "Consumed ${purchase.sku}, crediting ${pack.coins} coins")
                save.addCoins(pack.coins)
                ProgressState.sync(save)
                onConsumed()
            } else {
                Log.w(TAG, "Consume failed: ${result.message}")
                val message = "اعمال خرید ناموفق بود"
                errorMessage = message
                onError(message)
            }
            isPurchasing = false
        }
    }

    /**
     * Applies a *paid* premium purchase.
     *
     * Records the purchase token before granting, so a restore can never re-apply
     * the same transaction — see [restorePremium] for why that was granting
     * premium for free on every launch after it expired.
     */
    private fun grantPremium(purchaseToken: String? = null) {
        if (!purchaseToken.isNullOrBlank()) {
            save.deliveredPremiumPurchaseToken = purchaseToken
        }
        save.grantPremium()
        // ProgressState is the snapshot other screens observe; [isPremium] itself
        // now reads through to the save, so it needs no assignment.
        ProgressState.sync(save)
        livesManager.grantLives(LivesManager.MAX_LIVES)
    }

    /**
     * Asks Myket again for everything this account owns, and re-delivers it.
     *
     * ── Why this is a button and not just the launch path ─────────────────
     * [onSetupFinished] already re-queries on every launch, which is what makes
     * a normal purchase restore itself. But that is the *silent* path, and when
     * it fails there was nothing the player could do about it: no button, no
     * message, nothing but a reinstall or a support ticket. Someone who has
     * already paid is exactly the person who must not be left without a way to
     * claim what they bought.
     *
     * @param onResult called on the billing thread with a sentence to show.
     */
    fun restorePurchases(onResult: (String) -> Unit) {
        val h = helper
        if (h == null || !isReady) {
            onResult("اتصال به فروشگاه برقرار نشد. کمی بعد دوباره امتحان کن.")
            return
        }
        h.queryInventoryAsync(true, MyketConfig.ALL_PRODUCT_SKUS) { r, inventory ->
            if (!r.isSuccess) {
                Log.w(TAG, "Restore query failed: ${r.message}")
                onResult("بازیابی خریدها ناموفق بود: ${r.message}")
                return@queryInventoryAsync
            }
            restoreExistingPurchases(inventory)
            onResult(
                if (save.isPremium) "پریمیوم بازیابی شد ✓"
                else "خرید قبلی برای این حساب پیدا نشد"
            )
        }
    }

    fun dispose() {
        helper?.dispose()
        helper = null
    }
}