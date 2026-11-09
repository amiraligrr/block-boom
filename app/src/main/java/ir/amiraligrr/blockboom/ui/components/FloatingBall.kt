package ir.amiraligrr.blockboom.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import ir.amiraligrr.blockboom.ui.theme.NeonCyan
import ir.amiraligrr.blockboom.ui.theme.NeonGold
import ir.amiraligrr.blockboom.ui.theme.NeonPink
import ir.amiraligrr.blockboom.ui.theme.NeonGreen
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium floating orb — dual contra-rotating orbits, nebula glow, and
 * 16-point comet tail with gold/cyan sparkle. Pure GPU canvas, no recomposition.
 */
@Composable
fun FloatingBall(
    ballSize: Dp,
    modifier: Modifier = Modifier,
    orbitRadiusRatio: Float = 0.34f,
    durationMillis: Int = 2400
) {
    val transition = rememberInfiniteTransition(label = "floatingBall")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitAngle"
    )
    val angle2 by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween((durationMillis * 1.6f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitAngle2"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width, size.height) * 0.5f
        val orbitRadius = size.minDimension * orbitRadiusRatio
        val outerRadius = orbitRadius * 1.42f
        val ballRadius = size.minDimension * 0.09f

        // Double nebula glow — inner + outer
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonGreen.copy(alpha = 0.22f * pulse),
                    NeonPink.copy(alpha = 0.07f),
                    Color.Transparent
                ),
                center = center,
                radius = outerRadius * 1.4f
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonCyan.copy(alpha = 0.14f),
                    Color.Transparent
                ),
                center = center,
                radius = orbitRadius * 1.9f
            )
        )

        // Outer faint orbit ring — contra rotation
        drawCircle(
            brush = Brush.sweepGradient(
                listOf(
                    NeonGold.copy(alpha = 0.28f),
                    NeonCyan.copy(alpha = 0.18f),
                    NeonGreen.copy(alpha = 0.22f),
                    NeonGold.copy(alpha = 0.28f)
                )
            ),
            radius = outerRadius,
            center = center,
            style = Stroke(width = size.minDimension * 0.0045f, cap = StrokeCap.Round)
        )
        // Inner orbit ring
        drawCircle(
            brush = Brush.sweepGradient(listOf(NeonCyan.copy(alpha = 0.45f), NeonGreen.copy(alpha = 0.42f))),
            radius = orbitRadius,
            center = center,
            style = Stroke(width = size.minDimension * 0.006f, cap = StrokeCap.Round)
        )

        // Sparkle dust on outer orbit — 6 points
        for (k in 0 until 6) {
            val a = (angle2 + k * 60f) * 0.0174533f
            val sx = center.x + cos(a) * outerRadius
            val sy = center.y + sin(a) * outerRadius
            val alpha = 0.45f + 0.35f * sin(angle2 * 0.02f + k)
            drawCircle(
                color = if (k % 2 == 0) NeonGold else NeonCyan,
                radius = size.minDimension * 0.012f,
                center = Offset(sx, sy),
                alpha = alpha.coerceIn(0f, 1f)
            )
        }

        // Comet trail — 16 points with gold/cyan alternate + taper
        for (i in 1..16) {
            val trailAngle = (angle - i * 11f) * 0.0174533f
            val tx = center.x + cos(trailAngle) * orbitRadius
            val ty = center.y + sin(trailAngle) * orbitRadius
            val alpha = (1f - i / 16f) * 0.62f
            val r = ballRadius * (1f - i * 0.045f).coerceAtLeast(0.26f)
            drawCircle(
                color = when (i % 4) {
                    0 -> NeonGold
                    1 -> NeonPink
                    else -> NeonCyan
                },
                radius = r,
                center = Offset(tx, ty),
                alpha = alpha
            )
        }

        // Ball outer halo pulse
        val radians = angle * 0.0174533f
        val ballX = center.x + cos(radians) * orbitRadius
        val ballY = center.y + sin(radians) * orbitRadius

        drawCircle(
            color = NeonCyan.copy(alpha = 0.28f * pulse),
            radius = ballRadius * 2.8f,
            center = Offset(ballX, ballY)
        )
        drawCircle(
            color = NeonGreen.copy(alpha = 0.16f),
            radius = ballRadius * 1.65f,
            center = Offset(ballX, ballY)
        )
        // Core with triple gradient
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White, NeonCyan, NeonGreen, Color(0xFF0F5E2A)),
                center = Offset(ballX - ballRadius * 0.28f, ballY - ballRadius * 0.32f),
                radius = ballRadius * 1.7f
            ),
            radius = ballRadius,
            center = Offset(ballX, ballY)
        )
        // Inner specular highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.42f),
            radius = ballRadius * 0.34f,
            center = Offset(ballX - ballRadius * 0.32f, ballY - ballRadius * 0.34f)
        )
    }
}
