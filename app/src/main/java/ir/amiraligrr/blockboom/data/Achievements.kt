package ir.amiraligrr.blockboom.data

import ir.amiraligrr.blockboom.R

/**
 * Milestones, and the coins they pay.
 *
 * ── Why coins and not items ──────────────────────────────────────────────
 * Skins are the obvious thing to unlock and the wrong one here. They live in a
 * separate shop with its own currency path, they cost real money as well, and
 * they do nothing for the player who is stuck at level 30 with a cannon they
 * cannot afford to improve. Paying coins puts the reward into the exact loop
 * that is hard — the upgrade track — so a milestone is felt in the game rather
 * than admired in a menu.
 *
 * ── Why these thresholds ─────────────────────────────────────────────────
 * Every threshold is reachable inside one ordinary session on a current build,
 * or is a long-term total that any committed player crosses eventually. Nothing
 * here should need a deliberate grind: an achievement the player has to go out
 * of their way for is a chore wearing a trophy's clothes.
 *
 * The per-run ones are checked against a *run*, not a lifetime total, because
 * that is the moment they are earned and the moment they can be celebrated. The
 * lifetime ones are cumulative and unlock once.
 */
enum class Achievement(
    val id: String,
    val emoji: String,
    val titleRes: Int,
    val descRes: Int,
    val coins: Long,
    /** Lifetime stat this is measured against, or null for a per-run one. */
    val lifetimeMetric: StatMetric? = null,
    val threshold: Long = 0L
) {
    // ── Per-run ────────────────────────────────────────────────────────────
    FIRST_RUN(
        "first_run", "🎮",
        R.string.ach_first_run_title, R.string.ach_first_run_desc,
        coins = 300
    ),
    COMBO_10(
        "combo_10", "🔥",
        R.string.ach_combo_10_title, R.string.ach_combo_10_desc,
        coins = 400
    ),
    COMBO_25(
        "combo_25", "⚡",
        R.string.ach_combo_25_title, R.string.ach_combo_25_desc,
        coins = 900
    ),
    LEVEL_10(
        "level_10", "🚩",
        R.string.ach_level_10_title, R.string.ach_level_10_desc,
        coins = 500
    ),
    LEVEL_50(
        "level_50", "🎯",
        R.string.ach_level_50_title, R.string.ach_level_50_desc,
        coins = 1_500
    ),
    LEVEL_100(
        "level_100", "💎",
        R.string.ach_level_100_title, R.string.ach_level_100_desc,
        coins = 4_000
    ),
    RUN_SCORE_5K(
        "run_5k", "🌟",
        R.string.ach_run_5k_title, R.string.ach_run_5k_desc,
        coins = 700
    ),

    // ── Lifetime ───────────────────────────────────────────────────────────
    BLOCKS_1K(
        "blocks_1k", "🧱",
        R.string.ach_blocks_1k_title, R.string.ach_blocks_1k_desc,
        coins = 600, lifetimeMetric = StatMetric.BLOCKS, threshold = 1_000
    ),
    BLOCKS_25K(
        "blocks_25k", "🏗️",
        R.string.ach_blocks_25k_title, R.string.ach_blocks_25k_desc,
        coins = 5_000, lifetimeMetric = StatMetric.BLOCKS, threshold = 25_000
    ),
    BLOCKS_100K(
        "blocks_100k", "🌋",
        R.string.ach_blocks_100k_title, R.string.ach_blocks_100k_desc,
        coins = 20_000, lifetimeMetric = StatMetric.BLOCKS, threshold = 100_000
    ),
    RUNS_50(
        "runs_50", "🔁",
        R.string.ach_runs_50_title, R.string.ach_runs_50_desc,
        coins = 2_000, lifetimeMetric = StatMetric.RUNS, threshold = 50
    ),
    LEVELS_500(
        "levels_500", "📚",
        R.string.ach_levels_500_title, R.string.ach_levels_500_desc,
        coins = 8_000, lifetimeMetric = StatMetric.LEVELS, threshold = 500
    ),
    COMBO_LIFETIME_50(
        "combo_lifetime_50", "👑",
        R.string.ach_combo_lifetime_title, R.string.ach_combo_lifetime_desc,
        coins = 6_000, lifetimeMetric = StatMetric.BEST_COMBO, threshold = 50
    ),
    COINS_50K(
        "coins_50k", "💰",
        R.string.ach_coins_50k_title, R.string.ach_coins_50k_desc,
        coins = 3_000, lifetimeMetric = StatMetric.COINS, threshold = 50_000
    );

    /** The cumulative stat a lifetime achievement reads. */
    enum class StatMetric { RUNS, LEVELS, BLOCKS, SHOTS, BEST_COMBO, COINS }

    companion object {
        fun byId(id: String): Achievement? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Tracks which [Achievement]s have been unlocked and pays them out.
 *
 * ── Unlocking is checked, never granted ───────────────────────────────────
 * The caller passes the current run and lifetime numbers; this decides what is
 * newly satisfied. There is no "grant achievement" path, deliberately: an
 * achievement the game can award on request is one that can be awarded twice,
 * and the failure mode is paying coins that were never earned. Everything flows
 * through [collect], which is idempotent per id.
 *
 * Persisted as a comma-separated id list, matching how the skin inventories are
 * already stored, so there is one shape of "the player owns a set of things" in
 * this save file rather than two.
 */
class Achievements(private val save: SaveManager) {

    /** Ids already unlocked. */
    private val unlocked: MutableSet<String> =
        (save.unlockedAchievements.split(',').mapNotNull { it.trim().takeIf(String::isNotEmpty) })
            .toMutableSet()

    fun isUnlocked(a: Achievement): Boolean = a.id in unlocked

    fun unlockedCount(): Int = unlocked.size

    fun total(): Int = Achievement.entries.size

    /**
     * A snapshot of one run's results, plus the lifetime stats.
     *
     * Passed in rather than read from a ViewModel so this class has no Android
     * dependency and can be reasoned about (and unit-tested) as pure logic.
     */
    data class Snapshot(
        val runStarted: Boolean = false,
        val runScore: Int = 0,
        val runBestCombo: Int = 0,
        val deepestLevel: Int = 0,
        val statRuns: Int = 0,
        val statLevelsCleared: Int = 0,
        val statBlocks: Int = 0,
        val statShots: Int = 0,
        val statBestCombo: Int = 0,
        val statCoinsEarned: Long = 0
    )

    /**
     * Checks every achievement against [snap] and returns the ones newly earned.
     *
     * Coins are added here, in the same call, so a caller cannot mark something
     * unlocked and forget to pay it. Callers should surface [Result] to the
     * player; an achievement nobody is told about has not been given.
     */
    fun collect(snap: Snapshot): Result {
        val fresh = ArrayList<Pair<Achievement, Long>>(2)

        for (a in Achievement.entries) {
            if (a.id in unlocked) continue
            if (!isSatisfied(a, snap)) continue
            unlocked.add(a.id)
            fresh += a to a.coins
        }

        if (fresh.isEmpty()) return Result(emptyList(), 0L)

        var total = 0L
        for ((a, coins) in fresh) {
            save.addCoins(coins)
            total += coins
        }
        persist()

        return Result(fresh.map { it.first }, total)
    }

    private fun isSatisfied(a: Achievement, s: Snapshot): Boolean {
        // A per-run achievement must be earned in an actual run; without this
        // the FIRST_RUN check would fire for a player who has never pressed play
        // but happens to hold a lifetime total from a restored account.
        if (a.lifetimeMetric == null && !s.runStarted) return false

        val metric = a.lifetimeMetric ?: return when (a) {
            Achievement.FIRST_RUN -> true
            Achievement.COMBO_10 -> s.runBestCombo >= 10
            Achievement.COMBO_25 -> s.runBestCombo >= 25
            Achievement.LEVEL_10 -> s.deepestLevel >= 10
            Achievement.LEVEL_50 -> s.deepestLevel >= 50
            Achievement.LEVEL_100 -> s.deepestLevel >= 100
            Achievement.RUN_SCORE_5K -> s.runScore >= 5_000
            else -> false
        }

        val value = when (metric) {
            Achievement.StatMetric.RUNS -> s.statRuns.toLong()
            Achievement.StatMetric.LEVELS -> s.statLevelsCleared.toLong()
            Achievement.StatMetric.BLOCKS -> s.statBlocks.toLong()
            Achievement.StatMetric.SHOTS -> s.statShots.toLong()
            Achievement.StatMetric.BEST_COMBO -> s.statBestCombo.toLong()
            Achievement.StatMetric.COINS -> s.statCoinsEarned
        }
        return value >= a.threshold
    }

    private fun persist() {
        // Sorted so the stored string is stable, which makes diffing a save
        // readable and keeps the value from depending on enum iteration order.
        save.unlockedAchievements = unlocked.sorted().joinToString(",")
    }

    /** What [collect] earned. */
    data class Result(
        val unlocked: List<Achievement>,
        val coins: Long
    ) {
        val isEmpty: Boolean get() = unlocked.isEmpty()
    }
}