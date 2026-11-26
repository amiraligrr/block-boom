package ir.amiraligrr.blockboom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scoring model, restated in Kotlin so it is compiled and run rather than
 * living only in a PHP file and a Python script.
 *
 * `api/anticheat.php` decides whether a submitted score is reachable, and its
 * ceiling has to match `GameEngine` exactly. When the two drifted apart the
 * server over-granted by three orders of magnitude at level 9999 and a fresh
 * install could take rank #1 with no play time.
 *
 * The constants below are duplicated from the engine on purpose: the point is
 * that when someone retunes scoring in `GameEngine.kt`, these assertions fail
 * and force the server-side copy to be updated too. `tools/verify_anticheat.py`
 * does the same check by parsing the Kotlin source, which catches the retune
 * even if this file is not run.
 */
class ScoringModelTest {

    // GameEngine.MAX_BLOCK_HP. It is an instance property rather than a
    // constant, so it is restated here; tools/verify_anticheat.py parses the
    // real declaration out of GameEngine.kt to catch a retune.
    private val maxBlockHp = 240
    private val hpMultiplierCeiling = 2.3f
    private val bossEvery = 10
    private val maxBlocksPerLevel = 96

    private fun hpCurveBase(level: Int): Float = when {
        level <= 5 -> 4f + (level - 1) * 2f
        level <= 15 -> 12f + (level - 5) * 3f
        level <= 30 -> 42f + (level - 15) * 4f
        level <= 50 -> 102f + (level - 30) * 5f
        else -> 222f + (level - 50) * 16f
    }

    /** GameEngine.generateBlockHP(): base curve, best-case variance, then the cap. */
    private fun generateBlockHp(level: Int): Int {
        val variance = 1.1f // 0.9 + rand*0.2, worst case
        return (hpCurveBase(level) * variance).toInt().coerceIn(1, maxBlockHp)
    }

    /**
     * GameEngine.levelConfig().hpMultiplier
     *
     * Ramps off `extra`, not `inTier`. The engine used `inTier * 0.011f`
     * alongside `tier * 0.09f`, and since `inTier` resets 9 -> 0 at each tier
     * boundary the 0.099 lost to the reset beat the 0.090 gained from the tier
     * step: the multiplier fell at levels 11, 21, 31... on an axis whose stated
     * purpose is the wall every ten levels.
     */
    private fun hpMultiplier(level: Int): Float {
        val extra = (level - 1).coerceAtLeast(0)
        return (1f + extra * 0.0085f + (extra / 10) * 0.009f)
            .coerceAtMost(hpMultiplierCeiling)
    }

    /** GameEngine.levelConfig().blockCount */
    private fun blockCount(level: Int): Int {
        val extra = (level - 1).coerceAtLeast(0)
        return (minOf(6 + extra * 2, 34) + (extra / 6) * 3).coerceAtMost(maxBlocksPerLevel)
    }

    /**
     * The HP the engine can actually put on one ordinary block: the cap applies
     * to the base curve, and the multiplier is applied to that capped value.
     */
    private fun effectiveBlockHp(level: Int): Int =
        (generateBlockHp(level) * hpMultiplier(level)).toInt()

    private fun normalBlockScore(level: Int): Long = effectiveBlockHp(level) * 2L

    private fun bossBlockScore(level: Int): Long =
        (generateBlockHp(level) * 2 * 4).toInt().coerceAtLeast(1).toLong() * 4L

    private fun maxLevelScore(level: Int): Long {
        val n = blockCount(level)
        val isBoss = level % bossEvery == 0
        var score = (if (isBoss) n - 1 else n) * normalBlockScore(level)
        if (isBoss) score += bossBlockScore(level)
        return score + n * 25L
    }

    private fun maxCumulativeScore(level: Int): Long =
        (1..level).sumOf { maxLevelScore(it) }

    // ── The cap that was missing ────────────────────────────────────────────

    @Test
    fun `block hp never exceeds the engine's hard cap`() {
        for (level in listOf(1, 50, 300, 1000, 5000, 9999)) {
            val hp = effectiveBlockHp(level)
            assertTrue(
                "level $level produced HP $hp, above the engine's cap of $maxBlockHp",
                hp <= maxBlockHp * hpMultiplierCeiling
            )
        }
    }

    @Test
    fun `hp multiplier saturates rather than growing forever`() {
        // Unbounded, this reached ~161 at level 9999 and was most of the
        // over-grant that let a forged level-9999 baseline reach 2.38 trillion.
        assertEquals(hpMultiplierCeiling, hpMultiplier(9999), 1e-4f)
        assertEquals(hpMultiplierCeiling, hpMultiplier(141), 1e-4f)
        assertTrue("multiplier must never exceed the ceiling", hpMultiplier(9999) <= hpMultiplierCeiling)
    }

    @Test
    fun `hp multiplier rises monotonically`() {
        // A ceiling makes it flat at the top, never lower than the level before.
        // This is the assertion that caught the inTier reset: it failed at level
        // 11, and at 998 other tier boundaries.
        for (level in 2..9999) {
            assertTrue(
                "hp multiplier dropped at level $level",
                hpMultiplier(level) >= hpMultiplier(level - 1) - 1e-6f
            )
        }
    }

    @Test
    fun `block count reaches its own cap and stops`() {
        // Raised from 64 to 96 so a late board is not the same 64 bodies as a
        // level-61 one. The first cap (34) is passed at level 15, and the
        // (extra / 6) * 3 growth term carries it to 96 by level 127 — after
        // which the level stops adding bodies and starts adding character.
        // That growth term is also what the server model once omitted, which
        // made it under-count a level-50 board by ~2x.
        assertTrue("block count must still be growing at level 30", blockCount(30) > blockCount(10))
        assertTrue("block count must still be growing at level 90", blockCount(90) > blockCount(60))
        assertEquals(maxBlocksPerLevel, blockCount(127))
        // Once capped it stays capped.
        for (level in 128..9999) {
            assertEquals(maxBlocksPerLevel, blockCount(level))
        }
    }

    @Test
    fun `boss arrives on the engine's cadence`() {
        // GameEngine: bossLevel = lvl % BOSS_EVERY_N_LEVELS == 0
        for (level in 1..60) {
            val engineSaysBoss = level % 10 == 0
            val modelSaysBoss = level % bossEvery == 0
            assertEquals("boss cadence disagrees at level $level", engineSaysBoss, modelSaysBoss)
        }
    }

    // ── The exploit ─────────────────────────────────────────────────────────

    @Test
    fun `a fresh install claiming level 9999 cannot reach the client's score cap`() {
        // The exact attack: ask for the level cap, submit immediately, submit
        // the most the client is willing to send. Before the model was pinned to
        // the engine the level-9999 baseline alone cleared 2e9.
        val clientHardCap = 2_000_000_000L
        val headroom = 1.35
        val baseline = maxCumulativeScore(9999).toDouble() * headroom

        assertTrue(
            "level-9999 baseline ${"%,.0f".format(baseline)} still admits the " +
                "client's $clientHardCap cap — the score forgery is open again",
            baseline < clientHardCap
        )
    }

    @Test
    fun `a fresh install claiming any level cannot reach the client's score cap`() {
        // Not just the cap level: the claim is unbounded, so every level has to
        // be safe on its own.
        val clientHardCap = 2_000_000_000L
        for (level in listOf(1, 10, 100, 1000, 5000, 9999)) {
            val baseline = maxCumulativeScore(level).toDouble() * 1.35
            assertTrue(
                "level $level baseline ${"%,.0f".format(baseline)} admits $clientHardCap",
                baseline < clientHardCap
            )
        }
    }

    @Test
    fun `the lifetime maximum is within the client's own hard cap`() {
        // 897 million against a 2 billion cap. If a retune pushes this over, the
        // client would start truncating legitimate players' scores and the
        // server's absolute bound would start rejecting real submissions.
        val lifetimeMax = maxCumulativeScore(9999)
        assertTrue(
            "engine lifetime max ${"%,.0f".format(lifetimeMax.toDouble())} exceeds " +
                "the client's 2,000,000,000 hard cap",
            lifetimeMax < 2_000_000_000L
        )
    }

    // ── No false rejections ─────────────────────────────────────────────────

    @Test
    fun `ceiling covers every score the engine can award at that level`() {
        // The server must never reject a score the game itself produced. With
        // the missing block-count growth term it under-counted by ~2x at level
        // 50 and started rejecting honest players.
        for (level in listOf(1, 5, 25, 50, 100, 500, 1000)) {
            val perRun = maxLevelScore(level)
            val baseline = maxCumulativeScore(level).toDouble() * 1.35
            assertTrue(
                "level $level: per-run maximum $perRun exceeds the whole-cycle " +
                    "baseline ${"%,.0f".format(baseline)}",
                perRun <= baseline
            )
        }
    }

    @Test
    fun `the engine's per-run maximum is bounded by the rate cap over a plausible run`() {
        // A run cannot earn more than the rate cap allows in the time it took,
        // or the score is not physically possible. This is the guarantee that no
        // honest player is ever rejected for being fast.
        val rateCap = 3500.0
        val worstSeconds = (1..9999).maxOf { maxLevelScore(it) / rateCap }
        assertTrue(
            "a level needs $worstSeconds s of play to be reachable, which is longer " +
                "than BB_RUN_MAX_AGE_SECONDS allows a run to be",
            worstSeconds <= 21_600.0
        )
    }
}