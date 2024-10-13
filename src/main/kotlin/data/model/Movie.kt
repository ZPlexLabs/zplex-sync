package zechs.zplex.sync.data.model

import zechs.zplex.sync.data.model.tmdb.Studio

data class Movie(
    val id: Int, // This is tmdbId, as serves as the primary key
    val imdbId: String,
    val imdbRating: Double?,
    val imdbVotes: Int,
    val releaseDate: Long?,
    val releaseYear: Int,
    val parentalRating: String?,
    val runtime: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val logoImage: String?,
    val trailerLink: String?, // This is YouTube video id
    val tagLine: String?,
    val plot: String?,
    val director: String?,
    val cast: List<Cast>,
    val crew: List<Crew>,
    val genres: List<Genre>,
    val studios: List<Studio>,
    val externalLinks: List<ExternalLink>,
    val collectionId: Int?,
    val title: String,
    val fileId: String
)