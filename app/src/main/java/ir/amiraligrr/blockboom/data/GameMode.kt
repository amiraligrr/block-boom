package ir.amiraligrr.blockboom.data

/**
 * The two ways to play Block Boom.
 *
 * [STAGE] is the campaign: the player's saved level is advanced after every
 * clear, and it is never submitted to the leaderboard.
 *
 * [RECORD] is one endless attempt that always begins at level 1. Its only
 * purpose is the shared record board, so it must be the same challenge for
 * everybody: no saved checkpoint, no skipped levels, and no stage progress
 * mixed into the ranking.
 */
enum class GameMode {
    /** Campaign: saved level, one heart per attempt, upgrade breather between levels. */
    STAGE,

    /** Endless record attempt: always starts at level 1 and feeds the record board. */
    RECORD,

    /** Standalone boss ladder: starts at the next boss level, no campaign checkpoint. */
    BOSS,

    /** Sixty-second score chase: the board keeps refilling until the clock runs out. */
    TIME_ATTACK;

    /** True for the modes that are one attempt and cannot spend a second heart. */
    val isOneAttempt: Boolean
        get() = this == RECORD || this == BOSS || this == TIME_ATTACK

    /** True when the mode must not write the campaign checkpoint or its chests. */
    val isSideMode: Boolean
        get() = this != STAGE

    companion object {
        fun fromStorage(value: String?): GameMode =
            values().firstOrNull { it.name == value } ?: STAGE
    }
}
