package ir.amiraligrr.blockboom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.data.SaveManager
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "You are premium" banner, pinned above the shop's tabs.
 *
 * Renders nothing at all when the player is not premium, so it costs the shop
 * layout nothing in the common case.
 *
 * ── Why this says more than "premium" ────────────────────────────────────
 * The confirmation it replaces was a single tick in the middle of the premium
 * card. The question a player actually has after paying is "how long have I got",
 * and nothing on the screen answered it — `SaveManager.isPremium` is derived
 * from an expiry timestamp, so the remaining time is knowable but was never
 * shown. An operator-granted premium has the same shape, and for those players
 * this is the only place that says their gift is active and when it ends.
 *
 * Reads the same `premiumUntilMillis` that `isPremium` is derived from rather
 * than a second source of truth, so the banner cannot claim premium is active
 * while the rest of the app says otherwise.
 */
@Composable
fun PremiumActiveBanner(
    saveManager: SaveManager,
    modifier: Modifier = Modifier
) {
    val until = saveManager.premiumUntilMillis
    val remainingMs = until - System.currentTimeMillis()
    // Same predicate as SaveManager.isPremium, so the two can never disagree.
    if (remainingMs <= 0L) return

    val c = joyThemeColors()
    val days = (remainingMs / 86_400_000L).toInt()
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(c.amber.copy(alpha = 0.22f), c.emerald.copy(alpha = 0.18f))
                )
            )
            .border(1.5.dp, c.amber.copy(alpha = 0.55f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.amber.copy(alpha = 0.22f))
                .border(1.dp, c.amber.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "👑", fontSize = 18.sp)
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = "پریمیوم فعال است",
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = expiryLabel(until, days),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Explicit, so the answer to "can I buy it again" is on the screen
        // rather than something the player has to infer from a missing button.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(c.emerald.copy(alpha = 0.16f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = "خرید دوباره ممکن نیست",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = c.emerald,
                maxLines = 1
            )
        }
    }
}

/**
 * "۲۳ روز دیگر" / "تا ۱۵ مهر".
 *
 * Both, because they answer different questions: the day count is what the
 * player plans around, and the date is what they need when the two devices they
 * play on disagree. Under a day, the countdown alone — a day count of 0 would
 * read as "expired" while premium is still running.
 */
private fun expiryLabel(untilMillis: Long, days: Int): String {
    // `Locale(String)` was deprecated in API 21. `forLanguageTag` is the
    // supported equivalent and produces the same locale for "fa".
    val date = SimpleDateFormat("d MMM", Locale.forLanguageTag("fa"))
        .format(Date(untilMillis))
    return if (days >= 1) "$days روز دیگر • تا $date" else "کمتر از یک روز • تا $date"
}
