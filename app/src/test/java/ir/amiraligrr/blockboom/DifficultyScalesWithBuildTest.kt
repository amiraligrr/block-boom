package ir.amiraligrr.blockboom

import ir.amiraligrr.blockboom.data.UpgradeId
import ir.amiraligrr.blockboom.data.UpgradeManager
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Difficulty has to be a function of the player's *build*, not only of the level
 * number.
 *
 * This is the invariant that was missing, and it is the one that decides whether
 * the upgrade track is a progression or a difficulty slider pointed at the
 * player. Measured on the old curve, difficulty grew by exactly 8.0x between
 * level 200 and level 9999 *for every build alike*: a maxed cannon has ~300x the
 * output of a starting one, and all of it was handed straight back as "this
 * level is easy". Buying upgrades did not make the player stronger, it made the
 * game stop being a game.
 *
 * The mechanism is armour, because armour is the one axis that costs the player
 * something real rather than asking them to read a bigger number, and because it
 * is score-neutral — `scoreValue` is assigned before plating is applied, so the
 * anti-cheat ceiling and the leaderboard are untouched by any of this.
 */
class DifficultyScalesWithBuildTest {

    // ── The engine's knobs, restated ────────────────────────────────────────

    private val armourLevelK = 4.2f
    private val armourLevelExp = 1.2f
    private val armourBuildFeedback = 0.05f
    private val armourBuildExp = 0.8f
    private val maxBlockHp = 240
    private val hpMultiplierCeiling = 2.3f
    private val fragmentArmourDivisor = 3

    private fun hpBase(level: Int): Float = when {
        level <= 5 -> 4f + (level - 1) * 2f
        level <= 15 -> 12f + (level - 5) * 3f
        level <= 30 -> 42f + (level - 15) * 4f
        level <= 50 -> 102f + (level - 30) * 5f
        else -> 222f + (level - 50) * 16f
    }

    private fun hpMultiplier(level: Int): Float {
        val extra = (level - 1).coerceAtLeast(0)
        return (1f + extra * 0.0085f + (extra / 10) * 0.009f)
            .coerceAtMost(hpMultiplierCeiling)
    }

    /** damage, fire-rate level, ball count, pierce level. */
    private fun powerIndex(build: IntArray): Float {
        val start = UpgradeManager.FIRE_RATE_BASE * 1f * 1f * 1f
        val now = UpgradeManager.fireRateFor(build[1]) * build[2] * build[0] *
            (build[3] + 1)
        return (now / start).coerceAtLeast(1f)
    }

    private fun buildArmourFactor(build: IntArray): Float =
        1f + armourBuildFeedback * (powerIndex(build) - 1f).pow(armourBuildExp)

    private fun armourFor(level: Int, build: IntArray): Int {
        val extra = (level - 1).coerceAtLeast(0)
        if (extra < 24) return 0
        val levelArmour = ((extra - 24f) / armourLevelK).pow(armourLevelExp)
        return (levelArmour * buildArmourFactor(build)).toInt()
    }

    /** Seconds to destroy one ordinary block. */
    private fun timePerBlock(level: Int, build: IntArray): Float {
        val rate = UpgradeManager.fireRateFor(build[1])
        val dps = rate * build[2] * build[0] * (build[3] + 1)
        return ehpPerBlock(level, build) / dps
    }

    /** Effective HP of one ordinary block, splitting and plating included. */
    private fun ehpPerBlock(level: Int, build: IntArray): Float {
        val hp = (hpBase(level) * 1.1f).toInt().coerceIn(1, maxBlockHp) *
            hpMultiplier(level)
        val armour = armourFor(level, build).toFloat()
        if (armour <= 0f) return hp * 2f // parent + fragments
        val plating = armour * 0.55f                                  // share armed
        val onFragments = plating / fragmentArmourDivisor            // plating survives
        return hp * 2f + plating + onFragments
    }

    // Builds: damage, fire-rate level, ball count, pierce level.
    private val maxed = intArrayOf(2, 15, 5, 7)
    private val fewUpgrades = intArrayOf(2, 6, 3, 2)
    private val starting = intArrayOf(1, 2, 1, 0)

    // ── The invariant ───────────────────────────────────────────────────────

    @Test
    fun `a maxed cannon faces a deeper board than a barely-upgraded one`() {
        // The whole point. Before this existed the two were identical at every
        // level beyond 200.
        val maxedAt = ehpPerBlock(2000, maxed)
        val fewAt = ehpPerBlock(2000, fewUpgrades)
        val startingAt = ehpPerBlock(2000, starting)

        assertTrue(
            "a maxed cannon faces $maxedAt EHP but a 'few upgrades' player only " +
                "$fewAt — upgrades are being handed straight back as ease",
            maxedAt > fewAt
        )
        assertTrue(
            "build scaling is not reaching the starting cannon either",
            fewAt > startingAt
        )
    }

    @Test
    fun `the gap between a maxed and a weak cannon narrows but never inverts`() {
        // Two things have to hold at once, and they pull against each other:
        //
        //  - the maxed player must face a THICKER wall (more EHP per block),
        //    otherwise upgrades are being handed back as ease;
        //  - but the maxed player must still clear it much FASTER, otherwise the
        //    shop is selling nothing that changes how the game plays.
        //
        // So: wall ratio up, speed ratio still comfortably above 1.
        val depthWallRatios = listOf(100, 500, 2000, 9999).map { level ->
            level to ehpPerBlock(level, maxed) / ehpPerBlock(level, fewUpgrades)
        }

        // 1. The build is *always* read, never inverted: a maxed cannon meets a
        //    thicker wall than a weak one at every single level.
        for ((level, wallRatio) in depthWallRatios) {
            assertTrue(
                "level $level: a maxed cannon meets ${wallRatio}x the wall of a " +
                    "'few upgrades' player — the build is not being read",
                wallRatio > 1f
            )
            assertTrue(
                "level $level: wall ratio is $wallRatio, which means the two " +
                    "builds are indistinguishable and the shop sells nothing",
                wallRatio < 12f
            )
        }

        // 2. It matters *more* the deeper you go, which is the whole point.
        //    Base block HP does not depend on the build at all, so on a shallow
        //    board — where there is barely any plating yet — upgrades buy close
        //    to their full nominal value. That is the right way round: the shop
        //    has to matter where the player is spending coins on it. It is the
        //    late board, the one that used to be identical for everyone, that has
        //    to read the build.
        for (i in 1 until depthWallRatios.size) {
            val (prevLevel, prev) = depthWallRatios[i - 1]
            val (level, now) = depthWallRatios[i]
            assertTrue(
                "the wall a maxed build meets got *thinner* with depth: " +
                    "$prev at level $prevLevel, $now at level $level",
                now > prev
            )
        }
        val capWall = depthWallRatios.last().second
        assertTrue(
            "at the level cap a maxed build meets only ${capWall}x a weak one's " +
                "wall, which is too close to the old behaviour to matter",
            capWall >= 2.5f
        )

        // 3. Upgrades still buy real speed, everywhere.
        for (level in listOf(100, 500, 2000, 9999)) {
            val speedRatio = timePerBlock(level, fewUpgrades) / timePerBlock(level, maxed)
            assertTrue(
                "level $level: upgrades buy only a ${speedRatio}x speedup, which " +
                    "does not make them feel like upgrades",
                speedRatio > 2f
            )
            assertTrue(
                "level $level: upgrades buy a ${speedRatio}x speedup, which " +
                    "trivialises the level",
                speedRatio < 9f
            )
        }
    }

    @Test
    fun `armour is free before it has anything to scale`() {
        // Nothing to multiply before level 25, so the build factor cannot leak
        // difficulty into the first levels of the game.
        for (level in 1..24) {
            assertTrue(
                "level $level should have no armour, had ${armourFor(level, maxed)}",
                armourFor(level, maxed) == 0
            )
        }
    }

    @Test
    fun `armour never decreases as levels rise, for any build`() {
        for (build in listOf(maxed, fewUpgrades, starting)) {
            var previous = 0
            for (level in 1..3000) {
                val now = armourFor(level, build)
                assertTrue(
                    "armour dropped at level $level for build ${build.toList()}: " +
                        "$previous -> $now",
                    now >= previous
                )
                previous = now
            }
        }
    }

    @Test
    fun `difficulty growth from level 200 to the cap now favours the strong build`() {
        // The measurement that motivated all of this: the old curve grew 8.0x for
        // every build, so the *ratio* of growth between a maxed and a weak
        // player was 1.0. It must now be clearly above 1.
        fun growth(build: IntArray): Float =
            ehpPerBlock(9999, build) / ehpPerBlock(200, build)

        val maxedGrowth = growth(maxed)
        val fewGrowth = growth(fewUpgrades)
        assertTrue(
            "a maxed cannon's board grew ${maxedGrowth}x while a weak player's " +
                "grew ${fewGrowth}x — the curve is not reading the build",
            maxedGrowth > fewGrowth * 1.4f
        )
    }

    @Test
    fun `a maxed build is genuinely stronger than a weak one at the same level`() {
        // Sanity: the feedback exponent is below 1, so upgrades must still buy
        // real speed. Roughly 4x on a deep level is the target.
        val level = 2000
        val speedup = timePerBlock(level, fewUpgrades) / timePerBlock(level, maxed)
        assertTrue(
            "upgrades only buy a ${speedup}x speedup — the shop sells nothing",
            speedup > 2f
        )
        assertTrue(
            "upgrades buy a ${speedup}x speedup, which trivialises the level",
            speedup < 8f
        )
    }

    @Test
    fun `the upgrade caps are the ones the difficulty model assumes`() {
        // The model above reads the real caps. If one moves, the power index and
        // therefore every armour number moves with it, so pin them.
        assertTrue(
            "DAMAGE caps at ${UpgradeManager.MAX_DAMAGE}; the model assumes 2",
            UpgradeManager.MAX_DAMAGE == 2
        )
        assertTrue(
            "FIRE_RATE caps at ${UpgradeManager.MAX_FIRE_RATE}; the model assumes 15",
            UpgradeManager.MAX_FIRE_RATE == 15
        )
        assertTrue(
            "MULTI_BALL caps at ${UpgradeManager.MAX_MULTI_BALL}; the model assumes 5",
            UpgradeManager.MAX_MULTI_BALL == 5
        )
        assertTrue(
            "PIERCE caps at ${UpgradeManager.MAX_PIERCE}; the model assumes 7",
            UpgradeManager.MAX_PIERCE == 7
        )
    }

    @Test
    fun `a maxed build really does out-damage a starting one by the assumed margin`() {
        val ratio = powerIndex(maxed) / powerIndex(starting)
        // ~300x. If the tracks are retuned without revisiting the feedback
        // constant, this is the number that silently stops being true.
        assertTrue(
            "maxed power index is ${powerIndex(maxed)}x starting, " +
                "was assumed to be ~300x",
            ratio in 200f..500f
        )
    }
}