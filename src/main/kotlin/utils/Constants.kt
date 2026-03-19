package zechs.zplex.sync.utils

class Constants {

    companion object {
        const val TMDB_API_URL = "https://api.themoviedb.org"
        const val OMDB_API_URL = "https://www.omdbapi.com"
        const val TMDB_IMAGE_PREFIX = "https://www.themoviedb.org/t/p"
        val IS_DEBUG: Boolean = System.getProperty("app.env")?.equals("dev", true) ?: false
    }

}