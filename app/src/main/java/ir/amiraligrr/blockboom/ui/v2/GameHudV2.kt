package ir.amiraligrr.blockboom.ui.v2

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.data.GameMode
import ir.amiraligrr.blockboom.domain.GameEngine
import ir.amiraligrr.blockboom.ui.components.ComboMeter
import ir.amiraligrr.blockboom.ui.components.PowerUpTimer
import ir.amiraligrr.blockboom.ui.formatNumber
import kotlin.math.ceil

private val HudBg = Color(0xE6100624)
private val HudLine = Color(0xFF7B49C8)
private val HudText = Color(0xFFF7F0FF)
private val HudMuted = Color(0xFFB7A4D8)
private val HudGold = Color(0xFFFFC93C)
private val HudCyan = Color(0xFF23D5FF)
private val HudRed = Color(0xFFFF5A6E)
private val HudGreen = Color(0xFF35E08B)

/**
 * The new in-game HUD.
 *
 * One compact glass bar instead of the old four stacked strips. The play field
 * keeps its height, the numbers that matter (level, score, coins, hearts) are
 * in one line, and the pause button is a single 40dp target instead of a small
 * icon floating over the field.
 */
@Composable
fun GameHudV2(
    engine: GameEngine,
    hearts: Int,
    coins: Long,
    isPremium: Boolean,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val score by animateIntAsState(
        targetValue = if (engine.mode == GameMode.RECORD || engine.mode == GameMode.TIME_ATTACK) engine.runScore else engine.score,
        animationSpec = tween(180),
        label = "hudScore"
    )
    val progress = if (engine.totalBlocksInLevel <= 0) 0f
    else (engine.blocksDestroyed.toFloat() / engine.totalBlocksInLevel).coerceIn(0f, 1f)
    val timed = engine.mode == GameMode.TIME_ATTACK

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(HudBg)
            .border(1.dp, HudLine.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A1450))
                    .border(1.dp, HudPurple.copy(alpha = 0.7f), CircleShape)
                    .clickable(onClick = onPause),
                contentAlignment = Alignment.Center
            ) { Text("⏸", color = HudText, fontSize = 16.sp) }

            Spacer(Modifier.width(9.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (engine.mode == GameMode.BOSS) "باس" else "مرحله",
                        color = HudMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (engine.mode == GameMode.BOSS) formatNumber(engine.level) else formatNumber(engine.level),
                        color = HudText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                    if (isPremium) {
                        Spacer(Modifier.width(4.dp))
                        Text("👑", fontSize = 10.sp)
                    }
                }
                Text(
                    text = formatNumber(score),
                    color = HudGold,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                HudValue("🪙", formatNumber(coins), HudGold)
                Spacer(Modifier.height(3.dp))
                HudValue("❤", if (isPremium) "∞" else formatNumber(hearts), HudRed)
            }
        }

        Spacer(Modifier.height(7.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF2A174B))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(HudCyan, HudPurple, HudGreen)))
            )
        }

        if (timed) {
            Spacer(Modifier.height(6.dp))
            val remaining = engine.timeAttackRemaining
            val fraction = (remaining / GameEngine.TIME_ATTACK_SECONDS).coerceIn(0f, 1f)
            val urgent = remaining <= 10f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏱", fontSize = 11.sp)
                Spacer(Modifier.width(5.dp))
                Text(
                    "٠٠:${"%02d".format(ceil(remaining).toInt().coerceAtLeast(0))}",
                    color = if (urgent) HudRed else HudCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF2A174B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (urgent) Brush.horizontalGradient(listOf(HudRed, HudRed))
                                else Brush.horizontalGradient(listOf(HudCyan, HudGreen))
                            )
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PowerUpTimer(engine = engine, modifier = Modifier.weight(1f, fill = false))
            ComboMeter(combo = engine.combo, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HudValue(emoji: String, value: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 11.sp)
        Spacer(Modifier.width(3.dp))
        Text(value, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

private val HudPurple = Color(0xFF9B4DFF)
