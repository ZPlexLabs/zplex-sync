package zechs.zplex.sync.data.model

data class MovieResponse(
    val id: Int,
    val title: String?,
    val poster_path: String?,
    val vote_average: Double?
)