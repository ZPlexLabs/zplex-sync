package zechs.zplex.sync.data.remote;

import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;
import zechs.zplex.sync.data.remote.model.GenreResponse;
import zechs.zplex.sync.data.remote.model.MovieResponse;

public interface TmdbApi {

    @GET("3/movie/{movie_id}")
    MovieResponse getMovie(
            @Path("movie_id") Integer movie_id,
            @Query("language") String language,
            @Query("append_to_response") String append_to_response
    );

    @GET("3/genre/movie/list")
    GenreResponse getMovieGenres(@Query("language") String language);

}