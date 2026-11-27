package ir.amiraligrr.blockboom.ui.v2.intro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.R
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * Process-level record that the cold-start intro has begun.
 *
 * Deliberately not persisted: a genuine cold launch must show the intro again.
 * A configuration change, a return from gameplay or an activity recreation
 * must not, because this field lives for the process lifetime.
 */
object SessionIntro {
    @Volatile
    var started: Boolean = false
}

private val IntroBgTop = Color(0xFF07020F)
private val IntroBgBottom = Color(0xFF12062A)
private val IntroPurple = Color(0xFF9B4DFF)
private val IntroCyan = Color(0xFF23D5FF)
private val IntroGold = Color(0xFFFFC93C)

/**
 * A short, original cold-launch title sequence.
 *
 * It is intentionally asset-light: the real launcher icon, two lines of type,
 * and a handful of particles. The game's home screen is already composed
 * underneath, so the fade at the end is a reveal rather than a screen load.
 */
@Composable
fun CinematicIntro(onFinished: () -> Unit) {
    val context = LocalContext.current
    val lowRam = remember(context) {
        runCatching {
            (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
                .isLowRamDevice
        }.getOrDefault(false)
    }
    val reducedMotion = remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }

    var step by remember { mutableIntStateOf(0) }
    val iconScale by animateFloatAsState(
        targetValue = if (step >= 1) 1f else 0.86f,
        animationSpec = tween(if (reducedMotion) 1 else 520, easing = FastOutSlowInEasing),
        label = "introIcon"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (step >= 1) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 1 else 420),
        label = "introIconAlpha"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (step >= 2) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 1 else 420),
        label = "introTitle"
    )

    LaunchedEffect(Unit) {
        // Mark it immediately: an activity recreation during the intro must
        // not restart the sequence.
        SessionIntro.started = true
        if (reducedMotion) {
            step = 4
            delay(900)
            onFinished()
            return@LaunchedEffect
        }
        delay(220)
        step = 1
        delay(700)
        step = 2
        delay(700)
        step = 3
        delay(900)
        onFinished()
    }

    val finish = {
        if (step >= 2) onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(IntroBgTop, IntroBgBottom)))
            .clickable(onClick = finish)
    ) {
        IntroParticles(animate = !lowRam && !reducedMotion)

        // Ambient violet light behind the mark.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(if (lowRam) 260.dp else 340.dp)
                .background(
                    Brush.radialGradient(
                        listOf(IntroPurple.copy(alpha = 0.26f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(if (lowRam) 96.dp else 118.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        alpha = iconAlpha
                    }
                    .clip(RoundedCornerShape(28.dp))
            )

            Spacer(Modifier.height(22.dp))

            AnimatedVisibility(
                visible = step >= 2,
                enter = fadeIn(tween(420)) +
                    slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 8 }
            ) {
                Text(
                    text = "BLOCK BOOM",
                    style = TextStyle(
                        fontSize = if (lowRam) 34.sp else 42.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFDFC8FF))),
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.graphicsLayer { alpha = titleAlpha }
                )
            }

            Spacer(Modifier.height(10.dp))

            AnimatedVisibility(
                visible = step >= 2,
                enter = fadeIn(tween(420, delayMillis = 120)) +
                    slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 8 }
            ) {
                Text(
                    text = "Shoot  •  Break  •  Win!",
                    color = IntroCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(28.dp))

            AnimatedVisibility(
                visible = step >= 3,
                enter = fadeIn(tween(520, delayMillis = 80)) +
                    slideInVertically(tween(520, easing = FastOutSlowInEasing)) { it / 6 }
            ) {
                Text(
                    text = "made by Amiraligrr for everyone, everywhere",
                    color = Color(0xFFD9CDF0),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }

        Text(
            text = if (step >= 2) "برای رد کردن بزن" else "",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 38.dp)
        )
    }
}

@Composable
private fun IntroParticles(animate: Boolean) {
    val phase = if (!animate) 0f else {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "introParticles")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 6.28318f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = tween(16000, easing = androidx.compose.animation.core.LinearEasing)
            ),
            label = "introPhase"
        )
        p
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val dots = if (animate) 22 else 10
        repeat(dots) { i ->
            val fx = ((i * 37) % 100) / 100f
            val fy = ((i * 61) % 100) / 100f
            val drift = sin(phase + i) * size.width * 0.012f
            val alpha = if (animate) 0.10f + 0.18f * ((i % 5) / 5f) else 0.10f
            drawCircle(
                color = if (i % 3 == 0) IntroGold.copy(alpha = alpha) else IntroPurple.copy(alpha = alpha),
                radius = size.width * (0.003f + (i % 3) * 0.002f),
                center = Offset(size.width * fx + drift, size.height * fy)
            )
        }
    }
}
