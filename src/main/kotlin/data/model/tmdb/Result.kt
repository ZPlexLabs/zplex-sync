package zechs.zplex.sync.data.model.tmdb

data class Result(
    val key: String,
    val official: Boolean,
    val published_at: String,
    val site: String,
    val type: String
)