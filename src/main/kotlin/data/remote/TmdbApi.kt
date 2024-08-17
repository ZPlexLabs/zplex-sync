package zechs.zplex.sync.data.remote

import data.model.ShowExternalIdResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import zechs.zplex.sync.data.model.MovieResponse
import zechs.zplex.sync.data.model.SeasonResponse
import zechs.zplex.sync.data.model.TvResponse

interface TmdbApi {

    @GET("3/tv/{tv_id}")
    fun getShow(
        @Path("tv_id")
        tv_id: Int,
        @Query("language")
        language: String = "en-US"
    ): TvResponse

    @GET("3/tv/{tv_id}/external_ids")
    fun getShowExternalIds(
        @Path("tv_id")
        tv_id: Int,
        @Query("language")
        language: String = "en-US"
    ): ShowExternalIdResponse

    @GET("3/tv/{tv_id}/season/{season_number}")
    fun getSeason(
        @Path("tv_id")
        tv_id: Int,
        @Path("season_number")
        season_number: Int,
        @Query("language")
        language: String = "en-US",
    ): SeasonResponse

    @GET("3/movie/{movie_id}")
    fun getMovie(
        @Path("movie_id")
        movie_id: Int,
        @Query("language")
        language: String = "en-US"
    ): MovieResponse

}