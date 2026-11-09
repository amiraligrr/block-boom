package ir.amiraligrr.blockboom.ui.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ir.amiraligrr.blockboom.BlockBoomApp
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.data.LivesManager
import ir.amiraligrr.blockboom.data.AdManager
import ir.amiraligrr.blockboom.data.ProgressState
import ir.amiraligrr.blockboom.data.SaveManager
import ir.amiraligrr.blockboom.data.SoundManager
import ir.amiraligrr.blockboom.data.rememberSoundManager
import ir.amiraligrr.blockboom.data.SkinManager
import ir.amiraligrr.blockboom.domain.GameEngine
import ir.amiraligrr.blockboom.domain.GameStatus
import ir.amiraligrr.blockboom.domain.SoundEvent
import ir.amiraligrr.blockboom.ui.components.GameCoach
import ir.amiraligrr.blockboom.ui.components.GameField
import ir.amiraligrr.blockboom.ui.formatNumber
import ir.amiraligrr.blockboom.ui.components.ComboMeter
import ir.amiraligrr.blockboom.ui.components.HeaderBar
import ir.amiraligrr.blockboom.ui.components.LevelProgressBar
import ir.amiraligrr.blockboom.ui.components.AppIcon
import ir.amiraligrr.blockboom.ui.components.ButtonSize
import ir.amiraligrr.blockboom.ui.components.NeonButton
import ir.amiraligrr.blockboom.ui.components.PowerUpScreenOverlay
import ir.amiraligrr.blockboom.ui.components.PowerUpTimer
import ir.amiraligrr.blockboom.ui.theme.DarkSurfaceVariant
import ir.amiraligrr.blockboom.ui.theme.DeepSpace
import ir.amiraligrr.blockboom.ui.theme.NeonCyan
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import ir.amiraligrr.blockboom.ui.theme.NeonGold
import ir.amiraligrr.blockboom.ui.theme.NeonGreen
import ir.amiraligrr.blockboom.ui.theme.OffWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn

/** Longest simulation step allowed in one frame (50ms ~ 3 frames at 60fps). */
private const val MAX_FRAME_SECONDS = 0.05f

/**
 * Reserved height for each HUD strip above the play field.
 *
 * Both the power-up chips and the combo meter collapse to nothing when idle, and
 * anything that changes size directly above a `weight(1f)` area re-measures it.
 * Constant slots keep the play field — and therefore the physics, which is driven
 * from the measured canvas size — identical whether or not a combo is running.
 *
 * 40dp fits the tallest thing each strip can draw: a 26dp chip with 6dp padding,
 * and the combo row's title over a 6px bar inside 6dp padding.
 */
private val HUD_STRIP_HEIGHT = 40.dp

/**
 * The live game screen. Physics and rendering both run on the main thread,
 * driven by the Compose frame clock; only the tick counter and HUD state are
 * Compose snapshot state.
 *
 * Top-down layout: HeaderBar (hearts/coins/level) -> level progress bar ->
 * HUD strip -> the play field, with the coach as a centred overlay *inside*
 * the play field. The level-modifier announcement strip is gone: the play
 * field takes the 40dp it used to reserve.
 *
 * There is no banner ad here, and there has not been for a while: every screen
 * draws from the single slot at the bottom of the window, owned by
 * [ir.amiraligrr.blockboom.ui.navigation.AppNavigation]. This comment used to
 * list it as the first thing on the screen, which is how it ended up duplicated
 * into four screens before it was centralised.
 */
@Composable
fun GameScreen(
    engine: GameEngine,
    initialCoins: Long,
    onLevelContinue: () -> Unit,
    onGameOver: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as BlockBoomApp
    val saveManager = app.saveManager
    val livesManager = app.livesManager
    val skinManager = app.skinManager
    // The whole background is one resolved value, so buying a new one in the
    // shop swaps the sky *and* the scene in a single recomposition.
    val backdrop = remember(skinManager.selectedBackgroundSkin) { skinManager.getBackdrop() }

    val soundManager = rememberSoundManager(saveManager)

    // The in-game coach, for the first few runs.
    //
    // Keyed off the coach *revision* (`needsCoach()`), not `tutorialSeen` and
    // not the `coachDone` boolean:
    //
    //  - `tutorialSeen` was set the moment the player tapped Play on the home
    //    screen, so it recorded "saw the arrow on a card", not "understands
    //    the game". Keyed off it, the coach would never have appeared at all.
    //  - `coachDone` alone is a single bit, so it could not distinguish "saw the
    //    coach we ship today" from "saw an older, shorter one". The current
    //    coach warns that a block reaching you costs a heart, which is the rule
    //    that ends runs; existing players had already set that bit and would
    //    never have been told.
    //
    // Versioning means the current coach is shown once to anyone who has not
    // seen this revision, and never re-shown to someone who has.
    var showCoach by remember { mutableStateOf(saveManager.needsCoach()) }

    // Only while a run is actually happening. Showing a "shoot!" prompt over
    // the pause or game-over screen is nonsense.
    val coachActive = showCoach && engine.status == GameStatus.Playing

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycle = lifecycleOwner.lifecycle

    // Keep the display awake for as long as this screen is composed.
    //
    // A level is a minute of holding a phone still and tapping it a few times,
    // which is exactly the shape of input Android's screen timeout is designed
    // for: mid-run the screen dims and locks, and the player's run is over by a
    // lock screen rather than by a block — which reads as the game hanging rather
    // than as the phone being sensible. Cleared on dispose, so every other screen
    // gets the normal timeout back.
    val keepAwake = LocalActivity.current
    DisposableEffect(keepAwake) {
        val window = keepAwake?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Auto-pause when the app leaves the foreground. Music is handled
    // centrally in AppNavigation now, so this only deals with the engine.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> engine.pauseIfPlaying()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // The game loop. The simulation and the canvas draw both run on the main
    // thread on purpose: the entity lists are plain ArrayLists that the draw
    // pass walks every frame, so a single thread removes any chance of a torn
    // read. dt is clamped so a long stall (app resume, GC pause) can never
    // teleport a block through the cannon and instantly kill the player.
    //
    // It also stops while paused. The loop used to run unconditionally, so
    // update() + onFrameRendered() fired 60x/second behind the pause overlay,
    // keeping the frame clock and the canvas alive for nothing.
    // The coach's hold. True while a tutorial step that asks the player to do
    // something is on screen.
    //
    // Read inside the frame loop rather than read once outside it: the loop is
    // keyed on [engine] only, so a variable captured here would be the value
    // it held when the effect started — permanently false, and the hold would
    // never apply. `state.value` is read per frame instead, which is what
    // makes it a live gate.
    val coachHoldsBoard = remember { mutableStateOf(false) }

    LaunchedEffect(engine) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                val step = lastNanos != 0L &&
                    engine.status == GameStatus.Playing &&
                    !coachHoldsBoard.value
                if (step) {
                    val dt = ((now - lastNanos) / 1_000_000_000.0).toFloat()
                        .coerceIn(0f, MAX_FRAME_SECONDS)
                    engine.update(dt)
                }
                lastNanos = now
                // Bumps the snapshot tick the canvas reads -> redraws this frame.
                engine.onFrameRendered()
            }
        }
    }

    // Connect engine sound events to SoundManager. The music track itself is
    // selected by route in AppNavigation.
    LaunchedEffect(engine, soundManager) {
        engine.onSound = { event: SoundEvent ->
            soundManager.play(event)
        }
    }
    // Duck music when paused or level complete for better focus
    LaunchedEffect(engine.status) {
        when (engine.status) {
            GameStatus.Paused -> soundManager.duckMusic(true)
            GameStatus.Playing -> soundManager.duckMusic(false)
            GameStatus.LevelComplete -> soundManager.duckMusic(true)
            else -> soundManager.duckMusic(false)
        }
    }
    // Handle terminal / mid-run states. Losing a run spends one heart; when a
    // block slams the cannon the level is replayed (not lost) while hearts remain.
    // Level completion is shown as an animated overlay (see below).
    // ── The paid rescue ─────────────────────────────────────────────────
    //
    // Shown *instead of* acting on the hit, never after it. The handler below
    // waits for this to resolve, so the player gets a real choice rather than a
    // heart already spent and an offer made afterwards — which would be the one
    // arrangement that reliably converts an advert into a refusal.
    //
    // It also has to be optional in the strict sense: no ad loaded, or the
    // player says no, and the normal path runs exactly as it did before this
    // existed.
    var reviveOffer by remember { mutableStateOf(false) }
    var reviveBusy by remember { mutableStateOf(false) }

    // True once the current hit has been paid for.
    //
    // The handler below is keyed on `engine.status`, which is a plain field rather
    // than snapshot state, so the effect is re-keyed by the 60fps recomposition
    // the frame loop causes — and a status that *stays* put (the player is looking
    // at the revive offer, or the advert is still opening) means the same hit can
    // be handed to this handler twice. Charging two hearts for one mistake is the
    // worst thing this screen can do, and it does it silently.
    var hitSettled by remember { mutableStateOf(false) }

    // Resolves the hit once the offer has been answered.
    val settleHit = remember {
        {
            if (hitSettled) return@remember
            hitSettled = true
            val premium = saveManager.isPremium
            if (premium) {
                engine.startLevel(engine.level)
            } else if (livesManager.decrementLife()) {
                if (livesManager.lives > 0) {
                    engine.startLevel(engine.level)
                } else {
                    soundManager.play(SoundEvent.GAME_OVER)
                    onGameOver()
                }
            } else {
                soundManager.play(SoundEvent.GAME_OVER)
                onGameOver()
            }
        }
    }

    LaunchedEffect(engine.status) {
        when (engine.status) {
            // Re-armed: a restarted level is a new hit waiting to happen.
            GameStatus.Playing -> hitSettled = false
            GameStatus.HitByBlock -> {
                // Premium already has ad-free play and never spends a heart, so
                // there is nothing here to sell them.
                val canOffer = !saveManager.isPremium &&
                    !engine.reviveUsedThisLevel &&
                    AdManager.isRewardedLoaded()
                if (canOffer) {
                    reviveOffer = true
                } else {
                    settleHit()
                }
            }
            GameStatus.GameOver -> {
                soundManager.play(SoundEvent.GAME_OVER)
                onGameOver()
            }
            else -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // The screen behind the HUD. It was a fixed pale mint while every
            // background the player could buy was dark, so the header and the
            // strip above the field sat on a light band and the field below it
            // was near-black — one screen split into two different products.
            // Tinted from the sky's own top colour so the seams disappear.
            .background(backdrop.sky.first())
    ) {
        HeaderBar(
            hearts = livesManager.lives,
            coins = initialCoins + engine.coinsInLevel,
            level = engine.level,
            isPremium = ProgressState.isPremium.value
        )

        LevelProgressBar(
            destroyed = engine.blocksDestroyed,
            total = engine.totalBlocksInLevel
        )

        // ── HUD strip: power-ups and combo, side by side in ONE slot ──────────
        //
        // These were two separate strips, each reserving a fixed 40dp so its
        // content appearing and disappearing could not re-measure the play field
        // below. That fixed the layout jump but introduced a worse problem: 80dp
        // was permanently reserved for a strip that is usually empty, so the
        // play field lost 80dp of height on every level to host two rows of
        // nothing. On a 6.1" phone that is a visible dead band across the middle
        // of the screen.
        //
        // One strip now holds both, power-ups on the leading edge and the combo
        // on the trailing one, so they share the 40dp instead of each claiming
        // their own. Both can be visible at once — a 4-chain with a freeze
        // running shows both — and the cost is one row either way.
        //
        // Keeping the row *reserved* rather than overlaying the field is
        // deliberate: the top of the field is where blocks enter, so anything
        // drawn over it hides exactly the thing the player needs to react to.
        //
        // The fixed height is still load-bearing. Both children collapse when
        // idle, and anything that changes size directly above a `weight(1f)`
        // area re-measures it — and the play field's measured size is what the
        // physics reads, so a resize moves the cannon and every falling block.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HUD_STRIP_HEIGHT)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PowerUpTimer(
                    engine = engine,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ComboMeter(
                    combo = engine.combo,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            GameField(
                engine = engine,
                skinManager = skinManager,
                backdrop = backdrop,
                modifier = Modifier.fillMaxSize()
            )

            // Full-screen power-up overlay.
            //
            // Above the field (so the ice/fire/gold are never hidden by a
            // falling block) but below the score pill and pause button (so the
            // two things you must always be able to read and tap are never
            // tinted or obscured).
            //
            // It is inside the same Box as the field, so it covers exactly the
            // field and nothing else — the header, the ad and the combo meter
            // above stay clean.
            PowerUpScreenOverlay(
                engine = engine,
                modifier = Modifier.fillMaxSize()
            )

            // Score pill + pause button overlays
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                // Scrim behind the number: it used to be OffWhite text placed
                // directly on the play field, which vanished on light skins.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(Color.Black.copy(alpha = 0.42f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${stringResource(R.string.score)}: ${engine.score}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            ) {
                NeonButton(
                    text = "",
                    onClick = { engine.togglePause() },
                    containerColor = DarkSurfaceVariant,
                    borderColor = NeonCyan,
                    size = ButtonSize.Small,
                    icon = AppIcon.PAUSE
                )
            }

            // The coach, centred over the play field.
            //
            // It used to be the first child of the screen's column, pinned under
            // the status bar. A fixed-height card there displaces everything
            // below it, so the screen visibly shrank while it was talking — and
            // what it kept shrinking was the play field it was describing. The
            // player was reading about falling blocks that had been pushed off
            // the bottom of the screen.
            //
            // It lives inside the play field's Box now, as a centred overlay. That
            // placement is the only one of the three that costs nothing:
            //
            //  - as a sibling in the column it would push the field down
            //  - as its own `weight(1f)` slot it would halve the field, and
            //    *change that size as the steps advanced* — which re-measures the
            //    canvas, and `GameField` feeds that measurement straight back into
            //    `engine.setFieldSize`, moving the cannon and every falling block
            //    several times in the first half-minute of the player's first run
            //  - inside the field's own Box it changes nothing about the field's
            //    size, so the physics never sees it
            //
            // Centred vertically it sits between the HUD above and the cannon
            // below, and it is the one overlay here that is not anchored to a
            // screen edge.
            if (coachActive) {
                GameCoach(
                    engine = engine,
                    onDismiss = {
                        // Dismissal is final for this revision. A coach that
                        // reappears after being closed is not a coach, it is a
                        // nag. Recording the revision (not just the boolean) is
                        // what stops it coming back — and is also what makes a
                        // *later* revision eligible to show once.
                        saveManager.coachDone = true
                        saveManager.coachSeenVersion = SaveManager.COACH_VERSION
                        showCoach = false
                    },
                    onFinished = {
                        saveManager.coachDone = true
                        saveManager.coachSeenVersion = SaveManager.COACH_VERSION
                        showCoach = false
                    },
                    onHoldChanged = { coachHoldsBoard.value = it },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (engine.status == GameStatus.Paused) {
                PauseOverlay(
                    onResume = { engine.togglePause() },
                    onHome = onExit
                )
            }

            if (engine.status == GameStatus.LevelComplete) {
                LevelCompleteOverlay(
                    engine = engine,
                    onContinue = onLevelContinue
                )
            }

            // There is no inline game-over overlay. The status handler below
            // navigates to GameOverScreen, and this overlay used to render
            // for the frame in between — a second, different "game over"
            // appearing for a blink before the real one arrived. One screen
            // owns the moment.
        }
    }

    // Watching the advert revives in place; the hit never reaches the life
    // handler at all, so no heart is spent.
    //
    // Emitted *after* the screen above, not before it. The destination lays its
    // children out stacked, so emission order is both paint order and hit-test
    // order: emitted first, the offer's scrim was painted underneath the
    // column's opaque background (so it was invisible) and the game canvas
    // swallowed every tap aimed at its buttons (so they were unreachable). That
    // left the board frozen on `HitByBlock` with no way to take the rescue or
    // decline it — the only way out was the system back button.
    if (reviveOffer) {
        ReviveOffer(
            busy = reviveBusy,
            coinCost = engine.reviveCoinCost(engine.level),
            coinBalance = saveManager.coins,
            onWatchAd = {
                reviveBusy = true
                AdManager.showRewarded(
                    onReward = {
                        reviveBusy = false
                        reviveOffer = false
                        engine.revive()
                    },
                    onFail = {
                        reviveBusy = false
                        reviveOffer = false
                        settleHit()
                    }
                )
            },
            // Paid revive: same rescue, charged in coins. The charge goes
            // through first — a failed charge leaves the offer open so the
            // player can still watch the ad instead.
            onPayCoins = { cost ->
                if (saveManager.spendCoins(cost)) {
                    reviveOffer = false
                    engine.revive()
                }
            },
            onDecline = {
                reviveOffer = false
                settleHit()
            }
        )
    }
}

/**
 * The one-time rescue offer, shown over a frozen board after a block hit.
 *
 * ── It has to be a real choice ────────────────────────────────────────────
 * Declining is a full-width button, not a dismiss gesture, and there is no
 * countdown racing it. Every variant with a timer puts a price on the player's
 * attention that they never agreed to, and the ones that auto-decline convert a
 * would-be viewer into a permanent no.
 *
 * The trade is stated plainly instead of being buried: this keeps the level,
 * spends no heart, clears the bottom of the board and hands over a shield. A
 * player who cannot tell what an advert is buying will not watch it, and one who
 * thinks they are being sold a full restart will feel cheated afterwards.
 *
 * The ad itself is never shown when none is loaded — the caller checks
 * [AdManager.isRewardedLoaded] first — so this cannot offer a video that then
 * fails to appear and charge the player a delay for nothing.
 */
@Composable
private fun ReviveOffer(
    busy: Boolean,
    coinCost: Long,
    coinBalance: Long,
    onWatchAd: () -> Unit,
    onPayCoins: (Long) -> Unit,
    onDecline: () -> Unit
) {
    val c = joyThemeColors()
    val canAfford = coinBalance >= coinCost

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "🛡️", fontSize = 44.sp)

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.revive_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Black
                ),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                // The deal, itemised. Vague copy here is why so many "continue"
                // buttons go unwatched: the player cannot tell what they get.
                text = stringResource(R.string.revive_body),
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.82f)),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))

            NeonButton(
                text = stringResource(
                    if (busy) R.string.revive_watching else R.string.revive_watch
                ),
                onClick = { if (!busy) onWatchAd() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                containerColor = c.emerald,
                borderColor = Color.White,
                contentColor = Color.White,
                size = ButtonSize.Large,
                icon = AppIcon.PLAY
            )

            Spacer(Modifier.height(10.dp))

            // Paid revive: no advert, same rescue. Priced in coins and shown
            // with the price on the button, so it is a decision rather than a
            // trap — and disabled, not hidden, when the balance is short, so
            // the player learns what it costs instead of wondering where it
            // went.
            NeonButton(
                text = formatNumber(coinCost),
                onClick = { if (!busy && canAfford) onPayCoins(coinCost) },
                enabled = !busy && canAfford,
                modifier = Modifier.fillMaxWidth(),
                containerColor = NeonGold,
                borderColor = Color.White,
                contentColor = Color(0xFF3A2A00),
                size = ButtonSize.Large,
                icon = AppIcon.COIN
            )

            Spacer(Modifier.height(10.dp))

            NeonButton(
                text = stringResource(R.string.revive_decline),
                onClick = onDecline,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                containerColor = c.surfaceSoft,
                borderColor = c.cardBorder,
                // Was an explicit `Color.White` on a near-white mint fill — the
                // one that started this. Omitted now, so the button derives a
                // readable label from what it paints.
                size = ButtonSize.Medium
            )

            if (busy) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.revive_loading),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun PauseOverlay(
    onResume: () -> Unit,
    onHome: () -> Unit
) {
    val c = joyThemeColors()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                // The card was a hardcoded dark-green gradient in a HUD that is
                // otherwise fully themed — in light mode it sat black-green
                // against pale cards. The theme's own surface, like every
                // other card in the app.
                .background(Brush.verticalGradient(listOf(c.surface, c.surfaceSoft)))
                .border(1.2.dp, c.cardBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 28.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.verticalGradient(listOf(NeonGreen.copy(alpha = 0.28f), NeonGreen.copy(alpha = 0.12f))))
                        .border(1.dp, NeonGreen.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "", color = NeonGreen, fontSize = 28.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.pause),
                    color = OffWhite,
                    fontSize = 32.sp,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black)
                )
                Text(
                    text = "نفس عمیق بکش ✨",
                    color = OffWhite.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(28.dp))
                NeonButton(
                    text = stringResource(R.string.resume) + "  ▶",
                    onClick = onResume,
                    containerColor = NeonGreen,
                    borderColor = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                NeonButton(
                    text = stringResource(R.string.home),
                    onClick = onHome,
                    containerColor = DarkSurfaceVariant,
                    borderColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Level complete overlay.
 *
 * This previously had NO continue button at all: `onContinue` was accepted and
 * never used, so finishing a level showed a pulsing circle that looped forever
 * and the player was stuck. The entire progression was blocked behind it.
 *
 * The burst now runs once instead of looping, and the button fades in after it.
 * Tapping anywhere also continues, so nobody can be trapped on this screen.
 */
@Composable
private fun LevelCompleteOverlay(
    engine: GameEngine,
    onContinue: () -> Unit
) {
    val reveal = remember { Animatable(0f) }
    var buttonShown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        buttonShown = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { buttonShown = true; onContinue() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .graphicsLayer {
                        // one-shot overshoot, settles at 1.0
                        val s = if (reveal.value > 0f) 1f + 0.16f * (1f - reveal.value) else 0.6f
                        scaleX = s; scaleY = s; alpha = reveal.value
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(NeonGreen.copy(alpha = 0.38f), NeonGreen.copy(alpha = 0.16f), Color.Transparent)
                        )
                    )
                    .border(4.dp, NeonGreen.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "\u2726", fontSize = 40.sp, color = NeonGold)
                    Text(
                        text = stringResource(R.string.level_complete),
                        style = MaterialTheme.typography.headlineSmall,
                        color = OffWhite,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${stringResource(R.string.level)} ${engine.level}",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonGold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        // Labelled, not just "+123". The bare plus read as a
                        // bonus of some kind — coins, a multiplier — when it
                        // is simply what the level paid.
                        text = "${stringResource(R.string.score)} ${engine.score}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = OffWhite,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            AnimatedVisibility(
                visible = buttonShown,
                enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.9f, animationSpec = tween(220))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NeonButton(
                        text = stringResource(R.string.btn_continue),
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                        size = ButtonSize.XLarge,
                        containerColor = joyThemeColors().emerald,
                        contentColor = Color.White,
                        borderColor = Color.White
                    )
                    // There used to be a second button here, labelled "ارتقا",
                    // that called onContinue — the same handler as Continue. Two
                    // buttons that do the same thing read as a bug whichever one
                    // is pressed, and the upgrade shop has its own screen
                    // between runs.
                }
            }
        }
    }
}
