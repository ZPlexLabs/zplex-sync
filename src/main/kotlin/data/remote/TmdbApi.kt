package zechs.zplex.sync.data.remote

import data.model.tmdb.GenreResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import zechs.zplex.sync.data.model.tmdb.MovieResponse
import zechs.zplex.sync.data.model.tmdb.SeasonResponse
import zechs.zplex.sync.data.model.tmdb.TvResponse

interface TmdbApi {

    @GET("3/tv/{tv_id}")
    fun getShow(
        @Path("tv_id")
        tv_id: Int,
        @Query("language")
        language: String = "en",
        @Query("append_to_response")
        append_to_response: String
    ): TvResponse

    @GET("3/tv/{tv_id}/season/{season_number}")
    fun getSeason(
        @Path("tv_id")
        tv_id: Int,
        @Path("season_number")
        season_number: Int,
        @Query("language")
        language: String = "en",
    ): SeasonResponse

    @GET("3/movie/{movie_id}")
    fun getMovie(
        @Path("movie_id")
        movie_id: Int,
        @Query("language")
        language: String = "en",
        @Query("append_to_response")
        append_to_response: String
    ): MovieResponse

    @GET("3/genre/movie/list")
    fun getMovieGenres(
        @Query("language")
        language: String = "en"
    ): GenreResponse

    @GET("3/genre/tv/list")
    fun getShowGenres(
        @Query("language")
        language: String = "en"
    ): GenreResponse

}