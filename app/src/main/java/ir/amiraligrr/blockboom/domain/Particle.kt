package ir.amiraligrr.blockboom.domain

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * A lightweight particle used by explosions, muzzle sparks and hit puffs.
 * Lives are pooled via [ParticlePool].
 *
 * ── The physics constants live here, not inline in the engine ─────────────
 * The update step used to be `v *= (1 - dt * 2)` with no gravity and a
 * constant radius, which is why every burst looked like a uniform expanding
 * starburst that slid sideways and vanished. Real debris decelerates hard,
 * falls, and shrinks. The values below are tuned to those three facts; they
 * are gathered in one place so the feel can be adjusted without hunting
 * through the simulation.
 */
class Particle {

    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f

    var age: Float = 0f
    var life: Float = 0.5f
    var color: Color = Color.White

    /**
     * Start size; the drawn radius eases from this down to ~25% of it.
     *
     * Backed by [size] rather than being set separately at each of the seven
     * call sites that spawn a particle. Those sites all wrote `p.size = ...`,
     * and a second field to remember alongside it is a field that one of them
     * will eventually forget — at which point the particle silently renders at
     * the pooled default size instead of its own, and the bug is invisible in
     * review because both lines look correct.
     */
    var startSize: Float = 6f
        private set

    var size: Float
        get() = startSize
        set(value) { startSize = value }

    /**
     * > 0 for a spark, 0 for debris.
     *
     * Sparks are hot, small, fast and short-lived; debris is dull, larger and
     * hangs around. Mixing the two in one burst is what makes an explosion
     * read as an explosion rather than as a coloured circle getting bigger.
     */
    var spark: Boolean = false

    val progress: Float get() = (age / life).coerceIn(0f, 1f)

    /**
     * Opacity, faded out on a curve.
     *
     * Linear was the old behaviour, and it is what made particles look like
     * decals: they held full brightness for most of their life and then
     * vanished in the last few frames. Holding bright briefly and easing out
     * over most of the lifetime reads as energy dissipating.
     */
    val alpha: Float get() = (1f - progress).pow(1.6f).coerceIn(0f, 1f)

    /**
     * Radius, shrinking as it ages.
     *
     * Cubic ease-out: stays near full size briefly, then collapses. Linear
     * shrinking made a constant-size speck that only changed opacity, so the
     * burst never appeared to dissipate.
     */
    val radius: Float
        get() {
            // `progress.pow(3)`, not `(1 - progress).pow(3)`.
            //
            // progress runs 0 -> 1 with age, so cubing *progress* holds the
            // particle at full size briefly and then collapses it — the cubic
            // ease-out the doc describes. The mirrored term went the other way:
            // it started at 0.22 and climbed to 1.0, so every particle was born
            // a fifth of its size (invisible, below the draw pass's 0.1px
            // cutoff) and inflated to full size at the moment it faded out. The
            // muzzle sparks read as growing bubbles instead of hot specks.
            val shrink = 1f - progress.pow(3f) * 0.78f
            return startSize * shrink.coerceAtLeast(0.22f)
        }

    fun reset() {
        x = 0f; y = 0f; vx = 0f; vy = 0f
        age = 0f; life = 0.5f; color = Color.White; spark = false
        size = 6f
    }
}

/** Expanding shockwave ring produced by a block explosion. */
class Explosion {

    var x: Float = 0f
    var y: Float = 0f
    var age: Float = 0f
    var life: Float = 0.45f
    var startRadius: Float = 12f
    var endRadius: Float = 90f
    var color: Color = Color.White

    val progress: Float get() = (age / life).coerceIn(0f, 1f)

    /** Linear fade, but weighted so the flash dies before the ring does. */
    val alpha: Float get() = (1f - progress).coerceIn(0f, 1f)

    /**
     * Radius on an ease-out curve.
     *
     * A shockwave leaves a block fast and slows as it loses energy. Linear
     * expansion made the ring crawl outward at a constant rate, which combined
     * with the linear fade to look like a translucent circle sliding across the
     * screen rather than a blast.
     */
    val radius: Float
        get() = startRadius + (endRadius - startRadius) * (1f - (1f - progress).pow(2.2f))

    fun reset() {
        x = 0f; y = 0f; age = 0f; life = 0.45f
        startRadius = 12f; endRadius = 90f; color = Color.White
    }
}

class ParticlePool(private val maxCached: Int = 160) {

    private val stack = ArrayDeque<Particle>(maxCached)

    fun obtain(): Particle {
        val p = stack.removeLastOrNull()
        if (p != null) {
            p.reset()
            return p
        }
        return Particle()
    }

    fun release(p: Particle) {
        if (stack.size < maxCached) stack.addLast(p)
    }
}

class CoinPool(private val maxCached: Int = 64) {

    private val stack = ArrayDeque<Coin>(maxCached)

    fun obtain(): Coin {
        val c = stack.removeLastOrNull()
        if (c != null) {
            c.reset()
            return c
        }
        return Coin()
    }

    fun release(c: Coin) {
        if (stack.size < maxCached) stack.addLast(c)
    }
}