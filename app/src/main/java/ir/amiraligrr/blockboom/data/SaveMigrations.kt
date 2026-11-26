package ir.amiraligrr.blockboom.data

import android.util.Log
import kotlin.math.ceil
import kotlin.math.pow

/**
 * One-time save migrations, run once per install from [BlockBoomApp.onCreate].
 *
 * These exist because this app stores progression in a single flat set of
 * `SharedPreferences` keys with no schema version. When a balance change lowers
 * a cap, nothing rewrites the stored levels — the shop hides the row behind
 * "MAX" and the engine clamps at the point it builds the stat, so the game keeps
 * working. What it does *not* do is notice that the player already paid for
 * levels that no longer do anything.
 *
 * Every migration therefore needs two properties, and both are load-bearing:
 *
 *  - **It must be idempotent.** A migration that pays out coins runs exactly
 *    once. It records a version stamp in the same preferences file it is
 *    migrating, so re-launching cannot pay twice, and so a player who clears app
 *    data gets a clean slate rather than a repeat.
 *  - **It must be stamped, not inferred.** "Is the player above the cap?" is
 *    true on every launch for a player who was above it, so it cannot also be
 *    the thing that decides whether to compensate them.
 */
object SaveMigrations {

    private const val TAG = "BlockBoomMigrate"
    private const val PREFS = "blockboom_prefs"

    /** Bump when adding a migration; each one checks its own stamp. */
    private const val KEY_SCHEMA_VERSION = "save_schema_version"
    private const val CURRENT_SCHEMA = 2

    // ── The old curve, reproduced ─────────────────────────────────────────
    //
    // The compensation has to price the levels the player bought with the curve
    // that was live *when they bought them*, not the current one. Upgrades are
    // currently 1.6 -> 2.2x; using today's constants would refund a fraction of
    // what was actually paid for a mid-track level, which is both wrong and hard
    // to notice once it ships.
    //
    // These are duplicated on purpose and pinned forever. If the live pricing
    // ever changes again, these must NOT be updated with it.
    private const val LEGACY_COST_GROWTH = 1.85
    private const val LEGACY_COST_MULTIPLIER = 1.6

    /**
     * Scales the refund. 1.0 pays back exactly what the removed levels cost.
     *
     * Lower it to soften the credit without touching the logic. The full figure
     * is ~81,000 coins for a player who was maxed on both tracks, against the
     * ~355,000 a full build costs now — so 1.0 returns roughly a quarter of the
     * cost of the build those levels were part of. That is the honest number for
     * "you paid for this and it no longer exists", and it goes only to players
     * who were actually above the new caps; everyone else gets nothing.
     */
    private const val REFUND_SCALE = 1.0

    /** Base costs, copied from the catalogue as it was when those coins were paid. */
    private const val MULTI_BALL_BASE = 90L
    private const val PIERCE_BASE = 140L

    /** Price of moving a track from [level] to `level + 1`, as it was then. */
    private fun legacyPrice(base: Long, level: Int): Long {
        if (level <= 0) return 0L
        return ceil(base * level.toDouble().pow(LEGACY_COST_GROWTH) * LEGACY_COST_MULTIPLIER)
            .toLong()
    }

    /**
     * Total price of the levels being removed from a track.
     *
     * Levels [newCap] through [storedLevel - 1] are what disappears, and the
     * price of a *level* is the cost of buying into it — so the removed levels
     * are the steps `newCap -> newCap+1` up to `stored-1 -> stored`.
     */
    private fun priceOfRemovedLevels(base: Long, newCap: Int, stored: Int): Long {
        var total = 0L
        for (step in newCap until stored) {
            total += legacyPrice(base, step)
        }
        return total
    }

    /**
     * Runs every outstanding migration. Safe to call on every launch.
     *
     * @return coins granted by this call, for the caller to announce. Zero for
     *   every player not affected.
     */
    fun run(save: SaveManager): Long {
        val ctx = save.appContext
        val prefs = ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val done = prefs.getInt(KEY_SCHEMA_VERSION, 0)
        if (done >= CURRENT_SCHEMA) return 0L

        var granted = 0L

        if (done < 2) {
            granted += refundCappedUpgrades(save)
        }

        prefs.edit().putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA).apply()
        return granted
    }

    /**
     * Refunds upgrade levels that a cap reduction made unusable, then clamps.
     *
     * MULTI_BALL went 12 -> 5 and PIERCE 10 -> 7. A player who had bought past
     * the new cap paid real coins — or a lot of grinding — for levels the game
     * now ignores, with no way to tell and no recourse. The cannon also lost
     * about two thirds of its peak output (3,861 -> 1,170 dps), which is the
     * point of the rebalance but is still a nerf to someone who earned the old
     * numbers.
     *
     * The refund is the historical price of exactly the levels being removed,
     * computed with the curve that was live at the time. That is a defensible
     * number — it is not a judgement call about what they "deserved", it is what
     * they paid for something that no longer exists.
     *
     * Clamping the stored level in the same pass leaves the save honest: the
     * shop's "MAX" and the cannon's behaviour finally describe the same thing.
     * The engine already clamped at the point it built the stat, so this is
     * about persistence, not behaviour.
     *
     * @return coins refunded.
     */
    private fun refundCappedUpgrades(save: SaveManager): Long {
        var refund = 0L

        val multi = save.upgradeMultiBall
        if (multi > UpgradeManager.MAX_MULTI_BALL) {
            refund += priceOfRemovedLevels(MULTI_BALL_BASE, UpgradeManager.MAX_MULTI_BALL, multi)
            save.upgradeMultiBall = UpgradeManager.MAX_MULTI_BALL
            Log.i(TAG, "multi_ball clamped $multi -> ${UpgradeManager.MAX_MULTI_BALL}")
        }

        val pierce = save.upgradePierce
        if (pierce > UpgradeManager.MAX_PIERCE) {
            refund += priceOfRemovedLevels(PIERCE_BASE, UpgradeManager.MAX_PIERCE, pierce)
            save.upgradePierce = UpgradeManager.MAX_PIERCE
            Log.i(TAG, "pierce clamped $pierce -> ${UpgradeManager.MAX_PIERCE}")
        }

        if (refund <= 0L) return 0L

        val payout = ceil(refund * REFUND_SCALE).toLong()
        save.addCoins(payout)
        Log.i(TAG, "balance-change refund: $payout coins (raw $refund)")
        return payout
    }
}