package ir.amiraligrr.blockboom.data

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.adivery.sdk.Adivery
import com.adivery.sdk.AdiveryAdListener
import com.adivery.sdk.AdiveryBannerAdView
import com.adivery.sdk.AdiveryListener
import com.adivery.sdk.BannerSize
import ir.amiraligrr.blockboom.BuildConfig
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adivery wrapper.
 *
 * ── On revenue per 1000 impressions ──────────────────────────────────────
 * eCPM itself is set by the ad network from advertiser demand, the player's
 * geo, and the season. No amount of code moves that number directly, and
 * anyone who says otherwise is either describing something else or about to
 * commit ad fraud (which is grounds for a publisher ban and, in most
 * jurisdictions, illegal — fabricated impressions and auto-clicks are exactly
 * the "invalid traffic" networks claw back money for).
 *
 * What code *does* move is revenue per session, and the levers used here are
 * the honest ones:
 *
 *  1. **Stable user id.** [Adivery.setUserId] was never called, so the network
 *     had no way to frequency-cap or build an audience profile. Over-serving
 *     the same person is how publishers get pushed onto low-value, frequency-
 *     capped inventory. This is the single cheapest eCPM win available.
 *  2. **Rewarded ads are the highest-RPM format by a wide margin** (commonly
 *     several times a banner), so the aim is more *completed* rewarded views
 *     at moments the player already wanted something.
 *  3. **Don't lose impressions to readiness.** [showRewarded] used to bail out
 *     silently when nothing was preloaded, so a player who tapped "double my
 *     coins" at the wrong moment got nothing and no ad was billed. It now
 *     holds the request and shows as soon as the ad is ready.
 *  4. **Interstitial with a frequency cap.** Paid per view, but a casual game
 *     that shows one every level loses players, and lost players cost far more
 *     than the extra impression earns. Hence every 3rd level, and never
 *     directly after another ad.
 *
 * Deliberately NOT done: auto-clicking ads, refreshing banners on a timer to
 * inflate the count, hiding banners offscreen, or showing an ad without the
 * player asking. Those are invalid traffic and get accounts banned.
 */
object AdManager {

    const val TAG = "BlockBoomAd"

    private var appContext: Context? = null

    /**
     * An interstitial every Nth level completion, not every level.
     *
     * This was 2, down from 3, having been raised from 1 in the other direction.
     * Every value in that sequence is wrong, and the direction of travel is the
     * problem: each change was made to squeeze more out of non-premium players,
     * and nobody checked what it does to the player actually playing.
     *
     * Two means an ad after every second level. A level takes 30-90 seconds with
     * the current balance, so a session becomes an alternating pattern of play and
     * a full-screen ad, several times a minute, for the entire time the player is
     * engaged. That is not monetisation, it is a tax on the one thing they came
     * for, and the outcome is a session that ends rather than a session that
     * watches ads. It also has a floor problem: no value below ~4 can be tuned
     * safely, because a player who clears levels quickly hits the interval much
     * faster than the level counter suggests.
     *
     * Five is a better trade and still a real one: an ad roughly once every three
     * minutes of play, on a natural break, which is what the interstitial format
     * is actually built for.
     *
     * NOTE: this is inert while [AdiveryConfig.INTERSTITIAL_PLACEMENT_ID] is
     * blank — no placement id means no interstitial, whatever this is set to.
     * That is why 2 sat here unnoticed: the number had never once been used.
     */
    private const val INTERSTITIAL_EVERY_N_LEVELS = 5

    /**
     * Floor on how close together two interstitials may be, in real time.
     *
     * The level counter alone cannot guarantee a sane pace. Levels have wildly
     * different durations, `onLevelContinue` can fire repeatedly during the
     * level-complete flow, and a player who clears a stage in twelve seconds
     * would otherwise get the interval measured in seconds rather than minutes.
     *
     * Two minutes is chosen against [INTERSTITIAL_EVERY_N_LEVELS]: even a player
     * clearing back-to-back stages cannot trip it, so the level counter is the one
     * the player feels and this is only the backstop against the pathological
     * case.
     */
    private const val INTERSTITIAL_MIN_GAP_MS = 120_000L

    /** Seconds to wait after a level before offering the interstitial. */
    private const val INTERSTITIAL_DELAY_MS = 700L

    /** Guards against two ad shows racing (double-tap on the reward button). */
    private val showing = AtomicBoolean(false)

    /**
     * How long to wait for a rewarded ad to actually reach the screen before
     * giving the request up.
     *
     * The SDK reports a refusal to show only through its own logger, so the only
     * way to tell "shown" from "silently declined" is to watch for the shown
     * callback. Comfortably longer than a cold start plus a video fetch, and far
     * shorter than a player will tolerate a dead button.
     */
    private const val REWARDED_SHOW_WATCHDOG_MS = 15_000L

    @Volatile private var levelsSinceInterstitial = 0

    /** Wall-clock of the last interstitial, or 0 if none has ever been shown. */
    @Volatile private var lastInterstitialAtMs = 0L

    /** A rewarded request that arrived before an ad was ready. */
    private var pendingRewarded: (() -> Unit)? = null

fun init(context: Context) {
        appContext = context.applicationContext
        // Was BuildConfig.DEBUG, i.e. off in every release build — combined with
        // minify stripping Log.d, a failing banner produced no output at
        // all. Left on: it only writes to logcat, costs nothing, and is the
        // difference between a diagnosable no-fill and a guess.
        Adivery.setLoggingEnabled(true)
        val app = appContext as? Application ?: return
        Adivery.configure(app, AdiveryConfig.APP_ID)

        // (1) A stable per-install id. This is what lets the network cap
        // frequency per person and target them; without it every install looks
        // like a brand-new anonymous user.
        runCatching { Adivery.setUserId(LeaderboardManager.getDeviceId(appContext!!)) }
            .onFailure { Log.d(TAG, "setUserId unavailable: ${it.message}") }

        // (2) Resolve a queued rewarded request the moment the ad arrives.
        //
        // `showRewarded` holds the request when nothing is preloaded and calls
        // `prepareRewarded()`, then gives up after 8 seconds. The give-up branch
        // was the *only* thing that could ever finish it: `onRewardedLoaded()`
        // existed and did exactly the right thing, but nothing in the app ever
        // called it, because it was written as a function instead of being wired
        // to the SDK's load event.
        //
        // The effect was invisible in testing and expensive in production. The
        // window where nothing is loaded is exactly the window a player taps a
        // reward button — cold start, poor connection, or right after the last
        // one was consumed. So the game silently refused to pay out: no ad, no
        // reward, no message explaining why, just an `onFail` toast. From the
        // ad network's side that is also a lost impression, so both halves of the
        // deal failed at once.
        //
        // `addGlobalListener` fires for every placement, so the id is filtered
        // here — otherwise a banner or interstitial load would try to present a
        // rewarded ad the player never asked for.
        val listener = object : AdiveryListener() {
            override fun onRewardedAdLoaded(placementId: String) {
                if (placementId == AdiveryConfig.REWARDED_PLACEMENT_ID) {
                    onRewardedLoaded()
                }
            }
        }
        runCatching { Adivery.addGlobalListener(listener) }
            .onSuccess { globalListener = listener }
            .onFailure { Log.w(TAG, "global ad listener unavailable: ${it.message}") }

        preloadAll()
    }

    private fun preloadAll() {
        prepareRewarded()
        prepareInterstitial()
    }

    // ── rewarded ───────────────────────────────────────────────────────────

    fun isRewardedLoaded(): Boolean = Adivery.isLoaded(AdiveryConfig.REWARDED_PLACEMENT_ID)

    fun prepareRewarded() {
        appContext?.let { Adivery.prepareRewardedAd(it, AdiveryConfig.REWARDED_PLACEMENT_ID) }
    }

    /** True once a real interstitial placement id has been configured. */
    private val interstitialEnabled: Boolean
        get() = AdiveryConfig.INTERSTITIAL_PLACEMENT_ID.isNotBlank()

    fun isInterstitialLoaded(): Boolean =
        interstitialEnabled && Adivery.isLoaded(AdiveryConfig.INTERSTITIAL_PLACEMENT_ID)

    fun prepareInterstitial() {
        if (!interstitialEnabled) return
        appContext?.let { Adivery.prepareInterstitialAd(it, AdiveryConfig.INTERSTITIAL_PLACEMENT_ID) }
    }

    /**
     * Shows a rewarded ad and fires [onReward] only if the user actually earned
     * it.
     *
     * (3) If nothing is preloaded the request is *held* rather than dropped.
     * The old code called onFail() immediately, which both lost the player the
     * reward and produced no billable impression.
     */
    fun showRewarded(onReward: () -> Unit, onFail: () -> Unit = {}) {
        if (!showing.compareAndSet(false, true)) {
            onFail()
            return
        }
        if (isRewardedLoaded()) {
            presentRewarded(onReward, onFail)
        } else {
            pendingRewarded = {
                presentRewarded(onReward, onFail)
            }
            prepareRewarded()
            // Don't leave the player staring at a button that will never fire.
            appContext?.let {
                android.os.Handler(it.mainLooper).postDelayed({
                    if (pendingRewarded != null && !isRewardedLoaded()) {
                        pendingRewarded = null
                        showing.set(false)
                        onFail()
                    }
                }, 8000)
            }
        }
    }

    private fun presentRewarded(onReward: () -> Unit, onFail: () -> Unit) {
        val placementId = AdiveryConfig.REWARDED_PLACEMENT_ID
        // Whether the SDK actually put an ad on screen. Read by the watchdog
        // below, so it has to be safely publishable from the SDK's thread.
        val didShow = AtomicBoolean(false)
        Adivery.addPlacementListener(
            placementId,
            object : AdiveryListener() {
                override fun onRewardedAdShown(placementId: String) {
                    didShow.set(true)
                }

                override fun onRewardedAdClosed(placementId: String, isRewarded: Boolean) {
                    Adivery.removePlacementListener(placementId)
                    showing.set(false)
                    pendingRewarded = null
                    appContext?.let { AdAnalytics.log(it, "rewarded_shown", "rewarded") }
                    if (isRewarded) {
                        appContext?.let { AdAnalytics.log(it, "rewarded_complete", "rewarded") }
                        onReward()
                    }
                    // Keep the next one warm so the same loss doesn't repeat.
                    prepareRewarded()
                }

                override fun log(placementId: String, message: String) {
                    Log.d(TAG, "[$placementId] $message")
                }
            }
        )
        Adivery.showAd(placementId)

        // Watchdog for a show that never happened.
        //
        // `Adivery.showAd` returns silently when it decides not to show — no
        // exception, and no `onRewardedAdClosed`. Its failure reasons
        // ("impression cap exceeded", "internal error", "placement id not
        // active") are swallowed by the SDK's own logger, so `runCatching`
        // cannot see them either. `showing` was cleared *only* by the close
        // callback, so any of those outcomes latched the guard to `true` for the
        // life of the process and every later rewarded request was rejected
        // before an ad could even be requested — double coins, the +2 hearts
        // rescue and the free upgrade all became unavailable, reporting only
        // "ad not ready", with no way to recover short of restarting the app.
        //
        // This gives up the show if nothing is on screen in time, and hands the
        // failure back to the caller like any other. Generous enough that a slow
        // first frame cannot trip it, short enough that a player is not left
        // watching a dead button.
        appContext?.let {
            android.os.Handler(it.mainLooper).postDelayed({
                if (!didShow.get()) {
                    Adivery.removePlacementListener(placementId)
                    showing.set(false)
                    pendingRewarded = null
                    appContext?.let { ctx -> AdAnalytics.log(ctx, "rewarded_show_timeout", "rewarded") }
                    onFail()
                }
            }, REWARDED_SHOW_WATCHDOG_MS)
        }
    }

    // ── interstitial (4) ───────────────────────────────────────────────────

    /**
     * Offers an interstitial on a natural break, at most once every
     * [INTERSTITIAL_EVERY_N_LEVELS] levels, and never straight after another
     * ad. Safe to call on every level completion.
     */
    fun maybeShowInterstitialOnLevelComplete(
        activity: Activity?,
        isPremium: Boolean = false,
        onSkipped: () -> Unit = {}
    ) {
        // Premium pays for the removal of ads, so the interstitial has to be
        // included in that promise. It was added later and not gated, which is
        // why a paying user still saw one every three levels.
        if (isPremium) { onSkipped(); return }

        levelsSinceInterstitial++
        if (levelsSinceInterstitial < INTERSTITIAL_EVERY_N_LEVELS) {
            onSkipped()
            return
        }
        if (activity == null || activity.isFinishing) { onSkipped(); return }
        if (!isInterstitialLoaded() || !showing.compareAndSet(false, true)) {
            prepareInterstitial()
            onSkipped()
            return
        }
        // Time floor. Checked *after* the level counter so a skipped opportunity
        // still counts toward the next one — otherwise a player who is never
        // eligible on time would keep resetting the interval and never see an ad
        // at all.
        val now = System.currentTimeMillis()
        if (lastInterstitialAtMs != 0L && now - lastInterstitialAtMs < INTERSTITIAL_MIN_GAP_MS) {
            showing.set(false)
            onSkipped()
            return
        }
        // Captured before the write below, so a show that turns out not to have
        // happened can hand the previous floor back rather than erasing it.
        val previousInterstitialAt = lastInterstitialAtMs
        lastInterstitialAtMs = now
        levelsSinceInterstitial = 0
        val placementId = AdiveryConfig.INTERSTITIAL_PLACEMENT_ID
        Adivery.addPlacementListener(
            placementId,
            object : AdiveryListener() {
                override fun onInterstitialAdClosed(placementId: String) {
                    Adivery.removePlacementListener(placementId)
                    showing.set(false)
                    appContext?.let { AdAnalytics.log(it, "interstitial_closed", "interstitial") }
                    prepareInterstitial()
                }

                override fun onInterstitialAdShown(placementId: String) {
                    appContext?.let { AdAnalytics.log(it, "impression", "interstitial") }
                }

                override fun onInterstitialAdClicked(placementId: String) {
                    appContext?.let { AdAnalytics.log(it, "click", "interstitial") }
                }
            }
        )
        activity.window.decorView.postDelayed({
            runCatching { Adivery.showAd(placementId) }
                .onFailure {
                    // Never actually shown, so it should not count as a show for
                    // the time floor either — otherwise one SDK throw costs the
                    // player an ad slot for the next two minutes for no reason.
                    //
                    // "Should not count" means restoring what was there, not
                    // writing 0: `lastInterstitialAtMs` treats 0 as the sentinel
                    // for "nothing has ever been shown", so zeroing it did not
                    // merely skip this attempt, it removed the two-minute floor
                    // for the rest of the session and left the cap at nothing
                    // but the level counter. The level counter is restored too,
                    // since its opportunity has not been spent either.
                    showing.set(false)
                    lastInterstitialAtMs = previousInterstitialAt
                    levelsSinceInterstitial = INTERSTITIAL_EVERY_N_LEVELS - 1
                }
        }, INTERSTITIAL_DELAY_MS)
    }

    /**
     * Presents a queued rewarded ad now that one has arrived.
     *
     * Called from the global SDK listener — see the note in [init]. Kept public
     * and separate from the listener so the queue logic has one owner and the
     * listener has none.
     */
    fun onRewardedLoaded() {
        val queued = pendingRewarded ?: return
        pendingRewarded = null
        queued()
    }

    fun release() {
        pendingRewarded = null
        // A show left in flight when the manager is torn down would otherwise
        // leave the guard stuck, rejecting every later rewarded request.
        showing.set(false)
        levelsSinceInterstitial = 0
        lastInterstitialAtMs = 0L
        globalListener?.let { l ->
            runCatching { Adivery.removeGlobalListener(l) }
            globalListener = null
        }
        runCatching { Adivery.onDestroy() }
    }

    /**
     * The listener handed to [Adivery.addGlobalListener], kept so [release] can
     * remove exactly the object that was registered rather than a fresh one.
     */
    private var globalListener: AdiveryListener? = null
}

/**
 * The banner slot: one fixed-height band at the bottom of every screen.
 *
 * ── Why the height is fixed, and why that used to be a bug ────────────
 * This slot used to animate between [AD_SLOT_HEIGHT] and zero, so that a
 * placement with no fill left no band at all. That was a nice idea and it never
 * ran: `AdiveryBanner` appended its own `.height(AD_SLOT_HEIGHT)` *after* the
 * height it was handed, and in a Compose modifier chain the last `height` wins.
 * So the slot was 60dp forever, the `animateDpAsState` animated a value nobody
 * read, and the no-fill case was a permanent 60dp hole.
 *
 * Two things were tried in the wrong order and neither is right on its own:
 * collapsing the slot avoids the hole but reflows the screen when a fill lands
 * or drops — and on the game screen a reflow re-measures the play field, which
 * moves the cannon and every falling block mid-level.
 *
 * So the height stays fixed and the *appearance* is what changed: with no fill
 * the band draws nothing at all. Not a bordered box, not a label, not a pulsing
 * loading bar — the band is simply background, indistinguishable from any other
 * margin. When there is a fill, the banner is in the same place it was always
 * going to be, at the same size, on every screen, which is what "the ad is always
 * where you can see it" has to mean if it is going to mean anything.
 */
@Composable
fun AdSlot(
    modifier: Modifier = Modifier,
) {
    AdiveryBanner(modifier = modifier.fillMaxWidth().height(AD_SLOT_HEIGHT))
}

@Composable
fun AdiveryBanner(
    modifier: Modifier = Modifier,
) {
    // Composable scope, so the no-fill report below has a Context to hand to
    // AdAnalytics. AdManager.appContext is private to the object and this is a
    // top-level composable.
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var retryCount by remember { mutableIntStateOf(0) }
    // Whether the no-fill below has already been reported for this view.
    //
    // Not reset by the retry: a placement with no fill reports once, not once
    // per retry cycle. Resetting it re-armed the reporting effect every time the
    // retry cleared `failed`, so a permanently-unfilled placement posted a fresh
    // `banner:no_fill_timeout` row every retry-plus-timeout — roughly every 27
    // seconds, for as long as the screen stayed composed.
    var noFillReported by remember { mutableStateOf(false) }

    // The ad view, captured so the retry can actually reach it.
    //
    // It used to exist only as the return value of the `AndroidView` factory
    // lambda, which Compose runs once per composition. Nothing outside could
    // get at it, so the retry effect below had no way to ask for an ad again —
    // it cleared two booleans and changed nothing. The recovery it documents did
    // not exist.
    val holder = remember { BannerViewHolder() }

    // Retry a failed slot.
    //
    // A banner that errors once stayed empty for as long as the screen was
    // composed: `failed` was only ever cleared by navigating away and back, so
    // one dropped request on a bad connection meant no ads for the rest of that
    // visit. The SDK's own `setRetryOnError(true)` covers a request it can retry;
    // this is the recovery for the case where it gave up entirely, so it issues
    // a fresh request on the view rather than only resetting local state.
    //
    // Capped so a permanently-unavailable placement cannot become a request
    // loop, which the network treats as invalid traffic.
    LaunchedEffect(failed) {
        if (!failed) return@LaunchedEffect
        if (retryCount >= BANNER_MAX_RETRIES) {
            // Out of attempts: stop showing the failure state, and let the
            // slot sit empty rather than retrying forever.
            failed = false
            return@LaunchedEffect
        }
        delay(BANNER_RETRY_DELAY_MS)
        retryCount++
        failed = false
        loaded = false
        AdAnalytics.log(context, "ad_request", "banner")
        runCatching { holder.view?.loadAd() }
    }

    // Report a fill that never arrived.
    //
    // The banner used to be completely silent when the network simply had
    // nothing to give: no `onAdLoaded`, no `onError`, nothing logged and
    // nothing sent to the server. On the panel that looks identical to a build
    // where the banner composable was removed, which is why "the ads never show"
    // could sit uninvestigated for days — there was no row to look at, only an
    // absence.
    //
    // This makes the absence a fact. If the SDK has neither loaded nor errored
    // within the window, that is a no-fill and it is reported as one — once.
    LaunchedEffect(loaded, failed) {
        if (loaded || failed || noFillReported) return@LaunchedEffect
        delay(BANNER_FILL_TIMEOUT_MS)
        if (loaded || failed || noFillReported) return@LaunchedEffect
        noFillReported = true
        Log.w(AdManager.TAG, "banner: no fill after ${BANNER_FILL_TIMEOUT_MS}ms")
        AdAnalytics.log(context, "ad_error", "banner:no_fill_timeout")
        // Treated as a failure so the retry timer starts.
        failed = true
    }

    // Second chance at the impression.
    //
    // The SDK callback path above is correct, but some Adivery builds report a
    // fill without ever calling onAdShown, and the postDelayed attached to the
    // ad view can be lost if the view is detached during a navigation. This
    // effect lives in the composition that is actually on screen, so a banner
    // that is visible for more than a moment is counted even when the callback
    // never arrives. The shared flag keeps it to one row per fill.
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        delay(1_200L)
        if (loaded && !holder.impressionLogged) {
            holder.impressionLogged = true
            AdAnalytics.log(context.applicationContext, "impression", "banner")
        }
    }

    val shape = RoundedCornerShape(14.dp)

    // The slot is always exactly [AD_SLOT_HEIGHT] tall, on every screen, in every
    // state. See [AdSlot] for why the height does not move.
    //
    // Nothing is drawn into the band unless there is a creative to draw.
    //
    // The version that used to live here painted a bordered, translucent box in
    // all four states and printed the words "• فضای خالی" — "empty space" —
    // inside it when the placement had no fill, with a 4dp loading bar on top
    // that came back every twelve seconds as the retry timer fired.
    //
    // That is the worst possible outcome. It is not an advert and it says so, in
    // Persian, in the one place the player was already looking for something to
    // click; and the blinking bar made the whole screen look broken rather than
    // un-monetised. The absence of a fill is a fact for the dashboard, not
    // something to render on the device.
    //
    // The ad view is deliberately left fully opaque and untouched, rather than
    // being alpha'd out until a fill arrives. Alpha 0 would hide a stray fill,
    // but it also means the view is briefly invisible to the SDK at exactly the
    // moment a creative lands, and `onAdShown()` — the callback that bills the
    // impression — fires around then. Not dimming the view is the safer trade:
    // an SDK view with nothing to show already draws nothing, and if it ever does
    // paint something, that is the ad's own pixels rather than our guess at them.
    //
    // The frame and the border come back only once a creative is actually
    // present, so a filled banner is bounded by something that looks deliberate
    // instead of by a rectangle around nothing.
    Box(
        modifier = modifier
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // The ad view is ALWAYS composed.
        //
        // It used to be gated behind `if (loaded)`, with a placeholder drawn in
        // the meantime. That is a deadlock: `loaded` is only ever set by
        // `onAdLoaded()`, a callback that can only fire from the
        // AdiveryBannerAdView — which, being inside the gated branch, was never
        // created. So the slot sat on its loading bar forever and no ad ever
        // appeared. The view has to exist for the SDK to ask for an ad at all.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                try {
                    AdiveryBannerAdView(context).apply {
                        // The slot reserves a fixed height even when nothing has
                        // filled yet; the native banner view can paint its own
                        // opaque background during that window and show up as a
                        // black band. Keep the slot visually empty until a fill
                        // actually exists.
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setPlacementId(AdiveryConfig.BANNER_PLACEMENT_ID)
                        // Published so the retry effect can re-request on this
                        // exact view.
                        holder.view = this
                        // Standard 320x50. SMART_BANNER is adaptive and, on
                        // this device, returned a creative taller than the
                        // 60dp slot. Adivery then reported the view as only
                        // 63% visible and refused to track the show, so the
                        // impression never reached the network. A fixed size
                        // is fully visible inside the fixed slot; the small
                        // loss of adaptive width is worth actually counting.
                        setBannerSize(BannerSize.BANNER)
                        // Retry on a no-fill. Without it one failed request left
                        // the view permanently empty for as long as it was
                        // composed — a single transient network error turned
                        // into "ads never load again" for the rest of the
                        // session, with no code path that could recover it.
                        setRetryOnError(true)
                        setBannerAdListener(object : AdiveryAdListener() {
                            override fun onError(reason: String) {
                                // This used to be Log.d only, which is a no-op in
                                // a minified release build, so a permanent no-fill
                                // looked identical to "no network". The reason is
                                // reported to the server and is visible in
                                // admin/ads.php, which is the only way to tell an
                                // unapproved Adivery account from a blocked request
                                // without a device attached to adb.
                                Log.e(AdManager.TAG, "banner error: $reason")
                                AdAnalytics.log(
                                    context.applicationContext,
                                    "ad_error",
                                    "banner:" + reason.take(48)
                                )
                                failed = true
                                loaded = false

                                // One immediate retry. The old code used this
                                // branch to fall back from SMART_BANNER to a
                                // standard 320x50 request after an "Internal
                                // error"; the standard size is now the primary
                                // request, so this is simply a second attempt
                                // before the slower retry timer takes over. If
                                // it also fails, the panel gets both rows.
                                if (!holder.retriedOnce) {
                                    holder.retriedOnce = true
                                    runCatching {
                                        holder.view?.setBannerSize(BannerSize.BANNER)
                                        AdAnalytics.log(
                                            context.applicationContext,
                                            "ad_request",
                                            "banner"
                                        )
                                        holder.view?.loadAd()
                                    }.onFailure {
                                        Log.e(AdManager.TAG, "banner fallback failed: ${it.message}")
                                    }
                                }
                            }

                            override fun onAdLoaded() {
                                Log.i(AdManager.TAG, "banner loaded")
                                loaded = true
                                holder.impressionLogged = false
                                AdAnalytics.log(context.applicationContext, "ad_loaded", "banner")

                                // Some SDK builds fire onAdLoaded but never
                                // onAdShown, which made a filled banner count as
                                // zero impressions. Give onAdShown a short head
                                // start, then record the view once. The flag is
                                // shared with onAdShown so a normal callback
                                // sequence still produces exactly one row.
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

                            /**
                             * A real ad view. The impression used to be logged
                             * from this `factory` lambda, which re-runs every
                             * time the view is recreated (navigation, rotation)
                             * and fired *before* anything was rendered — so
                             * "impressions" counted banner view constructions
                             * rather than views.
                             */
                            override fun onAdShown() {
                                if (!holder.impressionLogged) {
                                    holder.impressionLogged = true
                                    AdAnalytics.log(
                                        context.applicationContext,
                                        "impression",
                                        "banner"
                                    )
                                }
                            }

                            override fun onAdClicked() {
                                AdAnalytics.log(context.applicationContext, "click", "banner")
                            }
                        })
                        // The request is a first-class diagnostic. A slot with
                        // no fill must still leave evidence that it asked.
                        AdAnalytics.log(context.applicationContext, "ad_request", "banner")
                        loadAd()
                    }
                } catch (e: Exception) {
                    Log.e(AdManager.TAG, "Failed to create AdiveryBannerAdView", e)
                    android.view.View(context).also { failed = true }
                }
            }
        )

        // A hairline frame, drawn only around a real creative.
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
 * The request uses Adivery's standard 320x50dp BANNER size. The frame is a
 * fixed 60dp: 10dp taller than the creative, so the view is fully visible with
 * breathing room above and below instead of being clipped. This matters for
 * more than looks — a partially visible AdView is reported as "show wasn't
 * tracked", so the network never counts that impression. SMART_BANNER was
 * adaptive and produced exactly that failure on a 1080x2400 device.
 *
 * Fixed rather than animated on purpose — see [AdSlot] for the reflow that
 * animating it caused on the game screen. It is public because a screen that
 * overlays content on top of the banner has to reserve exactly this much room;
 * they used to hard-code 56dp — the old slot height — so raising it here
 * silently moved the last button under the ad. Read it, don't repeat it.
 */
val AD_SLOT_HEIGHT = 60.dp

/**
 * Holds the banner view so the retry effect can reach it.
 *
 * The view is created inside `AndroidView`'s `factory` lambda, which returns it
 * as the composed node — that return value goes to Compose, not to a variable
 * anything else can read. Remembering the view here is what makes an actual
 * re-request possible.
 */
private class BannerViewHolder {
    var view: AdiveryBannerAdView? = null

    /** One impression per fill, even if the SDK calls shown twice. */
    var impressionLogged = false

    /** The first request failed; retry the same standard size once immediately. */
    var retriedOnce = false
}

/**
 * How many times a failed banner slot re-requests before it gives up for the
 * rest of the time the screen is composed.
 *
 * Bounded so a placement with no fill cannot become a request loop, which the
 * network treats as invalid traffic.
 */
private const val BANNER_MAX_RETRIES = 3

/**
 * How long a failed banner slot waits before asking again.
 *
 * Long enough that a slow network is not hammered, short enough that a player
 * who opens a screen during a blip still sees an ad. The SDK's own
 * `setRetryOnError(true)` retries the request; this is the recovery for the case
 * where it gave up entirely and the view is left empty.
 */
private const val BANNER_RETRY_DELAY_MS = 12_000L

/**
 * How long a banner request may stay silent before it is treated as a no-fill.
 *
 * Long enough that a normal fill on a slow connection is not mistaken for a
 * failure, short enough that the panel shows a row rather than nothing at all.
 */
private const val BANNER_FILL_TIMEOUT_MS = 15_000L
