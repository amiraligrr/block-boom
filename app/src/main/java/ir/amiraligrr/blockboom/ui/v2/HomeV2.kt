package ir.amiraligrr.blockboom.ui.v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.data.GameMode
import ir.amiraligrr.blockboom.data.LivesManager
import ir.amiraligrr.blockboom.data.SaveManager
import ir.amiraligrr.blockboom.ui.dialogs.NoLivesDialog
import ir.amiraligrr.blockboom.ui.screens.DailyRewardDialog
import ir.amiraligrr.blockboom.ui.components.AppIcon
import ir.amiraligrr.blockboom.ui.components.AppIconView
import ir.amiraligrr.blockboom.ui.formatNumber
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
//  The new generation's home. Visual language taken from the Neon Menu
//  reference: deep violet space, 3D gold wordmark, floating cubes, glowing
//  mode cards, and a bottom tab bar.
// ─────────────────────────────────────────────────────────────────────────────

private val VoidTop = Color(0xFF080318)
private val VoidMid = Color(0xFF170A3A)
private val VoidBottom = Color(0xFF05020F)
private val NeonPurple = Color(0xFF9B4DFF)
private val NeonBlue = Color(0xFF3D8BFF)
private val NeonMagenta = Color(0xFFE23BFF)
private val NeonCyan = Color(0xFF23D5FF)
private val GoldTop = Color(0xFFFFE58A)
private val GoldBottom = Color(0xFFFF9D1C)
private val Ink = Color(0xFF1A0A00)

/**
 * Four doors into the game. Kept separate from [GameMode] because the menu is
 * also the place where a mode gets its presentation (name, colour, art), and
 * the engine should not know how a card is painted.
 */
enum class MenuMode(
    val titleFa: String,
    val subtitleFa: String,
    val icon: AppIcon,
    val top: Color,
    val bottom: Color,
) {
    CLASSIC("کلاسیک", "مراحل و پیشرفت", AppIcon.TARGET, Color(0xFF2D7BFF), Color(0xFF0B2E8C)),
    INFINITY("بی‌نهایت", "رکورد و لیدربورد", AppIcon.INFINITY, Color(0xFF35E08B), Color(0xFF0B6B3A)),
}

@Composable
fun HomeV2(
    saveManager: SaveManager,
    livesManager: LivesManager,
    onPlayMode: (GameMode) -> Unit,
    onShop: () -> Unit,
    onSkins: () -> Unit,
    onMissions: () -> Unit,
    onFreeCoins: () -> Unit,
    onSettings: () -> Unit,
    onLeaderboard: () -> Unit,
    onStats: () -> Unit,
    onAchievements: () -> Unit,
    onSocial: () -> Unit,
    onMore: () -> Unit,
    onDailyReward: () -> Unit,
    onExit: () -> Unit,
) {
    var showNoLives by remember { mutableStateOf(false) }
    var showDaily by remember { mutableStateOf(saveManager.isDailyRewardAvailable()) }
    var dailyClaimed by remember { mutableStateOf<Long?>(null) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val lastMode = remember { mutableStateOf(GameMode.STAGE) }

    fun start(mode: GameMode) {
        lastMode.value = mode
        if (livesManager.tryConsumeLife()) {
            onPlayMode(mode)
        } else {
            showNoLives = true
        }
    }

    val startFromMenu: (MenuMode) -> Unit = { menu ->
        start(
            when (menu) {
                MenuMode.CLASSIC -> GameMode.STAGE
                MenuMode.INFINITY -> GameMode.RECORD
            }
        )
    }

    if (showNoLives) {
        NoLivesDialog(
            livesManager = livesManager,
            onWatchAd = {
                showNoLives = false
                start(lastMode.value)
            },
            onGoPremium = { showNoLives = false; onShop() },
            onDismiss = { showNoLives = false }
        )
    }

    if (showDaily) {
        DailyRewardDialog(
            streak = saveManager.dailyStreak,
            onClaim = {
                dailyClaimed = saveManager.claimDailyReward()
                showDaily = false
                onDailyReward()
            },
            onDismiss = { showDaily = false }
        )
    }

    val c = joyThemeColors()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.backgroundTop, c.backgroundMid, c.backgroundBottom)))
    ) {
        NeonMenuBackdrop()

        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = entered,
                enter = fadeIn(tween(420, easing = FastOutSlowInEasing)) +
                    slideInVertically(
                        animationSpec = tween(420, easing = FastOutSlowInEasing),
                        initialOffsetY = { it / 12 }
                    ),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // The menu is a single-screen composition. On a short phone
                // the fixed design height would clip the mode cards, so scale
                // the whole content block instead of introducing a scrollbar
                // the reference does not have.
                val designHeight = 760.dp
                val scale = ((maxHeight * 0.94f) / designHeight).coerceIn(0.56f, 1f)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (scale >= 0.999f) maxHeight else maxHeight / scale)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                        .padding(horizontal = 16.dp)
                ) {
                Spacer(Modifier.height(4.dp))
                ProfileRow(
                    name = saveManager.playerName.ifBlank { "بازیکن" },
                    level = saveManager.accountLevel(),
                    xpProgress = saveManager.xpProgress(),
                    onSettings = onSettings
                )
                Spacer(Modifier.height(8.dp))
                CurrencyRow(
                    coins = saveManager.coins,
                    onAdd = onShop
                )
                Spacer(Modifier.height(8.dp))
                EventRow(
                    dailyAvailable = saveManager.isDailyRewardAvailable(),
                    streak = saveManager.dailyStreak,
                    onDaily = {
                        if (saveManager.isDailyRewardAvailable()) showDaily = true else onDailyReward()
                    },
                    onAchievements = onAchievements
                )

                Spacer(Modifier.height(10.dp))
                LogoBlock()
                Spacer(Modifier.height(8.dp))

                Spacer(Modifier.height(10.dp))
                ModeGrid(onPick = startFromMenu)

                Spacer(Modifier.height(10.dp))
                QuickActions(
                    onShop = onShop,
                    onSkins = onSkins,
                    onMissions = onMissions,
                    onFreeCoins = onFreeCoins
                )


                if (dailyClaimed != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "🎁 +${formatNumber(dailyClaimed!!)} سکه به حسابت اضافه شد",
                        color = GoldTop,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(8.dp))
                }
            }
            }

            BottomNavV2(
                onHome = { },
                onStats = onStats,
                onLeaderboard = onLeaderboard,
                onSocial = onSocial,
                onMore = onMore
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Backdrop
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NeonMenuBackdrop() {
    val c = joyThemeColors()
    val context = LocalContext.current
    val lowRam = remember(context) {
        runCatching {
            (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
                .isLowRamDevice
        }.getOrDefault(false)
    }
    // A static backdrop on a low-RAM device keeps the menu off the GPU's
    // critical path. The cubes and glow are decoration, not information.
    val phase = if (lowRam) 0f else {
        val infinite = rememberInfiniteTransition(label = "menuBackdrop")
        val animated by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 6.28318f,
            animationSpec = infiniteRepeatable(
                animation = tween(22000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
        animated
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Planet in the upper right.
        drawCircle(
            brush = Brush.radialGradient(
                colors = if (c.isDark) listOf(Color(0xFF6D5BFF), Color(0xFF2B146B), Color(0x00120A2E)) else listOf(Color(0xFFC9B8FF), Color(0xFFEDE5FF), Color(0x00FFFFFF)),
                center = Offset(size.width * 0.86f, size.height * 0.16f),
                radius = size.width * 0.62f
            ),
            radius = size.width * 0.52f,
            center = Offset(size.width * 0.86f, size.height * 0.16f)
        )

        // Purple horizon glow behind the logo.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(c.emerald.copy(alpha = if (c.isDark) 0.30f else 0.14f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.42f),
                radius = size.width * 0.72f
            ),
            radius = size.width * 0.72f,
            center = Offset(size.width * 0.5f, size.height * 0.42f)
        )

        // Castle silhouette at the lower edge.
        val castle = Path().apply {
            val base = size.height * 0.86f
            moveTo(0f, size.height)
            lineTo(0f, base)
            var x = 0f
            val step = size.width / 9f
            repeat(9) { i ->
                val h = if (i % 2 == 0) size.height * 0.10f else size.height * 0.055f
                lineTo(x, base - h)
                lineTo(x + step * 0.5f, base - h)
                lineTo(x + step * 0.5f, base - h - size.height * 0.02f)
                lineTo(x + step * 0.7f, base - h - size.height * 0.02f)
                lineTo(x + step * 0.7f, base - h)
                lineTo(x + step, base - h)
                x += step
            }
            lineTo(size.width, base)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(castle, color = if (c.isDark) Color(0xFF1B0B3F) else Color(0xFFD9CCF5))
        drawPath(castle, color = c.emerald.copy(alpha = 0.10f))

        // Falling blocks. The menu should read as the game's own board
        // breathing behind the UI, not as unrelated floating decorations.
        val palette = listOf(
            Color(0xFF7B3BFF), Color(0xFF2D7BFF), Color(0xFFE53BFF),
            Color(0xFF35E08B), Color(0xFFFF8A2A), Color(0xFF9B4DFF),
            Color(0xFF23D5FF), Color(0xFFFFD23F)
        )
        val progress = phase / 6.28318f
        val count = if (lowRam) 5 else 12
        repeat(count) { i ->
            val lane = ((i * 0.137f) + 0.06f) % 1f
            val fallSpeed = 0.72f + (i % 4) * 0.18f
            val fall = (progress * fallSpeed + i * 0.13f) % 1f
            val side = size.width * (0.050f + (i % 4) * 0.011f)
            val sway = sin(phase * 0.8f + i * 1.3f) * size.width * 0.014f
            val center = Offset(
                x = size.width * lane + sway,
                y = -side + (size.height + side * 2f) * fall
            )
            rotate(degrees = (phase * 24f + i * 37f) % 360f, pivot = center) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            palette[i % palette.size].copy(alpha = 0.92f),
                            palette[i % palette.size].copy(alpha = 0.30f)
                        ),
                        start = center - Offset(side / 2f, side / 2f),
                        end = center + Offset(side / 2f, side / 2f)
                    ),
                    topLeft = Offset(center.x - side / 2f, center.y - side / 2f),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(side * 0.22f)
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.55f),
                    topLeft = Offset(center.x - side * 0.28f, center.y - side * 0.30f),
                    size = Size(side * 0.20f, side * 0.12f),
                    cornerRadius = CornerRadius(side * 0.05f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Header: identity + currency
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileRow(
    name: String,
    level: Int,
    xpProgress: Float,
    onSettings: () -> Unit,
) {
    val c = joyThemeColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFB26BFF), Color(0xFF4E1A9E), Color(0xFF1B0A3E))
                        )
                    )
                    .border(2.dp, Color(0xFFD7B6FF).copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }
            AppIconView(
                icon = AppIcon.CROWN,
                tint = GoldTop,
                size = 16.dp,
                modifier = Modifier.offset(x = 2.dp, y = (-7).dp)
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = name,
                color = c.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Text(
                text = "سطح $level",
                color = c.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .height(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(c.surfaceSoft)
                    .border(1.dp, Color(0xFF6A41B8).copy(alpha = 0.55f), RoundedCornerShape(50))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(xpProgress.coerceIn(0f, 1f))
                        .height(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(NeonPurple, NeonMagenta)))
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(c.surface)
                .border(1.dp, c.cardBorder.copy(alpha = 0.6f), CircleShape)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center
        ) {
            AppIconView(icon = AppIcon.GEAR, tint = c.textSecondary, size = 17.dp)
        }
    }
}

@Composable
private fun CurrencyRow(coins: Long, onAdd: () -> Unit) {
    CurrencyPill(
        icon = AppIcon.COIN,
        value = formatNumber(coins),
        tint = GoldTop,
        modifier = Modifier.fillMaxWidth(),
        onAdd = onAdd
    )
}

@Composable
private fun CurrencyPill(
    icon: AppIcon,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit
) {
    val c = joyThemeColors()
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(c.surface)
            .border(1.dp, tint.copy(alpha = 0.55f), RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(start = 5.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(tint, tint.copy(alpha = 0.55f)))),
            contentAlignment = Alignment.Center
        ) {
            AppIconView(icon = icon, tint = c.onGold, size = 15.dp)
        }
        Text(
            text = value,
            color = c.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF9B4DFF), Color(0xFF5B20B0))))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) { Text(text = "+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun EventRow(
    dailyAvailable: Boolean,
    streak: Int,
    onDaily: () -> Unit,
    onAchievements: () -> Unit
) {
    val c = joyThemeColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        EventCard(
            icon = AppIcon.GIFT,
            title = "هدیه روزانه",
            subtitle = if (dailyAvailable) "آماده دریافت!" else "زنجیره: $streak روز",
            accent = if (dailyAvailable) GoldTop else c.textSecondary,
            modifier = Modifier.weight(1f),
            onClick = onDaily
        )
        EventCard(
            icon = AppIcon.TROPHY,
            title = "دستاوردها",
            subtitle = "مدال‌هایت را ببین",
            accent = Color(0xFF7DE3FF),
            modifier = Modifier.weight(1f),
            onClick = onAchievements
        )
    }
}

@Composable
private fun EventCard(
    icon: AppIcon,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = joyThemeColors()
    Row(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(c.surface, c.surfaceSoft)
                )
            )
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconView(icon = icon, tint = accent, size = 28.dp)
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(text = subtitle, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
        }
        Text(text = "›", color = c.textPrimary.copy(alpha = 0.8f), fontSize = 20.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Logo and play
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun pressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = tween(110),
        label = "press"
    ).value
}

@Composable
private fun LogoBlock() {
    val c = joyThemeColors()
    val float = rememberInfiniteTransition(label = "logoFloat")
    val offset by float.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoY"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = offset.dp.toPx() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "BLOCK",
            style = TextStyle(
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                brush = Brush.verticalGradient(listOf(c.textPrimary, c.emerald, c.emerald.copy(alpha = 0.75f))),
                shadow = Shadow(color = Color(0xFF0B0620), offset = Offset(0f, 7f), blurRadius = 14f)
            ),
            letterSpacing = (-2).sp
        )
        Text(
            text = "BOOM",
            style = TextStyle(
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                brush = Brush.verticalGradient(listOf(GoldTop, Color(0xFFFFC02E), GoldBottom)),
                shadow = Shadow(color = Color(0xFF4A1600), offset = Offset(0f, 8f), blurRadius = 16f)
            ),
            letterSpacing = (-3).sp,
            modifier = Modifier.offset(y = (-7).dp)
        )
        Text(
            text = "Shoot  •  Break  •  Win!",
            color = c.textSecondary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            modifier = Modifier.offset(y = (-8).dp)
        )
    }
}

private val ColorsSubtitle = Color(0xFFE9D8FF)

@Composable
private fun PlayButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    val glow = rememberInfiniteTransition(label = "playGlow")
    val pulse by glow.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "playPulse"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .height(68.dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                    alpha = 0.55f
                }
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(GoldTop.copy(alpha = 0.35f), GoldBottom.copy(alpha = 0.15f))))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(18.dp, RoundedCornerShape(50), ambientColor = GoldBottom, spotColor = GoldBottom)
                .clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(GoldTop, Color(0xFFFFC531), GoldBottom)))
                .border(3.dp, Color(0xFFFFF0B8), RoundedCornerShape(50))
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconView(icon = AppIcon.PLAY, tint = Ink, size = 26.dp, strokeWidth = 2.4.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = "بازی",
                color = Ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )
        }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Quick actions + modes
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuickActions(
    onShop: () -> Unit,
    onSkins: () -> Unit,
    onMissions: () -> Unit,
    onFreeCoins: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickTile(AppIcon.SHOP, "فروشگاه", Modifier.weight(1f), onShop)
        QuickTile(AppIcon.CROWN, "اسکین", Modifier.weight(1f), onSkins)
        QuickTile(AppIcon.MISSIONS, "ماموریت", Modifier.weight(1f), onMissions, badge = true)
        QuickTile(AppIcon.GIFT, "سکه رایگان", Modifier.weight(1f), onFreeCoins)
    }
}

@Composable
private fun QuickTile(
    icon: AppIcon,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    badge: Boolean = false
) {
    val c = joyThemeColors()
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(70.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(c.surface, c.surfaceSoft)))
            .border(1.dp, c.cardBorder.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            AppIconView(icon = icon, tint = c.emerald, size = 20.dp)
            if (badge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 7.dp, y = (-3).dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B5C))
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = c.textPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ModeGrid(onPick: (MenuMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ModeCard(MenuMode.CLASSIC, Modifier.fillMaxWidth()) { onPick(it) }
        ModeCard(MenuMode.INFINITY, Modifier.fillMaxWidth()) { onPick(it) }
    }
}

@Composable
private fun ModeCard(
    mode: MenuMode,
    modifier: Modifier = Modifier,
    onClick: (MenuMode) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    Box(
        modifier = modifier
            .height(96.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(mode.top, mode.bottom)))
            .border(1.5.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick(mode) }
    ) {
        // Diagonal shine.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, Color.Black.copy(alpha = 0.18f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIconView(
                icon = mode.icon,
                tint = Color.White,
                size = if (mode == MenuMode.INFINITY) 34.dp else 30.dp,
                strokeWidth = 2.2.dp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = mode.titleFa,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = mode.subtitleFa,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Bottom navigation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BottomNavV2(
    onHome: () -> Unit,
    onStats: () -> Unit,
    onLeaderboard: () -> Unit,
    onSocial: () -> Unit,
    onMore: () -> Unit
) {
    val c = joyThemeColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(28.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
                .clip(RoundedCornerShape(28.dp))
                .background(c.surface)
                .border(1.dp, c.cardBorder.copy(alpha = 0.65f), RoundedCornerShape(28.dp))
                .padding(horizontal = 5.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(AppIcon.HOME, "خانه", active = true, onClick = onHome)
            NavItem(AppIcon.CHART, "آمار", onClick = onStats)
            NavItem(AppIcon.TROPHY, "لیدربورد", onClick = onLeaderboard, badge = true)
            NavItem(AppIcon.USER, "اجتماعی", onClick = onSocial, badge = true)
            NavItem(AppIcon.MORE, "بیشتر", onClick = onMore)
        }
    }
}

@Composable
private fun NavItem(
    icon: AppIcon,
    label: String,
    active: Boolean = false,
    badge: Boolean = false,
    onClick: () -> Unit
) {
    val c = joyThemeColors()
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .then(
                if (active) {
                    Modifier
                        .background(c.emerald.copy(alpha = 0.12f))
                        .border(1.dp, c.emerald.copy(alpha = 0.30f), RoundedCornerShape(22.dp))
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            AppIconView(
                icon = icon,
                tint = if (active) c.emerald else c.textSecondary,
                size = 24.dp,
                strokeWidth = 1.9.dp
            )
            if (badge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(c.coral)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = if (active) c.textPrimary else c.textSecondary,
            fontSize = 11.5.sp,
            fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
            maxLines = 1
        )
    }
}
