package ir.amiraligrr.blockboom.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * How long a kill keeps its combo alive, in seconds.
 *
 * Shared by the engine (which configures the tracker) and the HUD meter (which
 * labels the window), so it lives here rather than as a magic number at either
 * site.
 */
const val COMBO_WINDOW_SECONDS = 1f

/**
 * Bonus score paid per step of the combo chain.
 *
 * Lives here, next to the window, because two places need to agree on it: the
 * engine pays it out and the HUD meter advertises it. When the meter showed a
 * literal `count * 5` it would silently start lying the day the payout was
 * rebalanced.
 */
const val COMBO_SCORE_PER_TIER = 5

/**
 * Combo tiers: the minimum chain length, and the score multiplier it pays.
 *
 * ── Why the payout is flat ────────────────────────────────────────────────
 * Every earlier version of this curve was a straight line — `count * 5` — which
 * meant a 20-chain paid exactly ten times a 2-chain and a 25-chain paid exactly
 * ten too. There was no point on the ladder worth reaching: holding a chain
 * alive was neither rewarded nor punished beyond the fourth kill, so the skill
 * the mechanic implies was simply not expressed.
 *
 * The tiers below make the last step the whole point. Chaining is *hard* — the
 * window is one second and a big board does not die fast — so a ×5 band that
 * only opens at 30 is something the player has to actively aim for, and the
 * reward for reaching it is larger than the sum of everything before it.
 *
 * ── Why this scales SCORE and not COINS ──────────────────────────────────
 * Deliberately, and this is a balance constraint rather than a stylistic one.
 * Coins buy upgrades, and upgrade pricing has been tuned against a measured
 * income of roughly 1,275 coins per level to put a full six-track build at
 * level ~304. Paying extra coins per tier would raise income, which pulls the
 * whole track earlier and silently undoes that tuning — and it would do so
 * unevenly, because only players good at chaining would see it, which is the
 * worst possible distribution for a change nobody would notice happening.
 *
 * Score is not part of that economy. It feeds the leaderboard and nothing else,
 * so scaling it rewards skill without touching the coin faucet or the price
 * curve. If the economy is ever retuned, this split is the thing to revisit —
 * coins could move onto the tiers then, and should not before.
 */
private val COMBO_TIERS = intArrayOf(0, 5, 10, 20, 30)
private val COMBO_MULTIPLIERS = intArrayOf(1, 2, 3, 4, 5)

/**
 * Score multiplier for a chain of [count].
 *
 * Returns 1 for anything below the first threshold, which is also the answer for
 * a chain of zero or one — those pay no combo bonus at all, handled by the
 * caller.
 */
fun comboMultiplierFor(count: Int): Int {
    if (count <= 0) return 1
    var tier = 0
    for (i in COMBO_TIERS.indices) {
        if (count >= COMBO_TIERS[i]) tier = i
    }
    return COMBO_MULTIPLIERS[tier]
}

/**
 * Bonus score for one kill at chain length [count].
 *
 * The single definition of the payout, read by both the engine (which pays it)
 * and the HUD meter (which advertises it) — see [COMBO_SCORE_PER_TIER] for why
 * that matters.
 */
fun comboBonusScore(count: Int): Int = count * COMBO_SCORE_PER_TIER * comboMultiplierFor(count)

/**
 * The next chain length that raises the multiplier, or 0 if there is no further
 * tier.
 *
 * Shown in the HUD so the ladder is a target rather than a rule the player has
 * to infer. Without it the thresholds are invisible: a player at a chain of
 * eight has no way to know that nine is worth nothing and ten is worth double,
 * so the number on screen has no meaning beyond "bigger".
 */
fun nextComboTier(count: Int): Int {
    for (i in COMBO_TIERS.indices) {
        if (COMBO_TIERS[i] > count) return COMBO_TIERS[i]
    }
    return 0
}

/**
 * The kill-combo window.
 *
 * A combo is N blocks destroyed inside [windowSeconds] of each other. It pays
 * bonus score, and from three up it also pays a coin — the mechanic the whole
 * "chase the chain" loop hangs on, so it deserves to be *visible* for the whole
 * window rather than only announced by a popup that fades in under a second.
 *
 * Hit streaks and kill combos are timed independently. They used to share one
 * `lastHitTime`, which meant a kill always saw `now - lastHitTime == 0` (the
 * preceding `onHit` had just stamped it) and so every block destroyed for the
 * whole level counted as a combo — inflating score and handing out a free coin
 * per kill regardless of how slowly the player was playing.
 *
 * ── Why this is snapshot state ─────────────────────────────────────────────
 * The combo meter in the HUD has to redraw every frame while the window is
 * draining. Feeding it from the engine's `tick` would recompose the whole HUD
 * subtree 60x/second, and polling a plain `var` from a composable registers no
 * observation at all, so the meter would simply never update. [count] and
 * [fractionRemaining] are therefore Compose snapshot state written by the game
 * loop and read by the meter.
 *
 * Both are written from the main thread only (the frame loop), so no snapshot
 * conflict handling is needed.
 */
class ComboTracker(private val windowSeconds: Float = COMBO_WINDOW_SECONDS) {

    /**
     * Consecutive kills inside the window. 0 when no combo is running.
     *
     * Snapshot state so the HUD meter observes it. Never assign from the
     * draw phase — use [onBlockDestroyed] / [reset] / [tick].
     */
    var count: Int by mutableIntStateOf(0)
        private set

    /**
     * How much of the combo window is left, 1f down to 0f.
     *
     * Read by the meter's draining bar. Separate from [count] because the bar
     * has to animate on every frame while the *number* only changes on a kill;
     * deriving both from one field made the number flicker with the bar.
     */
    var fractionRemaining: Float by mutableFloatStateOf(0f)
        private set

    /** The largest combo reached this run, for the end-of-run summary. */
    var best: Int by mutableIntStateOf(0)
        private set

    private var lastKillTime: Float = -100f

    /**
     * Call on every block destroyed.
     *
     * @return the new combo count, which is what `destroyBlock` pays out on.
     */
    fun onBlockDestroyed(now: Float): Int {
        count = if (now - lastKillTime <= windowSeconds) count + 1 else 1
        lastKillTime = now
        fractionRemaining = 1f
        if (count > best) best = count
        return count
    }

    /**
     * Advances the window. Called once per simulation step.
     *
     * This is what expires a combo, and it is deliberately driven by
     * simulation time rather than wall-clock so that a paused game does not
     * silently lose the player's chain.
     *
     * @param now current `gameElapsed` in seconds.
     * @return the combo count, which is 0 once the window has lapsed. Reading
     *         it here rather than at the next kill is what lets the HUD fade
     *         the meter out on time.
     */
    fun tick(now: Float): Int {
        if (count == 0) return 0
        val left = windowSeconds - (now - lastKillTime)
        if (left <= 0f) {
            count = 0
            fractionRemaining = 0f
            return 0
        }
        fractionRemaining = (left / windowSeconds).coerceIn(0f, 1f)
        return count
    }

    fun reset() {
        count = 0
        fractionRemaining = 0f
        lastKillTime = -100f
    }
}
