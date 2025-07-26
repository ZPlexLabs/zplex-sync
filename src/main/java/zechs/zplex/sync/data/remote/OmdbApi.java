package zechs.zplex.sync.data.remote;

import retrofit2.http.GET;
import retrofit2.http.Query;
import zechs.zplex.sync.data.remote.model.OmdbMovieResponse;
import zechs.zplex.sync.data.remote.model_enum.OmdbPlot;

public interface OmdbApi {

    @GET("/")
    OmdbMovieResponse fetchMovieById(
            @Query("i") String tmdbId,
            @Query("plot") OmdbPlot plot
    );

}
