package ir.amiraligrr.blockboom.data

data class LeaderboardResponse(
    val data: List<LeaderboardEntry>,
    val pagination: PaginationInfo,
    val userRank: Int? = null,
    val userEntry: LeaderboardEntry? = null,
    val period: String = "all"
)

data class PaginationInfo(
    val page: Int,
    val limit: Int,
    val total: Int,
    val totalPages: Int,
    val hasMore: Boolean
)