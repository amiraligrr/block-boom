package ir.amiraligrr.blockboom.ui.screens

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import android.app.Activity
import ir.amiraligrr.blockboom.BlockBoomApp
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.data.AdManager
import ir.amiraligrr.blockboom.data.AppConfigRepository
import ir.amiraligrr.blockboom.data.DailyReminder
import ir.amiraligrr.blockboom.data.ProgressState
import ir.amiraligrr.blockboom.data.SaveManager
import ir.amiraligrr.blockboom.domain.LevelModifier
import ir.amiraligrr.blockboom.domain.GameEngine
import ir.amiraligrr.blockboom.data.rememberSoundManager
import kotlinx.coroutines.launch
import ir.amiraligrr.blockboom.ui.components.AppIcon
import ir.amiraligrr.blockboom.ui.components.AppIconView
import ir.amiraligrr.blockboom.ui.components.ButtonSize
import ir.amiraligrr.blockboom.ui.components.FloatingBall
import ir.amiraligrr.blockboom.ui.components.HeaderBar
import ir.amiraligrr.blockboom.ui.components.JoyCard
import ir.amiraligrr.blockboom.ui.components.JoyChip
import ir.amiraligrr.blockboom.ui.components.JoyElevation
import ir.amiraligrr.blockboom.ui.components.JoyIconTile
import ir.amiraligrr.blockboom.ui.components.JoyShape
import ir.amiraligrr.blockboom.ui.components.JoyTrack
import ir.amiraligrr.blockboom.ui.components.NeonButton
import ir.amiraligrr.blockboom.ui.components.CompactPremiumStrip
import ir.amiraligrr.blockboom.ui.components.PremiumUpsellCard
import ir.amiraligrr.blockboom.ui.components.TutorialHint
import ir.amiraligrr.blockboom.ui.components.TutorialPulseRing
import ir.amiraligrr.blockboom.ui.dialogs.NoLivesDialog
import ir.amiraligrr.blockboom.ui.dialogs.RateUsDialog
import ir.amiraligrr.blockboom.ui.formatNumber
import ir.amiraligrr.blockboom.ui.theme.NeonCyan
import ir.amiraligrr.blockboom.ui.theme.NeonGold
import ir.amiraligrr.blockboom.ui.theme.JoyThemeColors
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors

/**
 * The rating prompt, once the store link has come back from the server.
 *
 * A wrapper rather than the dialog itself, because the link is the one input the
 * dialog cannot supply for itself and it arrives asynchronously. It shows
 * nothing until there is a real URL: a rating dialog with nowhere to send the
 * player has already spent the ask and delivered nothing, which is worse than
 * not asking.
 *
 * With no link it exits through [onSkip] and stamps nothing, so a link appearing
 * later still finds the player eligible.
 */
@Composable
private fun RateUsPrompt(
    onSkip: () -> Unit,
    onOpenStore: (Int) -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit
) {
    val context = LocalContext.current
    val rateUrl by AppConfigRepository.rateUrl.collectAsState()

    // HomeScreen does not run the config check itself — BlockBoomApp does it at
    // startup — so this is the one place the URL may legitimately still be
    // missing. Ask, and let the flow above supply it when it lands.
    LaunchedEffect(Unit) { AppConfigRepository.check() }

    val url = rateUrl
    if (url.isBlank()) {
        LaunchedEffect(Unit) { onSkip() }
        return
    }

    RateUsDialog(
        onRate = { stars ->
            onOpenStore(stars)
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                )
            } catch (_: Exception) {}
        },
        onLater = onLater,
        onNever = onNever,
        onDismiss = onLater
    )
}

@Composable
fun HomeScreen(
    onPlay: () -> Unit,
    onShop: () -> Unit,
    onUpgrade: () -> Unit,
    onSettings: () -> Unit,
    onLeaderboard: () -> Unit = {},
    onStats: () -> Unit = {},
    onSupport: () -> Unit = {},
    onInvite: () -> Unit = {},
    onExit: () -> Unit = {}
) {
    val c = joyThemeColors()

    val context = LocalContext.current
    val app = context.applicationContext as BlockBoomApp
    val saveManager = app.saveManager
    val livesManager = app.livesManager
    val billingManager = app.billingManager
    val activity = context as? Activity

    var showNoLives by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(!saveManager.tutorialSeen) }
    var showDailyReward by remember { mutableStateOf(saveManager.isDailyRewardAvailable()) }
    var dailyRewardClaimed by remember { mutableStateOf<Long?>(null) }

    // ── The rating prompt ────────────────────────────────────────────────
    //
    // Shown after a delay, and only when the player qualifies and the operator
    // has configured a store link. Both conditions are checked every time the
    // flow re-checks, so a fresh install that has not played is never asked and
    // a player who has said "don't ask again" is not either.
    //
    // RateUsPrompt is a [RateUsPrompt] composable below — it is the one thing
    // on this screen that needs the server's answer, so it owns its own state
    // rather than threading a URL through every dialogs call.
    var showRateUs by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Long enough that the app has settled and a link has come back from
        // app_config.php, short enough that the player is still looking at the
        // home screen rather than already inside a level.
        kotlinx.coroutines.delay(2_500)
        if (saveManager.shouldPromptForRating()) showRateUs = true
    }

    // Coins the server delivered on launch — a verified Myket purchase, a
    // redeemed referral code, or a bonus an operator issued. The claim runs in
    // BlockBoomApp.collectPendingRewards() off the main thread, so this only has
    // to notice the result and say so. Without this the player paid for 5,000
    // coins, the grant sat in the database forever, and the app said nothing.
    //
    // Collected rather than polled. The 12s poll that used to sit here could
    // never fire on its own — see BlockBoomApp.deliveredRewardCoins.
    var deliveredReward by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        app.deliveredRewardCoins.collect { flow ->
            if (flow > 0) {
                // Consume, so a configuration change does not replay the
                // banner for a grant that was already announced.
                val got = app.consumeDeliveredReward()
                if (got > 0) deliveredReward = got
            }
        }
    }

    // The balance-change refund, announced the same way.
    //
    // A silent payout would be worse than none: the player opens the shop,
    // notices twelve fewer balls than they paid for, and finds an unexplained
    // number in their balance. Saying it happened — and why — is the difference
    // between a rebalance and a bug report.
    var balanceRefund by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        app.balanceRefundCoins.collect { flow ->
            if (flow > 0) {
                val got = app.consumeBalanceRefund()
                if (got > 0) balanceRefund = got
            }
        }
    }

    val markTutorialSeen: () -> Unit = {
        if (showTutorial) {
            showTutorial = false
            saveManager.tutorialSeen = true
        }
    }

    // ── Notification permission, asked once, and only after it makes sense ──
    //
    // The trigger is `statRuns >= 1`: the player has finished a run, so the
    // daily streak is something they now have. Asking at launch instead — the
    // obvious place to put it — is the worst one available. A permission dialog
    // over an onboarding wizard asks a stranger to grant ongoing notifications
    // before they know what the app is for, and the answer is always no, and a
    // first refusal is what makes the second request awkward.
    //
    // `notifPromptShown` is stamped before asking, not after answering, so
    // dismissing the dialog counts as having been asked. On API 33+ the system
    // stops showing it after two denials, so a flag that keeps offering would be
    // offering something the OS will no longer deliver.
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Whatever the answer, the reason is now armed and the alarm chain should
        // match reality: cancel it if refused, arm it if granted.
        saveManager.notificationsEnabled = granted
        DailyReminder.sync(context, saveManager)
    }

    LaunchedEffect(Unit) {
        val shouldAsk = saveManager.notificationsEnabled &&
            !saveManager.notifPromptShown &&
            saveManager.statRuns >= 1 &&
            DailyReminder.needsRuntimePermission() &&
            !DailyReminder.canPost(context, saveManager)
        if (shouldAsk) {
            saveManager.notifPromptShown = true
            notifLauncher.launch("android.permission.POST_NOTIFICATIONS")
        }
    }

    // Arm the alarm even on installs below API 33, where no runtime prompt exists
    // and notifications work as soon as the channel is made. Without this the
    // reminder would silently never be scheduled for the large majority of the
    // devices this app actually runs on.
    LaunchedEffect(Unit) {
        if (!DailyReminder.needsRuntimePermission()) {
            DailyReminder.ensureChannel(context)
            DailyReminder.sync(context, saveManager)
        }
    }

    // Music and the lifecycle observer now live in AppNavigation, keyed by
    // route. Keeping copies here meant HomeScreen's onDispose paused the music
    // every time the player opened Shop or Settings and never brought it back.
    val soundManager = rememberSoundManager(saveManager)

    LaunchedEffect(Unit) {
        ProgressState.sync(saveManager)
        livesManager.refresh()
        showDailyReward = saveManager.isDailyRewardAvailable()
    }

    // Handle back press to exit app
    val backCallback = remember { object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            onExit()
        }
    } }
    // See AppNavigation: `LocalActivity` rather than casting the context, so a
    // null host is a missing back handler instead of a crash. It resolves to the
    // base `Activity`, which has no back dispatcher, so the cast to
    // `ComponentActivity` still has to happen — but as a `?`, on the result of a
    // call that is allowed to be null, rather than on the context itself.
    val componentActivity = LocalActivity.current as? ComponentActivity
    DisposableEffect(componentActivity) {
        componentActivity?.onBackPressedDispatcher?.addCallback(backCallback)
        onDispose { backCallback.remove() }
    }

    // No auto-submit here on purpose. This used to POST the score every time
    // the home screen composed with a changed level/score/premium flag, which
    // combined with the game-over submit and a 3-attempt retry loop to hammer
    // the endpoint into HTTP 429 — the "leaderboard error" players kept seeing.
    // Submission now happens once, at the end of a run (GameViewModel), and on
    // demand from the leaderboard screen.

    val playPressed: () -> Unit = {
        markTutorialSeen()
        if (livesManager.tryConsumeLife()) onPlay() else showNoLives = true
    }

    if (showNoLives) {
        NoLivesDialog(
            livesManager = livesManager,
            onWatchAd = { showNoLives = false },
            onGoPremium = { showNoLives = false; onShop() },
            onDismiss = { showNoLives = false }
        )
    }

    if (showRateUs) {
        RateUsPrompt(
            onSkip = { showRateUs = false },
            onOpenStore = { _ ->
                saveManager.rateLastAskedAt = System.currentTimeMillis()
                saveManager.rateLeftForStore = true
                showRateUs = false
            },
            onLater = {
                saveManager.rateLastAskedAt = System.currentTimeMillis()
                showRateUs = false
            },
            onNever = {
                saveManager.rateDeclined = true
                showRateUs = false
            }
        )
    }

    if (showDailyReward) {
        DailyRewardDialog(
            streak = saveManager.dailyStreak,
            onClaim = {
                val reward = saveManager.claimDailyReward()
                dailyRewardClaimed = reward
                ProgressState.sync(saveManager)
                showDailyReward = false
            },
            onDismiss = { showDailyReward = false }
        )
    }


    Box(modifier = Modifier.fillMaxSize()) {
        NeonBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            HeaderBar(
                hearts = livesManager.lives,
                coins = ProgressState.coins.longValue,
                isPremium = ProgressState.isPremium.value,
                onSettings = { markTutorialSeen(); onSettings() }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(2.dp))

                // ── Hero ────────────────────────────────────────────────
                // One row, not a stack.
                //
                // The ball, the wordmark and the level pill used to be three
                // stacked blocks, about 196dp in total. Stacked like that, and
                // with the play button plus the action grid pinned below them,
                // the scrolling viewport on a 6.1" phone was left with roughly
                // the height of one card — so the level preview was half cut off
                // and the leaderboard strip was barely a sliver. Side by side
                // the same content is about 70dp, which is the difference
                // between seeing the screen and scrolling to find it.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Glow sized to the ball it lights, not a fixed 220dp
                        // box that pushed everything below it down the screen.
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .blur(20.dp)
                                .background(c.emerald.copy(alpha = 0.16f), RoundedCornerShape(42.dp))
                        )
                        FloatingBall(
                            ballSize = 52.dp,
                            modifier = Modifier.size(52.dp)
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NeonLogo(showTutorial)
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(c.surface)
                                .border(1.dp, c.cardBorder, RoundedCornerShape(50.dp))
                                .shadow(6.dp, RoundedCornerShape(50.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
                                .padding(horizontal = 14.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.my_level, saveManager.currentLevel),
                                style = TextStyle(
                                    color = c.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.6.sp
                                )
                            )
                        }
                        // A personal best is the cheapest long-term goal in
                        // the game: it is already recorded by SaveManager and
                        // it gives the next run a number to beat. Hidden until
                        // the first finished run so a new player is not shown
                        // a meaningless zero.
                        val bestRun = saveManager.statBestRunScore
                        if (bestRun > 0) {
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(c.chip)
                                    .border(1.dp, c.cardBorder, RoundedCornerShape(50.dp))
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "🏆 رکورد ${formatNumber(bestRun)}",
                                    style = TextStyle(
                                        color = c.textSecondary,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }


                // ── What is on the home screen, and why ───────────────────
                //
                // This column used to carry five stacked cards: level preview,
                // leaderboard strip, daily missions, a ~124dp premium pitch and
                // the ad banner. On a 6.1" phone, with the play button, the
                // "how to play" strip and a two-row action grid pinned below, the
                // scroll viewport was left showing roughly one card — so the
                // screen read as clutter and the player had to scroll to find out
                // what the game even is.
                //
                // Now: the level preview (what you are playing) and the daily
                // missions (what to do) are the only two cards. The leaderboard
                // is already one of the four action tiles below, the premium
                // pitch is a compact strip rather than a hero card, and the
                // banner sits at the very bottom of the scroll where it is
                // visible without being in the way.
                LevelPreviewCard(saveManager = saveManager)

                Spacer(Modifier.height(12.dp))

                DailyMissionsCard(saveManager = saveManager)

                if (showTutorial) {
                    Spacer(Modifier.height(4.dp))
                    TutorialHint(text = stringResource(R.string.tap_to_start))
                }

                // The premium pitch, compressed to a single line.
                //
                // It was a ~124dp card with a headline, a body paragraph, a
                // feature list and a button — the largest object on a screen
                // whose job is to start a game. Everything except the one line
                // that says what premium does and one button to get there was
                // restating the same thing.
                if (!ProgressState.isPremium.value) {
                    Spacer(Modifier.height(14.dp))
                    CompactPremiumStrip(
                        isNewPlayer = showTutorial,
                        onClick = { markTutorialSeen(); onShop() }
                    )
                }

                Spacer(Modifier.height(10.dp))

                Spacer(Modifier.height(12.dp))
            }

            // The play button and the action row are PINNED below the scroll
            // area. They used to live at the bottom of the scrolling column,
            // which pushed the single most important control in the app below
            // the fold — hidden behind the action grid and only reachable after
            // a scroll most players never do.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                if (showTutorial) {
                    Box(modifier = Modifier.matchParentSize()) {
                        ir.amiraligrr.blockboom.ui.components.TutorialPulseRing(
                            show = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                ir.amiraligrr.blockboom.ui.components.NeonButton(
                    text = stringResource(R.string.start_game),
                    onClick = playPressed,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = c.emerald,
                    borderColor = Color.White,
                    contentColor = Color.White,
                    size = ButtonSize.XLarge,
                    icon = AppIcon.PLAY
                )
            }

            Spacer(Modifier.height(8.dp))

            // Pinned main buttons — always visible in front of eyes (4 buttons, clean)
            ActionButtonRow(
                onShop = { markTutorialSeen(); onShop() },
                onUpgrade = { markTutorialSeen(); onUpgrade() },
                onLeaderboard = { markTutorialSeen(); onLeaderboard() },
                onStats = { markTutorialSeen(); onStats() },
                onSupport = { markTutorialSeen(); onSupport() },
                onInvite = { markTutorialSeen(); onInvite() }
            )

        }
    }

    // ── Toasts ───────────────────────────────────────────────────────
    //
    // Emitted after the screen above, never before it. A NavHost destination
    // stacks its children, so emission order is paint order: emitted first,
    // these sat underneath `NeonBackground()`'s opaque full-screen gradient
    // and were never seen at all. They are the three banners that tell the
    // player a purchase, a rebalance or the daily reward actually paid out, so
    // "nothing happened" was the message they were built to replace.

    deliveredReward?.let {
        LaunchedEffect(it) {
            kotlinx.coroutines.delay(3200)
            deliveredReward = null
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(
                modifier = Modifier
                    .padding(top = 84.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFA7F3D0), Color(0xFF10B981))))
                    .border(1.4.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.reward_delivered, it),
                    color = Color(0xFF064E3B),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
    balanceRefund?.let {
        LaunchedEffect(it) {
            kotlinx.coroutines.delay(5200)
            balanceRefund = null
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(
                modifier = Modifier
                    .padding(top = 84.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))))
                    .border(1.4.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.balance_refund, it),
                    color = Color(0xFF78350F),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
    dailyRewardClaimed?.let { reward ->
        LaunchedEffect(reward) {
            kotlinx.coroutines.delay(2200)
            dailyRewardClaimed = null
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(
                modifier = Modifier
                    .padding(top = 84.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))))
                    .border(1.4.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "+$reward  🪙",
                    color = Color(0xFF78350F),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }

    // ── Server announcements ───────────────────────────────────────────────
    //
    // Fetched on launch and consumed here, so an announcement posted in the
    // admin panel actually reaches the player. These are the same
    // fetch_notifications.php rows the panel writes to; until now nothing read
    // them back.
    //
    // A card rather than another toast, because an announcement has a title
    // and a sentence and does not fit on one line — and a message that is
    // truncated to fit a toast is a message nobody can act on. It sits until
    // dismissed, or eight seconds if left alone, and every announcement goes
    // on the stack rather than overwriting the one before it.
    val announcements by app.announcements.collectAsState()
    if (announcements.isNotEmpty()) {
        AnnouncementStack(
            announcements = announcements,
            onDismiss = { app.consumeAnnouncements() }
        )
    }
}

/**
 * Server announcements, newest on top.
 *
 * Auto-dismisses after eight seconds so a player who is mid-run is not left
 * with a card over the board; a tap dismisses immediately. Held in local state
 * rather than read from the flow directly so that dismissing is instant and
 * does not wait for the flow to round-trip.
 */
@Composable
private fun AnnouncementStack(
    announcements: List<ir.amiraligrr.blockboom.data.RewardService.Announcement>,
    onDismiss: () -> Unit
) {
    val c = joyThemeColors()
    var visible by remember(announcements) { mutableStateOf(true) }

    LaunchedEffect(announcements) {
        kotlinx.coroutines.delay(8_000)
        visible = false
    }

    if (!visible) {
        // One frame later, so the exit is not fought by the recomposition.
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .padding(top = 76.dp, start = 14.dp, end = 14.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            announcements.take(3).forEach { a ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.surface)
                        .border(1.4.dp, c.emerald.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .clickable { visible = false }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(c.emerald.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📣", fontSize = 17.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = a.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = c.textPrimary,
                                fontWeight = FontWeight.Black
                            ),
                            maxLines = 1
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = a.message,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = c.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            ),
                            maxLines = 4
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Level preview — GLASS + BOSS TEASER
// ─────────────────────────────────────────────────────────────

/**
 * The boss is the last step of the cycle, and the cycle is as long as the engine
 * says it is.
 *
 * It used to be a hard-coded five, matching a second hard-coded five elsewhere in
 * this file, while [GameEngine] spawned a boss every ten. The card told the player
 * "next level: boss!" one level before a boss existed. See
 * [GameEngine.BOSS_EVERY_N_LEVELS].
 */
private val BOSS_CYCLE = GameEngine.BOSS_EVERY_N_LEVELS

private fun isBossStep(step: Int): Boolean = step == BOSS_CYCLE

@Composable
fun LevelPreviewCard(saveManager: SaveManager) {
    val c = joyThemeColors()

    val level = saveManager.currentLevel
    // `currentLevel` is the level that is about to be *played*, and the engine
    // makes a boss of the level when `level % BOSS_CYCLE == 0`. So the count of
    // levels already cleared inside the current cycle is one behind it: at the
    // start of a cycle it is 0, and on the boss level itself it is
    // `BOSS_CYCLE - 1`.
    //
    // Using `level % BOSS_CYCLE` instead — the level *is* the cleared count —
    // was wrong at both ends of the cycle and worst in the middle of the one
    // that matters: on a boss level it computed 0, so the card announced "10
    // levels to boss" and an empty progress bar on the very level that is the
    // boss, and `isBossNext` was false, so the "next level is a boss" callout
    // could never appear there at all.
    val clearedInCycle = (level - 1).mod(BOSS_CYCLE)
    val rawProgress = clearedInCycle / BOSS_CYCLE.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "bossProg"
    )
    // `untilBoss` counts the boss step itself as one away, so on a boss level
    // this is 1 and `isBossNext` is true — which is what the card needs to say.
    val untilBoss = BOSS_CYCLE - clearedInCycle
    val isBossNext = untilBoss == 1

    val shimmerTrans = rememberInfiniteTransition(label = "bossShimmer")
    val shimmer by shimmerTrans.animateFloat(0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "s")
    val bossPulse by shimmerTrans.animateFloat(0.9f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "bp")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .border(1.dp, c.cardBorder, RoundedCornerShape(22.dp))
            .shadow(12.dp, RoundedCornerShape(22.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))))
                            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AppIconView(
                            icon = AppIcon.TROPHY,
                            tint = Color(0xFF78350F),
                            size = 17.dp,
                            strokeWidth = 2.1.dp
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.next_boss_in),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = c.textSecondary,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )
                        )
                        Text(
                            text = if (isBossNext) {
                                stringResource(R.string.next_level_boss)
                            } else {
                                stringResource(R.string.boss_hint)
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = c.textMuted,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(
                            if (isBossNext) Brush.horizontalGradient(listOf(Color(0xFFFF5A5F), Color(0xFFFF8A3D)))
                            else Brush.horizontalGradient(listOf(Color(0xFF143D2E), Color(0xFF1A5C3F)))
                        )
                        .border(1.dp, if (isBossNext) Color.White.copy(alpha = 0.45f) else NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(50.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .graphicsLayer { scaleX = if (isBossNext) bossPulse else 1f; scaleY = if (isBossNext) bossPulse else 1f },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$untilBoss ${stringResource(R.string.levels)}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (isBossNext) Color.White else NeonCyan,
                            fontWeight = FontWeight.Black
                        )
                    )
                }
            }

            // Track — light clean
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    // Was a literal #ECFDF5. That is a near-white mint with no
                    // dark counterpart, so a player on system dark mode — or
                    // anyone who picked dark in settings — got a brilliant bar
                    // punched through a dark card, and the emerald fill on top
                    // of it had almost no contrast left. c.track is the themed
                    // well, defined for both palettes.
                    .background(c.track)
                    .border(1.dp, c.trackBorder, RoundedCornerShape(8.dp))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        // The travelling gradient is built here, in the draw
                        // phase. Passing `shimmer` into Brush.horizontalGradient
                        // in the modifier chain made this whole card recompose
                        // 60x/second — and each recomposition re-read
                        // saveManager.currentLevel from SharedPreferences.
                        .drawBehind {
                            val span = size.width
                            val cx = shimmer * (span + 160f) - 80f
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(NeonGold, NeonCyan, c.emerald),
                                    startX = cx - 160f,
                                    endX = cx + 160f
                                )
                            )
                        }
                )
                // tip spark
                if (animatedProgress in 0.05f..0.97f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .height(8.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color.White, RoundedCornerShape(50.dp))
                                .border(1.5.dp, NeonGold, RoundedCornerShape(50.dp))
                        )
                    }
                }
            }

            // Dots pipeline one full cycle — every level up to and including the boss.
            //
            // Ten dots, not five, because the cycle is ten. The dots are
            // proportionally smaller than the five-dot row they replaced so they
            // still fit the card: 12dp for a normal step, 18dp for the boss,
            // with the connectors taking whatever is left between them.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                for (i in 1..BOSS_CYCLE) {
                    // `reached` used `(level % 5) >= i % 5`, which for the last
                    // step compares against 0 and so is true for every level — the
                    // boss dot read as "cleared" from the very first level of a
                    // fresh game. The boss is the last step of the cycle, so it is
                    // only lit once the cycle has actually reached it.
                    //
                    // It used to be `clearedInCycle >= BOSS_CYCLE`, which is
                    // unreachable: `clearedInCycle` stops one short of the cycle
                    // length, because the cycle resets the moment the boss is
                    // beaten. So the crown never lit at all — least of all on the
                    // boss level, the one level where it is the point.
                    val reached = if (isBossStep(i)) isBossNext else clearedInCycle >= i
                    val isCurrent = if (isBossStep(i)) isBossNext else clearedInCycle + 1 == i
                    val isBoss = isBossStep(i)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val dotBgBrush: Brush? = if (isBoss && reached) Brush.radialGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF6F00))) else null
                        val dotBgColor: Color? = when {
                            isBoss && reached -> null
                            isBoss -> Color(0xFFFEF3C7)
                            reached -> NeonCyan
                            isCurrent -> Color.White
                            else -> c.dotIdle
                        }
                        val dotModifier = Modifier
                            .size(if (isBoss) 18.dp else 12.dp)
                            .clip(RoundedCornerShape(50.dp))
                            .then(if (dotBgBrush != null) Modifier.background(dotBgBrush) else Modifier.background(dotBgColor!!))
                        Box(
                            modifier = dotModifier
                                .border(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = when {
                                        isCurrent -> NeonGold
                                        isBoss && reached -> Color.White.copy(alpha = 0.7f)
                                        else -> c.cardBorder
                                    },
                                    shape = RoundedCornerShape(50.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBoss) Text(text = "👑", fontSize = 9.sp)
                            else if (reached) Text(text = "✓", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                        // Only the endpoints get a number. At 9sp, ten numbers
                        // under ten dots is unreadable clutter; the player is
                        // here for "how long until the boss", not for a lookup
                        // table, and the counter above the bar already says it.
                        if (i == 1 || isBoss) {
                            Text(
                                text = if (isBoss) "رئیس" else "$i",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    // Was `NeonGold`: 1.24:1 on the light card,
                                    // so the "you are here" step number was
                                    // invisible on a fresh game — the one
                                    // moment it matters most. `c.amber` keeps
                                    // the gold meaning at 4.56:1 on the chip
                                    // in light and 10.9:1 on dark.
                                    color = if (isCurrent) c.amber else c.textMuted,
                                    fontSize = 9.sp,
                                    fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Normal
                                )
                            )
                        } else {
                            Spacer(Modifier.height(13.dp))
                        }
                    }
                    if (i < BOSS_CYCLE) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .padding(horizontal = 3.dp)
                                .background(
                                    if (clearedInCycle >= i) NeonCyan.copy(alpha = 0.55f) else c.dotIdle,
                                    RoundedCornerShape(1.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyMissionsCard(saveManager: SaveManager) {
    // The day rollover writes to prefs, so it happens once on entry rather
    // than in the composable body (where it ran on every recomposition).
    val c = joyThemeColors()

    LaunchedEffect(saveManager) { saveManager.refreshMissions() }

    // These are snapshot state on SaveManager now, so reading them here is
    // free and only subscribes this card to its own values.
    //
    // Named rather than b/c/l because `c` is the palette holder everywhere else
    // in this file, and one function shadowing that with a coin counter is
    // exactly the kind of thing that makes a theme migration skip a screen.
    val blocks = saveManager.missionBlocks
    val coins = saveManager.missionCoins
    val lucky = saveManager.missionLucky
    val claimedState = saveManager.missionClaimed
    val complete = saveManager.canClaimMissions()

    // Only the border alpha animates, and it is read inside the draw phase
    // below — reading `glow` here used to recompose the whole card 60x/second.
    val infinite = rememberInfiniteTransition(label = "missionGlow")
    val glow by infinite.animateFloat(0.7f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "glow")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .border(1.dp, c.cardBorder, RoundedCornerShape(22.dp))
            .shadow(12.dp, RoundedCornerShape(22.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.verticalGradient(listOf(c.emerald.copy(alpha = 0.18f), c.emerald.copy(alpha = 0.08f))))
                            .border(1.dp, c.emerald.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text(text = "🎯", fontSize = 14.sp) }
                    Text(
                        text = "ماموریت روزانه",
                        style = MaterialTheme.typography.titleSmall.copy(color = c.textPrimary, fontWeight = FontWeight.Black)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(
                            when {
                                claimedState -> Brush.verticalGradient(listOf(Color(0xFFD1FAE5), Color(0xFFA7F3D0)))
                                complete -> Brush.horizontalGradient(listOf(NeonCyan, c.textPrimary))
                                else -> Brush.verticalGradient(listOf(c.chip, c.dotIdle))
                            }
                        )
                        // `glow` is read inside the draw lambda, not in the
                        // modifier chain. Reading an infinite animation while
                        // composing is what made this card recompose 60x/second.
                        .drawBehind {
                            if (complete && !claimedState) {
                                drawRoundRect(
                                    color = NeonGold.copy(alpha = 0.5f * glow),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(50.dp.toPx()),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                )
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            claimedState -> "✅ انجام شد"
                            complete -> "آماده دریافت!"
                            else -> "${listOf(blocks >= 30, coins >= 80, lucky >= 2).count { it }}/3"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = when {
                                claimedState -> c.textPrimary
                                complete -> Color.White
                                else -> c.textMuted
                            },
                            fontWeight = FontWeight.Black
                        )
                    )
                }
            }

            MissionRow(icon = "🧱", label = "۳۰ بلاک", cur = blocks, max = 30)
            MissionRow(icon = "🪙", label = "۸۰ سکه", cur = coins, max = 80)
            MissionRow(icon = "🍀", label = "۲ شانس", cur = lucky, max = 2)

            if (complete) {
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))))
                        .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        .clickable {
                            // claimMissions() flips the snapshot state, so the
                            // chip and this button both update on their own —
                            // no hand-assigned mirrors needed.
                            saveManager.claimMissions()
                            ProgressState.sync(saveManager)
                        }
                        .heightIn(min = 48.dp)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "🎁", fontSize = 18.sp)
                        Text(
                            // Reads the same constant claimMissions() pays.
                            // This label was hardcoded "دریافت ۳۵۰ سکه" while
                            // the payout was 100 — promising 3.5x on the screen
                            // where the player is most eager to collect.
                            text = "دریافت ${SaveManager.MISSION_REWARD_COINS} سکه",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = Color(0xFF78350F),
                                fontWeight = FontWeight.Black
                            )
                        )
                    }
                }
            } else if (claimedState) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surfaceSoft)
                        .border(1.dp, c.cardBorder, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "فردا دوباره بیا! ⏰", color = c.textMuted, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun MissionRow(icon: String, label: String, cur: Int, max: Int) {
    val c = joyThemeColors()

    val pAnim by animateFloatAsState(
        targetValue = (cur.toFloat() / max).coerceIn(0f, 1f),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "missionP"
    )
    val done = cur >= max
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (done) Brush.verticalGradient(listOf(NeonCyan.copy(alpha = 0.22f), NeonCyan.copy(alpha = 0.10f)))
                    // A literal white second stop turned this icon plate into a
                    // white square in dark mode. Use theme surfaces for both.
                    else Brush.verticalGradient(listOf(c.chip, c.surfaceSoft))
                )
                .border(1.dp, if (done) NeonCyan.copy(alpha = 0.28f) else c.cardBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { Text(text = icon, fontSize = 15.sp) }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = if (done) c.textPrimary else c.textSecondary, fontWeight = if (done) FontWeight.Bold else FontWeight.Normal)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(c.track)
                .border(1.dp, c.trackBorder, RoundedCornerShape(6.dp))
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pAnim)
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (done) Brush.horizontalGradient(listOf(NeonCyan, NeonGold))
                        else Brush.horizontalGradient(listOf(c.textPrimary.copy(alpha = 0.18f), c.textPrimary.copy(alpha = 0.10f)))
                    )
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(if (done) NeonCyan.copy(alpha = 0.14f) else c.chip)
                .border(1.dp, if (done) NeonCyan.copy(alpha = 0.22f) else c.cardBorder, RoundedCornerShape(50.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (done) "✓" else "$cur/$max",
                color = if (done) c.textPrimary else c.textMuted,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, fontSize = 11.sp)
            )
        }
    }
}

/** Fixed-size tiles. */
private val ACTION_ROW_HEIGHT = 54.dp
private val ACTION_GAP = 6.dp

@Composable
fun ActionButtonRow(
    onShop: () -> Unit,
    onUpgrade: () -> Unit,
    onLeaderboard: () -> Unit = {},
    onStats: () -> Unit = {},
    onSupport: () -> Unit = {},
    onInvite: () -> Unit = {},
) {
    // Three rows of 54dp with a 6dp gap is 174dp, down from 206dp with the
    // previous row heights and gap. The difference is not cosmetic: the
    // scrolling column above has `weight(1f)`, so every dp taken here is a dp
    // removed from the cards the player is actually meant to read.
    Column(verticalArrangement = Arrangement.spacedBy(ACTION_GAP)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ACTION_GAP)
        ) {
            ActionButton(
                icon = "🛒",
                label = stringResource(R.string.shop),
                sub = "سکه و اسکین",
                vectorIcon = AppIcon.SHOP,
                // The top stop used to be #34D399: white 14sp-Black on it is
                // ~2.2:1, so the top half of every label washed out. These
                // deeper stops keep the colour identity and put both the main
                // label and the small subtitle over 5:1, not just over the
                // large-text floor.
                gradient = Brush.verticalGradient(listOf(Color(0xFF047857), Color(0xFF065F46))),
                accent = NeonCyan,
                onClick = onShop,
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                icon = "⚡",
                label = stringResource(R.string.upgrade),
                sub = "قدرت بیشتر",
                vectorIcon = AppIcon.UPGRADE,
                gradient = Brush.verticalGradient(listOf(Color(0xFFFEF3C7), Color(0xFFF59E0B))),
                accent = Color.White,
                darkText = true,
                onClick = onUpgrade,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ACTION_GAP)
        ) {
            ActionButton(
                icon = "🏆",
                label = "لیدربرد",
                sub = "برترین‌ها",
                vectorIcon = AppIcon.TROPHY,
                // White on #1893CE was 3.44:1 — fine for a large label but
                // washed out for the small subtitle. #0369A1 is 5.93:1 and
                // still unmistakably the blue tile.
                gradient = Brush.verticalGradient(listOf(Color(0xFF0369A1), Color(0xFF075985))),
                accent = Color.White,
                onClick = onLeaderboard,
                modifier = Modifier.weight(1f)
            )
            // Support used to live behind Settings, two taps away, and settings
            // itself was one of these four buttons. That made the one screen a
            // player reaches *because something is broken* the hardest one to
            // find — and it is exactly the screen someone needs after a factory
            // reset, when nothing looks right and nothing can be filed about it
            // from where they are standing.
            ActionButton(
                icon = "🎧",
                label = "پشتیبانی",
                sub = "تیکت و کمک",
                vectorIcon = AppIcon.SUPPORT,
                // White on #0C9C88 was 3.43:1. #0F766E is 5.47:1 and keeps
                // the calm teal identity without washing out the subtitle.
                gradient = Brush.verticalGradient(listOf(Color(0xFF0F766E), Color(0xFF115E59))),
                accent = NeonCyan,
                onClick = onSupport,
                modifier = Modifier.weight(1f)
            )
        }

        // The stats tile replaced the "how to play" tile. The tutorial is no
        // longer a home-screen tile: it lives in the game where it can actually
        // be practised, and this slot now turns player curiosity into
        // engagement through the stats screen.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ACTION_GAP)
        ) {
            ActionButton(
                icon = "📊",
                label = stringResource(R.string.stats_title),
                sub = stringResource(R.string.stats_records),
                vectorIcon = AppIcon.TROPHY,
                // White on #A78BFA was 2.72:1. #7C3AED is 5.70:1 and still
                // reads as the violet records tile.
                gradient = Brush.verticalGradient(listOf(Color(0xFF7C3AED), Color(0xFF5B21B6))),
                accent = Color.White,
                onClick = onStats,
                modifier = Modifier.weight(1f)
            )
            // Invite. The referral pipeline has existed on the server and in
            // RewardService the whole time, with nothing in the app calling it —
            // so no code was ever minted and no player could enter one. It is
            // the cheapest revenue the game has: it only pays when someone
            // installs who otherwise would not have, and it costs nothing.
            ActionButton(
                icon = "🎁",
                label = "دعوت",
                sub = "سکه بگیر",
                vectorIcon = AppIcon.GIFT,
                gradient = Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFD97706))),
                accent = Color.White,
                darkText = true,
                onClick = onInvite,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ActionButton(
    icon: String,
    label: String,
    sub: String,
    gradient: Brush,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    darkText: Boolean = false,
    /**
     * Drawn instead of [icon] when supplied.
     *
     * The emoji is kept as the fallback rather than removed so every call
     * site keeps compiling, but the five buttons on the home screen pass a
     * real vector: the emoji is chosen by the OS, changes with the system
     * font, cannot be tinted to match the tile it sits in, and is unreadable
     * at this size on a cheap panel.
     */
    vectorIcon: AppIcon? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "actionScale"
    )
    val textColor = if (darkText) Color(0xFF78350F) else Color.White
    val subColor = if (darkText) Color(0xFF92400E).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.78f)

    Box(
        modifier = modifier
            .height(ACTION_ROW_HEIGHT)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(gradient)
            .border(1.2.dp, Color.White.copy(alpha = if (darkText) 0.6f else 0.22f), RoundedCornerShape(18.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // No top gloss.
        //
        // There used to be a white 18%-alpha strip across the top 22dp for
        // depth. On a bright tile it is exactly where the label's glyphs sit,
        // and white text over white gloss is what washed the tops of the
        // labels out (~2.7:1 measured). Depth still reads from the border, the
        // shadow and the press scale; a sheen that eats the label is not depth.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.White.copy(alpha = if (darkText) 0.26f else 0.18f))
                    .border(1.dp, Color.White.copy(alpha = 0.26f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                // The vector when there is one, the emoji otherwise. The icon
                // plate is a fixed 32dp so a vector and an emoji occupy the
                // same optical weight in the same row.
                if (vectorIcon != null) {
                    AppIconView(
                        icon = vectorIcon,
                        tint = if (darkText) Color(0xFF78350F) else Color.White,
                        size = 18.dp,
                        strokeWidth = 2.1.dp
                    )
                } else {
                    Text(text = icon, fontSize = 17.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = textColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        letterSpacing = 0.2.sp,
                        lineHeight = 18.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = subColor,
                        fontSize = 9.5.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DailyRewardDialog(
    streak: Int,
    onClaim: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = joyThemeColors()

    val saveManager = (LocalContext.current.applicationContext as BlockBoomApp).saveManager
    // The same amount claimDailyReward() pays, so the "2x" button can credit
    // the second half without the dialog needing the result of the first claim.
    // This was a second, independent copy of the reward formula — and it did not
    // match claimDailyReward(), which paid (40 * streak) capped at 200. So the
    // dialog offered up to 700 and the ad doubled up to 700 more, for a real
    // payout of 200. Read it from SaveManager now; there is one definition.
    val rewardForStreak = saveManager.dailyRewardForStreak(streak + 1)
    var doubledByAd by remember { mutableStateOf(false) }
    var adLoading by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(c.surface)
                .border(1.2.dp, c.cardBorder, RoundedCornerShape(26.dp))
                .shadow(20.dp, RoundedCornerShape(26.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
                .padding(22.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))))
                        .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(text = "🎁", fontSize = 30.sp) }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.daily_reward_title),
                    style = MaterialTheme.typography.headlineSmall.copy(color = c.textPrimary, fontWeight = FontWeight.Black)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.daily_reward_desc),
                    style = MaterialTheme.typography.bodyMedium.copy(color = c.textMuted),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 1..7) {
                        val isActive = i <= streak + 1
                        val isDone = i <= streak
                        val isCurrent = i == streak + 1
                        val chipBg2: Brush = when {
                            isDone -> Brush.verticalGradient(listOf(NeonCyan, Color(0xFF0D9488)))
                            isCurrent -> Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)))
                            else -> Brush.verticalGradient(listOf(c.chip, c.dotIdle))
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(chipBg2)
                                .border(
                                    width = if (isCurrent) 1.6.dp else 1.dp,
                                    color = when {
                                        isDone -> NeonCyan.copy(alpha = 0.55f)
                                        isCurrent -> Color.White.copy(alpha = 0.85f)
                                        else -> Color.White.copy(alpha = 0.08f)
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .graphicsLayer { scaleX = if (isCurrent) 1.08f else 1f; scaleY = if (isCurrent) 1.08f else 1f },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isDone) "✓" else "$i",
                                color = when {
                                    // The completed chip is bright NeonCyan;
                                    // a white tick on it was 1.8:1. Dark ink
                                    // keeps the tick legible in both themes.
                                    isDone -> Color(0xFF04211A)
                                    isCurrent -> Color(0xFF78350F)
                                    else -> c.textMuted
                                },
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        // The old literal #ECFDF5 stayed near-white in dark
                        // mode, leaving the themed light reward text on a
                        // light pill. Use the theme well instead.
                        .background(c.surfaceSoft)
                        .border(1.dp, c.cardBorder, RoundedCornerShape(50.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                Text(
                    // From SaveManager, not a local `100 * (streak + 1)`. The
                    // dialog used to display up to 700 while claimDailyReward()
                    // paid at most 200 — and the "2× by watching an ad" path
                    // doubled the displayed figure, so the ad promised a
                    // number the game had no intention of paying.
                    text = "${saveManager.dailyRewardForStreak(streak + 1)} 🪙",
                    color = c.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
                }
                Spacer(Modifier.height(18.dp))
                ir.amiraligrr.blockboom.ui.components.NeonButton(
                    text = stringResource(R.string.claim_now) + "  ✨",
                    onClick = onClaim,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = NeonGold,
                    borderColor = Color.White,
                    contentColor = Color(0xFF78350F),
                    size = ButtonSize.Medium
                )
                // "2x by watching" on the daily reward.
                //
                // This is the highest-intent rewarded-ad moment in the game:
                // the player is about to claim something, already wants more of
                // it, and the doubling costs them one video. Rewarded video is
                // also several times the RPM of a banner, so a handful of these
                // per day is worth more than any amount of extra banner
                // impressions — and unlike forcing a banner it doesn't push
                // anyone away.
                if (!doubledByAd) {
                    Spacer(Modifier.height(10.dp))
                    ir.amiraligrr.blockboom.ui.components.NeonButton(
                        text = "🎬  با تماشای تبلیغ، دو برابر بگیر",
                        onClick = {
                            adLoading = true
                            AdManager.showRewarded(
                                onReward = {
                                    adLoading = false
                                    doubledByAd = true
                                    onClaim()
                                    // onClaim closes the sheet; the extra award
                                    // is credited straight after.
                                    saveManager.addCoins(rewardForStreak)
                                    ProgressState.sync(saveManager)
                                },
                                onFail = {
                                    adLoading = false
                                    // Ads can legitimately be unavailable (no
                                    // fill, or the pre-load has not landed
                                    // yet). Never punish the player for that.
                                    onClaim()
                                }
                            )
                        },
                        enabled = !adLoading,
                        modifier = Modifier.fillMaxWidth(),
                        // White looked right in light mode but left the
                        // themed light text on a white button in dark mode.
                        // The surface follows the theme; the gold border keeps
                        // it visually secondary to the claim button.
                        containerColor = c.surface,
                        borderColor = NeonGold,
                        contentColor = c.textPrimary,
                        size = ButtonSize.Medium
                    )
                }
                Spacer(Modifier.height(10.dp))
                ir.amiraligrr.blockboom.ui.components.NeonButton(
                    text = stringResource(R.string.back),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = c.chip,
                    borderColor = c.cardBorder,
                    contentColor = c.textMuted,
                    size = ButtonSize.Medium
                )
            }
        }
    }
}
