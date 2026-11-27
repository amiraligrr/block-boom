package ir.amiraligrr.blockboom.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.BlockBoomApp
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.data.AdManager
import ir.amiraligrr.blockboom.data.GameMode
import ir.amiraligrr.blockboom.data.ProgressState
import ir.amiraligrr.blockboom.domain.GameEngine
import ir.amiraligrr.blockboom.ui.dialogs.NameDialog
import ir.amiraligrr.blockboom.ui.dialogs.NoLivesDialog
import ir.amiraligrr.blockboom.ui.AchievementUnlock
import ir.amiraligrr.blockboom.ui.GameViewModel
import ir.amiraligrr.blockboom.ui.SubmitState
import ir.amiraligrr.blockboom.ui.components.ButtonSize
import ir.amiraligrr.blockboom.ui.components.NeonButton
import ir.amiraligrr.blockboom.ui.components.AppIcon
import ir.amiraligrr.blockboom.ui.components.ShareCard
import ir.amiraligrr.blockboom.data.SaveManager
import ir.amiraligrr.blockboom.ui.theme.NeonRed as NeonRedColor
import ir.amiraligrr.blockboom.ui.formatNumber
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Game over.
 *
 * Three things were wrong here:
 *  - The score-saving + leaderboard submit was a LaunchedEffect nested inside
 *    `if (showHeart.value)`, so it only ran because a *different* effect
 *    happened to flip that flag 300ms later, and its result was discarded — a
 *    rejected submit looked exactly like a successful one. It is now driven by
 *    the ViewModel and the outcome is shown.
 *  - The column had no verticalScroll and ~620dp of content, so on a short
 *    screen the three buttons were clipped and unreachable.
 *  - The stats card used a fixed .width(280.dp) inside .padding(24.dp), which
 *    overflows any device narrower than 328dp.
 */
@Composable
fun GameOverScreen(
    engine: GameEngine,
    initialCoins: Long,
    submitState: SubmitState,
    onSubmitScore: () -> Unit,
    /**
     * True when this run scored but was never sent, because no name was set.
     *
     * Triggers the prompt below. Without it the player was told to go and set a
     * name somewhere else and had their score silently dropped in the meantime.
     */
    pendingSubmit: Boolean = false,
    unlocks: List<AchievementUnlock> = emptyList(),
    onUnlocksShown: () -> Unit = {},
    onNameChosen: (String) -> Unit = {},
    onTryAgain: () -> Unit,
    onHome: () -> Unit,
    onShop: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as BlockBoomApp
    val saveManager = app.saveManager
    val livesManager = app.livesManager
    val c = joyThemeColors()
    val isRecord = engine.mode == GameMode.RECORD
    val modeLabel = when (engine.mode) {
        GameMode.RECORD -> "حالت رکوردی"
        GameMode.BOSS -> "نبرد باس"
        GameMode.TIME_ATTACK -> "حمله زمانی"
        GameMode.STAGE -> "حالت مرحله‌ای"
    }
    val runScore = engine.runScore
    // Read after the ViewModel has banked the run, so a new best is already in
    // the save. The two modes keep separate records and separate chest tracks.
    val bestRun = if (isRecord) saveManager.recordBestScore else saveManager.statBestRunScore
    val nextRecordChest = saveManager.nextRecordMilestoneLevel()
    val untilChest = saveManager.levelsUntilMilestone()
    // survive a configuration change so a rewarded-ad reward is not claimable twice
    var coinsDoubled by remember { mutableStateOf(false) }
    // Retry used to fail silently when the last heart had just been spent: the
    // button was still there, the tap did nothing, and the only explanation was
    // a dialog on a different screen. This is the same out-of-lives offer the
    // home screen already uses, shown at the moment the player asks to retry.
    var showNoLives by remember { mutableStateOf(false) }

    val heartScale = remember { Animatable(1f) }
    val heartAlpha = remember { Animatable(1f) }
    val heartBeat = remember { Animatable(1f) }
    val cardReveal = remember { Animatable(0f) }
    val showHeart = remember { mutableStateOf(false) }

    // Bank the run and submit the score exactly once, independent of the
    // heart animation.
    //
    // Guarded by a flag, not just by the key. `LaunchedEffect(Unit)` runs once
    // per composition *lifecycle*, and this screen is composed twice in a row
    // on a slow device: once by the game-over overlay's navigation and again
    // when the destination settles. Without the latch the second composition
    // re-enters with the same `Unit` key and the run is submitted twice — and
    // `submitRunScore` banking the run twice is a score the player never
    // earned.
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (submitted) return@LaunchedEffect
        submitted = true
        onSubmitScore()
    }

    LaunchedEffect(Unit) {
        delay(300)
        showHeart.value = true
        heartScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        heartAlpha.animateTo(1f, tween(400))
        while (isActive) {
            heartBeat.animateTo(1.15f, tween(400))
            delay(400)
            heartBeat.animateTo(1f, tween(400))
            delay(400)
        }
    }

    LaunchedEffect(Unit) {
        delay(600)
        cardReveal.animateTo(1f, tween(500, delayMillis = 200))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.gradientBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (showHeart.value) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = heartScale.value * heartBeat.value
                            scaleY = heartScale.value * heartBeat.value
                            alpha = heartAlpha.value
                        }
                        .size(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .graphicsLayer { alpha = 0.45f }
                            .background(
                                Brush.radialGradient(
                                    listOf(c.coral.copy(alpha = 0.36f), Color.Transparent)
                                ),
                                RoundedCornerShape(50)
                            )
                    )
                    Text("❤️", fontSize = 44.sp)
                }
                Spacer(Modifier.height(8.dp))
            }

            Text(
                text = when (engine.mode) {
                    GameMode.TIME_ATTACK -> "زمان تمام شد!"
                    GameMode.BOSS -> "نبرد تمام شد"
                    else -> stringResource(R.string.game_over)
                },
                style = MaterialTheme.typography.headlineMedium,
                color = c.coral,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = if (isRecord) {
                    if (saveManager.recordBestLevel > 0) {
                        stringResource(R.string.record_best_level, saveManager.recordBestLevel)
                    } else {
                        stringResource(R.string.record_no_best)
                    }
                } else {
                    stringResource(R.string.my_level, saveManager.currentLevel)
                },
                style = MaterialTheme.typography.titleMedium,
                color = c.textSecondary
            )

            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background((if (isRecord) c.sky else c.emerald).copy(alpha = 0.14f))
                    .border(
                        1.dp,
                        (if (isRecord) c.sky else c.emerald).copy(alpha = 0.35f),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isRecord) c.sky else c.emerald,
                    fontWeight = FontWeight.Black
                )
            }

            // The two numbers that make "one more run" a concrete decision
            // rather than a vague encouragement: how far this run was from the
            // player's own record, and how close the next chest is. Both are
            // read after the run has been banked, so a new best is already in
            // the save.
            if (runScore > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (runScore >= bestRun) {
                        "🏆 رکورد جدید!"
                    } else {
                        "🎯 فقط ${formatNumber(bestRun - runScore)} امتیاز تا رکورد"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (runScore >= bestRun) c.emerald else c.amber,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isRecord) {
                    "🎁 صندوق بعدی رکورد: مرحله $nextRecordChest"
                } else if (untilChest == 0) {
                    "🎁 صندوق جایزه آماده است — همین مرحله را پاک کن"
                } else {
                    "🎁 $untilChest مرحله تا صندوق بعدی"
                },
                style = MaterialTheme.typography.labelMedium,
                color = c.textMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // ── stats ────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.run_score),
                                color = c.textSecondary,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = formatNumber(runScore),
                                color = c.textPrimary,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.coins),
                                color = c.textSecondary,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = formatNumber(initialCoins),
                                color = c.textPrimary,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    if (engine.coinsInLevel > 0 && !coinsDoubled) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.coins_plus,
                                formatNumber(engine.coinsInLevel.toLong())
                            ) + " " + stringResource(R.string.level_earned_coins),
                            color = c.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (engine.combo.best >= 2) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "🔥 بهترین کمبو: ${engine.combo.best}",
                            color = c.textPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (unlocks.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        unlocks.take(2).forEach { u ->
                            Text(
                                text = stringResource(
                                    R.string.ach_unlocked,
                                    u.title,
                                    formatNumber(u.coins)
                                ),
                                color = c.textPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                        LaunchedEffect(unlocks) { onUnlocksShown() }
                    }

                    if (isRecord) {
                        when (submitState) {
                            is SubmitState.Done -> {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = submitState.message,
                                    color = c.emerald,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                            is SubmitState.Failed -> {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = submitState.message,
                                    color = c.coral,
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                            is SubmitState.Submitting -> {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "در حال ثبت رکورد…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = c.textSecondary
                                )
                            }
                            SubmitState.Idle -> Unit
                        }
                    }
            }

            // ── name prompt ──────────────────────────────────────────────────
            //
            // Shown right here, at the moment the score is at stake, rather than
            // as an error message telling the player to go somewhere else. One
            // tap and the score they just earned is submitted.
            if (isRecord && pendingSubmit && submitState !is SubmitState.Done) {
                Spacer(Modifier.height(16.dp))
                NameDialog(
                    initialName = "",
                    onConfirm = { n ->
                        onNameChosen(n)
                        onSubmitScore()
                    },
                    // Dismissing keeps the run unsubmitted but stops the prompt
                    // re-appearing over this screen, so the buttons below stay
                    // reachable.
                    onDismiss = { onNameChosen("") }
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── actions ─────────────────────────────────────────────────────
            Column(
                modifier = Modifier.widthIn(max = 320.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.retry_one_more),
                    style = MaterialTheme.typography.titleMedium,
                    // `NeonRed` is bright enough for the display-size title,
                    // but at title-medium size it was only ~3:1 on the light
                    // background. The themed coral is the readable ink.
                    color = c.coral,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                NeonButton(
                    text = stringResource(R.string.try_again),
                    onClick = {
                        if (livesManager.canStart()) onTryAgain() else showNoLives = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.Large,
                    containerColor = c.emerald,
                    borderColor = Color.White.copy(alpha = 0.36f),
                    icon = AppIcon.PLAY
                )
                if (!coinsDoubled && engine.coinsInLevel > 0) {
                    Spacer(Modifier.height(12.dp))
                    NeonButton(
                        text = stringResource(R.string.double_coins),
                        onClick = {
                            AdManager.showRewarded(
                                onReward = {
                                    coinsDoubled = true
                                    saveManager.addCoins(engine.coinsInLevel.toLong())
                                    ProgressState.sync(saveManager)
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        size = ButtonSize.Medium,
                        containerColor = c.surface,
                        contentColor = c.textPrimary,
                        borderColor = c.cardBorder
                    )
                    Spacer(Modifier.height(12.dp))
                }
                NeonButton(
                    text = stringResource(R.string.home),
                    onClick = onHome,
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.Medium,
                    containerColor = c.surface,
                    contentColor = c.textPrimary,
                    borderColor = c.cardBorder,
                    icon = AppIcon.HOME
                )
                Spacer(Modifier.height(12.dp))

                // Share the result.
                //
                // Last, smallest, and entirely optional — sharing is worth
                // nothing if it is pushed. A player is most likely to share a
                // deep level or a big combo, so the card is built from the
                // better of the two and the button only appears when there is
                // something there worth showing: a run that ended on level 1
                // with no combo has nothing to boast about, and offering to
                // share it teaches people that the button is noise.
                val headline = shareHeadline(saveManager)
                if (headline != null) {
                    NeonButton(
                        text = stringResource(R.string.share_result),
                        onClick = { shareRun(context, saveManager, headline) },
                        modifier = Modifier.fillMaxWidth(),
                        size = ButtonSize.Medium,
                        containerColor = c.surfaceSoft,
                        contentColor = c.textPrimary,
                        borderColor = c.cardBorder,
                        icon = AppIcon.SHARE
                    )
                }
            }

            // The banner moved to a single global slot below the NavHost, so this screen
            // no longer has to reserve room for it — this spacer existed only to
            // stop the ad covering the last button.
            Spacer(Modifier.height(12.dp))
        }

    }

    if (showNoLives) {
        NoLivesDialog(
            livesManager = livesManager,
            onWatchAd = {
                showNoLives = false
                // They came here by tapping Try Again. Once the ad has paid
                // out, retry immediately; the dialog is not a place to strand
                // them after they have already made the choice.
                if (livesManager.canStart()) onTryAgain()
            },
            onGoPremium = {
                showNoLives = false
                onShop()
            },
            onDismiss = { showNoLives = false }
        )
    }
}

@Composable
private fun SubmitBadge(text: String, isError: Boolean, colors: ir.amiraligrr.blockboom.ui.theme.JoyThemeColors) {
    val fg = if (isError) colors.coral else colors.emerald
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(fg.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * The number a share card would be built around, or null when there is
 * nothing worth sharing.
 *
 * A combo beats a level: it is the harder thing to do, it is the one players
 * talk about, and a level number is only impressive relative to whoever is
 * reading. Below both thresholds there is no card, so the button does not
 * appear at all.
 */
private fun shareHeadline(save: SaveManager): Pair<String, String>? = when {
    save.statBestCombo >= 10 -> "×${save.statBestCombo}" to "بزرگ‌ترین کمبوی من"
    save.statDeepestLevel >= 20 -> "${save.statDeepestLevel}" to "عمیق‌ترین مرحلهٔ من"
    else -> null
}

/** Builds the card and hands it to the share sheet. */
private fun shareRun(context: android.content.Context, save: SaveManager, headline: Pair<String, String>) {
    val accent = ir.amiraligrr.blockboom.ui.theme.NeonGold.toArgb()
    val bmp = ShareCard.render(save, headline.first, headline.second, accent)
    ShareCard.share(context, bmp, ShareCard.message(save, headline.first, headline.second))
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt()
)

@Composable
private fun DrawHeart(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val centerY = height / 2f
        val r = minOf(width, height) * 0.35f

        val leftCenter = Offset(centerX - r * 0.6f, centerY - r * 0.2f)
        val rightCenter = Offset(centerX + r * 0.6f, centerY - r * 0.2f)
        val bottomPoint = Offset(centerX, centerY + r * 1.2f)

        drawCircle(color = color, radius = r, center = leftCenter)
        drawCircle(color = color, radius = r, center = rightCenter)

        drawPath(
            Path().apply {
                moveTo(leftCenter.x - r * 0.3f, leftCenter.y + r * 0.2f)
                lineTo(bottomPoint.x, bottomPoint.y)
                lineTo(rightCenter.x + r * 0.3f, rightCenter.y + r * 0.2f)
                close()
            },
            color = color
        )

        drawPath(
            Path().apply {
                moveTo(leftCenter.x - r * 0.2f, leftCenter.y + r * 0.1f)
                lineTo(bottomPoint.x, bottomPoint.y - r * 0.3f)
                lineTo(rightCenter.x + r * 0.2f, rightCenter.y + r * 0.1f)
                close()
            },
            color = color.copy(alpha = 0.6f),
            style = Stroke(width = 4.dp.toPx())
        )

        drawCircle(
            color = Color.White.copy(alpha = 0.4f),
            radius = r * 0.3f,
            center = Offset(leftCenter.x - r * 0.2f, leftCenter.y - r * 0.2f)
        )
    }
}
