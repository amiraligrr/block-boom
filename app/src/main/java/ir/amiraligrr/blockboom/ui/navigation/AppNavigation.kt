package ir.amiraligrr.blockboom.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ir.amiraligrr.blockboom.BlockBoomApp
import ir.amiraligrr.blockboom.ui.GameViewModel
import ir.amiraligrr.blockboom.data.AccessState
import ir.amiraligrr.blockboom.data.AdSlot
import ir.amiraligrr.blockboom.data.AppConfigRepository
import ir.amiraligrr.blockboom.data.PlayerSyncManager
import ir.amiraligrr.blockboom.data.ProgressState
import ir.amiraligrr.blockboom.ui.components.AccessBlockedScreen
import ir.amiraligrr.blockboom.ui.screens.GameOverScreen
import ir.amiraligrr.blockboom.ui.screens.GameScreen
import ir.amiraligrr.blockboom.ui.screens.HomeScreen
import ir.amiraligrr.blockboom.ui.screens.LeaderboardScreen
import ir.amiraligrr.blockboom.ui.screens.OnboardingScreen
import ir.amiraligrr.blockboom.ui.screens.SettingsScreen
import ir.amiraligrr.blockboom.ui.screens.SupportScreen
import ir.amiraligrr.blockboom.ui.screens.ShopScreen
import ir.amiraligrr.blockboom.ui.screens.StatsScreen
import ir.amiraligrr.blockboom.ui.screens.UpgradeScreen
import ir.amiraligrr.blockboom.ui.screens.InviteFriendsScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Game : Screen("game")
    data object Upgrade : Screen("upgrade")
    data object GameOver : Screen("game_over")
    data object Shop : Screen("shop")
    data object Settings : Screen("settings")
    data object Leaderboard : Screen("leaderboard")
    data object Onboarding : Screen("onboarding")
    data object Support : Screen("support")
    data object Stats : Screen("stats")
    data object Invite : Screen("invite")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val gameViewModel: GameViewModel = viewModel()
    val saveManager = rememberSaveManager()
    val showOnboarding = remember(saveManager) { mutableStateOf(!saveManager.onboardingCompleted) }
    val submitState by gameViewModel.submitState.collectAsState()
    val supportOverride = remember { mutableStateOf(false) }

    val context = LocalContext.current
    val soundManager = (context.applicationContext as BlockBoomApp).soundManager

    // Every "go home" exit funnels through here.
    //
    // `popBackStack(route, false)` only *pops to* a destination that is already
    // on the stack; when it is not, the call logs and returns false without
    // navigating. Home is not always on the stack — onboarding sends the player
    // straight into the game, so a first-run session has exactly one entry,
    // Game — which left the pause overlay's "home", game-over's "home" and the
    // system back gesture all silently doing nothing. The player could not get
    // out of the game except by killing the process.
    //
    // Falling back to a plain navigate means the exit always works, whatever
    // shape the stack happens to be in.
    val goHome: () -> Unit = {
        if (!navController.popBackStack(Screen.Home.route, inclusive = false)) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Game.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // ── The gate ────────────────────────────────────────────────────────
    // Runs before any destination: a banned device or a disabled build never
    // reaches the NavHost, so there is no back gesture, deep link, or restored
    // back stack that gets them into a game they are not allowed to play.
    AppConfigRepository.init(context)
    val access by AppConfigRepository.state.collectAsState()

    // Re-check when the app comes back to the foreground, so an admin ban or a
    // version switch takes effect without the user reinstalling.
    val gateOwner = LocalLifecycleOwner.current
    DisposableEffect(gateOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START) AppConfigRepository.check()
        }
        gateOwner.lifecycle.addObserver(obs)
        onDispose { gateOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) { AppConfigRepository.check() }

    when (val a = access) {
        is AccessState.Banned, is AccessState.VersionBlocked -> {
            AccessBlockedScreen(state = a) {
                // A banned player can still reach support — that is the point
                // of the button.
                AppConfigRepository.reset()
                supportOverride.value = true
            }
            return
        }
        else -> Unit
    }

    // Support is reachable both from Settings and from the ban screen. The
    // override lets the ban screen open it without going through navigation,
    // which is not composed while blocked.
    if (supportOverride.value) {
        SupportScreen(onBack = {
            supportOverride.value = false
            AppConfigRepository.check(force = true)
        })
        return
    }

    // Music is owned here, by route, rather than by individual screens.
    //
    // It used to be started by HomeScreen and GameScreen and paused in their
    // onDispose. Navigation Compose disposes the previous destination when you
    // navigate forward, so tapping Shop, Settings, Leaderboard or Upgrade
    // paused the music and left the app completely silent on six of its nine
    // screens — including Game Over, right after the loudest run.
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    LaunchedEffect(route) {
        if (route == null) return@LaunchedEffect
        if (saveManager.onboardingCompleted.not() && route == Screen.Onboarding.route) {
            soundManager.pauseMusic()
        } else if (route == Screen.Game.route) {
            soundManager.startGameMusic()
        } else {
            soundManager.startHomeMusic()
        }
    }

    // One lifecycle observer for the whole app, instead of one per screen that
    // only existed on two of them.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> soundManager.pauseMusic()
                Lifecycle.Event.ON_RESUME -> soundManager.resumeMusic()
                // Closing or the process holding the activity is the last
                // useful moment to write today's bucket and the progress
                // merge: after this the user is offline or unreachable, and a
                // missing sync is the one error a reinstall cannot fix.
                Lifecycle.Event.ON_STOP -> PlayerSyncManager.syncNow(force = true)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── One ad slot, for the whole app ────────────────────────────────────
    //
    // Every screen used to draw its own `AdiveryBanner` and put it somewhere
    // different: pinned to the top of the game screen, at the bottom of the
    // scroll on the home screen, floating over the content on the game-over
    // screen, and inside the content column on the shop. That is what "the ads
    // move around" means to a player, and it cost a different set of bugs each
    // time — the game-over slot had to reserve space by hand so it stopped
    // covering the last button.
    //
    // There is exactly one slot now, below the NavHost, at a fixed height on
    // every screen. The consequences are all in its favour:
    //
    //  - it can never cover a control, because nothing is drawn underneath it
    //  - the space it takes is identical everywhere, so no screen reflows when
    //    navigating between screens
    //  - the play field shrinks by one constant amount rather than by whatever
    //    the screen underneath decided to reserve
    //  - it is loaded once and stays loaded across navigation, because the slot
    //    is never torn down
    //
// Premium players get no slot at all, so their layout is not missing 60dp of
    // empty band — which would itself be an advert-shaped hole.
    //
    // ── Insets are applied ONCE, here, and nowhere else ──────────────────
    // Both the NavHost and the ad slot used to carry `safeDrawingPadding()`, and
    // `safeDrawingPadding` pads *all four* edges. In a genuinely edge-to-edge app
    // — `enableEdgeToEdge()` plus transparent system bars — that produced a band
    // of nothing under the game on every screen, roughly 156dp tall:
    //
    //   [ nav bar ]   <- charged by the NavHost's bottom padding
    //   [ status bar ] <- charged by the SLOT's TOP padding, directly above the ad
    //   [ ad, 60dp ]
    //   [ nav bar ]   <- charged again by the slot's bottom padding
    //
    // The status-bar-sized strip immediately above the banner is the "empty gap
    // above the ad under the game" complaint. The navigation bar was charged
    // twice. Neither was ever visible on purpose.
    //
    // Applying `safeDrawing` to this root Column instead puts the status bar
    // above everything, the navigation bar below everything, and leaves the
    // interior — NavHost and ad slot — flush against each other. `AdManager`'s
    // own doc comment already asked for "no extra navigation inset"; this is
    // what that looks like when it is actually honoured.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
    // ── Screenshot / QA hook ────────────────────────────────────────────
        //
        // `adb shell am start -e bb_route shop` opens a destination directly.
        //
        // Exists because this app is frequently reviewed on a device where
        // touch injection is blocked (MIUI with USB security on): `input tap`
        // throws SecurityException and there is no other way to reach anything
        // past the home screen, so every screen below it would be unreviewable.
        //
        // Gated on a debug build and on the app being debuggable, so a release
        // APK ignores the extra entirely — a shipping binary cannot be walked
        // through its own screens by anything that can send it an intent.
        val qaRoute = remember {
            val debuggable = try {
                (context.applicationInfo.flags and
                    android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            } catch (_: Throwable) { false }
            if (debuggable) {
                (context as? android.app.Activity)
                    ?.intent?.getStringExtra("bb_route")
            } else null
        }
        LaunchedEffect(qaRoute) {
            val route = qaRoute?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
            // Only real destinations, so a typo cannot navigate somewhere
            // undefined and crash the nav graph during a test run.
            val known = listOf(
                Screen.Home, Screen.Game, Screen.Upgrade, Screen.GameOver,
                Screen.Shop, Screen.Settings, Screen.Leaderboard,
                Screen.Onboarding, Screen.Support, Screen.Stats, Screen.Invite
            ).map { it.route }
            if (route in known) {
                navController.navigate(route) { launchSingleTop = true }
            }
        }

        NavHost(
        navController = navController,
        startDestination = if (showOnboarding.value) Screen.Onboarding.route else Screen.Home.route,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it / 4 },
                animationSpec = tween(360, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(260)) + scaleIn(initialScale = 0.97f, animationSpec = tween(360, easing = FastOutSlowInEasing))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 4 },
                animationSpec = tween(320, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(240)) + scaleOut(targetScale = 0.98f, animationSpec = tween(320))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 4 },
                animationSpec = tween(360, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(260)) + scaleIn(initialScale = 0.97f, animationSpec = tween(360))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it / 4 },
                animationSpec = tween(320, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(240)) + scaleOut(targetScale = 0.98f)
        }
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                saveManager = saveManager,
                onComplete = {
                    showOnboarding.value = false
                    // A first launch goes straight into the game, with the coach
                    // already armed, rather than to the home screen.
                    //
                    // Welcome / name / language teaches the player nothing about
                    // the game. Landing them on the home screen meant the first
                    // thing they had to work out on their own was what the play
                    // button does; the coach — five steps, each waiting for the
                    // action it is describing — was only reachable from behind
                    // that button. The instruction and the thing being instructed
                    // should be the same screen.
                    //
                    // No heart is spent: the coach runs inside the level the
                    // player was going to play anyway, and `GameScreen` only
                    // consumes one on the first real death.
                    gameViewModel.startNewGame()
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            // `LocalActivity` rather than `LocalContext as ComponentActivity`.
            //
            // The cast is a hard assumption that the context is the activity: it
            // throws in a preview or a test, and it cannot express "there is no
            // activity", which is a real state during configuration change. The
            // Compose-aware accessor returns the same instance in production and
            // null everywhere else, so the two call sites below degrade instead of
            // crashing.
            val activity = LocalActivity.current
            HomeScreen(
                onPlay = {
                    gameViewModel.startNewGame()
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.Home.route)
                        launchSingleTop = true
                    }
                },
                onShop = { navController.navigate(Screen.Shop.route) },
                onSettings = { navController.navigate(Screen.Settings.route) },
                onUpgrade = {
                    navController.navigate(Screen.Upgrade.route) {
                        launchSingleTop = true
                    }
                },
                onLeaderboard = {
                    navController.navigate(Screen.Leaderboard.route) {
                        launchSingleTop = true
                    }
                },
                onStats = {
                    navController.navigate(Screen.Stats.route) {
                        launchSingleTop = true
                    }
                },
                onSupport = {
                    navController.navigate(Screen.Support.route) {
                        launchSingleTop = true
                    }
                },
                onInvite = {
                    navController.navigate(Screen.Invite.route) {
                        launchSingleTop = true
                    }
                },
                onExit = { activity?.finish() }
            )
        }
        composable(Screen.Game.route) {
            val saveManager = rememberSaveManager()
            // Read here, in the composable body, because `onLevelContinue` is an
            // ordinary lambda: it is called later from a callback, and a
            // CompositionLocal cannot be read from there.
            val gameActivity = LocalActivity.current
            GameScreen(
                engine = gameViewModel.engine,
                initialCoins = saveManager.coins,
                onLevelContinue = {
                    // A level clear is the one moment in this game where the
                    // player is guaranteed to still be engaged, so it is the
                    // only place an interstitial is offered. AdManager caps it
                    // at one per 3 levels and skips it when nothing is loaded.
                    ir.amiraligrr.blockboom.data.AdManager
                        .maybeShowInterstitialOnLevelComplete(
                            // Null-tolerant on purpose: `maybeShowInterstitial`
                            // treats a null activity as "skip and carry on", which
                            // is the correct behaviour during a configuration
                            // change and preferable to a cast that throws.
                            activity = gameActivity,
                            isPremium = saveManager.isPremium
                        ) {
                            navController.navigate(Screen.Upgrade.route) {
                                launchSingleTop = true
                            }
                        }
                },
                onGameOver = {
                    navController.navigate(Screen.GameOver.route) {
                        launchSingleTop = true
                    }
                },
                onExit = {
                    gameViewModel.quitToHome()
                    goHome()
                }
            )
        }
        composable(Screen.Upgrade.route) {
            val isAfterLevel = gameViewModel.engine.status == ir.amiraligrr.blockboom.domain.GameStatus.LevelComplete
            UpgradeScreen(
                engine = gameViewModel.engine,
                showLevelHeader = isAfterLevel,
                onContinue = {
                    if (isAfterLevel) {
                        gameViewModel.startNextLevel()
                    }
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.GameOver.route) {
            val saveManager = rememberSaveManager()
            // Back from GameOver used to bounce straight back into it: the pop
            // revealed GameScreen, whose LaunchedEffect(engine.status) saw
            // GameOver again and navigated forward. Treating back as "go home"
            // breaks the loop and does not lose the run's coins.
            BackHandler {
                gameViewModel.quitToHome()
                goHome()
            }
            GameOverScreen(
                engine = gameViewModel.engine,
                initialCoins = saveManager.coins,
                submitState = submitState,
                onSubmitScore = { gameViewModel.submitRunScore() },
                pendingSubmit = gameViewModel.pendingSubmit.collectAsState().value,
                unlocks = gameViewModel.unlocks.collectAsState().value,
                onUnlocksShown = { gameViewModel.clearUnlocks() },
                onNameChosen = { gameViewModel.setPlayerName(it) },
                onTryAgain = {
                    // Must consume a life - prevents infinite retry exploit
                    val ok = gameViewModel.startNewGameConsuming()
                    if (ok) {
                        navController.popBackStack()
                    } else {
                        // No lives left - stay on GameOver, dialog will handle
                    }
                },
                onHome = {
                    gameViewModel.quitToHome()
                    goHome()
                }
            )
        }
        composable(Screen.Shop.route) {
            ShopScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSupport = {
                    navController.navigate(Screen.Support.route) { launchSingleTop = true }
                },
            )
        }
        composable(Screen.Leaderboard.route) {
            LeaderboardScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Stats.route) {
            StatsScreen(
                saveManager = saveManager,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Support.route) {
            SupportScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Invite.route) {
            InviteFriendsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }

    // The slot only occupies space when there is an ad in it. It used to reserve
    // its height unconditionally, so a placement with no fill left a permanent
    // empty 60dp band across the bottom of every screen — a black box that
    // looked like a rendering fault. Collapsing when empty means no advert means
    // no gap either.
    if (!ProgressState.isPremium.value) {
        AdSlot(modifier = Modifier.fillMaxWidth())
    }
    }
}

@Composable
fun rememberSaveManager(): ir.amiraligrr.blockboom.data.SaveManager {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as BlockBoomApp
    return androidx.compose.runtime.remember(context) { app.saveManager }
}