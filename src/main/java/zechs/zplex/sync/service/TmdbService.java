package zechs.zplex.sync.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zechs.zplex.sync.data.remote.TmdbApi;
import zechs.zplex.sync.data.remote.model.GenreResponse;
import zechs.zplex.sync.data.remote.model.MovieResponse;

import java.util.Arrays;
import java.util.List;

@Service
public final class TmdbService {

    private final TmdbApi tmdbApi;

    @Autowired
    public TmdbService(TmdbApi tmdbApi) {
        this.tmdbApi = tmdbApi;
    }

    public GenreResponse getGenres(String language) {
        return this.tmdbApi.getMovieGenres(language);
    }

    public MovieResponse getMovie(Integer movieId, String language) {
        List<String> extras = Arrays.asList("images", "external_ids", "credits", "videos");
        return this.tmdbApi.getMovie(movieId, language, String.join(",", extras));
    }

}
