package ir.amiraligrr.blockboom.domain

enum class GameStatus {
    Playing,
    Paused,
    LevelComplete,
    GameOver,

    /** A block slammed into the cannon body: -1 heart, replay the level. */
    HitByBlock
}