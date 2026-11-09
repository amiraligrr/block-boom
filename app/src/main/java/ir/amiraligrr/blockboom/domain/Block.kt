package ir.amiraligrr.blockboom.domain

import ir.amiraligrr.blockboom.domain.BlockShape
import kotlin.math.max

/**
 * A falling numbered block. HP is reduced by ball damage; at 0 the block
 * explodes into coins. Blocks come in several [BlockShape]s, drift sideways,
 * bounce off walls and the floor, and only hurt the player by physically
 * hitting the cannon body.
 */
class Block {

    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var w: Float = 0f
    var h: Float = 0f

    var hp: Int = 0
    var maxHp: Int = 0
    var scoreValue: Int = 0

    var isBoss: Boolean = false
    var isLucky: Boolean = false
    var shapeType: BlockShape = BlockShape.SQUARE
    var bossType: BossType = BossType.NONE

    /**
     * The boss level's MOTHER: the one block the level is about.
     *
     * Kept apart from [isBoss] because the two mean different things to the
     * level logic. A boss is the *end* of an ordinary level — the last block,
     * worth a big score. The mother is the *whole* of a boss level: she is the
     * only block on the board, she does not produce, and the level is over when
     * she dies.
     */
    var isMother: Boolean = false

    /**
     * One of the small blocks the mother throws off the floor on each bounce.
     *
     * Not cosmetic: [GameEngine.spawnSatellites] counts what is alive so the
     * cap is enforced, and the draw pass gives them her colour so a satellite
     * reads as hers rather than as an ordinary block that happened to arrive.
     */
    var isSatellite: Boolean = false

    // ── Modifier properties ─────────────────────────────────────────────
    // Each is only ever set by the level modifier that owns it, so they are all
    // false on an ordinary level and cost nothing there.

    /**
     * Ice: survives its first hit, then shatters.
     *
     * Drawn as a frosted, cracked block so the state is visible before the
     * player wastes a shot discovering it — a hidden two-hit block just reads
     * as the cannon being unreliable.
     */
    var isIce: Boolean = false

    /** Armour: a shield that must be stripped before HP is touched. */
    var hasArmor: Boolean = false

    /** Armour hitpoints remaining. Separate from [hp] on purpose. */
    var armorHp: Int = 0

    /**
     * The armour this block started with.
     *
     * Needed to show wear. The HUD draws an armour ring whose sweep is the
     * *fraction remaining*, which requires knowing the original value — with
     * only [armorHp] to go on, the fraction was always exactly 1 and a battered
     * block looked identical to a fresh one.
     */
    var armorMaxHp: Int = 0



    /** A boulder: much tougher than the rest of the board. */
    var isBoulder: Boolean = false

    /**
     * True for pieces produced by [GameEngine.trySplitBlock]. Fragments never
     * split again — without this a single big block became 2 -> 4 -> 16 pieces
     * and one level ended up with hundreds of blocks to iterate and draw.
     */
    var isFragment: Boolean = false

    /**
     * Ground bounces performed so far.
     *
     * The rule is a budget, not a damper: every bounce is the same height (see
     * `GameEngine.bounceSpeed`). Once the budget is spent the block stops
     * hopping and crawls along the floor toward the cannon; only a real
     * collision with the cannon ends the run.
     */
    var bounceCount: Int = 0

    /**
     * True after the block has spent its bounce budget and has become the
     * level's single floor threat.
     *
     * This is a separate flag, not `bounceCount >= budget`, because the
     * engine changes the budget with level depth. A block that was already
     * spent at level 299 must not suddenly start bouncing again when the
     * level rolls over to 300.
     *
     * It is no longer a homing crawl. A block used to track the cannon across
     * the floor forever, which produced the "stuck to the floor and chased me
     * into a corner" deaths with no counterplay. It now stops, telegraphs for
     * [GameEngine]'s last-stand window, then charges once in a fixed direction.
     * The player can shoot it during the telegraph or step out of the lane.
     */
    var isHoming: Boolean = false

    /**
     * Seconds left in the last-stand telegraph. While positive the block is
     * stationary and visibly winding up; it is also fully shootable.
     */
    var lastStandTimer: Float = 0f

    /** Fixed horizontal direction (-1 or +1) of the last-stand charge. */
    var chargeDir: Float = 0f

    /** True once the telegraph has elapsed and the charge is under way. */
    var isCharging: Boolean = false

    /** Seconds spent charging, used to expire a charge that never connects. */
    var chargeElapsed: Float = 0f

    /** White flash right after a hit (1 -> 0). */
    var hitFlash: Float = 0f

    /**
     * Set by [applyHit], cleared when the inflation animation finishes.
     *
     * A lucky block shows "?" rather than its HP, so the player cannot tell how
     * much punishment a shot has left from the number. Without a physical
     * reaction, hitting it did nothing observable — which is exactly why it read
     * as broken. Now each hit visibly swells the block and pops a ring of debris
     * out of it, and it settles back, so progress toward the payout is something
     * you can see without a counter.
     *
     * Non-lucky blocks ignore this entirely; the board's normal hit feedback is
     * the number going down.
     */
    var luckyPuff: Float = 0f

    /** True while [luckyPuff] is animating, so the draw pass can branch once. */
    val isPuffing: Boolean get() = luckyPuff > 0f

    /** Shake animation while > 0. */
    var shakeTime: Float = 0f
    var shakePhase: Float = 0f

    /**
     * Stable per-block identity for its colour, in [0,1).
     *
     * Assigned once at spawn and never changed.
     *
     * The colour used to be derived from [shakePhase], which is a poor choice
     * for two reasons: it is an *angle* (0..2π), so the value wraps and the
     * derived hue jumps discontinuously; and it is mutated by the hit-shake
     * animation, so a block visibly changed colour every time it was hit. This
     * is the replacement — a plain number that only ever means "which block is
     * this", so its colour is stable for the block's whole life.
     */
    var colorSeed: Float = 0f

    /**
     * Which entry of [ir.amiraligrr.blockboom.ui.theme.BlockPalette] this block
     * wears, in [0, 16).
     *
     * This is the block's *actual* colour. [colorSeed] survives only as the
     * input to the small per-block tone variation layered on top.
     *
     * It is dealt from a shuffled deck by the engine at spawn, so two blocks
     * in flight never share a colour until the whole palette has been used —
     * which is what makes a crowded board readable. Fragments copy the
     * parent's value, so a block keeps its colour as it breaks apart.
     */
    var colorIndex: Int = 0

    // `hitCombo` used to live here and was incremented on every hit. Nothing
    // ever read it — combos are tracked once, globally, by ComboTracker — so it
    // cost a field on every pooled block and an increment on every hit to hold
    // a value nothing could observe.

    val centerX: Float get() = x + w * 0.5f
    val centerY: Float get() = y + h * 0.5f
    val bottom: Float get() = y + h
    val radius: Float get() = (w + h) * 0.25f

    fun applyHit() {
        hitFlash = 1f
        shakeTime = 0.18f
        luckyPuff = 1f
    }

    fun updateShake(dt: Float) {
        if (shakeTime > 0f) shakeTime -= dt
        if (hitFlash > 0f) hitFlash -= dt * 3f
        // Slower than the flash, so the swell is readable rather than a twitch.
        // Roughly a third of a second from full to settled.
        if (luckyPuff > 0f) luckyPuff = max(0f, luckyPuff - dt * 3f)
    }
}

enum class BossType {
    NONE,
    CLASSIC,      // Standard boss
    TITAN,        // Giant, slow, massive HP
    SHADOW,       // Fast, teleports, phases through hits
    INFERNO       // Explodes on hit, spreads fire
}