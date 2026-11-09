package ir.amiraligrr.blockboom.ui.screens

import kotlinx.coroutines.isActive
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.ui.components.TutorialPulseRing
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import ir.amiraligrr.blockboom.ui.theme.NeonCyan
import ir.amiraligrr.blockboom.ui.theme.NeonPink
import ir.amiraligrr.blockboom.ui.theme.NeonGreen
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------- background helpers

fun positiveModulo(value: Float, mod: Float): Float {
    val m = if (mod <= 0f) 1f else mod
    return ((value % m) + m) % m
}

data class BgStar(val x: Float, val speed: Float, val phase: Float, val size: Float) {
    fun yEx(height: Float): Float = positiveModulo(x * 12.9898f * height, height)
}

data class BgParticle(
    val x: Float,
    val y: Float,
    val speedPx: Float,
    val phase: Float,
    val size: Float,
    val hot: Boolean
)

// ---------------------------------------------------------------- background — PREMIUM MESH

@Composable
fun NeonBackground() {
    val c = joyThemeColors()
    val dark = c.isDark
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nano -> time = nano / 1_000_000_000f }
        }
    }

    val stars = remember {
        List(90) { i ->
            BgStar(
                x = 0.05f + 0.90f * abs(sin(i * 12.9898f)),
                speed = 0.6f + (i % 5) * 0.35f,
                phase = i * 1.7f,
                size = 1.1f + (i % 4) * 0.7f
            )
        }
    }
    val particles = remember {
        List(30) { i ->
            BgParticle(
                x = 0.06f + 0.88f * abs(sin(i * 7.3f)),
                y = 0.04f + 0.92f * abs(sin(i * 3.1f + 0.5f)),
                speedPx = 14f + (i % 6) * 7f,
                phase = i * 2.1f,
                size = 2.2f + (i % 4) * 1.4f,
                hot = i % 4 == 0
            )
        }
    }

    // The blobs, sparkles and orbs are re-tinted rather than re-authored: the
    // composition is identical, only the palette behind it changes. Alphas are
    // halved in dark mode because a saturated blob at light-mode strength sits
    // on near-black and turns into a bruise.
    val blobA = if (dark) c.emerald else c.emerald
    val blobAStrength = if (dark) 0.20f else 0.16f
    val blobBStrength = if (dark) 0.16f else 0.13f
    val blobCStrength = if (dark) 0.16f else 0.13f
    val blobDStrength = if (dark) 0.14f else 0.11f
    val blobEStrength = if (dark) 0.12f else 0.09f
    val sparkleStrength = if (dark) 0.60f else 0.55f
    val orbStrength = if (dark) 0.20f else 0.14f
    val orbCore = if (dark) 0.72f else 0.82f

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(brush = c.gradientBackground)

        // Soft candy blobs — pastel pops on light mint, deep aurora on dark
        val pulse = 0.85f + 0.15f * sin(time * 0.42f)
        val pulse2 = 0.88f + 0.12f * sin(time * 0.33f + 1.4f)
        // Emerald blob top-left
        drawCircle(
            brush = Brush.radialGradient(
                listOf(blobA.copy(alpha = blobAStrength * pulse), Color.Transparent),
                center = Offset(size.width * 0.22f + sin(time * 0.2f) * size.width * 0.03f, size.height * 0.24f),
                radius = size.minDimension * 0.62f
            ),
            radius = size.minDimension * 0.62f,
            center = Offset(size.width * 0.22f, size.height * 0.24f)
        )
        // Coral peach blob center-right
        drawCircle(
            brush = Brush.radialGradient(
                listOf(c.coral.copy(alpha = blobBStrength * pulse2), Color.Transparent),
                center = Offset(size.width * 0.84f, size.height * 0.66f),
                radius = size.minDimension * 0.56f
            ),
            radius = size.minDimension * 0.56f,
            center = Offset(size.width * 0.84f, size.height * 0.66f)
        )
        // Sky teal blob bottom-left
        drawCircle(
            brush = Brush.radialGradient(
                listOf(c.sky.copy(alpha = blobCStrength), Color.Transparent),
                center = Offset(size.width * 0.16f, size.height * 0.88f),
                radius = size.minDimension * 0.48f
            ),
            radius = size.minDimension * 0.48f,
            center = Offset(size.width * 0.16f, size.height * 0.88f)
        )
        // Sunny gold top-right
        drawCircle(
            brush = Brush.radialGradient(
                listOf(c.amber.copy(alpha = blobDStrength * pulse), Color.Transparent),
                center = Offset(size.width * 0.72f, size.height * 0.18f),
                radius = size.minDimension * 0.42f
            ),
            radius = size.minDimension * 0.42f,
            center = Offset(size.width * 0.72f, size.height * 0.18f)
        )
        // Extra mint center
        drawCircle(
            brush = Brush.radialGradient(
                listOf(c.teal.copy(alpha = blobEStrength * pulse2), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.48f),
                radius = size.minDimension * 0.70f
            ),
            radius = size.minDimension * 0.70f,
            center = Offset(size.width * 0.5f, size.height * 0.48f)
        )

        // Twinkling sparkles
        for (s in stars) {
            val twinkle = 0.18f + 0.42f * (0.5f + 0.5f * sin(time * s.speed + s.phase))
            drawCircle(
                color = c.mintLight.copy(alpha = twinkle * sparkleStrength),
                radius = s.size,
                center = Offset(s.x * size.width, s.yEx(size.height))
            )
        }

        // Floating candy orbs drifting upward
        for (p in particles) {
            val span = size.height + 40f
            val pos = positiveModulo(p.y * span - time * p.speedPx, span) - 20f
            val wobble = sin(time * 1.1f + p.phase) * size.width * 0.015f
            val alphaPulse = 0.34f + 0.24f * sin(time * 1.4f + p.phase)
            val orbColor = when {
                p.hot -> c.amber
                p.phase % 3 < 1 -> c.teal
                else -> c.sky
            }
            drawCircle(
                color = orbColor.copy(alpha = alphaPulse * orbStrength),
                radius = p.size * 3.2f,
                center = Offset(p.x * size.width + wobble, pos)
            )
            drawCircle(
                color = orbColor,
                radius = p.size,
                center = Offset(p.x * size.width + wobble, pos),
                alpha = alphaPulse * orbCore
            )
        }

        // Vignette. This one effect is inverted rather than re-tinted: on the
        // light palette it lifts the edges toward white, on the dark palette
        // the same lift would be a grey haze, so it sinks them toward black
        // and pulls the eye back to the centre of the board.
        drawRect(
            brush = Brush.radialGradient(
                if (dark) {
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.34f))
                } else {
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.42f))
                },
                center = Offset(size.width * 0.5f, size.height * 0.5f),
                radius = size.maxDimension * 0.78f
            )
        )
    }
}

// ---------------------------------------------------------------- logo — GLASS + STROKE + 3D

@Composable
fun NeonLogo(pulse: Boolean) {
    val c = joyThemeColors()
    val transition = rememberInfiniteTransition(label = "logoPulse")
    val scale by transition.animateFloat(
        initialValue = if (pulse) 0.97f else 1f,
        targetValue = if (pulse) 1.04f else 1.018f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (pulse) 800 else 1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )
    val floatY by transition.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "floatY"
    )

    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale; translationY = floatY }
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Halo. The glow is a tint of the wordmark's own colour, so on the
            // dark palette the wordmark is near-white and the halo has to be
            // near-white too — a fixed emerald halo around white text reads as
            // a dirty smudge.
            Box(
                modifier = Modifier
                    .size(196.dp, 48.dp)
                    .blur(16.dp)
                    .background(c.emerald.copy(alpha = 0.14f), RoundedCornerShape(50))
            )
            Box(contentAlignment = Alignment.Center) {
                // Shadow stroke for depth
                Text(
                    text = "BLOCK  BOOM",
                    style = TextStyle(
                        color = c.textPrimary.copy(alpha = 0.92f),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        shadow = Shadow(
                            offset = Offset(0f, 3f),
                            color = c.emerald.copy(alpha = 0.18f),
                            blurRadius = 10f
                        )
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { translationX = 1f; translationY = 1f }
                )
                // Main candy gradient text
                Text(
                    text = "BLOCK  BOOM",
                    style = TextStyle(
                        brush = Brush.horizontalGradient(
                            // Read light-to-dark in the light palette, and
                            // bright-to-deep in the dark one, so the wordmark
                            // keeps its contrast in both.
                            listOf(
                                c.textPrimary,
                                c.emerald,
                                c.teal,
                                if (c.isDark) c.mintLight else c.textPrimary
                            )
                        ),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
        // Subtitle chip
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .background(c.surface, RoundedCornerShape(50))
                .border(1.dp, c.emerald.copy(alpha = 0.14f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✦  ARCADE  BLAST  ✦",
                style = TextStyle(
                    color = c.emerald,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.2.sp
                )
            )
        }
    }
}
