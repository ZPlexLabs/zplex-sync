package zechs.zplex.sync.utils;

public final class Constants {

    public static final String TMDB_API_URL = "https://api.themoviedb.org";
    public static final String OMDB_API_URL = "https://www.omdbapi.com";

    public static final String TMDB_IMAGE_PREFIX = "https://www.themoviedb.org/t/p";

    public static final String TMDB_API_KEY = System.getenv("TMDB_API_KEY");
    public static final String OMDB_API_KEY = System.getenv("OMDB_API_KEY");
    public static final boolean IS_DEBUG = (System.getenv("IS_DEBUG") != null)
            ? Boolean.parseBoolean(System.getenv("IS_DEBUG"))
            : false;

}
