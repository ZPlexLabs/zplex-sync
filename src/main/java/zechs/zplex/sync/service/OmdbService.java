package zechs.zplex.sync.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zechs.zplex.sync.data.remote.OmdbApi;
import zechs.zplex.sync.data.remote.model.OmdbMovieResponse;
import zechs.zplex.sync.data.remote.model_enum.OmdbPlot;

@Service
public final class OmdbService {

    private final OmdbApi omdbApi;

    @Autowired
    public OmdbService(OmdbApi omdbApi) {
        this.omdbApi = omdbApi;
    }

    public OmdbMovieResponse getMovie(String imdbId) {
        return this.omdbApi.fetchMovieById(imdbId, OmdbPlot.Full);
    }
}
