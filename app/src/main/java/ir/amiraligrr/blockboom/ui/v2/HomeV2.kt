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
import ir.amiraligrr.blockboom.ui.formatNumber
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
private val TextMain = Color(0xFFF8F3FF)
private val TextDim = Color(0xFFB9A8D9)
private val CardLine = Color(0xFF7A4AC2)

/**
 * Four doors into the game. Kept separate from [GameMode] because the menu is
 * also the place where a mode gets its presentation (name, colour, art), and
 * the engine should not know how a card is painted.
 */
enum class MenuMode(
    val titleFa: String,
    val subtitleFa: String,
    val emoji: String,
    val top: Color,
    val bottom: Color,
) {
    CLASSIC("کلاسیک", "حالت اصلی بازی", "🎯", Color(0xFF2D7BFF), Color(0xFF0B2E8C)),
    BOSS("نبرد باس", "رئیس‌ها را شکست بده", "👹", Color(0xFFE53BFF), Color(0xFF7A0B7A)),
    INFINITY("بی‌نهایت", "بدون محدودیت", "∞", Color(0xFF35E08B), Color(0xFF0B6B3A)),
    TIME_ATTACK("حمله زمانی", "۶۰ ثانیه، بیشترین امتیاز", "⏱", Color(0xFFFF8A2A), Color(0xFF9C2A05)),
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
                MenuMode.BOSS -> GameMode.BOSS
                MenuMode.INFINITY -> GameMode.RECORD
                MenuMode.TIME_ATTACK -> GameMode.TIME_ATTACK
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(VoidTop, VoidMid, VoidBottom)))
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
                val scale = (maxHeight / designHeight).coerceIn(0.72f, 1f)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (scale >= 0.999f) maxHeight else designHeight)
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
                    level = saveManager.currentLevel,
                    onSettings = onSettings
                )
                Spacer(Modifier.height(8.dp))
                CurrencyRow(
                    coins = saveManager.coins,
                    gems = saveManager.gems,
                    onAdd = onShop
                )
                Spacer(Modifier.height(8.dp))
                EventRow(
                    dailyAvailable = saveManager.isDailyRewardAvailable(),
                    streak = saveManager.dailyStreak,
                    onDaily = {
                        if (saveManager.isDailyRewardAvailable()) showDaily = true else onDailyReward()
                    },
                    onLeague = onLeaderboard
                )

                Spacer(Modifier.height(10.dp))
                LogoBlock()
                Spacer(Modifier.height(8.dp))

                PlayButton { startFromMenu(MenuMode.CLASSIC) }

                Spacer(Modifier.height(10.dp))
                QuickActions(
                    onShop = onShop,
                    onSkins = onSkins,
                    onMissions = onMissions,
                    onFreeCoins = onFreeCoins
                )

                Spacer(Modifier.weight(1f))
                Text(
                    text = "حالت بازی",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = TextMain,
                        fontWeight = FontWeight.Black
                    )
                )
                Spacer(Modifier.height(8.dp))
                ModeGrid(onPick = startFromMenu)

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
                onAchievements = onAchievements,
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
    val infinite = rememberInfiniteTransition(label = "menuBackdrop")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Planet in the upper right.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF6D5BFF), Color(0xFF2B146B), Color(0x00120A2E)),
                center = Offset(size.width * 0.86f, size.height * 0.16f),
                radius = size.width * 0.62f
            ),
            radius = size.width * 0.52f,
            center = Offset(size.width * 0.86f, size.height * 0.16f)
        )

        // Purple horizon glow behind the logo.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(NeonPurple.copy(alpha = 0.40f), Color.Transparent),
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
        drawPath(castle, color = Color(0xFF1B0B3F))
        drawPath(castle, color = NeonPurple.copy(alpha = 0.16f))

        // Floating cubes. Each has its own lane, speed and tilt.
        val cubes = listOf(
            Triple(0.08f, 0.22f, Color(0xFF7B3BFF)),
            Triple(0.78f, 0.32f, Color(0xFF2D7BFF)),
            Triple(0.20f, 0.48f, Color(0xFFE53BFF)),
            Triple(0.88f, 0.55f, Color(0xFF35E08B)),
            Triple(0.12f, 0.70f, Color(0xFFFF8A2A)),
            Triple(0.70f, 0.78f, Color(0xFF9B4DFF)),
            Triple(0.46f, 0.12f, Color(0xFF23D5FF)),
            Triple(0.32f, 0.34f, Color(0xFFFFD23F))
        )
        cubes.forEachIndexed { i, (fx, fy, color) ->
            val drift = sin(phase + i * 0.9f) * size.width * 0.012f
            val bob = cos(phase * 0.8f + i) * size.height * 0.008f
            val center = Offset(size.width * fx + drift, size.height * fy + bob)
            val side = size.width * (0.055f + (i % 3) * 0.012f)
            rotate(degrees = (phase * 18f + i * 37f) % 360f, pivot = center) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.35f)),
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
private fun ProfileRow(name: String, level: Int, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(52.dp)
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
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Text(text = "👑", fontSize = 16.sp, modifier = Modifier.offset(x = 2.dp, y = (-7).dp))
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Text(
                text = "مرحله $level",
                color = TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .height(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF2B174E))
                    .border(1.dp, Color(0xFF6A41B8).copy(alpha = 0.55f), RoundedCornerShape(50))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((((level - 1).coerceAtLeast(0) % 10) + 1) / 10f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(NeonPurple, NeonMagenta)))
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF241142))
                .border(1.dp, CardLine.copy(alpha = 0.6f), CircleShape)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "⚙", color = TextMain, fontSize = 17.sp)
        }
    }
}

@Composable
private fun CurrencyRow(coins: Long, gems: Long, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CurrencyPill(
            emoji = "🪙",
            value = formatNumber(coins),
            tint = GoldTop,
            modifier = Modifier.weight(1f),
            onAdd = onAdd
        )
        CurrencyPill(
            emoji = "💎",
            value = formatNumber(gems),
            tint = Color(0xFFE38BFF),
            modifier = Modifier.weight(1f),
            onAdd = onAdd
        )
    }
}

@Composable
private fun CurrencyPill(
    emoji: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF1A0B36))
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
        ) { Text(text = emoji, fontSize = 14.sp) }
        Text(
            text = value,
            color = TextMain,
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
                .size(28.dp)
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
    onLeague: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        EventCard(
            emoji = "🎁",
            title = "هدیه روزانه",
            subtitle = if (dailyAvailable) "آماده دریافت!" else "زنجیره: $streak روز",
            accent = if (dailyAvailable) GoldTop else TextDim,
            modifier = Modifier.weight(1f),
            onClick = onDaily
        )
        EventCard(
            emoji = "🏆",
            title = "لیگ هفتگی",
            subtitle = "جایزه هفته را بگیر",
            accent = Color(0xFF7DE3FF),
            modifier = Modifier.weight(1f),
            onClick = onLeague
        )
    }
}

@Composable
private fun EventCard(
    emoji: String,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF25104A), Color(0xFF17082F))
                )
            )
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 22.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(text = subtitle, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text(text = "›", color = TextMain.copy(alpha = 0.8f), fontSize = 20.sp)
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
        Text(text = "👑", fontSize = 24.sp)
        Text(
            text = "BLOCK",
            style = TextStyle(
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFBFD8FF), Color(0xFF7C9BE8))),
                shadow = Shadow(color = Color(0xFF0B0620), offset = Offset(0f, 7f), blurRadius = 14f)
            ),
            letterSpacing = (-2).sp
        )
        Text(
            text = "BOOM",
            style = TextStyle(
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                brush = Brush.verticalGradient(listOf(GoldTop, Color(0xFFFFC02E), GoldBottom)),
                shadow = Shadow(color = Color(0xFF4A1600), offset = Offset(0f, 8f), blurRadius = 16f)
            ),
            letterSpacing = (-3).sp,
            modifier = Modifier.offset(y = (-9).dp)
        )
        Text(
            text = "Shoot  •  Break  •  Win!",
            color = ColorsSubtitle,
            fontSize = 11.sp,
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
                .height(64.dp)
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
            Text(text = "▶", color = Ink, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = "بازی",
                color = Ink,
                fontSize = 27.sp,
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
        QuickTile("🛍", "فروشگاه", Modifier.weight(1f), onShop)
        QuickTile("👑", "اسکین", Modifier.weight(1f), onSkins)
        QuickTile("📋", "ماموریت", Modifier.weight(1f), onMissions, badge = true)
        QuickTile("💎", "سکه رایگان", Modifier.weight(1f), onFreeCoins)
    }
}

@Composable
private fun QuickTile(
    emoji: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    badge: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2B1455), Color(0xFF150829))))
            .border(1.dp, CardLine.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Text(text = emoji, fontSize = 23.sp)
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
            color = TextMain,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ModeGrid(onPick: (MenuMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ModeCard(MenuMode.CLASSIC, Modifier.weight(1f)) { onPick(it) }
            ModeCard(MenuMode.BOSS, Modifier.weight(1f)) { onPick(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ModeCard(MenuMode.INFINITY, Modifier.weight(1f)) { onPick(it) }
            ModeCard(MenuMode.TIME_ATTACK, Modifier.weight(1f)) { onPick(it) }
        }
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
            .height(94.dp)
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color.Black.copy(alpha = 0.22f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.emoji,
                    color = Color.White,
                    fontSize = if (mode == MenuMode.INFINITY) 34.sp else 26.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(text = mode.titleFa, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = mode.subtitleFa,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
            }
            Text(text = "›", color = Color.White, fontSize = 22.sp)
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
    onAchievements: () -> Unit,
    onSocial: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B0620).copy(alpha = 0.97f))
            .border(width = 1.dp, color = NeonPurple.copy(alpha = 0.25f))
            .padding(vertical = 7.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem("🏠", "خانه", active = true, onClick = onHome)
        NavItem("📊", "آمار", onClick = onStats)
        NavItem("🏆", "دستاورد", onClick = onAchievements, badge = true)
        NavItem("👥", "اجتماعی", onClick = onSocial, badge = true)
        NavItem("▦", "بیشتر", onClick = onMore)
    }
}

@Composable
private fun NavItem(
    emoji: String,
    label: String,
    active: Boolean = false,
    badge: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (active) {
                    Modifier
                        .background(Brush.radialGradient(listOf(NeonPurple.copy(alpha = 0.35f), Color.Transparent)))
                        .border(1.dp, NeonPurple.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Text(text = emoji, fontSize = 18.sp)
            if (badge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-2).dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B5C))
                )
            }
        }
        Text(
            text = label,
            color = if (active) GoldTop else TextDim,
            fontSize = 8.5.sp,
            fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
            maxLines = 1
        )
    }
}
