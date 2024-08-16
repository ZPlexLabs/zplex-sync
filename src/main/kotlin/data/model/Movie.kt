package zechs.zplex.sync.data.model

data class Movie(
    val id: Int,
    val title: String,
    val posterPath: String?,
    val voteAverage: Double?,
    val releaseYear: Int,
    val fileId: String
)