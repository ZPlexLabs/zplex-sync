package zechs.zplex.sync.data.model.tmdb

import zechs.zplex.sync.data.model.Crew

data class TmdbCrew(
    val id: Int,
    val job: String?,
    val name: String,
    val profile_path: String?
) {
    fun toCrew(tmdbId: Int) = Crew(
        id = tmdbId,
        image = profile_path,
        name = name,
        job = job,
    )
}