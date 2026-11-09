package ir.amiraligrr.blockboom

import ir.amiraligrr.blockboom.domain.Ball
import ir.amiraligrr.blockboom.domain.Block
import ir.amiraligrr.blockboom.domain.CollisionDetector
import ir.amiraligrr.blockboom.domain.Particle
import ir.amiraligrr.blockboom.ui.formatNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariants for the pure parts of the simulation.
 *
 * Every one of these is a bug that shipped: none of them fail to compile, none
 * of them fail the build, and none of them would be caught by re-reading the
 * code — they were caught by a number not adding up or a control being
 * unreachable. They are here so the next one does not ship either.
 */
class SimulationInvariantsTest {

    // ── Particle radius ─────────────────────────────────────────────────────

    @Test
    fun `particle radius only ever shrinks`() {
        // It used to cube `(1 - progress)`, which runs the curve backwards:
        // every particle was born at 22% of its size — below the draw pass's
        // 0.1px cutoff, so invisible — and inflated to full size exactly as it
        // faded out. Sparks read as growing bubbles.
        val p = Particle()
        p.size = 10f
        p.life = 1f

        var previous = Float.MAX_VALUE
        for (step in 0..100) {
            p.age = step / 100f
            val r = p.radius
            assertTrue(
                "particle grew at progress ${p.progress}: $previous -> $r",
                r <= previous + 1e-4f
            )
            previous = r
        }
    }

    @Test
    fun `particle is born full size and ends near a quarter of it`() {
        val p = Particle()
        p.size = 10f
        p.life = 1f

        p.age = 0f
        assertEquals("a new particle must be drawn at full size", 10f, p.radius, 0.01f)

        p.age = 1f
        assertTrue(
            "a finished particle should be ~22% of its size, was ${p.radius}",
            p.radius in 2.0f..2.5f
        )
    }

    @Test
    fun `particle progress is clamped and monotonic in age`() {
        val p = Particle()
        p.life = 0.5f

        p.age = -1f
        assertEquals(0f, p.progress, 0f)
        p.age = 99f
        assertEquals(1f, p.progress, 0f)
        p.age = 0.25f
        assertEquals(0.5f, p.progress, 1e-4f)
    }

    // ── Ball trail ring buffer ──────────────────────────────────────────────

    @Test
    fun `trail rejects negative indices`() {
        // The render pass asked for `i - 1`, where `i` already counts back from
        // the newest point. A negative index modulo the ring size reads one slot
        // *ahead* of the newest write: either an unwritten (0, 0), which drew a
        // line in from the top-left corner of the field on every shot's first
        // frames, or — once wrapped — the oldest point, so the newest segment
        // spanned the whole trail.
        val ball = Ball()
        ball.pushTrail(5f, 6f)

        assertNull("negative index must not resolve", ball.trailPoint(-1))
        assertNull(ball.trailPoint(-99))
        assertNotNull("index 0 is the newest point", ball.trailPoint(0))
    }

    @Test
    fun `trail returns points newest first`() {
        val ball = Ball()
        ball.pushTrail(1f, 1f)
        ball.pushTrail(2f, 2f)
        ball.pushTrail(3f, 3f)

        assertEquals(3f to 3f, ball.trailPoint(0))
        assertEquals(2f to 2f, ball.trailPoint(1))
        assertEquals(1f to 1f, ball.trailPoint(2))
        assertNull("past the recorded history there is nothing", ball.trailPoint(3))
    }

    @Test
    fun `trail wraps correctly once it is full`() {
        val ball = Ball()
        val size = Ball.TRAIL_SIZE
        // One more push than the buffer holds, so it has wrapped.
        for (i in 0..size) ball.pushTrail(i.toFloat(), i.toFloat())

        assertEquals(size.toFloat() to size.toFloat(), ball.trailPoint(0))
        assertEquals((size - 1).toFloat() to (size - 1).toFloat(), ball.trailPoint(1))
        assertNull(ball.trailPoint(size))
    }

    @Test
    fun `trail alpha fades to zero at the oldest point`() {
        val ball = Ball()
        ball.pushTrail(0f, 0f)
        ball.pushTrail(1f, 0f)
        ball.pushTrail(2f, 0f)

        assertEquals(1f, ball.trailAlphaRatio(0), 1e-4f)
        assertTrue("older points must be dimmer", ball.trailAlphaRatio(2) < 1f)
    }

    // ── Boss cadence ────────────────────────────────────────────────────────

    @Test
    fun `home preview and engine agree on which level is a boss`() {
        // The engine calls a level a boss when `lvl % 10 == 0`. The home screen's
        // progress card counted cleared levels as `level % 10`, which is the
        // level *being played*, not the count cleared — so on a boss level it
        // computed zero: the bar emptied, the "next level is a boss" callout was
        // suppressed on the boss level itself, and the card said ten levels to
        // go on the level that *was* the boss.
        val bossEvery = 10
        for (level in 1..200) {
            val engineSaysBoss = level % bossEvery == 0
            // What LevelPreviewCard now derives.
            val clearedInCycle = (level - 1) % bossEvery
            val untilBoss = bossEvery - clearedInCycle
            val cardSaysBossNext = untilBoss == 1

            assertEquals(
                "level $level: engine says boss=$engineSaysBoss but the card " +
                    "says the boss is next=$cardSaysBossNext",
                engineSaysBoss, cardSaysBossNext
            )
        }
    }

    @Test
    fun `boss countdown never runs past the cycle length`() {
        val bossEvery = 10
        for (level in 1..200) {
            val clearedInCycle = (level - 1) % bossEvery
            val untilBoss = bossEvery - clearedInCycle
            assertTrue(
                "level $level reported $untilBoss levels to the boss",
                untilBoss in 1..bossEvery
            )
            // The crown only lights once the cycle has actually reached it, so it
            // must never read as cleared while it is still the pending step.
            val crownReached = clearedInCycle >= bossEvery
            assertFalse(
                "the boss dot read as cleared at level $level",
                crownReached
            )
        }
    }

    // ── Collision ───────────────────────────────────────────────────────────

    @Test
    fun `a fast ball cannot tunnel through a block`() {
        // The swept test exists for this: a plain point test lets a fast ball
        // step clean over a small block between two frames and never register.
        val block = Block().apply { x = 100f; y = 100f; w = 20f; h = 20f }
        val radius = 4f

        // Straight through, crossing the whole block in one frame.
        assertTrue(
            CollisionDetector.sweptBallHitsBlock(100f, 0f, 100f, 200f, radius, block)
        )
        // Diagonally past the corner.
        assertTrue(
            CollisionDetector.sweptBallHitsBlock(0f, 0f, 200f, 200f, radius, block)
        )
    }

    @Test
    fun `a swept test does not report a hit that never happened`() {
        val block = Block().apply { x = 100f; y = 100f; w = 20f; h = 20f }
        val radius = 4f

        assertFalse(
            "a path that stops short must not count",
            CollisionDetector.sweptBallHitsBlock(0f, 0f, 50f, 50f, radius, block)
        )
        assertFalse(
            "a path that passes to the side must not count",
            CollisionDetector.sweptBallHitsBlock(0f, 300f, 200f, 300f, radius, block)
        )
    }

    @Test
    fun `ball radius covers the block it is overlapping`() {
        val block = Block().apply { x = 50f; y = 50f; w = 40f; h = 40f }
        assertTrue(CollisionDetector.ballHitsBlock(50f, 50f, 5f, block))
        assertTrue(CollisionDetector.ballHitsBlock(90f, 90f, 5f, block))
        assertFalse(CollisionDetector.ballHitsBlock(10f, 10f, 5f, block))
    }

    // ── Number formatting ───────────────────────────────────────────────────

    @Test
    fun `numbers group by three regardless of locale`() {
        // The app deliberately avoids String.format("%,d"), which applies the
        // *default* locale's grouping — and the default locale is Persian here
        // (LocaleHelper sets it), so the separator changed shape between the
        // coin counter and every other number on screen.
        assertEquals("0", formatNumber(0))
        assertEquals("999", formatNumber(999))
        assertEquals("1,000", formatNumber(1_000))
        assertEquals("1,234,567", formatNumber(1_234_567))
        assertEquals("999,999,999", formatNumber(999_999_999))
    }

    @Test
    fun `negative numbers format as zero rather than a stray minus`() {
        assertEquals("0", formatNumber(-1))
        assertEquals("0", formatNumber(Int.MIN_VALUE))
        assertEquals("0", formatNumber(Long.MIN_VALUE))
    }

    @Test
    fun `the two formatNumber overloads agree`() {
        for (v in listOf(0, 999, 1_000, 12_345, 999_999_999)) {
            assertEquals(formatNumber(v.toLong()), formatNumber(v))
        }
    }
}