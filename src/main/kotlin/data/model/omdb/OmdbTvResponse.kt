package zechs.zplex.sync.data.model.omdb

/* After some analysis, the following fields have been determined to be non-null:
Actors, Genre, Language, Plot, Poster, Ratings, Released, Response, Title, Type, Year, imdbID, imdbVotes
*/
data class OmdbTvResponse(
    val imdbID: String,
    val imdbRating: String?,
    val imdbVotes: String,
    val Plot: String,
    val Rated: String?,
    val Released: String,
    val Runtime: String?,
    val Title: String,
    val Type: String,
    val Year: String
) {
    fun getLatestYear(): Int? {
        return Year
            .substringAfter("–", "")
            .takeIf { it.isNotBlank() }
            ?.toIntOrNull() ?: if (Year.contains("-")) Integer.MAX_VALUE else null
    }
}