package ir.amiraligrr.blockboom.data

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ir.amiraligrr.blockboom.BuildConfig
import ir.amiraligrr.blockboom.R
import ir.tapsell.sdk.Tapsell
import ir.tapsell.sdk.TapsellAd
import ir.tapsell.sdk.TapsellAdRequestListener
import ir.tapsell.sdk.TapsellAdRequestOptions
import ir.tapsell.sdk.TapsellAdShowListener
import ir.tapsell.sdk.TapsellShowOptions
import ir.tapsell.sdk.bannerads.TapsellBannerType
import ir.tapsell.sdk.bannerads.TapsellBannerView
import ir.tapsell.sdk.bannerads.TapsellBannerViewEventListener
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tapsell wrapper.
 *
 * The app has two ad formats:
 *
 *  1. A 320x50 banner in the one global slot below the navigation host. It is
 *     created once, kept alive across navigation, and destroyed when Compose
 *     tears the slot down.
 *  2. Rewarded video, requested before it is needed and shown only when the
 *     player explicitly asks for the reward.
 *
 * Rewarded requests are held rather than dropped. A player who taps "double my
 * coins" during a cold start or a slow network gets the ad as soon as it
 * arrives; if it never arrives, [showRewarded] reports the failure through
 * `onFail` and shows a short toast instead of leaving the button stuck.
 *
 * Ads are disabled in debug builds. The release build is the one that ships,
 * and keeping the SDK out of debug avoids emulator WebView crashes during UI
 * work and Robolectric runs. This is the same release-only policy the app used
 * before the Tapsell migration.
 */
object AdManager {

    const val TAG = "BlockBoomAd"

    private var appContext: Context? = null
    private var initialized = false

    /**
     * True once Tapsell's own initialization work has had time to run.
     *
     * [Tapsell.initialize] returns before it posts its setup to a worker
     * thread. A request issued in the same main-thread turn therefore reaches
     * the SDK with an empty app key and logs "Tapsell must be initialized
     * before requesting ad"; the first banner then waits for its full timeout
     * before it is retried. Gate the first requests on this flag.
     */
    private var sdkReady = false
    private var initReadyRunnable: Runnable? = null

    /** Caps the cold-start retry loop if the SDK never finishes setup. */
    private var initRetryCount = 0

    /** Guards against two rewarded shows racing (double-tap on the reward button). */
    private val showing = AtomicBoolean(false)

    private val lock = Any()

    /**
     * Id of the cached rewarded ad, or null when nothing is preloaded.
     *
     * Tapsell identifies a requested ad by its zone id and ad id. Keeping only
     * the id is enough to call [Tapsell.showAd] later and avoids holding on to
     * the heavier [TapsellAd] model.
     */
    private var rewardedAdId: String? = null

    /** True while a rewarded request is on the wire. */
    private var rewardedRequestInFlight = false

    /** A player request waiting for the rewarded ad to become available. */
    private var pendingRewarded: RewardRequest? = null

    private var pendingTimeout: Runnable? = null
    private var showTimeout: Runnable? = null

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private const val REWARDED_REQUEST_TIMEOUT_MS = 12_000L
    private const val REWARDED_SHOW_WATCHDOG_MS = 15_000L

    /**
     * Small grace period between [Tapsell.initialize] and the first request.
     * The SDK posts its setup to a single-thread executor; this lets that
     * runnable finish before a banner or rewarded request is submitted.
     */
    private const val SDK_INIT_GRACE_MS = 700L
    private const val MAX_INIT_RETRIES = 3

    fun init(context: Context) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "debug build: ad SDK disabled")
            return
        }

        val application = context.applicationContext as? Application ?: return
        appContext = application

        initReadyRunnable?.let { mainHandler.removeCallbacks(it) }
        initReadyRunnable = null
        sdkReady = false
        initRetryCount = 0

        runCatching {
            Tapsell.setDebugMode(application, false)
            Tapsell.initialize(application, TapsellConfig.APP_KEY)
            initialized = true
        }.onFailure {
            Log.e(TAG, "Tapsell initialization failed: ${it.message}", it)
        }

        if (initialized) {
            val ready = Runnable {
                sdkReady = true
                prepareRewarded()
            }
            initReadyRunnable = ready
            mainHandler.postDelayed(ready, SDK_INIT_GRACE_MS)
        }
    }

    /**
     * True after Tapsell's posted initialization has had a chance to run.
     * Banner code uses this to avoid racing the SDK's own setup.
     */
    fun isSdkReady(): Boolean = initialized && sdkReady

    // ── rewarded ───────────────────────────────────────────────────────────

    fun isRewardedLoaded(): Boolean = synchronized(lock) { rewardedAdId != null }

    fun prepareRewarded() {
        if (!initialized || !sdkReady || BuildConfig.DEBUG) return
        val context = appContext ?: return

        synchronized(lock) {
            if (rewardedAdId != null || rewardedRequestInFlight) return
            rewardedRequestInFlight = true
        }

        AdAnalytics.log(context, "ad_request", "rewarded")
        runCatching {
            Tapsell.requestAd(
                context,
                TapsellConfig.REWARDED_ZONE_ID,
                TapsellAdRequestOptions(TapsellAdRequestOptions.CACHE_TYPE_CACHED),
                rewardedRequestListener
            )
        }.onFailure {
            Log.e(TAG, "rewarded request failed: ${it.message}", it)
            failPending("request:${it.message}")
        }
    }

    private val rewardedRequestListener = object : TapsellAdRequestListener() {
        override fun onAdAvailable(adId: String) {
            onRewardedAvailable(adId)
        }

        override fun onAdAvailable(ad: TapsellAd) {
            onRewardedAvailable(ad.id)
        }

        override fun onNoAdAvailable() {
            failPending("no_ad_available")
        }

        override fun onNoNetwork() {
            failPending("no_network")
        }

        override fun onError(message: String) {
            // If the SDK's setup runnable is still in flight, the request is
            // rejected without being consumed. Retry once the app key is set
            // instead of surfacing a cold-start failure to the player.
            if (message.contains("must be initialized", ignoreCase = true) &&
                initRetryCount < MAX_INIT_RETRIES
            ) {
                initRetryCount++
                Log.w(TAG, "rewarded requested before Tapsell init; retrying")
                synchronized(lock) { rewardedRequestInFlight = false }
                mainHandler.postDelayed({ prepareRewarded() }, 350L)
                return
            }
            failPending("error:${message.take(48)}")
        }

        override fun onExpiring(ad: TapsellAd) {
            synchronized(lock) {
                if (rewardedAdId == ad.id) rewardedAdId = null
            }
            prepareRewarded()
        }
    }

    private fun onRewardedAvailable(adId: String) {
        if (adId.isBlank()) return
        val context = appContext ?: return
        initRetryCount = 0

        val request = synchronized(lock) {
            // The SDK may deliver both callback overloads. Once an ad is
            // cached or a show is already in progress, a second delivery is a
            // duplicate, not a second ad.
            if (pendingRewarded == null && (rewardedAdId != null || showing.get())) {
                return
            }
            rewardedRequestInFlight = false
            val queued = pendingRewarded
            if (queued != null) {
                pendingRewarded = null
            } else {
                rewardedAdId = adId
            }
            queued
        }

        AdAnalytics.log(context, "ad_loaded", "rewarded")
        if (request != null) {
            cancelPendingTimeout()
            presentRewarded(request, adId)
        }
    }

    /**
     * Shows a rewarded ad and fires [onReward] only if the player actually
     * earned it.
     *
     * If nothing is preloaded, the request is held until the SDK reports an ad
     * or the timeout expires. [onFail] is always called on a failure so the
     * caller can clear its "loading" state. A short toast is shown as well,
     * because several call sites otherwise had no visible feedback at all.
     *
     * [onShow] and [onClose] are lifecycle hooks for the screen that owns the
     * game. The ad activity pauses the Android activity on its own; these let a
     * screen stop and resume game simulation deliberately rather than relying
     * only on the activity lifecycle.
     */
    fun showRewarded(
        onReward: () -> Unit,
        onFail: () -> Unit = {},
        onShow: () -> Unit = {},
        onClose: () -> Unit = {}
    ) {
        if (!initialized || BuildConfig.DEBUG) {
            onFail()
            showUnavailableToast()
            return
        }
        if (!showing.compareAndSet(false, true)) {
            onFail()
            showUnavailableToast()
            return
        }

        val request = RewardRequest(onReward, onFail, onShow, onClose)

        val cachedId = synchronized(lock) {
            rewardedAdId.also { rewardedAdId = null }
        }
        if (cachedId != null) {
            presentRewarded(request, cachedId)
            return
        }

        val racedId = synchronized(lock) {
            pendingRewarded = request
            // An ad may have arrived between the read above and this block.
            rewardedAdId.also {
                if (it != null) {
                    rewardedAdId = null
                    pendingRewarded = null
                }
            }
        }
        if (racedId != null) {
            presentRewarded(request, racedId)
            return
        }

        prepareRewarded()
        schedulePendingTimeout()
    }

    private fun presentRewarded(request: RewardRequest, adId: String) {
        val context = appContext
        if (context == null) {
            finishWithFailure(request, "no_context", showToast = true)
            return
        }

        val didOpen = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        val rewarded = AtomicBoolean(false)

        val options = TapsellShowOptions().apply {
            setRotationMode(TapsellShowOptions.ROTATION_LOCKED_PORTRAIT)
            setImmersiveMode(true)
            setBackDisabled(false)
            setShowDialog(true)
            setWarnBackPressedDialogTitle("تبلیغ در حال پخش است")
            setWarnBackPressedDialogMessage("اگر خارج شوی، جایزه را از دست می‌دهی.")
            setWarnBackPressedDialogPositiveButtonText("ادامه بده")
            setWarnBackPressedDialogNegativeButtonText("خروج")
        }

        val listener = object : TapsellAdShowListener() {
            override fun onOpened() {
                didOpen.set(true)
                cancelShowTimeout()
                AdAnalytics.log(context, "rewarded_shown", "rewarded")
                runCatching { request.onShow() }
            }

            override fun onRewarded(completed: Boolean) {
                if (completed && rewarded.compareAndSet(false, true)) {
                    AdAnalytics.log(context, "rewarded_complete", "rewarded")
                    runCatching { request.onReward() }
                } else if (!completed) {
                    AdAnalytics.log(context, "rewarded_incomplete", "rewarded")
                }
            }

            override fun onClosed() {
                if (!finished.compareAndSet(false, true)) return
                showing.set(false)
                cancelShowTimeout()
                AdAnalytics.log(context, "rewarded_closed", "rewarded")
                runCatching { request.onClose() }
                // Give a reward callback that arrives just before/after close
                // a moment to land. If the player skipped, reset the caller's
                // busy state rather than leaving it stuck.
                mainHandler.postDelayed({
                    if (!rewarded.get()) runCatching { request.onFail() }
                }, 250L)
                prepareRewarded()
            }

            override fun onError(message: String) {
                if (!finished.compareAndSet(false, true)) return
                showing.set(false)
                cancelShowTimeout()
                AdAnalytics.log(context, "ad_error", "rewarded:${message.take(48)}")
                runCatching { request.onClose() }
                if (!didOpen.get()) {
                    finishWithFailure(request, "show:${message.take(48)}", showToast = true)
                } else {
                    runCatching { request.onFail() }
                    showUnavailableToast()
                }
                prepareRewarded()
            }

            override fun onAdClicked() {
                AdAnalytics.log(context, "click", "rewarded")
            }
        }

        runCatching {
            Tapsell.showAd(
                context,
                TapsellConfig.REWARDED_ZONE_ID,
                adId,
                options,
                listener
            )
        }.onFailure {
            Log.e(TAG, "rewarded show failed: ${it.message}", it)
            if (finished.compareAndSet(false, true)) {
                showing.set(false)
                finishWithFailure(request, "show:${it.message}", showToast = true)
            }
        }

        // If the ad never reaches the screen, do not leave the player on a
        // dead button and do not leave `showing` latched for the process.
        val watchdog = Runnable {
            if (!didOpen.get() && finished.compareAndSet(false, true)) {
                showing.set(false)
                AdAnalytics.log(context, "ad_error", "rewarded:show_timeout")
                finishWithFailure(request, "show_timeout", showToast = true)
                prepareRewarded()
            }
        }
        if (!didOpen.get() && !finished.get()) {
            showTimeout = watchdog
            mainHandler.postDelayed(watchdog, REWARDED_SHOW_WATCHDOG_MS)
        }
    }

    private fun schedulePendingTimeout() {
        cancelPendingTimeout()
        val runnable = Runnable {
            val request = synchronized(lock) {
                pendingRewarded.also { pendingRewarded = null }
            }
            if (request != null) {
                finishWithFailure(request, "request_timeout", showToast = true)
            }
        }
        pendingTimeout = runnable
        mainHandler.postDelayed(runnable, REWARDED_REQUEST_TIMEOUT_MS)
    }

    private fun cancelPendingTimeout() {
        pendingTimeout?.let { mainHandler.removeCallbacks(it) }
        pendingTimeout = null
    }

    private fun cancelShowTimeout() {
        showTimeout?.let { mainHandler.removeCallbacks(it) }
        showTimeout = null
    }

    /**
     * Reports a rewarded failure once. It clears the in-flight request and the
     * show guard so the next tap can try again immediately.
     */
    private fun failPending(reason: String) {
        val context = appContext
        if (context != null) {
            AdAnalytics.log(context, "ad_error", "rewarded:${reason.take(48)}")
        }

        val request = synchronized(lock) {
            rewardedRequestInFlight = false
            pendingRewarded.also { pendingRewarded = null }
        }

        if (request != null) {
            cancelPendingTimeout()
            finishWithFailure(request, reason, showToast = true)
        }
    }

    private fun finishWithFailure(request: RewardRequest, reason: String, showToast: Boolean) {
        showing.set(false)
        cancelPendingTimeout()
        cancelShowTimeout()
        Log.w(TAG, "rewarded unavailable: $reason")
        runCatching { request.onFail() }
        if (showToast) showUnavailableToast()
    }

    private fun showUnavailableToast() {
        val context = appContext ?: return
        mainHandler.post {
            runCatching {
                Toast.makeText(context, R.string.ad_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun release() {
        initialized = false
        sdkReady = false
        initRetryCount = 0
        initReadyRunnable?.let { mainHandler.removeCallbacks(it) }
        initReadyRunnable = null
        showing.set(false)
        cancelPendingTimeout()
        cancelShowTimeout()
        synchronized(lock) {
            rewardedAdId = null
            rewardedRequestInFlight = false
            pendingRewarded = null
        }
    }
}

/**
 * One rewarded request, with the callbacks the caller supplied.
 *
 * A data class rather than a bare lambda because a queued request needs to
 * carry both its reward and its failure path until the SDK answers.
 */
private data class RewardRequest(
    val onReward: () -> Unit,
    val onFail: () -> Unit,
    val onShow: () -> Unit,
    val onClose: () -> Unit
)

/**
 * The banner slot: one fixed-height band at the bottom of every screen.
 *
 * The height is fixed rather than animated. On the game screen a changing
 * height re-measures the play field, which moves the cannon and every falling
 * block mid-level. With no fill the band draws nothing; when a creative lands
 * it occupies the same place, at the same size, on every screen.
 */
@Composable
fun AdSlot(
    modifier: Modifier = Modifier,
    onHouseClick: (() -> Unit)? = null,
) {
    TapsellBanner(
        modifier = modifier.fillMaxWidth().height(AD_SLOT_HEIGHT),
        onHouseClick = onHouseClick
    )
}

@Composable
private fun TapsellBanner(
    modifier: Modifier = Modifier,
    onHouseClick: (() -> Unit)? = null,
) {
    // See AdManager.init: no SDK and no WebView in debug builds.
    if (BuildConfig.DEBUG) {
        Box(modifier = modifier)
        return
    }

    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var retryCount by remember { mutableIntStateOf(0) }
    var noFillReported by remember { mutableStateOf(false) }
    var gaveUp by remember { mutableStateOf(false) }
    // A blank 60dp band is worse than a house promo. Show the branded strip
    // after a short fill wait, and replace it the moment a real creative lands.
    var showHouse by remember { mutableStateOf(false) }
    val holder = remember { BannerViewHolder() }

    fun requestBanner() {
        val view = holder.view ?: return
        AdAnalytics.log(context.applicationContext, "ad_request", "banner")
        runCatching {
            view.loadAd(
                context,
                TapsellConfig.BANNER_ZONE_ID,
                TapsellBannerType.BANNER_320x50
            )
        }.onFailure {
            Log.e(AdManager.TAG, "banner load failed: ${it.message}", it)
            failed = true
        }
    }

    // Tapsell's banner view owns a WebView. Destroy it when the slot leaves the
    // composition (for example when a premium player's slot is removed) rather
    // than letting the WebView outlive the screen.
    DisposableEffect(Unit) {
        onDispose {
            runCatching { holder.view?.destroy() }
            holder.view = null
        }
    }

    // Do not race the SDK's own startup. Tapsell.initialize() returns before
    // its setup runnable has run; a banner requested in that window is rejected
    // with "must be initialized" and then sits empty until the retry timer.
    // Wait for AdManager's readiness flag, then make the first request once.
    LaunchedEffect(Unit) {
        var waited = 0L
        while (!AdManager.isSdkReady() && waited < BANNER_READY_TIMEOUT_MS) {
            delay(100L)
            waited += 100L
        }
        if (!loaded && !failed) requestBanner()
    }

    // A banner that errors once used to stay empty for the rest of the visit.
    // Re-request on the same view, with a hard cap so a permanently unfilled
    // placement cannot become an invalid-traffic loop.
    LaunchedEffect(failed) {
        if (!failed) return@LaunchedEffect
        if (retryCount >= BANNER_MAX_RETRIES) {
            failed = false
            gaveUp = true
            return@LaunchedEffect
        }
        delay(BANNER_RETRY_DELAY_MS)
        retryCount++
        failed = false
        loaded = false
        noFillReported = false
        gaveUp = false
        requestBanner()
    }

    // If Tapsell has no fill after all retries, keep the slot useful instead of
    // leaving a bare 60dp band. A house promo is clearly ours, never pretends
    // to be a paid creative, and gives the player a route to the ad-free shop.
    LaunchedEffect(gaveUp) {
        if (!gaveUp) return@LaunchedEffect
        delay(BANNER_HOUSE_RETRY_MS)
        gaveUp = false
        retryCount = 0
        failed = false
        loaded = false
        requestBanner()
    }

    // If the SDK neither fills nor errors, report the no-fill once so the
    // admin panel has a row instead of an unexplained empty band.
    LaunchedEffect(loaded) {
        if (loaded) {
            showHouse = false
            return@LaunchedEffect
        }
        delay(1_200L)
        if (!loaded) showHouse = true
    }

    LaunchedEffect(loaded, failed) {
        if (loaded || failed || noFillReported) return@LaunchedEffect
        delay(BANNER_FILL_TIMEOUT_MS)
        if (loaded || failed || noFillReported) return@LaunchedEffect
        noFillReported = true
        Log.w(AdManager.TAG, "banner: no fill after ${BANNER_FILL_TIMEOUT_MS}ms")
        AdAnalytics.log(context.applicationContext, "ad_error", "banner:no_fill_timeout")
        failed = true
    }

    // Tapsell reports a fill, not an impression. Count the visible banner once
    // after a short delay, and let onRequestFilled's own delayed check share
    // the flag so a normal callback sequence produces exactly one row.
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        delay(1_200L)
        if (loaded && !holder.impressionLogged) {
            holder.impressionLogged = true
            AdAnalytics.log(context.applicationContext, "impression", "banner")
        }
    }

    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier.padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                try {
                    TapsellBannerView(
                        viewContext,
                        TapsellBannerType.BANNER_320x50,
                        TapsellConfig.BANNER_ZONE_ID
                    ).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        holder.view = this
                        setEventListener(object : TapsellBannerViewEventListener {
                            override fun onNoAdAvailable() {
                                reportBannerError("no_ad_available")
                            }

                            override fun onNoNetwork() {
                                reportBannerError("no_network")
                            }

                            override fun onError(errorMessage: String) {
                                reportBannerError(errorMessage.take(48))
                            }

                            override fun onRequestFilled() {
                                Log.i(AdManager.TAG, "banner filled")
                                loaded = true
                                failed = false
                                noFillReported = false
                                holder.impressionLogged = false
                                AdAnalytics.log(
                                    context.applicationContext,
                                    "ad_loaded",
                                    "banner"
                                )

                                holder.view?.postDelayed({
                                    if (loaded && !holder.impressionLogged) {
                                        holder.impressionLogged = true
                                        AdAnalytics.log(
                                            context.applicationContext,
                                            "impression",
                                            "banner"
                                        )
                                    }
                                }, 1_200L)
                            }

                            override fun onHideBannerView() {
                                loaded = false
                                // A creative that disappears is a failure for
                                // our purposes: re-request rather than leave a
                                // permanently empty slot.
                                failed = true
                                AdAnalytics.log(
                                    context.applicationContext,
                                    "ad_error",
                                    "banner:hidden"
                                )
                            }

                            override fun onAdClicked() {
                                AdAnalytics.log(
                                    context.applicationContext,
                                    "click",
                                    "banner"
                                )
                            }

                            private fun reportBannerError(reason: String) {
                                Log.e(AdManager.TAG, "banner error: $reason")
                                AdAnalytics.log(
                                    context.applicationContext,
                                    "ad_error",
                                    "banner:${reason.take(48)}"
                                )
                                loaded = false
                                failed = true
                            }
                        })

                    }
                } catch (e: Exception) {
                    Log.e(AdManager.TAG, "Failed to create TapsellBannerView", e)
                    failed = true
                    android.view.View(viewContext)
                }
            }
        )

        if (!loaded && (showHouse || gaveUp)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF062E22),
                                Color(0xFF0B4A35),
                                Color(0xFF062E22)
                            )
                        )
                    )
                    .border(1.dp, Color(0xFF34D399).copy(alpha = 0.35f), shape)
                    .clickable(enabled = onHouseClick != null) { onHouseClick?.invoke() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "🏆", fontSize = 20.sp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Block Boom بدون تبلیغ",
                            color = Color(0xFFECFFF7),
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Black
                        )
                        Text(
                            text = "پریمیوم بگیر و بدون تبلیغ بازی کن",
                            color = Color(0xFF8BB8A6),
                            fontSize = 9.5.sp
                        )
                    }
                    if (onHouseClick != null) {
                        Text(
                            text = "ارتقا ‹",
                            color = Color(0xFFFBBF24),
                            fontSize = 10.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
            }
        }

        // A hairline frame only around a real creative.
        if (loaded) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            )
        }
    }
}

/**
 * One height for every banner, in every state, on every screen.
 *
 * Tapsell's standard banner is 320x50dp. A 60dp slot leaves 5dp of breathing
 * room above and below, so the creative is fully visible and the network can
 * count the impression. Fixed rather than animated — see [AdSlot] for why a
 * changing height is wrong on the game screen.
 */
val AD_SLOT_HEIGHT = 60.dp

/** Holds the banner view so the retry effect can reach it. */
private class BannerViewHolder {
    var view: TapsellBannerView? = null

    /** One impression per fill, even if both fill callbacks fire. */
    var impressionLogged = false
}

/**
 * How many times a failed banner slot re-requests before it gives up for the
 * rest of the time the screen is composed.
 */
private const val BANNER_MAX_RETRIES = 5

/** How long the banner waits for Tapsell's startup before its first request. */
private const val BANNER_READY_TIMEOUT_MS = 2_500L

/** How long a failed banner slot waits before asking again. */
private const val BANNER_RETRY_DELAY_MS = 4_000L

/** How long a banner request may stay silent before it is treated as a no-fill. */
private const val BANNER_FILL_TIMEOUT_MS = 8_000L

/** How long the house fallback waits before trying Tapsell again. */
private const val BANNER_HOUSE_RETRY_MS = 60_000L
