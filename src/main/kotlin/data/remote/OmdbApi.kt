package zechs.zplex.sync.data.remote

import data.model.OmdbResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OmdbApi {

    @GET("/")
    fun fetchById(
        @Query("i") tmdbId: String,
        @Query("plot") plot: String = "full",
    ): OmdbResponse

    @GET("/")
    fun fetchByName(
        @Query("t") name: String,
        @Query("y") year: Int,
        @Query("plot") plot: String = "full",
    ): OmdbResponse

}