package ir.amiraligrr.blockboom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.data.Achievement
import ir.amiraligrr.blockboom.data.Achievements
import ir.amiraligrr.blockboom.data.SaveManager
import ir.amiraligrr.blockboom.ui.formatNumber
import ir.amiraligrr.blockboom.ui.components.ButtonSize
import ir.amiraligrr.blockboom.ui.components.NeonButton
import ir.amiraligrr.blockboom.ui.theme.JoyThemeColors
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors

/**
 * The player's own numbers, for the whole install.
 *
 * ── Why this screen exists at all ─────────────────────────────────────────
 * The game already knew all of this. `Telemetry` tracks runs, levels, blocks,
 * coins, shots, combos and max-combo per install and posts them to the server on
 * a 12-second flush, and the admin panel shows all of it — so the data existed,
 * was correct, and was sent somewhere the player can never see. On the device
 * itself, `blocksDestroyed` and `shotsFired` were reset by every level and
 * `ComboTracker.best` was shown once on the game-over screen and then discarded.
 *
 * The gap is not data, it is a place to read it. A number like "41,208 blocks
 * destroyed" is a reason to come back: it is the visible record of having
 * actually played, and unlike a leaderboard rank it cannot be lost to a better
 * player. It is also the honest answer to "is this game going anywhere", which a
 * score alone does not give.
 *
 * Everything here reads from [SaveManager], so it works with no network and
 * cannot be made to disagree with the save. The server copy is for the operator;
 * this is for the player.
 */
@Composable
fun StatsScreen(
    saveManager: SaveManager,
    onBack: () -> Unit
) {
    val c = joyThemeColors()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.gradientBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.stats_title),
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = c.textPrimary,
                    fontWeight = FontWeight.Black
                ),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.stats_subtitle),
                style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))

            // ── Records ────────────────────────────────────────────────────
            //
            // Split from the totals because these are the numbers that cannot go
            // down, so they behave differently psychologically: they are the
            // half a player is trying to move.
            StatSection(c, stringResource(R.string.stats_records))

            StatRow(
                icon = "🏅",
                label = stringResource(R.string.stat_deepest_level),
                value = stringResource(R.string.my_level, saveManager.statDeepestLevel),
                highlight = true
            )
            StatRow(
                icon = "🔥",
                label = stringResource(R.string.stat_best_combo),
                value = "×${saveManager.statBestCombo}"
            )
            StatRow(
                icon = "⭐",
                label = stringResource(R.string.stat_best_run),
                value = formatNumber(saveManager.statBestRunScore.toLong())
            )

            Spacer(Modifier.height(16.dp))

            // ── Totals ─────────────────────────────────────────────────────
            StatSection(c, stringResource(R.string.stats_totals))

            StatRow(
                icon = "🎮",
                label = stringResource(R.string.stat_runs),
                value = formatNumber(saveManager.statRuns.toLong())
            )
            StatRow(
                icon = "🚩",
                label = stringResource(R.string.stat_levels_cleared),
                value = formatNumber(saveManager.statLevelsCleared.toLong())
            )
            StatRow(
                icon = "🧱",
                label = stringResource(R.string.stat_blocks),
                value = formatNumber(saveManager.statBlocksDestroyed.toLong())
            )
            StatRow(
                icon = "🎯",
                label = stringResource(R.string.stat_accuracy),
                value = if (saveManager.statShotsFired <= 0) "—"
                else "${"%.2f".format(saveManager.statAccuracy())} / ${
                    formatNumber(saveManager.statBlocksDestroyed.toLong())
                }"
            )
            StatRow(
                icon = "🪙",
                label = stringResource(R.string.stat_coins_earned),
                value = formatNumber(saveManager.statCoinsEarned)
            )
            StatRow(
                icon = "⏱️",
                label = stringResource(R.string.stat_play_time),
                value = playTimeLabel(saveManager.statPlaySeconds)
            )
            StatRow(
                icon = "🌍",
                label = stringResource(R.string.stat_total_score),
                value = formatNumber(saveManager.totalScore)
            )

            Spacer(Modifier.height(20.dp))

            // Shown instead of a wall of zeroes. A new player opening a stats
            // screen should be told what will fill it, not shown eight rows of
            // "0" and left to work out why.
            if (saveManager.statRuns == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.surface)
                        .border(1.dp, c.cardBorder, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.stats_empty),
                        style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Achievements ───────────────────────────────────────────────
            //
            // The full list, locked ones included, not just the ones earned.
            // A list that only ever grows tells a new player nothing; the
            // silhouette of what is still available is the actual content, and
            // it is the same reason games show locked rewards in shops.
            val ach = Achievements(saveManager)
            Text(
                text = stringResource(R.string.ach_progress, ach.unlockedCount(), ach.total()),
                style = MaterialTheme.typography.titleSmall.copy(
                    color = c.emerald,
                    fontWeight = FontWeight.Black
                ),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Achievement.entries.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pair.forEach { a ->
                        AchievementChip(
                            achievement = a,
                            unlocked = ach.isUnlocked(a),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Keep a lone final chip from stretching across the row.
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(20.dp))

            NeonButton(
                text = stringResource(R.string.back),
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                containerColor = c.surfaceSoft,
                borderColor = c.emerald,
                // No contentColor: this fell back to the old white default on a
                // near-white fill. Deriving it from the painted surface instead.
                size = ButtonSize.Large
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * One achievement, earned or not.
 *
 * Locked state is dimmed and desaturated rather than hidden or greyed to nothing:
 * the emoji stays legible so the list reads as fourteen things to aim at, which
 * is what makes it motivating. A fully invisible row reads as a rendering bug.
 */
@Composable
private fun AchievementChip(
    achievement: Achievement,
    unlocked: Boolean,
    modifier: Modifier = Modifier
) {
    val c = joyThemeColors()
    val ctx = LocalContext.current
    val alpha = if (unlocked) 1f else 0.42f

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (unlocked) c.gradientGoldSoft
                else Brush.horizontalGradient(
                    listOf(c.surfaceSoft, c.surfaceSoft)
                )
            )
            .border(
                1.dp,
                if (unlocked) c.amber.copy(alpha = 0.5f) else c.cardBorder,
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = achievement.emoji, fontSize = 20.sp, color = c.textPrimary.copy(alpha = alpha))
        Spacer(Modifier.height(4.dp))
        Text(
            text = ctx.getString(achievement.titleRes),
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (unlocked) c.onGold else c.textMuted,
                fontWeight = FontWeight.Black
            ),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (unlocked) stringResource(R.string.ach_unlocked_short, formatNumber(achievement.coins))
            else "🔒 " + formatNumber(achievement.coins),
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (unlocked) c.onGold.copy(alpha = 0.9f) else c.textMuted,
                fontSize = 10.sp
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatSection(c: JoyThemeColors, title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            color = c.emerald,
            fontWeight = FontWeight.Black
        ),
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun StatRow(
    icon: String,
    label: String,
    value: String,
    highlight: Boolean = false
) {
    val c = joyThemeColors()
    // Both branches must be the same type — a Color on one side and a Brush on
    // the other does not compile, and picking a solid for the normal rows would
    // have flattened the card gradient the rest of the app uses.
    val fill = if (highlight) {
        Brush.horizontalGradient(
            listOf(c.emerald.copy(alpha = 0.22f), c.emerald.copy(alpha = 0.06f))
        )
    } else {
        c.gradientCard
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .border(
                1.dp,
                if (highlight) c.emerald.copy(alpha = 0.45f) else c.cardBorder,
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(c.surfaceSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 16.sp)
        }

        Spacer(Modifier.size(12.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = c.textSecondary,
                fontSize = 14.sp
            ),
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                color = if (highlight) c.emerald else c.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp
            ),
            textAlign = TextAlign.End
        )
    }
}

/**
 * Play time as a person would say it.
 *
 * Seconds-to-`mm:ss` is the wrong unit here — a lifetime figure in seconds is
 * four digits nobody can feel, and the whole point is that this should feel like
 * an amount of time. Hours below ten, then days, because that is where the
 * number stops being impressive and starts being a genuine measure of somebody's
 * free time.
 */
private fun playTimeLabel(seconds: Long): String {
    if (seconds <= 0) return "—"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h >= 24 -> {
            val d = h / 24
            if (d >= 7) "${d / 7} هفته و ${d % 7} روز" else "$d روز و ${h % 24} ساعت"
        }
        h >= 10 -> "$h ساعت"
        else -> "$h ساعت و $m دقیقه"
    }
}