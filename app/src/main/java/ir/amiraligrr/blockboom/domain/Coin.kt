package ir.amiraligrr.blockboom.domain

enum class CoinState {
    /** Free-falling coin, or magnetized toward the HUD collector. */
    Free,
    Magnetized
}

/**
 * Coin spawned from an exploded block. Spins while alive, falls down and is
 * auto-collected after [life] seconds — or gets magnet-pulled to the HUD when
 * the magnet upgrade is active. Pooled to avoid GC.
 */
class Coin {

    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f

    var rotation: Float = 0f
    var spinSpeed: Float = 0f

    var age: Float = 0f
    var life: Float = 1f

    var state: CoinState = CoinState.Free
    var collected: Boolean = false

    fun reset() {
        x = 0f; y = 0f; vx = 0f; vy = 0f
        rotation = 0f; spinSpeed = 0f
        age = 0f; life = 1f
        state = CoinState.Free
        collected = false
    }
}