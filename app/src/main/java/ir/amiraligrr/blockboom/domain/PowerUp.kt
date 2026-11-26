package ir.amiraligrr.blockboom.domain

import androidx.compose.ui.graphics.Color
import ir.amiraligrr.blockboom.ui.theme.NeonCyan
import ir.amiraligrr.blockboom.ui.theme.NeonGold
import ir.amiraligrr.blockboom.ui.theme.NeonPink
import ir.amiraligrr.blockboom.ui.theme.NeonRed

enum class PowerUpType(
    val icon: String,
    val label: String,
    val duration: Float,
    val color: Color
) {
    FREEZE("❄️", "FREEZE!", 5f, NeonCyan),
    DOUBLE_DAMAGE("⚡", "DOUBLE DAMAGE!", 8f, NeonGold),
    RAPID_FIRE("🔥", "RAPID FIRE!", 6f, NeonRed),
    COIN_RUSH("🌟", "COIN RUSH!", 10f, NeonGold),

    /**
     * GHOST — the balls pass through everything.
     *
     * The one power-up that changes what the player is *aiming at*. Every other
     * one makes the player do the same thing faster; this one makes the board
     * briefly irrelevant, which is what makes it worth waiting for.
     *
     * Implemented as: blocks cannot destroy a ball while it is active. Note this
     * is the *inverse* of every other power-up — it grants immunity rather than
     * strength — and it deliberately does NOT make blocks unbreakable, because a
     * ghost that cannot shoot is not a reward.
     */
    GHOST("👻", "GHOST MODE!", 7f, Color(0xFFB39DFF)),

    /**
     * SHIELD — one free hit.
     *
     * The cannon absorbs a single "a block reached you" event, so a player who
     * is about to lose their last heart gets one more level rather than being
     * sent to the game-over screen. Held as a counter rather than a timer,
     * because "a shield" is a thing you own, not a state you are in.
     */
    SHIELD("🛡️", "SHIELD!", 0f, Color(0xFF7DD3FC)),

    NUKE("💥", "NUKE!", 0f, NeonPink);

    /**
     * Timed effects show a countdown chip; instant effects do not.
     *
     * NUKE and SHIELD have `duration = 0` because they are consumed on use, and
     * the HUD must not render a 0-second timer for them.
     */
    val isTimed: Boolean get() = duration > 0f
}

/**
 * A floating golden bonus cube. One-shot: any ball that touches it triggers the
 * power-up. Doesn't count against the level's block total.
 */
class BonusBlock {
    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var size: Float = 0f
    var rotation: Float = 0f
    var spin: Float = 0f
    var type: PowerUpType = PowerUpType.FREEZE
    var age: Float = 0f

    val centerX: Float get() = x + size * 0.5f
    val centerY: Float get() = y + size * 0.5f
    val bottom: Float get() = y + size
}

/**
 * A power-up drop that falls down after hitting a bonus block.
 * The cannon must catch it to activate the power-up.
 */
class PowerUpDrop {
    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var size: Float = 32f
    var rotation: Float = 0f
    var spin: Float = 180f
    var type: PowerUpType = PowerUpType.FREEZE
    var age: Float = 0f

    val centerX: Float get() = x + size * 0.5f
    val centerY: Float get() = y + size * 0.5f
    val bottom: Float get() = y + size
}