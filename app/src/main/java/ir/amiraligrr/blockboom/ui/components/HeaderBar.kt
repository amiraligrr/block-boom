package ir.amiraligrr.blockboom.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.data.LivesManager
import androidx.compose.ui.draw.shadow
import ir.amiraligrr.blockboom.ui.formatNumber
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import ir.amiraligrr.blockboom.ui.theme.NeonCyan
import ir.amiraligrr.blockboom.ui.theme.NeonGold
import ir.amiraligrr.blockboom.ui.theme.NeonRed
import ir.amiraligrr.blockboom.ui.theme.OffWhite

/**
 * Premium top bar — glass pills with living animations (scale pop on value change,
 * shimmer border, premium crown). Feels like a AAA HUD.
 */
@Composable
fun HeaderBar(
    hearts: Int,
    coins: Long,
    level: Int? = null,
    isPremium: Boolean = false,
    onSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val c = joyThemeColors()
    // Bounce triggers on value changes
    var heartsPop by remember { mutableStateOf(false) }
    var coinsPop by remember { mutableStateOf(false) }
    var lastHearts by remember { mutableStateOf(hearts) }
    var lastCoins by remember { mutableStateOf(coins) }

    LaunchedEffect(hearts) {
        if (hearts != lastHearts) {
            heartsPop = true; lastHearts = hearts
            kotlinx.coroutines.delay(220); heartsPop = false
        }
    }
    LaunchedEffect(coins) {
        if (coins != lastCoins) {
            coinsPop = true; lastCoins = coins
            kotlinx.coroutines.delay(260); coinsPop = false
        }
    }

    val heartsScale by animateFloatAsState(
        targetValue = if (heartsPop) 1.18f else 1f,
        animationSpec = tween(220, easing = FastOutSlowInEasing), label = "heartsPop"
    )
    val coinsScale by animateFloatAsState(
        targetValue = if (coinsPop) 1.18f else 1f,
        animationSpec = tween(220, easing = FastOutSlowInEasing), label = "coinsPop"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hearts pill — glass with red glow
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = heartsScale; scaleY = heartsScale }
        ) {
            HeaderPill(
                accent = NeonRed,
                glow = NeonRed.copy(alpha = 0.28f)
            ) {
                Text(text = "❤️", style = MaterialTheme.typography.titleMedium, fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isPremium) "∞" else stringResource(R.string.lives_out_of, hearts, LivesManager.MAX_LIVES),
                    // The pill is near-white in light mode and dark green in
                    // dark mode. The old `c.coral` was still only 3.59:1 on
                    // the light pill, and #F87171 was 4.38:1 on the dark one
                    // — both below the 4.5:1 normal-text bar for a number
                    // players check before every run. These two inks keep the
                    // red identity but clear the bar in their own theme.
                    color = if (isPremium) c.amber
                            else if (c.isDark) Color(0xFFFCA5A5)
                            else Color(0xFFB91C1C),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                if (isPremium) {
                    Spacer(Modifier.width(6.dp))
                    Text(text = "★", color = c.amber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (level != null) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val lvlPop = remember { mutableStateOf(false) }
                var lastLvl by remember { mutableStateOf(level) }
                LaunchedEffect(level) {
                    if (level != lastLvl) { lvlPop.value = true; lastLvl = level; kotlinx.coroutines.delay(200); lvlPop.value = false }
                }
                val s by animateFloatAsState(if (lvlPop.value) 1.14f else 1f, tween(180), label = "lvl")
                Box(Modifier.graphicsLayer { scaleX = s; scaleY = s }) {
                    HeaderPill(accent = NeonCyan, glow = NeonCyan.copy(alpha = 0.22f)) {
                        Text(
                            text = stringResource(R.string.my_level, level),
                            // `NeonCyan` is #2DD4BF — 1.81:1 on the light pill
                            // fill, so the level read as a smudge in light mode.
                            // `c.sky` clears 5:1 on every fill in both themes.
                            color = c.sky,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Box(Modifier.graphicsLayer { scaleX = coinsScale; scaleY = coinsScale }) {
            HeaderPill(accent = NeonGold, glow = NeonGold.copy(alpha = 0.28f)) {
                Text(text = "🪙", style = MaterialTheme.typography.titleMedium, fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                val animatedCoins by animateIntAsState(targetValue = coins.toInt(), tween(520, easing = FastOutSlowInEasing), label = "coinsAnim")
                Text(
                    text = formatNumber(animatedCoins),
                    // `NeonGold` is #FDE047 — 1.31:1 on the light pill fill.
                    // The coin balance is the second-most-read number in the
                    // app and was the one that vanished in light mode.
                    // `c.amber` is #B45309 / #FBBF24, >=4.5:1 on every fill in
                    // both themes, and still reads as the gold accent.
                    color = c.amber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Settings lives here rather than in the home screen's action grid.
        //
        // The grid has four slots and support genuinely belongs in one of them
        // — it is the screen a player reaches *because* something is broken.
        // Settings was displaced to make room, which left the only route to
        // sound and theme gone entirely.
        //
        // A gear in the corner is the conventional home for it, costs no grid
        // slot, and stays reachable from every screen that shows this bar.
        if (onSettings != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surface)
                    .border(1.dp, c.cardBorder, RoundedCornerShape(14.dp))
                    .clickable { onSettings() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⚙️", fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun HeaderPill(
    accent: Color,
    glow: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val c = joyThemeColors()
    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(1.dp, c.cardBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        // Soft accent glow underlay for premium pop — subtle on light
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(glow.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.align(Alignment.Center)
        ) { content() }
    }
}

/**
 * Addictive shimmer progress bar — triple gradient slides endlessly,
 * particle sparks at the tip and scale pop on progress.
 */
@Composable
fun LevelProgressBar(
    destroyed: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val c = joyThemeColors()
    val progress = if (total <= 0) 0f else (destroyed.toFloat() / total).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress, animationSpec = tween(700, easing = FastOutSlowInEasing), label = "prog"
    )
    val infinite = rememberInfiniteTransition(label = "shimmer")
    val shimmer by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1400, easing = androidx.compose.animation.core.LinearEasing)), label = "shimmer"
    )
    val glowPulse by infinite.animateFloat(
        initialValue = 0.78f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing)), label = "pulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // Measured so the spark can sit at the true end of the fill. It used a
        // hardcoded 320dp, which put the spark short of the end on a wide
        // screen and past the end on a narrow one.
        var trackWidth by remember { mutableIntStateOf(0) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(14.dp))
                // was a hard-coded near-black green; fine in light mode but
                // off-brand in dark mode. The track is still darker than the
                // fill in both themes.
                .background(joyThemeColors().surfaceSoft)
                .border(1.dp, c.cardBorder, RoundedCornerShape(14.dp))
                .onSizeChanged { trackWidth = it.width }
                .padding(3.dp)
        ) {
            // Track glow. The travelling gradient is built in the draw phase:
            // passing `shimmer` into Brush.horizontalGradient in the modifier
            // chain recomposed this header — which sits on every screen — 60
            // times a second.
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .drawBehind {
                        val span = size.width
                        val cx = shimmer * (span + 360f) - 180f
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(c.emerald, NeonCyan, NeonGold, c.emerald),
                                startX = cx - 180f,
                                endX = cx + 180f
                            )
                        )
                    }
            )
            // Top gloss
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent))
                    )
                    .align(Alignment.TopStart)
            )
            // Leading spark
            if (animatedProgress > 0.03f && animatedProgress < 0.98f && trackWidth > 0) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            translationX = animatedProgress * trackWidth - 6.dp.toPx()
                            scaleX = glowPulse
                            scaleY = glowPulse
                            alpha = glowPulse
                        }
                        .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(50))
                        .border(2.dp, NeonGold, RoundedCornerShape(50))
                )
            }
            Text(
                text = stringResource(R.string.blocks_progress, destroyed, total),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 0.6.sp),
                // Was `OffWhite` (light grey) — 1.8:1 on the emerald/cyan
                // fill, so the progress text was effectively invisible in both
                // themes. The bar's fill is a gradient of emerald→cyan→gold,
                // and the only ink that reads on *all* of it is the dark
                // `c.onPrimary` (same token the buttons use), which is
                // >=5:1 across the whole gradient in both themes.
                color = c.onPrimary
            )
        }
    }
}
