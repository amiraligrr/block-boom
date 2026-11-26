package ir.amiraligrr.blockboom.ui.dialogs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ir.amiraligrr.blockboom.ui.components.ButtonSize
import ir.amiraligrr.blockboom.ui.components.NeonButton
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors

/**
 * "Rate us", asked once the player has actually played.
 *
 * ── Why it is gated and not shown on the first launch ────────────────────
 * A rating is the single highest-leverage thing a player can do for a game that
 * lives in a store, and it is also the one that costs most: leave, find the
 * app, log in, tap stars. Asking on launch asks a stranger who has no opinion
 * yet, and the answer is always "later" — so the prompt burns the only first
 * impression it will ever get. The gate lives in
 * [ir.amiraligrr.blockboom.data.SaveManager.shouldPromptForRating]; this dialog
 * is only ever composed when it has already been passed.
 *
 * ── Why it can be told to go away forever ────────────────────────────────
 * "Later" re-arms after a cooldown; "never" does not. A prompt that ignores its
 * own dismissals is one the player learns to dismiss without reading, which is
 * how a game ends up with a player base that has all seen the ask and none of
 * whom have acted on it.
 *
 * @param onRate called with the star count the player picked, or 5 if they went
 *        straight to the store without choosing.
 */
@Composable
fun RateUsDialog(
    onRate: (Int) -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = joyThemeColors()
    var picked by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(c.surface)
                .border(1.5.dp, c.cardBorder, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.gradientGold),
                    contentAlignment = Alignment.Center
                ) { Text("⭐", fontSize = 30.sp) }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "از بازی خوشت اومد؟",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = c.textPrimary,
                        fontWeight = FontWeight.Black
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    // Says what the tap is for, rather than asking for a favour.
                    // A player told what happens next is far more likely to act
                    // than one asked to "support the developer".
                    text = "با یه امتیاز در فروشگاه، بقیه هم این بازی رو پیدا می‌کنن. چند ستاره می‌دی؟",
                    style = MaterialTheme.typography.bodyMedium.copy(color = c.textMuted),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                // Five tappable stars. Tapping one is the rating — it opens the
                // store and records how many were chosen — so the row is a
                // decision rather than decoration.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (star in 1..5) {
                        val filled = star <= picked
                        val press = remember { MutableInteractionSource() }
                        val pressed by press.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = when {
                                pressed -> 0.8f
                                star == picked && filled -> 1.2f
                                else -> 1f
                            },
                            animationSpec = tween(140),
                            label = "starScale$star"
                        )
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clickable(
                                    interactionSource = press,
                                    indication = null,
                                    onClick = { picked = star; onRate(star) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (filled) "★" else "☆",
                                fontSize = 32.sp,
                                color = if (filled) c.amber else c.textMuted,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                NeonButton(
                    // Always enabled, and never gated on picking a star first:
                    // the store link is the destination either way, and making
                    // the primary button depend on a second tap loses the
                    // players who already know what they want to do.
                    text = "رفتن به فروشگاه",
                    onClick = { onRate(picked.coerceAtLeast(5)) },
                    containerColor = c.emerald,
                    // Was `Color.White`: 2.4:1 on the emerald fill — the button
                    // label was effectively invisible. `c.onPrimary` is the
                    // dark ink paired with emerald, 5.4:1 in light and 5.4:1
                    // in dark on the emerald fill (the same token buttons use).
                    contentColor = c.onPrimary,
                    borderColor = c.emerald.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.Large
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NeonButton(
                        text = "بعداً",
                        onClick = onLater,
                        modifier = Modifier.weight(1f),
                        size = ButtonSize.Medium,
                        containerColor = c.surface,
                        contentColor = c.textSecondary,
                        borderColor = c.cardBorder
                    )
                    NeonButton(
                        text = "دیگه نپرس",
                        onClick = onNever,
                        modifier = Modifier.weight(1f),
                        size = ButtonSize.Medium,
                        containerColor = c.surface,
                        contentColor = c.textMuted,
                        borderColor = c.cardBorder
                    )
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
