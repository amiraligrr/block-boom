package ir.amiraligrr.blockboom.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.ui.theme.NeonRed
import kotlin.math.sin

/** Premium hearts — beating glass pills, staggered pulse and urgent shake on low life. */
@Composable
fun HeartsRow(
    filled: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "heartsBeat")

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until total) {
            val on = i < filled
            val isUrgent = filled == 1 && on
            // stagger delay per heart
            val delay = i * 120
            val beat by transition.animateFloat(
                initialValue = 1f,
                targetValue = if (on) 1.14f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(620 + i * 30, delayMillis = delay),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "beat$i"
            )
            val wobble by transition.animateFloat(
                initialValue = -1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900, delayMillis = delay), RepeatMode.Reverse),
                label = "wobble$i"
            )

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = if (on) beat else 0.96f
                        scaleY = if (on) beat else 0.96f
                        rotationZ = if (isUrgent) wobble * 3f else 0f
                        alpha = if (on) 1f else 0.42f
                    }
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (on) Brush.verticalGradient(listOf(NeonRed.copy(alpha = 0.28f), NeonRed.copy(alpha = 0.12f)))
                        else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent))
                    )
                    .border(
                        width = 1.dp,
                        color = if (on) NeonRed.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (on) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .blur(6.dp)
                            .background(NeonRed.copy(alpha = 0.35f), CircleShape)
                    )
                }
                Text(
                    text = "♥",
                    fontSize = 15.sp,
                    color = if (on) NeonRed else Color(0xFF3A3A52),
                    modifier = Modifier.graphicsLayer {
                        // tiny inner pulse
                        scaleX = if (on) 1f + (beat - 1f) * 0.6f else 1f
                        scaleY = if (on) 1f + (beat - 1f) * 0.6f else 1f
                    }
                )
            }
        }
    }
}
