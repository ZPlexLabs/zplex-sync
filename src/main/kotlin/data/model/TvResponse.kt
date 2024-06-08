package zechs.zplex.sync.data.model


data class TvResponse(
    val id: Int,
    val name: String?,
    val poster_path: String?,
    val vote_average: Double?,
    val seasons: List<TvSeason>?,
) {
    fun toShow() = Show(
        id = id,
        name = name ?: "",
        posterPath = poster_path,
        voteAverage = vote_average
    )
}

data class TvSeason(
    val id: Int,
    val name: String,
    val poster_path: String?,
    val episode_count: Int,
    val season_number: Int,
    val overview: String?,
    val air_date: String?
)