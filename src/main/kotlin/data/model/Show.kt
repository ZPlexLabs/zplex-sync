package zechs.zplex.sync.data.model

import zechs.zplex.sync.data.model.tmdb.Studio

data class Show(
    val id: Int, // This is tmdbId, as serves as the primary key
    val imdbId: String,
    val imdbRating: Double?,
    val imdbVotes: Int,
    val releaseDate: Long?,
    val releasedYear: Int,
    // if Integer.MAX_VALUE, then mark it as "Present", if null, then not going to be continued
    // else actual ending year
    val releaseYearTo: Int?,
    val parentalRating: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val logoImage: String?,
    val trailerLink: String?, // This is YouTube video url
    val plot: String?,
    val director: String?,
    val cast: List<Cast>,
    val crew: List<Crew>,
    val genres: List<Genre>,
    val studios: List<Studio>,
    val externalLinks: List<ExternalLink>,
    val title: String,
    val modifiedTime: Long
)

data class Season(
    val id: Int,
    val name: String?,
    val posterPath: String?,
    val overview: String,
    val releaseYear: Int,
    val releaseDate: Long?,
    val seasonNumber: Int,
    val showId: Int,
)

data class Episode(
    val id: Int,
    val title: String?,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val stillPath: String?,
    val overview: String?,
    val airdate: Long?, // in epoch
    val runtime: Int?, // in mins
    val seasonId: Int,
    val fileId: String,
)