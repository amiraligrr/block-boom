package ir.amiraligrr.blockboom.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.domain.GameEngine
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The in-game coach.
 *
 * ── Why the tutorial is here and not on a card ────────────────────────────
 * The old "tutorial" was a bouncing arrow pointing at the play button, on a
 * screen whose scroll viewport was a few dozen pixels tall — and every word of
 * instruction was about the *home screen*, not the game. A player who had never
 * launched a ball was asked to read four lines of text, then land in a game
 * with moving blocks, a cannon and no idea which of those three things responds
 * to their finger.
 *
 * Nothing describes a game as well as the game. So this drops the player into
 * the first level and waits for what they actually do, showing one line at a
 * time, in the order they will need it. Each step advances on the real event
 * — a drag, a shot fired, a block hit, a chain built — not on a timer, so a
 * player who is quick is not held up and one who is stuck is not rushed past
 * the thing they are stuck on.
 *
 * ── Where it sits, and why ────────────────────────────────────────────────
 * It used to be the first child of the game screen's column, pinned under the
 * status bar. In a fixed-height card that pushes everything below it down — the
 * header, the progress bar, the play field — so teaching the player about the
 * play field visibly shrank the play field. It is now a centred band in the
 * middle of the layout instead: far enough down to be read as part of the game,
 * high enough that the cannon and the falling blocks are both still on screen
 * either side of it.
 *
 * ── What it teaches, and why in this order ────────────────────────────────
 * The order is the order of the player's questions:
 *
 *   1. Which of these things is mine?   → Move. The cannon is the only thing
 *      that follows the finger, so it is the only thing worth teaching first.
 *   2. Do I have to aim?                → Shoot. No. It fires itself. This is
 *      the one mechanic that is genuinely counter-intuitive, and the thing
 *      players most often waste their first minute on.
 *   3. What am I shooting at?           → Break.
 *   4. What can hurt me?                → Danger. The engine ends the level
 *      when a block reaches the cannon (`GameStatus.HitByBlock`, a heart), and
 *      nothing on screen used to say so. A first-time player watches blocks
 *      tumble toward them, has no idea they are on a clock, and gets a
 *      game-over they read as "the game is broken". It is timed rather than
 *      gated because there is no event to wait for — it is a fact about the
 *      rules, not a thing to be done.
 *   5. How do I get more?                → Chain.
 *
 * ── It cannot block the game ──────────────────────────────────────────────
 * Every step has a timeout, and the whole coach can be dismissed with one tap
 * on the ✕. A coach that can trap someone in a level they have not understood
 * is worse than no coach. Nothing here writes to the engine.
 */
enum class CoachStep {
    /** "Move the cannon." Advances when the player has actually dragged it. */
    Move,

    /** "Shoot." Advances on the first ball being fired. */
    Shoot,

    /** "Break a block." Advances when the score first moves. */
    Break,

    /**
     * "Blocks that reach you kill you." Timed — there is no player action that
     * can complete it, it is a statement about the rules.
     */
    Danger,

    /** "Keep hitting within the window to build a chain." Timed. */
    Chain,

    /** Free play. */
    Done
}

/** The visible steps, in order. `Done` is not one of them. */
private val COACH_STEPS = listOf(
    CoachStep.Move, CoachStep.Shoot, CoachStep.Break, CoachStep.Danger, CoachStep.Chain
)

/** How often a gated step re-reads the signal it is waiting on. */
private const val STEP_POLL_INTERVAL_MS = 100L

/** Ceiling on a single gated step's wait, before the per-step cap takes over. */
private const val STEP_POLL_WINDOW_MS = 12_000L

/**
 * Whether a step freezes the board while it is on screen.
 *
 * ── Why the first three do and the last two do not ───────────────────────
 * "Move the cannon", "shoot" and "break a block" are three things the player
 * is being asked to *do*. They cannot be asked for while blocks are already
 * falling: the tutorial says break a block and a block hits the cannon
 * before the player has aimed, which is the single worst first impression the
 * game has, and it is why the coach used to be skipped — it taught the loop
 * by punishing an attempt at it.
 *
 * So the board holds for those three and only those. The last two steps —
 * what kills you and how a chain works — are things being *told*, and the
 * chain step in particular only means anything against a live board, so both
 * run with the game going and the coach sitting over it.
 */
fun CoachStep.holdsTheBoard(): Boolean = when (this) {
    CoachStep.Move, CoachStep.Shoot, CoachStep.Break -> true
    CoachStep.Danger, CoachStep.Chain, CoachStep.Done -> false
}

/** Emoji badge per step. */
private fun CoachStep.emoji(): String = when (this) {
    CoachStep.Move -> "👆"
    CoachStep.Shoot -> "🎯"
    CoachStep.Break -> "💥"
    CoachStep.Danger -> "☠️"
    CoachStep.Chain -> "🔥"
    CoachStep.Done -> ""
}

/**
 * Copy per step, read from resources.
 *
 * This used to be Persian string literals inline in the `when`. Every other word
 * in this app is a resource; these were the exception, so the coach could not be
 * re-worded, reviewed or translated without touching Kotlin — and could not be
 * checked by the resource tooling at all.
 */
@Composable
private fun CoachStep.copy(): Triple<String, String, String> {
    val ctx = LocalContext.current
    // Only the five visible steps reach this: the caller returns before
    // composing on CoachStep.Done. The empty branch keeps the `when`
    // exhaustive — Done is a state the engine passes through, not a card
    // that is ever shown.
    val titleRes = when (this) {
        CoachStep.Move -> R.string.coach_step_move_title
        CoachStep.Shoot -> R.string.coach_step_shoot_title
        CoachStep.Break -> R.string.coach_step_break_title
        CoachStep.Danger -> R.string.coach_step_danger_title
        CoachStep.Chain -> R.string.coach_step_chain_title
        CoachStep.Done -> R.string.coach_step_chain_title
    }
    val bodyRes = when (this) {
        CoachStep.Move -> R.string.coach_step_move_body
        CoachStep.Shoot -> R.string.coach_step_shoot_body
        CoachStep.Break -> R.string.coach_step_break_body
        CoachStep.Danger -> R.string.coach_step_danger_body
        CoachStep.Chain -> R.string.coach_step_chain_body
        CoachStep.Done -> R.string.coach_step_chain_body
    }
    return Triple(emoji(), ctx.getString(titleRes), ctx.getString(bodyRes))
}

@Composable
fun GameCoach(
    engine: GameEngine,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
    /**
     * True while this step is one the board should wait for.
     *
     * Reported rather than acted on here, because the simulation is the
     * engine's and only its owner may pause it — the coach is a view over the
     * game, and a view that stops the game is a view that can stop the game
     * forever if it is disposed mid-step.
     */
    onHoldChanged: (Boolean) -> Unit = {}
) {
    val c = joyThemeColors()

    var step by remember { mutableStateOf(CoachStep.Move) }

    // ── What the player actually did ──────────────────────────────────────
    //
    // All three are snapshot state on the engine, read from inside a coroutine
    // below rather than captured in the composition. The first version of this
    // coach read `engine.balls.size`, and because `balls` is a plain ArrayList
    // that registers no observation at all, the "shoot" step could never have
    // advanced on a real shot — it would have sat there until its timeout and
    // moved on without having checked. The engine now exposes `shotsFired` for
    // precisely this.
    //
    // `cannonMoves` is new, and it exists because the obvious thing to read was
    // a lie. `cannon.targetX` is set to the centre of the field by the first
    // layout pass, so "targetX != 0" was already true before the player had
    // touched the screen and the very first step of the tutorial verified
    // nothing at all. See [GameEngine.cannonMoves].
    //
    // Reading them here rather than as composition locals is what lets the
    // effect be keyed on `step` alone; see the note on the effect.

    // Advance on real events.
    //
    // Keyed on `step` alone, never on the signal itself. `shotsFired` climbs
    // every ~385ms at the starting fire rate, so keying on it tore this effect
    // down and relaunched it part-way through the dwell `delay` below — which
    // restarts the delay from zero, every time. The step being waited for was
    // therefore unreachable and the coach sat on "move the cannon" until its
    // timeout fired: the shoot / break / danger / chain instructions, and the
    // progress dots that count them, never appeared.
    //
    // The conditions are polled inside the effect instead, so a signal that
    // arrives while it is suspended is picked up rather than restarting it.
    LaunchedEffect(step) {
        // The condition this step waits on, and what it becomes once satisfied
        // and the dwell has passed. Steps with no mechanic to verify are timed.
        val condition: (() -> Boolean)?
        val dwellMs: Long
        val next: CoachStep
        when (step) {
            CoachStep.Move -> {
                condition = { engine.cannonMoves > 0 }
                dwellMs = 500          // let them feel that it responded
                next = CoachStep.Shoot
            }
            CoachStep.Shoot -> {
                condition = { engine.shotsFired > 0 }
                dwellMs = 700
                next = CoachStep.Break
            }
            CoachStep.Break -> {
                condition = { engine.score > 0 }
                dwellMs = 900
                next = CoachStep.Danger
            }
            CoachStep.Danger -> {
                condition = null
                dwellMs = 5_000        // long enough to read a two-line warning
                next = CoachStep.Chain
            }
            CoachStep.Chain -> {
                condition = null
                dwellMs = 3_200
                next = CoachStep.Done
            }
            CoachStep.Done -> {
                condition = null
                dwellMs = 0L
                next = CoachStep.Done
            }
        }

        if (step == CoachStep.Done) {
            onFinished()
            return@LaunchedEffect
        }

        if (condition != null) {
            // Bounded so a step whose action never arrives cannot poll forever.
            // Expiry is handled by the per-step cap effect below, which is what
            // makes every step escapable; `withTimeoutOrNull` returning null (the
            // timeout) means exactly that, so the step is left where it is.
            withTimeoutOrNull(STEP_POLL_WINDOW_MS) {
                while (!condition()) delay(STEP_POLL_INTERVAL_MS)
            } ?: return@LaunchedEffect
        }

        delay(dwellMs)
        step = next
    }

    // Every step is escapable on its own, so a player who already knows what to
    // do is not waiting on a condition they may never meet.
    LaunchedEffect(step) {
        val cap = when (step) {
            CoachStep.Move -> 9_000L
            CoachStep.Shoot -> 9_000L
            CoachStep.Break -> 14_000L
            CoachStep.Danger -> 5_000L
            CoachStep.Chain -> 3_200L
            CoachStep.Done -> 0L
        }
        if (cap > 0) {
            delay(cap)
            if (step != CoachStep.Done) step = CoachStep.Done
        }
    }

    // Danger is the one step about *losing*, so it is the one that gets a
    // different colour from the rest. Amber/coral against the emerald of the
    // other four makes it read as a warning at a glance, in peripheral vision,
    // while the player is looking at falling blocks rather than at the coach.
    val isDanger = step == CoachStep.Danger

    if (step == CoachStep.Done) return
    val stepIndex = COACH_STEPS.indexOf(step)

    // Tell the owner whether the board should be held, for this step and for
    // as long as the coach is on screen. Released on dispose, so a coach that
    // is dismissed or that navigates away cannot leave the game frozen.
    val hold = step.holdsTheBoard()
    LaunchedEffect(hold) { onHoldChanged(hold) }
    DisposableEffect(Unit) {
        onDispose { onHoldChanged(false) }
    }

    val (emoji, title, body) = step.copy()

    // A soft pulse on the step badge, so the eye finds the coach even mid-run.
    val pulse = rememberInfiniteTransitionSafe()
    val badgeScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "coachPulse"
    )

    // A one-shot entrance. This was `AnimatedVisibility(visible = true, ...)` —
    // a constant target, so the enter transition was already at its end on the
    // first frame and the exit could never run: the card simply popped in and
    // vanished. The animation is driven by `shown` instead, which flips once
    // per composition.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enter by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "coachEnter"
    )

    val accent = if (isDanger) c.coral else c.emerald

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    alpha = enter
                    translationY = (1f - enter) * 24f
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.gradientCard)
                    .border(1.5.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .scale(badgeScale)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.verticalGradient(
                                if (isDanger) listOf(c.coral, c.amber)
                                else listOf(c.emerald, c.mintMid)
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 20.sp)
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            // The warning step takes the accent colour for its
                            // title too, so it is identifiable without reading.
                            color = if (isDanger) c.coral else c.textPrimary,
                            fontWeight = FontWeight.Black
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isDanger) c.textSecondary else c.textMuted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Dismiss. One tap, always available, and it does not resume
                // later — a coach the player has dismissed once should stay
                // dismissed.
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(c.surfaceSoft)
                        .border(1.dp, c.cardBorder, RoundedCornerShape(11.dp))
                        .clickable { step = CoachStep.Done; onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", fontSize = 13.sp, color = c.textMuted)
                }
            }

            // Progress dots.
            //
            // Five steps that fire in sequence with no visible end is the shape
            // of an overlay people swipe away without reading, so the coach now
            // says how much of it is left. Filled up to the current step,
            // hollow after, with the active one wider — a segmented bar in
            // miniature.
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                COACH_STEPS.forEachIndexed { i, _ ->
                    val active = i == stepIndex
                    Box(
                        modifier = Modifier
                            .height(5.dp)
                            .width(if (active) 18.dp else 5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                when {
                                    active -> accent
                                    i < stepIndex -> accent.copy(alpha = 0.35f)
                                    else -> c.cardBorder
                                }
                            )
                    )
                    if (i != COACH_STEPS.lastIndex) Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}

/**
 * A single transition, created once per composition.
 *
 * `rememberInfiniteTransition` must not be created inside a `when` branch that
 * re-enters, or a step change both re-creates the transition and leaves the old
 * one running — which is how a coach ends up with two conflicting animators on
 * the same badge.
 */
@Composable
private fun rememberInfiniteTransitionSafe() =
    androidx.compose.animation.core.rememberInfiniteTransition(label = "coach")
