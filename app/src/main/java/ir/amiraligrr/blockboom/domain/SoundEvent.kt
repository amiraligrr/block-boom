package ir.amiraligrr.blockboom.domain

/**
 * Disposable game events that deserve a short audio sting. The engine only
 * *emits* these (cheap enum constants, no Android coupling); the UI layer
 * translates them into the actual [ir.amiraligrr.blockboom.data.SoundManager]
 * playback so the simulation stays pure.
 */
enum class SoundEvent {
    FIRE,
    BLOCK_HIT,
    BLOCK_CRACK,
    BLOCK_DESTROY,
    COIN,
    POWER_UP_SPAWN,
    POWER_UP_COLLECT,
    FREEZE_ACTIVATE,
    BOSS_HIT,
    BOSS_SPAWN,
    LEVEL_COMPLETE,
    GAME_OVER,
    HEART_LOST,
    COMBO
}
