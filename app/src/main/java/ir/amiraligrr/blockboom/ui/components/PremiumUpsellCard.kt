package ir.amiraligrr.blockboom.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import ir.amiraligrr.blockboom.ui.components.ButtonSize
import ir.amiraligrr.blockboom.ui.components.NeonButton

/**
 * Premium upsell for the home screen.
 *
 * Shown to every non-premium player, and badged/pulsed for players who have
 * not finished the tutorial, so the value of premium is visible on the very
 * first run instead of only to someone who goes looking for it.
 *
 * The "3x faster" claim is derived from the real economy — 2x coins and 40%
 * off upgrades — so the pitch and the pricing cannot quietly drift apart.
 *
 * Colours are chosen from combinations verified by tools/check_contrast.py:
 * white on the emerald button gradient (3.77:1) and white on the dark "new"
 * chip (9.7:1). Amber is deliberately NOT used as a button fill here — white on
 * gold measures 1.6:1.
 */
@Composable
fun PremiumUpsellCard(
    isNewPlayer: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = joyThemeColors()

    // One slow breathing pulse. Static cards get scrolled past; anything
    // faster reads as nagging and gets ignored.
    val pulse = rememberInfiniteTransition(label = "upsell")
    val glow by pulse.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val shape = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.surface, c.surfaceSoft)))
            .border(
                width = if (isNewPlayer) 2.dp else 1.5.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        c.amber.copy(alpha = if (isNewPlayer) glow else 0.55f),
                        c.emerald.copy(alpha = 0.40f)
                    )
                ),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "⭐", fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_premium_title),
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )
            if (isNewPlayer) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(c.textPrimary)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_premium_new_badge),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = c.surface
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.home_premium_body),
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted
        )

        Spacer(Modifier.height(12.dp))

        NeonButton(
            text = stringResource(R.string.home_premium_cta),
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            containerColor = c.emerald,
            borderColor = c.emerald,
            // `c.surface` was here, which is white in light mode and a *dark
            // green* in dark mode — so on a dark phone this was dark-green ink
            // on the dark emerald the button paints.
            contentColor = Color.White,
            size = ButtonSize.Medium
        )
    }
}

/**
 * The premium pitch, compressed to a single row.
 *
 * ── Why this replaced [PremiumUpsellCard] on the home screen ──────────────
 * The full card was ~124dp — a headline, a body paragraph, a three-item feature
 * list and a button. That made it the largest object on a screen whose only job
 * is to start a game, and it pushed the level preview and the daily missions
 * below the fold on a 6.1" phone. Everything it said except one line and one tap
 * was restating the same offer.
 *
 * What survives here is the part that actually persuades: what premium does
 * (no ads, double coins), the price, and the button. The feature list was the
 * part most likely to be skimmed and is the part the store page repeats in full.
 *
 * The full card is still used in the shop, where there is room for it and the
 * player has already decided to look.
 */
@Composable
fun CompactPremiumStrip(
    isNewPlayer: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = joyThemeColors()

    val pulse = rememberInfiniteTransition(label = "upsellStrip")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        c.amber.copy(alpha = 0.16f),
                        c.surface,
                        c.emerald.copy(alpha = 0.14f)
                    )
                )
            )
            .border(
                width = if (isNewPlayer) 1.8.dp else 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        c.amber.copy(alpha = if (isNewPlayer) glow else 0.5f),
                        c.emerald.copy(alpha = 0.35f)
                    )
                ),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.amber.copy(alpha = 0.18f))
                .border(1.dp, c.amber.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "⭐", fontSize = 17.sp)
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "پریمیوم",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Black
                )
                if (isNewPlayer) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(c.amber)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "تخفیف",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = c.surface
                        )
                    }
                }
            }
            Text(
                text = "بدون تبلیغ · سکه دوبرابر",
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false
            )
        }

        Spacer(Modifier.width(8.dp))

        NeonButton(
            text = stringResource(R.string.shop),
            onClick = onClick,
            size = ButtonSize.Small,
            containerColor = c.amber,
            // `c.surface` — white on the light stop of the gold gradient, which
            // measured 1.25:1. The dark ink every other gold button uses.
            contentColor = c.onGold,
            borderColor = Color.Transparent
        )

        Text(text = "‹", fontSize = 16.sp, color = c.textMuted)
    }
}
