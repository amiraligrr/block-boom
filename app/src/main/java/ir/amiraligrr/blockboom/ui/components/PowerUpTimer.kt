package ir.amiraligrr.blockboom.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.domain.GameEngine
import ir.amiraligrr.blockboom.domain.PowerUpType
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import kotlin.math.max

/** Seconds left at which a running power-up starts reading as urgent. */
private const val URGENT_SECONDS = 3f

/**
 * Premium HUD strip — glass pills with orbiting ring countdown and urgent pulse.
 */
@Composable
fun PowerUpTimer(engine: GameEngine, modifier: Modifier = Modifier) {
    val rows = engine.activePowerUps.entries.filter { it.key != PowerUpType.NUKE }
    if (rows.isEmpty()) return

    val c = joyThemeColors()
    val infinite = rememberInfiniteTransition(label = "pwrPulse")
    val glow by infinite.animateFloat(0.85f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow")

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { (type, remaining) ->
            val isUrgent = remaining <= URGENT_SECONDS
            val ratio = (remaining / max(type.duration, 0.1f)).coerceIn(0f, 1f)
            val urgentScale = if (isUrgent) glow else 1f

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    // Themed like every other pill in the HUD. This was a
                    // hardcoded dark-green gradient, so in light mode the one
                    // strip that is always on screen was the one strip that
                    // ignored the theme.
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(c.surface.copy(alpha = 0.92f), c.surfaceSoft.copy(alpha = 0.96f))
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = if (isUrgent) Color(0xFFFF5A5F).copy(alpha = 0.85f * urgentScale) else type.color.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(50.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Icon orb with countdown ring
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(22.dp)) {
                        Canvas(modifier = Modifier.size(22.dp)) {
                            // background ring
                            drawCircle(
                                color = Color.White.copy(alpha = 0.12f),
                                radius = size.minDimension * 0.5f,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                            // progress arc
                            drawArc(
                                color = if (isUrgent) Color(0xFFFF5A5F) else type.color,
                                startAngle = -90f,
                                sweepAngle = 360f * ratio,
                                useCenter = false,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                                size = Size(size.width, size.height)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(type.color.copy(alpha = 0.22f))
                                .border(1.dp, type.color.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = type.icon, fontSize = 9.sp)
                        }
                    }
                    Text(
                        text = "${remaining.toInt()}s",
                        color = if (isUrgent) Color(0xFFFF5A5F) else c.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                    // tiny label
                    Text(
                        text = when (type) {
                            PowerUpType.FREEZE -> "یخ"
                            PowerUpType.DOUBLE_DAMAGE -> "قدرت"
                            PowerUpType.RAPID_FIRE -> "رگبار"
                            PowerUpType.COIN_RUSH -> "سکه‌باران"
                            // GHOST used to fall into `else -> ""` and show a
                            // bare icon and number with no name — the one
                            // power-up whose whole point is that the player
                            // knows it is running.
                            PowerUpType.GHOST -> "شبح"
                            else -> ""
                        },
                        color = type.color.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
