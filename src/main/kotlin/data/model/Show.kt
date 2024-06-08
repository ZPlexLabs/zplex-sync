package zechs.zplex.sync.data.model

data class Show(
    val id: Int,
    val name: String,
    val posterPath: String?,
    val voteAverage: Double?
)

data class Season(
    val id: Int,
    val name: String,
    val posterPath: String?,
    val seasonNumber: Int,
    val showId: Int,
)

data class Episode(
    val id: Int,
    val title: String?,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val stillPath: String?,
    val seasonId: Int,
    val fileId: String,
)