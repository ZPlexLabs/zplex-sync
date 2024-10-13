package zechs.zplex.sync.data.model.tmdb

data class SeasonResponse(
    val id: Int,
    val name: String?,
    val air_date: String?,
    val overview: String?,
    val poster_path: String?,
    val season_number: Int,
    val episodes: List<SeasonEpisode>?
)


data class SeasonEpisode(
    val id: Int,
    val name: String?,
    val overview: String?,
    val air_date: String?,
    val runtime: Int?,
    val episode_number: Int,
    val season_number: Int,
    val still_path: String?
)