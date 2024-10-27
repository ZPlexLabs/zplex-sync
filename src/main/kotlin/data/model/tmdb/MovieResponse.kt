package zechs.zplex.sync.data.model.tmdb

import zechs.zplex.sync.data.model.ExternalLink
import zechs.zplex.sync.data.model.Genre
import java.time.Instant

data class MovieResponse(
    val backdrop_path: String?,
    val belongs_to_collection: BelongsToCollection?,
    val credits: Credits,
    val external_ids: Map<String, String?>?,
    val genres: List<Genre>,
    val id: Int,
    val images: Images,
    val poster_path: String?,
    val production_companies: List<ProductionCompany>?,
    val tagline: String,
    val title: String,
    val videos: Videos,
) {

    fun getBestLogoImage(): String? {
        return images.logos?.maxByOrNull { it.vote_average ?: 0.0 }?.file_path
    }

    fun getOfficialTrailer(): String? {
        return videos.results
            ?.filter { it.type == "Trailer" && it.site == "YouTube" }
            ?.sortedByDescending { it.official }
            ?.maxByOrNull { Instant.parse(it.published_at).toEpochMilli() }
            ?.key
            ?.takeIf { it.isNotEmpty() }
            ?.let { "https://www.youtube.com/watch?v=$it" }
    }

    fun getDirectorName(): String? {
        return credits.crew?.firstOrNull { it.job == "Director" }?.name
    }

    fun getExternalLinks(): List<ExternalLink> {
        return external_ids?.mapNotNull { (site, link) ->
            link?.let {
                ExternalLink(
                    id = id,
                    name = site,
                    url = link
                )
            }
        } ?: emptyList()
    }
}
