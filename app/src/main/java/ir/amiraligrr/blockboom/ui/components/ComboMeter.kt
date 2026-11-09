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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.domain.ComboTracker
import ir.amiraligrr.blockboom.domain.comboBonusScore
import ir.amiraligrr.blockboom.domain.comboMultiplierFor
import ir.amiraligrr.blockboom.domain.nextComboTier
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors

/**
 * The live combo meter.
 *
 * Until now the only combo feedback was a popup that faded out in under a
 * second. The player had no idea a chain was even running, how close it was to
 * expiring, or whether the next block was worth firing for — which is the whole
 * decision the combo mechanic exists to create. This is that feedback, held on
 * screen for exactly as long as the chain is alive.
 *
 * Reads [ComboTracker.count] and [ComboTracker.fractionRemaining], which are
 * snapshot state, so it repaints on its own without the game screen having to
 * plumb anything. The drain bar is drawn inside `drawBehind` on purpose: the
 * same travelling-highlight trick as `JoyProgressTrack`, because building a
 * `Brush` from an animation value in the modifier chain recomposes the
 * enclosing scope 60x/second and this sits inside the play HUD.
 *
 * Reserves no space when idle — it collapses rather than holding a gap — but
 * does animate its own appearance so a chain that starts mid-level does not
 * make the layout jump.
 */
@Composable
fun ComboMeter(
    combo: ComboTracker,
    modifier: Modifier = Modifier,
) {
    val count = combo.count
    val remaining = combo.fractionRemaining
    if (count < 2) return

    val c = joyThemeColors()

    // Intensity follows the *payout* tiers, which used to be a separate ladder
    // that had drifted: the meter stepped at 6 and 10 while the engine's curve
    // stepped at 5, 10 and 20. So the meter lit up amber at a chain of six —
    // where the payout was still ×1 — and never went past amber on a chain of
    // fifteen that was paying ×3. Two copies of one curve, disagreeing, and the
    // wrong one is the one the player looks at.
    val mult = comboMultiplierFor(count)
    val tier = mult.coerceIn(1, 5)
    val accent = when {
        tier >= 4 -> c.coral
        tier >= 2 -> c.amber
        else -> c.emerald
    }

    // Only the top tier pulses; pulsing every combo made the cheap ones feel
    // as valuable as the ones worth chasing. The pulse drives the border
    // brightness — it used to be computed and then thrown away, so an
    // infiniteRepeatable ran on a 60Hz screen for a value no pixel used.
    val infinite = rememberInfiniteTransition(label = "comboPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = if (tier >= 2) {
            infiniteRepeatable(tween(620), RepeatMode.Reverse)
        } else {
            infiniteRepeatable(tween(1400), RepeatMode.Reverse)
        },
        label = "comboPulse",
    )
    // The bar is drawn, not composed: `remaining` changes every frame and a
    // Brush built from it in composition would recompose this whole subtree.
    val barShape = RoundedCornerShape(3.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceSoft.copy(alpha = 0.92f))
            .border(
                width = 1.dp + if (tier >= 2) 1.dp else 0.dp,
                // Brightens with the chain and breathes at the top tier, so the
                // meter gets louder as it gets more valuable without changing
                // its size — anything that resized here would move the play field.
                color = accent.copy(alpha = 0.30f + 0.18f * tier + (if (tier >= 2) 0.22f * pulse else 0f)),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "🔥", fontSize = 13.sp)

        // The count, and the draining window underneath it.
        //
        // The word "کمبو" and the separate "+N" payout preview were both cut.
        // This shares a single row with the power-up chips, so it gets roughly
        // half the width; at that width a label, a number, a 96dp bar and a
        // second number could not coexist legibly. What is left is the two facts
        // that change the decision — how long the chain is, and how much time is
        // left to extend it. The payout is still shown, as a compact suffix on
        // the count, because "is this chain worth continuing" is the actual
        // question the meter exists to answer.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "x$count",
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    // The multiplier is only shown once it is above one, for the
                    // same reason the popup omits it: a constant "×1" next to
                    // every number teaches the player to stop reading it.
                    text = if (mult > 1) "${mult}× +${comboBonusScore(count)}"
                    else "+${comboBonusScore(count)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
            // Draining window bar — the player can see the chain dying.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(barShape)
                    .background(c.track)
                    .drawBehind {
                        val w = size.width * remaining
                        if (w > 0f) {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        accent.copy(alpha = 0.65f),
                                        accent.copy(alpha = 1f)
                                    )
                                ),
                                size = androidx.compose.ui.geometry.Size(w, size.height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                            )
                        }
                    }
            )

            // How many more kills until the multiplier steps up.
            //
            // Only shown while a chain is actually running, and only below the
            // final tier — once the top band is reached there is nothing left to
            // aim at, and a permanent "next tier: none" would sit there telling
            // the player they have run out of things to do.
            val next = nextComboTier(count)
            if (next > 0) {
                Text(
                    text = "$next تا ضریب بعدی",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textMuted,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }
    }
}
