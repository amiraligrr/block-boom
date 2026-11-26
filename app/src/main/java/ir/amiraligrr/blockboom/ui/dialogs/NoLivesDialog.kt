package ir.amiraligrr.blockboom.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.data.AdManager
import ir.amiraligrr.blockboom.data.AdRewards
import ir.amiraligrr.blockboom.data.LivesManager
import ir.amiraligrr.blockboom.ui.components.HeartsRow
import ir.amiraligrr.blockboom.ui.components.NeonButton
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import ir.amiraligrr.blockboom.ui.theme.NeonGold
import ir.amiraligrr.blockboom.ui.theme.NeonGreen
import ir.amiraligrr.blockboom.ui.theme.NeonRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * How many hearts the out-of-lives dialog actually draws.
 *
 * A `HeartsRow` costs about 34dp per heart, so the full 25 needs roughly 850dp —
 * several times the width of the dialog it sits in. The exact count is printed
 * underneath, so nothing is lost by capping the row.
 */
private const val HEARTS_ROW_CAP = 10

/** Shown when the player has no hearts left: premium, watch ad for lives, or close. */
@Composable
fun NoLivesDialog(
    livesManager: LivesManager,
    onWatchAd: () -> Unit,
    onGoPremium: () -> Unit,
    onDismiss: () -> Unit
) {
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Recompute the regen countdown ~twice a second while the dialog is open.
    LaunchedEffect(Unit) {
        while (isActive) {
            livesManager.refresh()
            delay(500)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        // Was DeepSpace (near-black) with white body text — the only dark
        // dialog in an otherwise all-white app, so it read as a different
        // product. It now uses the same light card as NameDialog, and every
        // colour comes from the theme so it also follows dark mode.
        val c = joyThemeColors()
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
                Text(
                    text = stringResource(R.string.out_of_lives),
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.coral,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                // Capped, and the real figure spelled out beside it.
                //
                // `MAX_LIVES` is 25 and `HeartsRow` lays out one fixed 28dp heart
                // plus 6dp of gap per entry, so asking for all of them asked for
                // ~840dp of row inside a dialog about 300dp wide: the tail was
                // clipped, and the player could not see how close they were to
                // full. Ten hearts fit, and the counter carries the exact number.
                val shownHearts = minOf(LivesManager.MAX_LIVES, HEARTS_ROW_CAP)
                HeartsRow(
                    filled = livesManager.lives.coerceAtMost(shownHearts),
                    total = shownHearts
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.lives_out_of,
                        livesManager.lives,
                        LivesManager.MAX_LIVES
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textMuted
                )

                Spacer(Modifier.height(8.dp))

                if (livesManager.lives < LivesManager.MAX_LIVES) {
                    Text(
                        text = "${stringResource(R.string.next_life_in)}: ${formatCountdown(livesManager.millisToNextReset)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.amber
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.life_regen_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textMuted,
                    textAlign = TextAlign.Center
                )

                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.amber
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Button 1: Watch Ad for Lives
                NeonButton(
                    text = stringResource(R.string.watch_ad_for_lives),
                    onClick = {
                        message = null
                        AdManager.showRewarded(
                            onReward = {
                                livesManager.grantLives(AdRewards.HEARTS_PER_AD)
                                message = context.getString(R.string.hearts_granted)
                                onWatchAd()
                            },
                            onFail = {
                                message = context.getString(R.string.ad_not_ready_retry)
                            }
                        )
                    },
                    containerColor = NeonGreen,
                    borderColor = NeonGold
                )

                Spacer(Modifier.height(12.dp))

                // Button 2: Go Premium
                NeonButton(
                    text = stringResource(R.string.buy_premium),
                    onClick = onGoPremium,
                    containerColor = NeonGold,
                    borderColor = Color.White,
                    contentColor = Color.Black
                )

                Spacer(Modifier.height(12.dp))

                // Button 3: Close
                NeonButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss,
                    containerColor = c.surfaceSoft,
                    contentColor = c.textPrimary,
                    borderColor = c.cardBorder
                )
            }
        }
    }
}

/** mm:ss from [millis], never negative. */
fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}