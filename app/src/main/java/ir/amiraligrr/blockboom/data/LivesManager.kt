package ir.amiraligrr.blockboom.data

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

/**
 * Heart/lives system: 25 hearts max, grant 5 lives every 90 minutes.
 * Premium users always have a full bar. Writes through [SaveManager].
 * Anti-cheat: uses both wall clock and elapsedRealtime to prevent
 * clock-forward cheating (بازکردن مجدد برای بینهایت بازی).
 */
class LivesManager(private val save: SaveManager) {

    var lives by mutableIntStateOf(MAX_LIVES)
        private set

    /** Milliseconds until the next regen (0 when full or premium). */
    var millisToNextReset by mutableLongStateOf(0L)
        private set

    init {
        refresh()
    }

    fun refresh() {
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()

        if (save.isPremium) {
            if (save.lives < MAX_LIVES || save.lastLifeReset != nowWall) {
                save.lives = MAX_LIVES
                save.lastLifeReset = nowWall
                save.lastElapsedRealtime = nowElapsed
                save.lastWallCheckMillis = nowWall
            }
            lives = MAX_LIVES
            millisToNextReset = 0L
            return
        }

        // --- Anti-tamper: detect wall clock rollback ---
        val lastWall = save.lastWallCheckMillis
        if (lastWall != 0L && nowWall < lastWall - 60_000L) {
            // Clock was set backwards - treat as tampering, don't grant lives
            // Update lastWall to now but don't reset hearts
            save.lastWallCheckMillis = nowWall
            save.lastElapsedRealtime = nowElapsed
            lives = save.lives.coerceIn(0, MAX_LIVES)
            millisToNextReset = if (lives >= MAX_LIVES) 0L else {
                // Use elapsedRealtime based estimate if available
                val savedElapsed = save.lastElapsedRealtime
                if (savedElapsed != 0L && nowElapsed >= savedElapsed) {
                    val elapsedReal = nowElapsed - savedElapsed
                    (REGEN_INTERVAL_MILLIS - elapsedReal).coerceAtLeast(0L)
                } else {
                    val wallElapsed = (nowWall - save.lastLifeReset).coerceAtLeast(0L)
                    (REGEN_INTERVAL_MILLIS - wallElapsed).coerceAtLeast(0L)
                }
            }
            return
        }
        save.lastWallCheckMillis = nowWall

        // --- Compute elapsed with dual-clock validation to prevent forward cheat ---
        val wallElapsed = (nowWall - save.lastLifeReset).coerceAtLeast(0L)
        val savedElapsed = save.lastElapsedRealtime
        val realElapsed: Long = if (savedElapsed == 0L) {
            // First run after upgrade - initialize elapsed baseline
            save.lastElapsedRealtime = nowElapsed
            wallElapsed
        } else if (nowElapsed < savedElapsed) {
            // Device rebooted (elapsed reset) - fall back to wall, but store new baseline
            save.lastElapsedRealtime = nowElapsed
            wallElapsed
        } else {
            val deltaReal = nowElapsed - savedElapsed
            // The trustworthy elapsed is the MIN of wall and realtime
            // If user forwarded wall clock by hours, wallElapsed >> deltaReal,
            // so we use deltaReal (small) and don't grant lives
            minOf(wallElapsed, deltaReal)
        }

        // Grant 5 lives per REGEN_INTERVAL_MILLIS (90 minutes)
        val intervalsPassed = realElapsed / REGEN_INTERVAL_MILLIS
        // Gated on `save.lives`, not on the `lives` snapshot. `lives` is a
        // `mutableIntStateOf(MAX_LIVES)` that is only assigned at the end of this
        // function, so on the first pass after construction it is still
        // MAX_LIVES no matter what the save holds — and `init { refresh() }` is
        // exactly that first pass, run from `BlockBoomApp.onCreate`. The block was
        // therefore skipped on every cold start, and the reset countdown below
        // was then computed as though nothing had been granted.
        if (intervalsPassed > 0 && save.lives < MAX_LIVES) {
            val livesToGrant = (intervalsPassed * LIVES_PER_REGEN).toInt().coerceAtMost(MAX_LIVES - save.lives)
            if (livesToGrant > 0) {
                save.lives = (save.lives + livesToGrant).coerceAtMost(MAX_LIVES)
                // Move the anchor forward by the intervals we consumed
                save.lastLifeReset += intervalsPassed * REGEN_INTERVAL_MILLIS
                save.lastElapsedRealtime += intervalsPassed * REGEN_INTERVAL_MILLIS
            }
        }

        lives = save.lives.coerceIn(0, MAX_LIVES)
        // Compute millisToNextReset based on realElapsed
        val effectiveElapsed = if (savedElapsed != 0L && nowElapsed >= savedElapsed) {
            minOf(wallElapsed, nowElapsed - savedElapsed)
        } else {
            wallElapsed
        }
        val elapsedInCurrentInterval = effectiveElapsed % REGEN_INTERVAL_MILLIS
        millisToNextReset = if (lives >= MAX_LIVES) 0L else {
            (REGEN_INTERVAL_MILLIS - elapsedInCurrentInterval).coerceAtLeast(0L)
        }
        // Persist current elapsed for next check if lives not full
        if (lives < MAX_LIVES && save.lastElapsedRealtime != nowElapsed) {
            // Don't update lastLifeReset every refresh, only elapsed marker
            // We keep lastElapsedRealtime as the marker for delta calc on next refresh
            // But to make delta meaningful, we need to keep it as "last check time"
            // Actually we want savedElapsed to be time of lastLifeReset, not last refresh
            // So we don't overwrite it on every refresh - only on regen grant
            // No-op here to preserve reset anchor
        }
    }

    /** True when a game can be started. Premium never runs out. */
    fun canStart(): Boolean {
        refresh()
        return save.isPremium || lives > 0
    }

    /** Consumes one heart to start a level. Returns false if no heart is available. */
    fun tryConsumeLife(): Boolean {
        refresh()
        return decrementLife()
    }

    /**
     * Removes one heart. Premium players never lose a heart; otherwise returns
     * false when there is nothing left to spend. Compose UI reads [lives]
     * directly (snapshot state) so the header re-renders immediately.
     */
    fun decrementLife(): Boolean {
        refresh()
        if (save.isPremium) return true
        if (lives <= 0) return false
        save.lives = lives - 1
        lives = save.lives
        return true
    }

    /** Grants [count] hearts (up to the max) and resets the regen anchor. */
    fun grantLives(count: Int = 1) {
        refresh()
        val nowWall = System.currentTimeMillis()
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        save.lives = (save.lives + count).coerceAtMost(MAX_LIVES)
        if (save.lives >= MAX_LIVES) {
            save.lastLifeReset = nowWall
            save.lastElapsedRealtime = nowElapsed
        }
        lives = save.lives
        millisToNextReset = 0L
        save.lastWallCheckMillis = nowWall
    }

    companion object {
        /**
         * 25, up from 15.
         *
         * A block reaching the cannon ends the level outright (the mercy reprieve
         * was removed), so hearts are now the *only* buffer between a mistake
         * and game over. At 15 a player who lost three in a row was nearly out,
         * which meant the first bad level of a session was also the one that
         * decided whether they played another ten minutes.
         *
         * At 25 there is room to lose a run, recover, and still have hearts
         * left — which is what makes a loss feel like a setback rather than a
         * reason to close the app.
         */
        const val MAX_LIVES = 25
        const val LIVES_PER_REGEN = 5
        const val REGEN_INTERVAL_MILLIS = 90 * 60 * 1000L // 90 minutes
    }
}