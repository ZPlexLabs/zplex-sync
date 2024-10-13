package zechs.zplex.sync.data.model.tmdb

import zechs.zplex.sync.data.model.ExternalLink
import zechs.zplex.sync.data.model.Genre
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*


data class TvResponse(
    val id: Int,
    val name: String?,
    val poster_path: String?,
    val seasons: List<TvSeason>?,
    val backdrop_path: String?,
    val credits: Credits,
    val external_ids: Map<String, String?>?,
    val genres: List<Genre>,
    val images: Images,
    val production_companies: List<ProductionCompany>?,
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

data class TvSeason(
    val id: Int,
    val name: String?,
    val poster_path: String?,
    val episode_count: Int,
    val season_number: Int,
    val overview: String?,
    val air_date: String?
) {
    fun getOverview(showName: String): String {
        val seasonName = "Season $season_number"
        val formattedDate = air_date?.let {
            val srcFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val dstFormat = DateTimeFormatter.ofPattern("EEEE dd, yyyy", Locale.ENGLISH)
            LocalDate.parse(it, srcFormat).format(dstFormat)
        }
        val premiered = "$seasonName of $showName" + (formattedDate?.let { " premiered on $it." } ?: "")
        return overview.takeUnless { it.isNullOrEmpty() } ?: premiered
    }
}