package zechs.zplex.sync.data.remote

import retrofit2.http.GET
import retrofit2.http.Query
import zechs.zplex.sync.data.model.omdb.OmdbMovieResponse
import zechs.zplex.sync.data.model.omdb.OmdbTvResponse

interface OmdbApi {

    @GET("/")
    fun fetchMovieById(
        @Query("i") tmdbId: String,
        @Query("plot") plot: String = "full",
    ): OmdbMovieResponse

    @GET("/")
    fun fetchTvById(
        @Query("i") tmdbId: String,
        @Query("plot") plot: String = "full",
    ): OmdbTvResponse

    @GET("/")
    fun fetchMovieByName(
        @Query("t") name: String,
        @Query("y") year: Int?,
        @Query("plot") plot: String = "full",
    ): OmdbMovieResponse

    @GET("/")
    fun fetchTvByName(
        @Query("t") name: String,
        @Query("y") year: Int?,
        @Query("plot") plot: String = "full",
    ): OmdbTvResponse

}