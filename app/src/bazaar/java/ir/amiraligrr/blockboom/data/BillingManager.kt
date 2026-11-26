package ir.amiraligrr.blockboom.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultRegistry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farasource.billing.BillingClient
import com.farasource.billing.BillingHelper
import com.farasource.billing.communication.OnBillingResultListener
import com.farasource.billing.util.Inventory
import com.farasource.billing.util.Purchase
import com.farasource.billing.util.TableCodes

/**
 * Cafe Bazaar billing.
 *
 * The Bazaar client is built on ActivityResult, so the registry is attached by
 * MainActivity before the shop is opened. The application-level manager can be
 * created earlier; [init] simply waits for the registry.
 *
 * The Bazaar library exposes the same IabHelper-style model as Myket:
 * launchBilling(sku, type, payload, autoConsume) and consume(purchase). There is
 * no separate acknowledge call in this SDK; non-consumable entitlements are
 * owned by the store. We therefore do not invent one.
 */
class BillingManager(
    private val context: Context,
    private val save: SaveManager,
    private val livesManager: LivesManager,
) : BillingProvider {

    companion object {
        private const val TAG = "BlockBoomBazaarBilling"
        const val PREMIUM_DURATION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }

    var isReady by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    val isPremium: Boolean get() = save.isPremium
    val canBuyPremium: Boolean get() = !save.isPremium

    private var client: BillingClient? = null
    private var registry: ActivityResultRegistry? = null
    private var wanted = false
    private var purchasing = false
    private var lastInventory: Inventory? = null

    private val pendingPurchases = mutableMapOf<String, Purchase>()
    private var pendingRequest: PurchaseRequest? = null
    private var pendingRestore: ((String) -> Unit)? = null
    private val consumeCallbacks = mutableMapOf<String, ConsumeCallback>()

    private val listener = object : OnBillingResultListener {
        override fun onBillingSuccess(purchase: Purchase) {
            val request = pendingRequest
            pendingRequest = null
            purchasing = false
            val token = purchase.token.orEmpty()
            if (token.isBlank() || !verify(purchase)) {
                val message = "تایید خرید بازار ناموفق بود"
                errorMessage = message
                request?.onError?.invoke(message)
                return
            }
            pendingPurchases[token] = purchase
            val product = StoreConfig.productFor(purchase.sku)
            if (product?.isPremium == true) {
                grantPremium(token)
                request?.onSuccess?.invoke()
            } else {
                consumeAndCredit(
                    purchase,
                    onConsumed = { request?.onSuccess?.invoke() },
                    onError = { message ->
                        errorMessage = message
                        request?.onError?.invoke(message)
                    },
                )
            }
        }

        override fun onConsumeFinished(purchase: Purchase, success: Boolean) {
            val token = purchase.token.orEmpty()
            val callback = consumeCallbacks.remove(token)
            if (success) {
                val product = StoreConfig.coinPackFor(purchase.sku)
                if (product != null) {
                    val ledgerKey = "${StoreConfig.STORE_ID}:$token"
                    if (!BillingLedger.isDelivered(context, ledgerKey)) {
                        save.addCoins(product.coins)
                        BillingLedger.markDelivered(context, ledgerKey)
                        ProgressState.sync(save)
                    } else {
                        Log.d(TAG, "purchase token already delivered; not double-crediting")
                    }
                }
                callback?.onSuccess?.invoke()
            } else {
                val message = "اعمال خرید ناموفق بود"
                errorMessage = message
                callback?.onError?.invoke(message)
            }
        }

        override fun onBillingStatus(code: Int) {
            when (code) {
                TableCodes.SETUP_SUCCESS -> {
                    isReady = true
                    errorMessage = null
                    Log.i(TAG, "Bazaar billing ready")
                }
                TableCodes.BILLING_IS_IN_PROGRESS -> {
                    // Not fatal; the existing purchase flow is still active.
                }
                TableCodes.MARKET_NOT_INSTALLED -> fail("بازار روی این دستگاه نصب نیست")
                TableCodes.NO_NETWORK -> fail("اینترنت وصل نیست")
                TableCodes.SETUP_FAILED -> fail("اتصال به بازار برقرار نشد")
                TableCodes.BILLING_FAILED -> {
                    val request = pendingRequest
                    pendingRequest = null
                    purchasing = false
                    request?.onCancelled?.invoke()
                }
                TableCodes.SUBSCRIPTIONS_NOT_SUPPORTED -> fail("خرید اشتراک در این نسخهٔ بازار پشتیبانی نمی‌شود")
                else -> {
                    if (!isReady) fail("فروشگاه بازار آماده نشد ($code)")
                }
            }
        }

        override fun onQueryInventoryFinished(inventory: Inventory) {
            lastInventory = inventory
            restoreExistingPurchases(inventory)
            pendingRestore?.let {
                pendingRestore = null
                it(if (save.isPremium) "پریمیوم بازیابی شد ✓" else "خرید قبلی برای این حساب پیدا نشد")
            }
        }
    }

    fun attachActivityResultRegistry(registry: ActivityResultRegistry) {
        this.registry = registry
        if (client != null) {
            client?.rebuildActivityResultRegistry(registry)
        } else if (wanted) {
            connectInternal()
        }
    }

    fun init() {
        wanted = true
        connectInternal()
    }

    private fun connectInternal() {
        if (client != null) return
        val reg = registry ?: return
        val c = BillingClient(reg, context)
        c.setGlobalAutoConsume(false)
        client = c
        c.setOnBillingResultListener(listener)
    }

    override fun connect() = init()

    override fun getProducts(): List<StoreProduct> = StoreConfig.ALL_PRODUCTS

    override fun purchase(
        activity: Activity,
        sku: String,
        onSuccess: () -> Unit,
        onCancelled: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val c = client
        if (c == null || !isReady) {
            val message = "فروشگاه بازار آماده نیست، کمی بعد تلاش کن"
            errorMessage = message
            onError(message)
            return
        }
        if (purchasing) {
            onError("در حال پردازش خرید قبلی، لطفاً صبر کن")
            return
        }
        val product = StoreConfig.productFor(sku)
        if (product == null) {
            onError("محصول ناشناخته")
            return
        }
        purchasing = true
        pendingRequest = PurchaseRequest(onSuccess, onCancelled, onError)
        val type = if (product.isPremium) BillingHelper.ITEM_TYPE_SUBS else BillingHelper.ITEM_TYPE_INAPP
        val payload = "bb-${System.currentTimeMillis()}-${(Math.random() * 1_000_000).toInt()}"
        runCatching {
            c.launchBilling(sku, type, payload, false)
        }.onFailure {
            purchasing = false
            pendingRequest = null
            val message = "شروع خرید بازار ناموفق بود"
            errorMessage = message
            onError(message)
        }
    }

    private fun consumeAndCredit(
        purchase: Purchase,
        onConsumed: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val token = purchase.token.orEmpty()
        if (token.isBlank()) {
            onError("شناسهٔ خرید بازار خالی است")
            return
        }
        val c = client
        if (c == null) {
            onError("اتصال به بازار برقرار نشد")
            return
        }
        consumeCallbacks[token] = ConsumeCallback(onConsumed, onError)
        pendingPurchases[token] = purchase
        runCatching { c.consume(purchase) }.onFailure {
            consumeCallbacks.remove(token)
            onError("مصرف خرید ناموفق بود")
        }
    }

    override fun consumePurchase(token: String, onResult: (Boolean) -> Unit) {
        val purchase = pendingPurchases[token]
        if (purchase == null) {
            onResult(false)
            return
        }
        consumeAndCredit(purchase, onConsumed = { onResult(true) }, onError = { onResult(false) })
    }

    override fun acknowledgePurchase(token: String, onResult: (Boolean) -> Unit) {
        // Bazaar's IAB SDK has no separate acknowledge API in this client.
        onResult(true)
    }

    override fun restorePurchases(onResult: (String) -> Unit) {
        val inventory = lastInventory
        if (inventory != null) {
            restoreExistingPurchases(inventory)
            onResult(if (save.isPremium) "پریمیوم بازیابی شد ✓" else "خرید قبلی برای این حساب پیدا نشد")
            return
        }
        if (client == null || !isReady) {
            onResult("فروشگاه بازار آماده نیست. کمی بعد دوباره تلاش کن.")
            return
        }
        pendingRestore = onResult
    }

    override fun disconnect() {
        client?.endConnection()
        client = null
        isReady = false
        lastInventory = null
        pendingRequest = null
        pendingRestore = null
        pendingPurchases.clear()
        consumeCallbacks.clear()
    }

    fun dispose() = disconnect()

    private fun restoreExistingPurchases(inventory: Inventory) {
        val owned = inventory.allPurchases ?: emptyList()
        for (purchase in owned) {
            if (!verify(purchase)) {
                Log.w(TAG, "skipping purchase with invalid signature: ${purchase.sku}")
                continue
            }
            val token = purchase.token.orEmpty()
            if (token.isBlank()) continue
            pendingPurchases[token] = purchase
            val product = StoreConfig.productFor(purchase.sku) ?: continue
            if (product.isPremium) {
                if (save.deliveredPremiumPurchaseToken != token) grantPremium(token)
            } else {
                consumeAndCredit(purchase, onConsumed = {}, onError = { message ->
                    Log.w(TAG, "restore consume failed: $message")
                })
            }
        }
    }

    private fun verify(purchase: Purchase): Boolean = runCatching {
        BillingClient.verifyPurchase(purchase, StoreConfig.IAB_PUBLIC_KEY)
    }.getOrDefault(false)

    private fun grantPremium(purchaseToken: String?) {
        if (!purchaseToken.isNullOrBlank()) save.deliveredPremiumPurchaseToken = purchaseToken
        save.grantPremium()
        ProgressState.sync(save)
        livesManager.grantLives(LivesManager.MAX_LIVES)
    }

    private fun fail(message: String) {
        errorMessage = message
        Log.w(TAG, message)
        val request = pendingRequest
        pendingRequest = null
        purchasing = false
        request?.onError?.invoke(message)
        val restore = pendingRestore
        pendingRestore = null
        restore?.invoke(message)
    }

    private data class PurchaseRequest(
        val onSuccess: () -> Unit,
        val onCancelled: () -> Unit,
        val onError: (String) -> Unit,
    )

    private data class ConsumeCallback(
        val onSuccess: () -> Unit,
        val onError: (String) -> Unit,
    )
}
