package ir.amiraligrr.blockboom.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.amiraligrr.blockboom.ui.theme.NeonCyan
import ir.amiraligrr.blockboom.ui.theme.NeonGold
import ir.amiraligrr.blockboom.ui.theme.OffWhite
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors

/**
 * First-launch tutorial hint: an animated bouncing arrow pointing down at the
 * play button with a short call-to-action text above it. Pure animation, no
 * state — visibility is controlled by the caller.
 */
@Composable
fun TutorialHint(
    text: String,
    modifier: Modifier = Modifier
) {
    val c = joyThemeColors()
    val transition = rememberInfiniteTransition(label = "tutorialHint")
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowBounce"
    )
    val glow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Restart
        ),
        label = "arrowGlow"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            // This hint is composited straight onto the home screen's page
            // background, which is a pale gradient in light mode — and
            // `NeonGold` measures 1.28:1 there. It was the "tap to start"
            // instruction, the first thing a new player has to read, and the
            // one text in the app they could not. `c.amber` clears 4.5:1 on
            // every stop of that gradient in light and every stop of the dark
            // one, and still reads as gold.
            color = c.amber,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Canvas(
            modifier = Modifier
                .size(width = 40.dp, height = 34.dp)
                .padding(bottom = 2.dp)
        ) {
            val ty = size.height * bounce * 0.35f
            val cx = size.width / 2f
            val top = 2.dp.toPx() + ty
            val bottom = size.height - ty

            // Glow behind the arrow
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(NeonCyan.copy(alpha = 0.5f), Color.Transparent)
                ),
                radius = size.minDimension * 0.55f * (1f + glow * 0.2f),
                center = Offset(cx, bottom - size.height * 0.3f)
            )

            val path = Path().apply {
                moveTo(cx - size.width * 0.32f, top)
                lineTo(cx + size.width * 0.32f, top)
                lineTo(cx, bottom)
                close()
            }
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    listOf(NeonCyan, Color.White),
                    startY = top,
                    endY = bottom
                )
            )
            drawPath(
                path = path,
                color = NeonCyan.copy(alpha = 0.6f),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

@Composable
fun TutorialPulseRing(show: Boolean, modifier: Modifier = Modifier) {
    if (!show) return
    val transition = rememberInfiniteTransition(label = "tutorialRing")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring"
    )
    Canvas(modifier = modifier) {
        val ringColor = OffWhite.copy(alpha = (1f - progress) * 0.7f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, ringColor, Color.Transparent),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = size.maxDimension * 0.6f
            ),
            radius = size.maxDimension / 2f,
            style = Stroke(width = 5.dp.toPx() * (1f - progress) + 1f)
        )
    }
}