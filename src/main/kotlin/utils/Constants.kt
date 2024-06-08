package zechs.zplex.sync.utils

class Constants {

    companion object {
        const val TMDB_API_URL = "https://api.themoviedb.org"
        const val TMDB_IMAGE_PREFIX = "https://www.themoviedb.org/t/p"
        val TMDB_API_KEY: String = System.getenv("TMDB_API_KEY")
        val IS_DEBUG: Boolean = System.getenv("IS_DEBUG")?.toBoolean() ?: false
    }

}