package ir.amiraligrr.blockboom.data

import android.content.Context
import ir.amiraligrr.blockboom.R
import kotlin.math.ceil
import kotlin.math.pow

enum class UpgradeId {
    DAMAGE,
    FIRE_RATE,
    MULTI_BALL,
    PIERCE,
    MAGNET,
    BULLET_SPEED
}

/**
 * Rules and pricing for the six upgrades. Current level is stored inside
 * [SaveManager]; every purchase writes back immediately (persisted).
 */
class UpgradeManager(
    private val context: Context,
    private val save: SaveManager
) {

    data class UpgradeDef(
        val id: UpgradeId,
        val nameRes: Int,
        val icon: String,
        val baseCost: Long,
        val maxLevel: Int,
        /**
         * A flat price per level, in coins, overriding the pricing curve.
         *
         * Only DAMAGE uses it. Its price is a *promise about how far into the
         * game it lands* — "you will not see this again for a hundred levels" —
         * and a curve that multiplies by `level^COST_GROWTH` and a global
         * multiplier cannot express that: two levels of it would be 4,000 and
         * 13,600, and the number the player reads on the card would not be the
         * number they were asked for.
         *
         * The premium discount still applies, so a flat price is a price, not a
         * tax.
         */
        val flatCost: Long? = null,
        /**
         * The level the player must have reached before this is buyable.
         *
         * A lock, not a level-0 state: a track the player has never started
         * cannot be "un-locked" retroactively, and the three tracks that make a
         * late cannon what it is (ball count, pierce, fire rate) are exactly the
         * ones that flatten the first hour if they are all affordable at once.
         * Buying is gated; levels already owned are never touched, and
         * [clampLevel] still caps them at [maxLevel] — so an existing save keeps
         * everything it had and simply stops being able to add to it.
         */
        val unlockLevel: Int = 1
    )

/**
     * The upgrade catalogue.
     *
     * Every number here was chosen by simulating a player who buys the cheapest
     * affordable upgrade at every level against the real coin faucets, not by
     * feel. See the companion model in the commit for this change; the three
     * numbers that carry the whole design are called out below.
     *
     * ── Why MULTI_BALL is capped at 5 ─────────────────────────────────────
     * It was 12, and balls per volley is the single strongest stat in the game:
     * twelve balls off one cannon at full rate is a stream wide enough to erase
     * whatever it is pointed at, and it made the other five upgrades irrelevant
     * (see PIERCE below for how that compounded).
     *
     * Five is the point where a full volley still needs aiming — the fan spans
     * about 16°, so the edges of a crowded board are still yours to choose. Past
     * that the player stops choosing a target and starts pointing at a region,
     * which is the moment the game stops being played and starts being watched.
     *
     * ── Why PIERCE is capped at 7 ────────────────────────────────────────
     * Pierce multiplies everything else: it is "hits per ball", so it is
     * multiplied by fire rate, ball count *and* damage. At its old cap of 10 it
     * was carrying about 95% of a maxed build's damage output while DAMAGE —
     * which reads as the most obvious stat in the shop — was carrying well under
     * 1%. That is the definition of a solved puzzle: one purchase does the work
     * and the rest are decoration.
     *
     * Seven keeps pierce strong (eight hits per ball) without letting it
     * subsume the other tracks.
     *
     * ── Why DAMAGE is capped at 2 ─────────────────────────────────────────
     * `cannon.damage` is that level 1:1. It was 20 once, and 20 damage made
     * every block on the board die to a single volley, so the level was over
     * before it could be read and there was nothing left to aim at. It was then
     * 3, which was low enough to be fair and high enough to matter — a third
     * level for a few hundred coins was a flat +33% on every hit in the game,
     * bought in the first ten minutes, and it flattened the HP curve from level
     * 8 onwards.
     *
     * Two levels, at 4,000 apiece, is the point where raw damage is a milestone
     * the player reaches *into* the game for. The remaining tracks carry the
     * progression, which is why they are the deepest upgrades in the game — and
     * why three of them are gated behind a level rather than available on the
     * first screen.
     */
    val defs: List<UpgradeDef> = listOf(
        UpgradeDef(
            UpgradeId.DAMAGE, R.string.upgrade_damage, "💥", 40, MAX_DAMAGE,
            // 4,000 flat. See [UpgradeDef.flatCost] — this one is a milestone,
            // not a slider.
            flatCost = DAMAGE_LEVEL_PRICE
        ),
        UpgradeDef(
            UpgradeId.FIRE_RATE, R.string.upgrade_fire_rate, "🔫", 60, MAX_FIRE_RATE,
            unlockLevel = FIRE_RATE_UNLOCK_LEVEL
        ),
        UpgradeDef(
            UpgradeId.MULTI_BALL, R.string.upgrade_multi_ball, "🎱", 90, MAX_MULTI_BALL,
            unlockLevel = MULTI_BALL_UNLOCK_LEVEL
        ),
        UpgradeDef(
            UpgradeId.PIERCE, R.string.upgrade_pierce, "🏹", 140, MAX_PIERCE,
            unlockLevel = PIERCE_UNLOCK_LEVEL
        ),
        UpgradeDef(UpgradeId.MAGNET, R.string.upgrade_magnet, "🧲", 70, MAX_MAGNET),
        UpgradeDef(UpgradeId.BULLET_SPEED, R.string.upgrade_bullet_speed, "🚀", 50, MAX_BULLET_SPEED)
    )

    /**
     * True when [id] cannot be bought yet because the player has not reached its
     * level.
     *
     * Only ever a *purchase* gate. Levels already owned above the lock are still
     * owned, still counted by the cannon, and still clamped to the cap — a lock
     * on a save written before the lock existed would otherwise silently rob a
     * player of upgrades they paid for.
     */
    fun isLocked(id: UpgradeId): Boolean = save.currentLevel < def(id).unlockLevel

    /** The level the player must reach before [id] unlocks, or 0 if it is open. */
    fun unlockLevelOf(id: UpgradeId): Int = def(id).unlockLevel

    fun def(id: UpgradeId): UpgradeDef = defs.first { it.id == id }

    /**
     * [id]'s level, forced into `0..[maxLevelOf]`.
     *
     * This is what gameplay should read. It is a no-op for every player whose
     * save is already inside the current caps.
     */
    fun clampedLevelOf(id: UpgradeId): Int = clampLevel(id, levelOf(id))

    fun levelOf(id: UpgradeId): Int = when (id) {
        UpgradeId.DAMAGE -> save.upgradeDamage
        UpgradeId.FIRE_RATE -> save.upgradeFireRate
        UpgradeId.MULTI_BALL -> save.upgradeMultiBall
        UpgradeId.PIERCE -> save.upgradePierce
        UpgradeId.MAGNET -> save.upgradeMagnet
        UpgradeId.BULLET_SPEED -> save.upgradeBulletSpeed
    }

    fun valueFor(id: UpgradeId): Int = levelOf(id)

    fun isMaxed(id: UpgradeId): Boolean = levelOf(id) >= def(id).maxLevel

    /**
     * What the upgrade costs, after the global multiplier and any premium
     * discount.
     *
     * The price is `base * level^COST_GROWTH * COST_MULTIPLIER`. Both constants
     * are set from simulation rather than from feel, because the curve has to do
     * two jobs at once: keep every individual level affordable, and make the
     * *last* level of every track cost enough that the build is still unfinished
     * when the player is deep into the game.
     *
     * Measured against the real faucets (roughly 1,275 coins per level once the
     * board reaches its full size), the current settings put a full six-track max
     * at level ~304 for a free player and level ~109 for premium. At level 100 a
     * free player owns about 63% of the levels — which sounds high until you
     * notice those are the cheap ones; the remaining 37% is where the money is.
     *
     * Monetisation model: the free grind is deliberately slower and premium is
     * sold as SPEED, not as access. Nothing is locked behind payment — every
     * upgrade is buyable with coins — but a premium player reaches the same build
     * roughly 2.7x sooner because they pay less AND earn more.
     *
     * A level-0 upgrade is free, which is correct: it is the "own it at all"
     * purchase and charging for it made the first PIERCE/MAGNET level look
     * broken.
     */
    fun costOf(id: UpgradeId): Long {
        if (isMaxed(id)) return Long.MAX_VALUE
        val level = levelOf(id)
        return ceil(rawPrice(id, level) * priceFactor()).toLong()
    }

    /**
     * The price a non-premium player pays, used to show a struck-through
     * "was" figure next to the discounted premium price.
     */
    fun listPriceOf(id: UpgradeId): Long {
        if (isMaxed(id)) return Long.MAX_VALUE
        return ceil(rawPrice(id, levelOf(id))).toLong()
    }

    /**
     * Undiscounted price of moving [id] from [level] to `level + 1`.
     *
     * Both price methods route through here so the card's struck-through figure
     * and the price actually charged can never disagree — they used to repeat
     * the same formula in two places.
     *
     * A level-0 upgrade is free (see [isUnowned]), so this returns 0 rather
     * than the base cost.
     */
    private fun rawPrice(id: UpgradeId, level: Int): Double {
        // A flat price applies to every level including the first one bought.
        // The "level 0 is free" rule below exists for PIERCE and MAGNET, which
        // start unowned; DAMAGE starts at 1, so it never reaches here at 0, and
        // if it somehow did, charging it is the correct behaviour anyway.
        def(id).flatCost?.let { return it.toDouble() }
        if (level <= 0) return 0.0
        val base = def(id).baseCost.toDouble()
        return base * (level.toDouble().pow(COST_GROWTH)) * COST_MULTIPLIER
    }

    private fun priceFactor(): Double = if (save.isPremium) PREMIUM_DISCOUNT else 1.0

    /** True when this player is getting the premium discount on upgrades. */
    fun hasPremiumDiscount(): Boolean = save.isPremium

    /** Returns true when purchased and applied. */
    fun buy(id: UpgradeId): Boolean {
        if (isMaxed(id)) return false
        if (isLocked(id)) return false
        val cost = costOf(id)
        if (save.coins < cost) return false
        save.coins = save.coins - cost
        increment(id)
        ProgressState.sync(save)
        return true
    }

    /**
     * Grants one level for free — used by the rewarded-ad reward.
     * Bypasses the coin check but not the max-level cap.
     */
    fun grantFreeLevel(id: UpgradeId): Boolean {
        if (isMaxed(id)) return false
        if (isLocked(id)) return false
        increment(id)
        ProgressState.sync(save)
        return true
    }

    private fun increment(id: UpgradeId) {
        when (id) {
            UpgradeId.DAMAGE -> save.upgradeDamage = save.upgradeDamage + 1
            UpgradeId.FIRE_RATE -> save.upgradeFireRate = save.upgradeFireRate + 1
            UpgradeId.MULTI_BALL -> save.upgradeMultiBall = save.upgradeMultiBall + 1
            UpgradeId.PIERCE -> save.upgradePierce = save.upgradePierce + 1
            UpgradeId.MAGNET -> save.upgradeMagnet = save.upgradeMagnet + 1
            UpgradeId.BULLET_SPEED -> save.upgradeBulletSpeed = save.upgradeBulletSpeed + 1
        }
    }

    /**
     * Whether the upgrade has never been bought.
     *
     * PIERCE and MAGNET start at 0, and the card used to render that literally:
     * "سوراخ‌کنی 0/10" and "آهنربا 0/15" with a magnet radius of 0. That reads as
     * a broken or zero-value item rather than "not owned yet", and it is the
     * reason the first levels of the shop looked wrong.
     */
    fun isUnowned(id: UpgradeId): Boolean = levelOf(id) <= 0

    /**
     * What the player has right now, e.g. "Damage 3".
     *
     * For an unowned upgrade this shows the value the first purchase grants (1)
     * rather than a literal 0, so the card always communicates something real.
     *
     * Reads through [clampedLevelOf], so an install that predates a lowered cap
     * cannot render a value the cannon will not actually use.
     */
    fun currentEffect(id: UpgradeId): String {
        val level = clampedLevelOf(id)
        return effectText(id, if (level <= 0) 1 else level)
    }

    /** Human-readable effect after buying the next level. */
    fun nextEffect(id: UpgradeId): String =
        effectText(id, (clampedLevelOf(id) + 1).coerceAtMost(maxLevelOf(id)))

    /**
 * Shots per second for a given FIRE_RATE level.
 *
 * Lives in the companion, and is read by [GameEngine.refreshCannonStats],
 * because it used to be duplicated: the engine computed `2.6 + (level-2)*0.55`
 * and the shop computed `2 + (level-2)*0.5`. Two copies of one formula is a bug
 * waiting for the day someone tunes one of them.
 */
fun fireRateFor(level: Int): Float = UpgradeManager.fireRateFor(level)

    private fun effectText(id: UpgradeId, level: Int): String = when (id) {
        UpgradeId.DAMAGE -> context.getString(R.string.effect_damage, level.toString())
        // These two used to re-derive the stat here, from numbers that had since
        // changed in GameEngine: the card promised 8.5 shots/s at max while the
        // cannon fired 9.75, and a magnet 20x wider than it actually was. Both now
        // read the engine's own numbers, so the shop cannot describe a different
        // cannon from the one the player is holding.
        UpgradeId.FIRE_RATE -> context.getString(
            R.string.effect_fire_rate, UpgradeManager.formatFloat(UpgradeManager.fireRateFor(level))
        )
        UpgradeId.MULTI_BALL -> context.getString(R.string.effect_multi_ball, level.toString())
        UpgradeId.PIERCE -> context.getString(R.string.effect_pierce, level.toString())
        UpgradeId.MAGNET -> context.getString(
            R.string.effect_magnet,
            (level * MAGNET_DP_PER_LEVEL).toInt().toString()
        )
        UpgradeId.BULLET_SPEED -> context.getString(R.string.effect_bullet_speed, level.toString())
    }

    companion object {
        /** Base shots per second at FIRE_RATE level 2, in shots/s. */
        const val FIRE_RATE_BASE = 2.6f

        /** How much each FIRE_RATE level adds, in shots/s. */
        const val FIRE_RATE_PER_LEVEL = 0.55f

        /**
         * Shots per second for a given FIRE_RATE level.
         *
         * Read by the shop *and* by the engine, so the two can never describe
         * different cannons.
         */
        fun fireRateFor(level: Int): Float =
            FIRE_RATE_BASE + (level - 2).coerceAtLeast(0) * FIRE_RATE_PER_LEVEL

        /** "7" for 7.0, "7.15" for 7.15 — no trailing ".0" on a whole number. */
        fun formatFloat(value: Float): String =
            if (value % 1f == 0f) value.toInt().toString()
            else "%.2f".format(value).replace(Regex("0+$"), "").replace(Regex("\\.$"), "")

        /**
         * Pixels of magnet radius per MAGNET level, before density scaling.
         *
         * The engine draws in px and the card shows dp, so both sides convert
         * through this instead of each naming its own number — which is exactly
         * how the card came to advertise 20 while the engine used 22.
         */
        const val MAGNET_DP_PER_LEVEL = 22f
        /**
         * The cap on each track.
         *
         * These are the single source of truth. [GameEngine.refreshCannonStats]
         * clamps through them because a stored level can be *above* the cap on an
         * install that predates a balance change: nothing rewrites saved levels
         * when a max drops, the shop only hides the row behind "MAX". Reading the
         * raw stored number meant players kept the old top end — twelve balls and
         * eleven pierce — long after the catalogue said five and seven. Read the
         * cap, never the stored level, when building a stat.
         */
        /**
         * DAMAGE caps at 2, and one level of it costs 4,000.
         *
         * It was 3, and `cannon.damage` *is* that number 1:1 — so the third level
         * was a flat +33% to every hit in the game, available for a few hundred
         * coins, and it flattened the HP curve at level 8 onwards: past it, a
         * block needed about two shots whatever the level was.
         *
         * Two levels plus a 4,000 price puts the other half of the output on
         * FIRE_RATE, MULTI_BALL and PIERCE, which are the tracks that keep
         * costing, and it makes raw damage something a player reaches *into* the
         * game for instead of something they tick off in the first ten minutes.
         */
        const val MAX_DAMAGE = 2

        /** What one level of DAMAGE costs, flat. See [UpgradeDef.flatCost]. */
        const val DAMAGE_LEVEL_PRICE = 3_000L

        /**
         * Level gates on the three strongest tracks.
         *
         * Ordered by how much they change the cannon rather than by price:
         * fire rate first (level 2), ball count second (level 4), pierce last
         * (level 8) because pierce is multiplied by everything else and so is
         * the level that decides whether the other two were worth taking.
         *
         * These were 3/6/12. A new player reached level 12 with roughly one
         * affordable purchase behind them, so the shop spent its first hour
         * looking locked. Opening the tracks earlier gives every level-up
         * something concrete to spend on; the caps and the power curve still
         * make the build a long-term goal.
         */
        const val FIRE_RATE_UNLOCK_LEVEL = 2
        const val MULTI_BALL_UNLOCK_LEVEL = 4
        const val PIERCE_UNLOCK_LEVEL = 8
        const val MAX_FIRE_RATE = 15
        const val MAX_MULTI_BALL = 5
        const val MAX_PIERCE = 7
        const val MAX_MAGNET = 15
        const val MAX_BULLET_SPEED = 18

        /** The cap on [id], without needing a manager instance. */
        fun maxLevelOf(id: UpgradeId): Int = when (id) {
            UpgradeId.DAMAGE -> MAX_DAMAGE
            UpgradeId.FIRE_RATE -> MAX_FIRE_RATE
            UpgradeId.MULTI_BALL -> MAX_MULTI_BALL
            UpgradeId.PIERCE -> MAX_PIERCE
            UpgradeId.MAGNET -> MAX_MAGNET
            UpgradeId.BULLET_SPEED -> MAX_BULLET_SPEED
        }

        /**
         * Forces a stored [level] into the range gameplay should actually use.
         *
         * No-op unless a cap was lowered after this save was written.
         */
        fun clampLevel(id: UpgradeId, level: Int): Int =
            level.coerceIn(0, maxLevelOf(id))

        /**
         * Global price multiplier.
         *
         * 1.35, down from 1.8. Against the real faucets (roughly 1,275 coins
         * per level) the old curve put a full six-track build at about 304
         * levels. That is a correct long tail for a mature account and a wall
         * for everyone else: the shop's useful purchases were spread so far
         * apart that a new player could not feel a single one of them.
         *
         * This curve lands a full build at roughly 114 levels free and about
         * 68 premium. It is still a months-long goal, but every early level
         * now buys something visible.
         *
         * The premium discount still applies on top.
         */
        const val COST_MULTIPLIER = 1.35

        /**
         * How steeply each successive level raises the price.
         *
         * The price is `base * level^COST_GROWTH * COST_MULTIPLIER`.
         *
         * This is a power curve, not a linear one. The shape matters more than
         * the value: a linear ramp hands a player 60% of the build by level 100
         * and then has nothing left to say; a power ramp keeps every level
         * individually affordable — the first level of the deepest track costs
         * a few hundred coins — while making the final levels of each track
         * cost more than all the early ones together. That is what turns
         * buying an upgrade from a chore into a decision.
         *
         * 1.65, down from 1.85, is the other half of the rebalance. Lowering
         * only the multiplier makes late levels cheap in absolute terms;
         * lowering the exponent as well is what shortens the tail without
         * flattening the early game.
         */
        const val COST_GROWTH = 1.65

        /**
         * Premium pays 60% of the multiplied price, and earns [SaveManager] coin
         * multiplier on top. Combined, a premium player progresses about 2.7x
         * faster: full max at level ~109 against ~304 for free.
         */
        const val PREMIUM_DISCOUNT = 0.6
    }
}
