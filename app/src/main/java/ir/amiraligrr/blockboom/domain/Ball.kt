package ir.amiraligrr.blockboom.domain

/**
 * A projectile fired by the cannon. Travels straight up, carries [damage]
 * and can pierce through [pierce] + 1 blocks when upgraded.
 *
 * Trail positions are stored in a fixed-ring buffer to avoid per-frame
 * allocations (GC pressure).
 */
class Ball {

    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f

    var radius: Float = 0f
    var damage: Int = 1
    var pierce: Int = 0
    var hits: Int = 0
    var age: Float = 0f

    /** Block hit during the current frame, so pierce can't re-hit it. */
    var lastHitBlock: Block? = null

    /** Grows from 0.3 to 1.0 over ~100ms (shoot-in animation). */
    var scale: Float = 0.3f

    /**
     * Drawn with a flame halo while RAPID FIRE is running.
     *
     * Set once at spawn from the power-up state, so the volley itself carries the
     * effect: RAPID FIRE previously changed only the shot *rate*, which the player
     * experiences as "more things happening" rather than as a power. Seeing the
     * stream catch fire says what is active without reading the HUD.
     */
    var isFlaming: Boolean = false

    /**
     * Drawn with a spectral outline while GHOST is running.
     *
     * Same reasoning as [isFlaming], and it matters more here: GHOST grants
     * immunity rather than strength, so nothing about the shot's damage changes.
     * Without a visual the player has no way to tell a GHOST ball from an ordinary
     * one, which makes the power-up indistinguishable from a bug — see
     * [GameEngine.consumedForHit] for the behaviour this flag advertises.
     */
    var isSpectral: Boolean = false

    val currentRadius: Float get() = radius * scale

    private val trailX = FloatArray(TRAIL_SIZE)
    private val trailY = FloatArray(TRAIL_SIZE)
    private var trailHead = 0
    private var trailCount = 0

    fun pushTrail(px: Float, py: Float) {
        trailHead = (trailHead + 1) % TRAIL_SIZE
        trailX[trailHead] = px
        trailY[trailHead] = py
        if (trailCount < TRAIL_SIZE) trailCount++
    }

    /** Index 0 = newest point. Returns null when not enough history exists. */
    fun trailPoint(fromNewest: Int): Pair<Float, Float>? {
        // Negative indices are rejected rather than wrapped. The modulo below
        // would happily turn -1 into a slot one ahead of the newest write, which
        // is either an unwritten (0, 0) or — once the ring has wrapped — the
        // oldest buffered point. Both were reachable from the render pass.
        if (fromNewest < 0 || fromNewest >= trailCount) return null
        val idx = (trailHead - fromNewest + TRAIL_SIZE) % TRAIL_SIZE
        return trailX[idx] to trailY[idx]
    }

    fun trailAlphaRatio(fromNewest: Int): Float {
        if (trailCount == 0) return 0f
        return 1f - fromNewest.toFloat() / trailCount
    }

    companion object {
        const val TRAIL_SIZE = 8
    }
}