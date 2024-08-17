package zechs.zplex.sync.utils

class Constants {

    companion object {
        const val TMDB_API_URL = "https://api.themoviedb.org"
        const val OMDB_API_URL = "https://www.omdbapi.com"
        const val TMDB_IMAGE_PREFIX = "https://www.themoviedb.org/t/p"
        val TMDB_API_KEY: String = System.getenv("TMDB_API_KEY")
        val OMDB_API_KEY: String = System.getenv("OMDB_API_KEY")
        val IS_DEBUG: Boolean = System.getenv("IS_DEBUG")?.toBoolean() ?: false
    }

}