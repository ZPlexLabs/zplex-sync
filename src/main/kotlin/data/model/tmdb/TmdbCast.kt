package zechs.zplex.sync.data.model.tmdb

import zechs.zplex.sync.data.model.Cast
import zechs.zplex.sync.data.model.Gender

data class TmdbCast(
    val character: String,
    val gender: Int,
    val id: Int,
    val name: String,
    val order: Int,
    val profile_path: String?
) {
    fun toCast(tmdbId: Int) = Cast(
        id = tmdbId,
        image = profile_path,
        name = name,
        role = character,
        gender = when (gender) {
            1 -> Gender.Female
            2 -> Gender.Male
            else -> Gender.Other
        }
    )
}