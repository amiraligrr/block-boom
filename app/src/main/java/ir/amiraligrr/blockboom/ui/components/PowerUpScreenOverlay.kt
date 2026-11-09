package ir.amiraligrr.blockboom.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import ir.amiraligrr.blockboom.domain.GameEngine
import ir.amiraligrr.blockboom.domain.PowerUpType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen overlays that make an active power-up *visible*.
 *
 * ── Why this exists ──────────────────────────────────────────────────────
 * FREEZE stops blocks falling. That is a completely invisible change: the
 * numbers stop moving, and a player who was not looking at a specific block has
 * no way to tell "the game froze" from "the game is stuck" — which is the worst
 * possible reading, because the correct response to a freeze is to keep
 * shooting, and the correct response to a hang is to stop. The only clue was a
 * small timer pill in the HUD.
 *
 * So every power-up now paints the whole play field, not just the play field's
 * HUD. The rule followed here: if an effect changes what the player is looking
 * at, the screen itself has to say so.
 *
 * Each overlay is tied to `powerUpSecondsLeft / duration`, so it fades out with
 * the power-up instead of snapping off at zero — and a power-up that is about to
 * expire gets visibly weaker, which is the honest warning.
 *
 * Drawn in a separate Canvas above the field rather than inside it, so it never
 * interferes with the entity draw order or the backdrop, and so it costs one
 * composable instead of branching the main draw pass 60 times a second.
 */
@Composable
fun PowerUpScreenOverlay(
    engine: GameEngine,
    modifier: Modifier = Modifier
) {
    // One transition for the whole overlay: the shimmer is shared by every
    // effect, so the frost glint and the heat wobble do not fight each other
    // when two power-ups overlap.
    val infinite = rememberInfiniteTransition(label = "powerUpOverlay")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600), RepeatMode.Restart),
        label = "overlayPhase"
    )

    Canvas(modifier = modifier) {
        drawPowerUpFreeze(engine, phase)
        drawPowerUpDoubleDamage(engine, phase)
        drawPowerUpRapidFire(engine, phase)
        drawPowerUpCoinRush(engine, phase)
    }
}

/** 1 while a power-up is at full strength, 0 as it runs out. */
private fun DrawScope.strength(engine: GameEngine, type: PowerUpType): Float {
    val left = engine.powerUpSecondsLeft(type)
    if (left <= 0f) return 0f
    val total = if (type.duration > 0f) type.duration else 1f
    return (left / total).coerceIn(0f, 1f)
}

// ─────────────────────────────────────────────────────────────────────────
//  FREEZE — the field is frozen over
//
//  Deliberately not a plain blue rectangle. A flat tint reads as a rendering
//  filter over the whole game and hides the blocks, which is the opposite of
//  what the player needs while aiming at them. This is built from what ice
//  actually looks like: a cool colour wash, a bright rim at the edges of the
//  screen where the "glass" meets the frame, and a few angular crystal facets
//  that drift slowly across the field. Blocks stay fully visible underneath.
// ─────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawPowerUpFreeze(engine: GameEngine, phase: Float) {
    val s = strength(engine, PowerUpType.FREEZE)
    if (s <= 0f) return

    val ice = Color(0xFF7DD3FC)
    val iceDeep = Color(0xFF38BDF8)

    // 1. Cool wash. Kept low so gameplay stays readable through it.
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                ice.copy(alpha = 0.20f * s),
                iceDeep.copy(alpha = 0.10f * s),
                Color(0xFFE0F2FE).copy(alpha = 0.16f * s),
            )
        ),
        size = size
    )

    // 2. Frozen rim — the "glass edge" of the screen. Brightest at the top
    //    where the ice would meet the frame.
    val rim = dpPx(3f)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.55f * s),
                Color.White.copy(alpha = 0.0f),
                Color.White.copy(alpha = 0.30f * s),
            ),
            startY = 0f,
            endY = rim * 3f
        ),
        size = size
    )
    drawLine(
        color = Color.White.copy(alpha = 0.42f * s),
        start = Offset(0f, rim * 0.5f),
        end = Offset(size.width, rim * 0.5f),
        strokeWidth = dpPx(1.5f)
    )

    // 3. Crystal facets. Deterministic positions derived from the index, so
    //    they hold still instead of jittering, and they drift slowly.
    val facets = 7
    for (i in 0 until facets) {
        val seed = i * 1.618f
        val drift = phase * dpPx(9f)
        val cx = ((sin(seed) * 0.5f + 0.5f) * size.width + drift) % (size.width + dpPx(60f))
        val x = cx - dpPx(30f)
        val y = ((cos(seed * 1.7f) * 0.5f + 0.5f) * size.height) + sin(phase * 6.28f + seed) * dpPx(6f)
        val r = dpPx(14f + 10f * abs(sin(seed * 2.3f)))

        val facet = Path().apply {
            moveTo(x, y - r)
            lineTo(x + r * 0.75f, y)
            lineTo(x, y + r)
            lineTo(x - r * 0.75f, y)
            close()
        }
        drawPath(
            path = facet,
            color = Color.White.copy(alpha = 0.16f * s),
            style = Stroke(width = dpPx(1.2f))
        )
        // A short internal glint, which is what sells it as ice rather than as
        // an outline.
        drawLine(
            color = Color.White.copy(alpha = 0.30f * s),
            start = Offset(x - r * 0.3f, y - r * 0.35f),
            end = Offset(x + r * 0.3f, y + r * 0.35f),
            strokeWidth = dpPx(1f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  DOUBLE DAMAGE — charged and hot
// ─────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawPowerUpDoubleDamage(engine: GameEngine, phase: Float) {
    val s = strength(engine, PowerUpType.DOUBLE_DAMAGE)
    if (s <= 0f) return

    val gold = Color(0xFFFDE047)
    val amber = Color(0xFFF59E0B)

    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                gold.copy(alpha = 0.10f * s),
                amber.copy(alpha = 0.16f * s),
            ),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.maxDimension * 0.75f
        ),
        size = size
    )

    // Rising energy motes — reads as "charging" rather than "tinted".
    val motes = 10
    for (i in 0 until motes) {
        val seed = i * 2.399f
        val t = (phase + abs(sin(seed))) % 1f
        val x = (sin(seed) * 0.5f + 0.5f) * size.width
        val y = size.height - t * size.height
        val r = dpPx(2.5f + 2f * abs(cos(seed)))
        drawCircle(
            color = gold.copy(alpha = 0.5f * s * (1f - t * 0.5f)),
            radius = r,
            center = Offset(x, y)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  RAPID FIRE — heat haze
//
//  Drawn as vertical shimmer bars rather than a tint: the cue for "firing
//  faster" is motion at the edges, which is where the player's eye already is
//  tracking the cannon's stream of shots.
// ─────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawPowerUpRapidFire(engine: GameEngine, phase: Float) {
    val s = strength(engine, PowerUpType.RAPID_FIRE)
    if (s <= 0f) return

    val flame = Color(0xFFFF8A3D)

    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                flame.copy(alpha = 0.20f * s),
                Color.Transparent,
                flame.copy(alpha = 0.12f * s),
            )
        ),
        size = size
    )

    val bars = 9
    for (i in 0 until bars) {
        val u = (i + 0.5f) / bars
        val x = u * size.width
        val wobble = sin(phase * 2f * PI.toFloat() * 2f + i * 1.1f)
        val h = dpPx(26f + 12f * abs(wobble))
        drawLine(
            color = flame.copy(alpha = 0.30f * s),
            start = Offset(x, size.height - h),
            end = Offset(x + wobble * dpPx(3f), size.height),
            strokeWidth = dpPx(3f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  COIN RUSH — gold shimmer
// ─────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawPowerUpCoinRush(engine: GameEngine, phase: Float) {
    val s = strength(engine, PowerUpType.COIN_RUSH)
    if (s <= 0f) return

    val coin = Color(0xFFFDE047)

    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                Color.Transparent,
                coin.copy(alpha = 0.12f * s),
                coin.copy(alpha = 0.18f * s),
            )
        ),
        size = size
    )

    // A slow diagonal sweep, the classic "something is happening here" cue.
    val sweep = phase * (size.width + size.height)
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                coin.copy(alpha = 0.14f * s),
                Color.Transparent,
            ),
            start = Offset(sweep - size.height, 0f),
            end = Offset(sweep, size.height)
        ),
        size = size
    )
}