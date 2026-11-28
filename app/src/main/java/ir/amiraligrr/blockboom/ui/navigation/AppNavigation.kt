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
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clipToBounds
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
import ir.amiraligrr.blockboom.data.GameMode
import ir.amiraligrr.blockboom.data.PlayerSyncManager
import ir.amiraligrr.blockboom.data.NewsItem
import ir.amiraligrr.blockboom.data.NewsManager
import ir.amiraligrr.blockboom.data.ProgressState
import ir.amiraligrr.blockboom.data.RewardService
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
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import ir.amiraligrr.blockboom.ui.v2.AchievementsScreen
import ir.amiraligrr.blockboom.ui.v2.AnnouncementModal
import ir.amiraligrr.blockboom.ui.v2.FreeCoinsScreen
import ir.amiraligrr.blockboom.ui.v2.HomeV2
import ir.amiraligrr.blockboom.ui.v2.MissionsScreen
import ir.amiraligrr.blockboom.ui.v2.NewsScreen

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
    data object Missions : Screen("missions")
    data object Achievements : Screen("achievements")
    data object FreeCoins : Screen("free_coins")
    data object News : Screen("news")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val gameViewModel: GameViewModel = viewModel()
    val saveManager = rememberSaveManager()
    val showOnboarding = remember(saveManager) { mutableStateOf(!saveManager.onboardingCompleted) }
    val submitState by gameViewModel.submitState.collectAsState()
    val supportOverride = remember { mutableStateOf(false) }
    // 0 = coins, 1 = skins, 2 = premium. The menu sends Skins here directly
    // instead of dropping the player on the coin tab and making them hunt.
    var shopInitialTab by remember { mutableStateOf(0) }

    val context = LocalContext.current
    val app = context.applicationContext as BlockBoomApp
    val soundManager = app.soundManager

    // Announcements already fetched by BlockBoomApp. The old home had a
    // one-off card and the V2 home had nothing, so a server announcement could
    // be marked read and never shown. This is the missing queue: one modal at
    // a time, explicit close, next item after that.
    var announcementQueue by remember {
        mutableStateOf<List<RewardService.Announcement>>(emptyList())
    }
    var currentAnnouncement by remember {
        mutableStateOf<RewardService.Announcement?>(null)
    }
    var newsItems by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        newsItems = NewsManager.fetchNews(context)
    }
    val incomingAnnouncements by app.announcements.collectAsState()
    LaunchedEffect(incomingAnnouncements) {
        if (incomingAnnouncements.isNotEmpty()) {
            announcementQueue = announcementQueue + app.consumeAnnouncements()
        }
    }
    LaunchedEffect(announcementQueue, currentAnnouncement) {
        if (currentAnnouncement == null && announcementQueue.isNotEmpty()) {
            currentAnnouncement = announcementQueue.first()
            announcementQueue = announcementQueue.drop(1)
        }
    }

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

    // ── Support is above the gate ───────────────────────────────────────
    // A banned player must be able to open support. This used to live *after*
    // the gate and reset the access state to get there, which raced the
    // foreground re-check and could put the ban screen straight back before
    // SupportScreen composed. Rendering support first means the button always
    // works, whatever the server says and however many checks are in flight.
    if (supportOverride.value) {
        SupportScreen(
            onBack = {
                supportOverride.value = false
                // Refresh in the background; the ban screen is already what
                // the current state says, so this cannot flicker the UI.
                AppConfigRepository.check(force = true)
            }
        )
        return
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
                // of the button. Do not touch the access state here; support
                // is rendered above the gate, so there is no race with the
                // background re-check.
                supportOverride.value = true
            }
            return
        }
        else -> Unit
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

    // ── One ad slot, at the top of the app ───────────────────────────────
    //
    // The banner used to sit below the NavHost, which meant a permanent 60dp
    // band under every screen and, on the home screen, an empty dark strip
    // directly above the navigation bar. It now lives *above* the NavHost:
    //
    //  - it can never cover a control, because nothing is drawn underneath it
    //  - it is outside the play field, so gameplay has its full height back
    //  - the height is constant, so loading an ad cannot re-measure a screen
    //  - it is deliberately hidden on the game, upgrade and onboarding routes
    //    where a banner would compete with the thing the player is doing
    //
    // The slot is created once and survives navigation, so a banner is not
    // requested again for every screen. Premium removes it entirely.
    //
    // Insets are applied ONCE here: the status bar sits above the banner and
    // the navigation bar below the NavHost. Neither child carries its own
    // safeDrawing padding, which used to charge the same system bar twice.
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

        // A banner is only useful where it is not in the way. On the game,
        // upgrade and onboarding routes the player is mid-task, and a top
        // banner there would push the play field down or sit over the very
        // controls the screen exists for.
        val showTopBanner = !ProgressState.isPremium.value &&
            route != null &&
            route !in setOf(
                // The banner is required at the top of the app, including the
                // home menu. It stays out of the live play field and the
                // onboarding flow, where it would sit over the thing the
                // player is trying to learn.
                Screen.Onboarding.route,
                Screen.Game.route,
                Screen.Upgrade.route
            )
        if (showTopBanner) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .background(joyThemeColors().backgroundTop)
            ) {
                AdSlot(
                    modifier = Modifier.fillMaxWidth(),
                    onHouseClick = {
                        // Only reachable on non-game routes, but pausing is
                        // cheap and keeps a future route from simulating
                        // underneath the shop.
                        gameViewModel.engine.pauseIfPlaying()
                        navController.navigate(Screen.Shop.route) {
                            launchSingleTop = true
                        }
                    }
                )
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
                    gameViewModel.startNewGame(GameMode.STAGE)
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            val activity = LocalActivity.current
            val homeSave = rememberSaveManager()
            val homeLives = (context.applicationContext as BlockBoomApp).livesManager
            HomeV2(
                saveManager = homeSave,
                livesManager = homeLives,
                onPlayMode = { mode ->
                    gameViewModel.startNewGame(mode)
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.Home.route)
                        launchSingleTop = true
                    }
                },
                onShop = {
                    shopInitialTab = 0
                    navController.navigate(Screen.Shop.route) { launchSingleTop = true }
                },
                onSkins = {
                    shopInitialTab = 1
                    navController.navigate(Screen.Shop.route) { launchSingleTop = true }
                },
                onMissions = {
                    navController.navigate(Screen.Missions.route) { launchSingleTop = true }
                },
                onFreeCoins = {
                    navController.navigate(Screen.FreeCoins.route) { launchSingleTop = true }
                },
                onSettings = { navController.navigate(Screen.Settings.route) },
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
                onAchievements = {
                    navController.navigate(Screen.Achievements.route) {
                        launchSingleTop = true
                    }
                },
                onSocial = {
                    navController.navigate(Screen.Invite.route) {
                        launchSingleTop = true
                    }
                },
                onMore = {
                    navController.navigate(Screen.Settings.route) {
                        launchSingleTop = true
                    }
                },
                onDailyReward = { },
                onExit = { activity?.finish() },
                news = newsItems.firstOrNull(),
                onNews = {
                    if (newsItems.isNotEmpty()) {
                        navController.navigate(Screen.News.route) { launchSingleTop = true }
                    }
                }
            )
        }
        composable(Screen.Game.route) {
            val saveManager = rememberSaveManager()
            GameScreen(
                engine = gameViewModel.engine,
                initialCoins = saveManager.coins,
                onLevelContinue = {
                    // Record, Infinity, Boss Fight and Time Attack are score
                    // chases: the next wave starts immediately. Only the
                    // campaign gets the upgrade breather between levels.
                    when (gameViewModel.engine.mode) {
                        GameMode.RECORD,
                        GameMode.BOSS -> gameViewModel.startNextLevel()
                        GameMode.STAGE -> navController.navigate(Screen.Upgrade.route) {
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
                },
                onShop = {
                    gameViewModel.quitToHome()
                    navController.navigate(Screen.Shop.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.Shop.route) {
            ShopScreen(
                onBack = { navController.popBackStack() },
                initialTab = shopInitialTab
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSupport = {
                    navController.navigate(Screen.Support.route) { launchSingleTop = true }
                },
                onStats = {
                    navController.navigate(Screen.Stats.route) { launchSingleTop = true }
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
        composable(Screen.Missions.route) {
            MissionsScreen(
                saveManager = rememberSaveManager(),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Achievements.route) {
            AchievementsScreen(
                saveManager = rememberSaveManager(),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.FreeCoins.route) {
            FreeCoinsScreen(
                saveManager = rememberSaveManager(),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.News.route) {
            // Re-fetch whenever the reader opens so a freshly published item
            // appears without restarting the app.
            LaunchedEffect(Unit) {
                newsItems = NewsManager.fetchNews(context)
            }
            NewsScreen(
                news = newsItems,
                onBack = { navController.popBackStack() }
            )
        }
    }

    }

    // Never interrupt a live run or the between-level upgrade with a modal;
    // the queue is held until the player is back on a calm screen.
    if (currentAnnouncement != null &&
        route != Screen.Game.route &&
        route != Screen.Upgrade.route
    ) {
        AnnouncementModal(
            announcement = currentAnnouncement!!,
            onClose = { currentAnnouncement = null }
        )
    }
}

@Composable
fun rememberSaveManager(): ir.amiraligrr.blockboom.data.SaveManager {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as BlockBoomApp
    return androidx.compose.runtime.remember(context) { app.saveManager }
}