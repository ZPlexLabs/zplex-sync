package zechs.zplex.sync.data.model.tmdb

data class SeasonResponse(
    val episodes: List<SeasonEpisode>?,
    val id: Int?,
    val name: String?,
    val air_date: String?,
    val overview: String?,
    val poster_path: String?,
    val season_number: Int
)


data class SeasonEpisode(
    val id: Int,
    val name: String?,
    val episode_number: Int,
    val season_number: Int,
    val still_path: String?
)