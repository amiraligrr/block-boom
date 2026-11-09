package ir.amiraligrr.blockboom.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.ui.formatNumber
import ir.amiraligrr.blockboom.ui.theme.DarkSurfaceVariant
import ir.amiraligrr.blockboom.ui.theme.NeonCyan
import ir.amiraligrr.blockboom.ui.theme.NeonGold
import ir.amiraligrr.blockboom.ui.theme.OffWhite
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors

@Composable
fun UpgradeCard(
    name: String,
    icon: String,
    accent: Color,
    currentLevel: Int,
    maxLevel: Int,
    effect: String,
    nextEffect: String,
    cost: Long,
    maxed: Boolean,
    /**
     * The pre-discount price. When it is higher than [cost] the original is
     * shown struck-through, so the premium saving is visible on the card
     * instead of being an invisible number in the pricing logic.
     */
    listPrice: Long = cost,
    unowned: Boolean = false,
    /**
     * True while the player has not reached this track's level yet.
     *
     * Rendered as its own state rather than as "cannot afford": a greyed-out
     * price next to a red button reads as a coin problem, and the player retries
     * the same purchase forever. A lock has to say *what unlocks it*.
     */
    locked: Boolean = false,
    lockedUntilLevel: Int = 1,
    canAfford: Boolean,
    onBuy: () -> Unit
) {
    val c = joyThemeColors()
    val animatedLevel by animateFloatAsState(
        targetValue = currentLevel.toFloat() / maxLevel.coerceAtLeast(1),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "lvl"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .border(1.dp, c.cardBorder, RoundedCornerShape(20.dp))
            .shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon orb with glow
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.32f), accent.copy(alpha = 0.12f))))
                        .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = icon, fontSize = 20.sp)
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = c.textPrimary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.2.sp
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (unowned)
                                "${stringResource(R.string.upgrade_level)} — /$maxLevel"
                            else
                                "${stringResource(R.string.upgrade_level)} $currentLevel/$maxLevel",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = c.textMuted,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                        )
                        if (maxed) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(NeonGold.copy(alpha = 0.18f))
                                    .border(1.dp, NeonGold.copy(alpha = 0.35f), RoundedCornerShape(50.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "MAX",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        // The badge is a tonal gold chip — a
                                        // pale wash with a gold border — so
                                        // `NeonGold` on it was ~1.3:1 in light
                                        // mode: a gold outline and gold text,
                                        // with the text invisible. `c.amber` is
                                        // the deep ink that pairs with a pale
                                        // gold fill, and is 4.56:1 on the chip
                                        // in light and 10.9:1 on its dark
                                        // counterpart. Keep the *border* neon:
                                        // the stroke only has to be visible,
                                        // not read.
                                        color = c.amber,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        } else if (locked) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(c.dotIdle.copy(alpha = 0.35f))
                                    .border(1.dp, c.cardBorder, RoundedCornerShape(50.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "🔒 ${stringResource(R.string.upgrade_level)} $lockedUntilLevel",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = c.textMuted,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }
                    }
                }
                // Level pips with glow
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(maxLevel.coerceAtMost(8)) { i ->
                        val filled = i < currentLevel
                        val scale by animateFloatAsState(if (filled) 1f else 0.88f, tween(300), label = "pip")
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (filled) accent else c.dotIdle)
                                .border(1.dp, if (filled) Color.White.copy(alpha = 0.28f) else c.cardBorder, CircleShape)
                                .padding(0.dp)
                        ) {
                            if (filled) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .align(Alignment.TopCenter)
                                        .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50.dp))
                                )
                            }
                        }
                    }
                }
            }

            // Progress track for level — light
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.track)
                    .border(1.dp, c.trackBorder, RoundedCornerShape(4.dp))
                    .padding(1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedLevel)
                        .height(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.68f))))
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.chip)
                        .border(1.dp, c.cardBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.upgrade_current, effect),
                            style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted, fontSize = 12.sp)
                        )
                        if (!maxed) {
                            Text(
                                text = "→ ${stringResource(R.string.upgrade_next, nextEffect)}",
                                style = MaterialTheme.typography.bodySmall.copy(color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            )
                        }
                    }
                }
            }

            if (locked) {
                // The button states the requirement rather than the price: the
                // price is irrelevant until the track exists, and showing a
                // number the player cannot act on is the fastest way to make a
                // locked track feel broken.
                NeonButton(
                    text = "🔒 " + stringResource(R.string.upgrade_locked, lockedUntilLevel),
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = c.chip,
                    borderColor = c.cardBorder,
                    contentColor = c.textMuted,
                    enabled = false,
                    size = ButtonSize.Small
                )
            } else if (maxed) {
                NeonButton(
                    text = "★ ${stringResource(R.string.maxed)}",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = c.dotIdle,
                    borderColor = c.cardBorder,
                    contentColor = c.textMuted,
                    enabled = false,
                    size = ButtonSize.Small
                )
            } else {
                NeonButton(
                    // Premium players see what they are saving, so the discount
                    // is visible on the card rather than buried in the pricing
                    // logic. Two bare numbers read as a bug; "X instead of Y"
                    // reads as a sale.
                    text = if (listPrice > cost)
                        "🪙 " + stringResource(
                            R.string.upgrade_price_discount,
                            formatNumber(cost),
                            formatNumber(listPrice)
                        )
                    else
                        "🪙 " + formatNumber(cost),
                    onClick = onBuy,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (canAfford) accent else c.chip,
                    borderColor = if (canAfford) Color.White.copy(alpha = 0.45f) else c.cardBorder,
                    contentColor = if (canAfford) Color.White else c.textMuted,
                    enabled = canAfford,
                    size = ButtonSize.Small
                )
            }
        }
    }
}
