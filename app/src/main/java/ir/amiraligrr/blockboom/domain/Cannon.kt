package ir.amiraligrr.blockboom.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * The auto-firing cannon. [x] is lerped toward [targetX] every frame so the
 * motion stays smooth (no jerky jumps), while [targetX] follows the finger.
 */
class Cannon {
    /**
     * Where the finger is.
     *
     * Snapshot state, not a plain `var`. It used to be a plain field (with
     * `@Volatile`, which does nothing useful here — it is only ever touched from
     * the main thread), so nothing could observe it. The in-game coach waits
     * for exactly this: "did the player move the cannon?" is a question that
     * cannot be answered by reading a field nothing recomposes on, so the coach
     * would have sat on its first step until its timeout and then advanced
     * without ever having checked.
     */
    var targetX by mutableFloatStateOf(0f)

    var x: Float = 0f

    /** Y position of the cannon base line (bottom of the field). */
    var baseY: Float = 0f

    /** Radius of the cannon sphere, in px. */
    var radius: Float = 0f

    /** Breathing-glow phase accumulator. */
    var pulseTime: Float = 0f

    /** 1 right after a shot, decays to 0. Used for the muzzle flash. */
    var recoil: Float = 0f

    // Upgrade-backed combat stats, refreshed from SaveManager via
    // GameEngine.refreshCannonStats() so purchases apply instantly.
    var damage: Int = 1
    var fireRate: Float = 2f
    var multiBall: Int = 1
    var pierce: Int = 0
    var bulletSpeed: Float = 0f
    var magnetRadius: Float = 0f

    val centerX: Float get() = x
    val centerY: Float get() = baseY - radius * 0.55f

    /** Scroll movement, call from UI thread. */
    fun moveTo(dx: Float) {
        targetX += dx
    }
}