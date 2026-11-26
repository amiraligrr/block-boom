package ir.amiraligrr.blockboom.data

data class LeaderboardEntry(
    val name: String,
    val level: Int,
    val score: Long,
    val isPremium: Boolean,
    val crown: String = if (isPremium) "👑" else "",
    val updatedAt: String = "",
    /** Absolute rank, assigned server-side. 0 when unknown. */
    val rank: Int = 0
)
