package zechs.zplex.sync.data.model

data class ExternalLink(
    val id: Int, // This will be tmdbId of original movie/tv show
    val name: String,
    val url: String
)