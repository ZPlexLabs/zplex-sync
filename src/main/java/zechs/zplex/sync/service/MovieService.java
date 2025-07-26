package zechs.zplex.sync.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zechs.zplex.sync.data.local.model.File;
import zechs.zplex.sync.data.local.model.Genre;
import zechs.zplex.sync.data.local.model.Studio;
import zechs.zplex.sync.data.local.model.movie.Movie;
import zechs.zplex.sync.data.local.model.movie.MovieCast;
import zechs.zplex.sync.data.local.model.movie.MovieCrew;
import zechs.zplex.sync.data.local.model.movie.MovieExternalLink;
import zechs.zplex.sync.data.remote.model.MovieResponse;
import zechs.zplex.sync.data.remote.model.OmdbMovieResponse;
import zechs.zplex.sync.repository.MovieRepository;
import zechs.zplex.sync.utils.Info;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static zechs.zplex.sync.utils.OmdbHelperUtils.parseOmdbRating;
import static zechs.zplex.sync.utils.OmdbHelperUtils.parseOmdbVotes;
import static zechs.zplex.sync.utils.OmdbHelperUtils.parseParentalRating;
import static zechs.zplex.sync.utils.OmdbHelperUtils.parseReleasedDate;
import static zechs.zplex.sync.utils.OmdbHelperUtils.parseRuntime;

@Service
@RequiredArgsConstructor
public class MovieService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MovieService.class);
    private final MovieRepository movieRepository;
    private final TmdbService tmdbService;
    private final OmdbService omdbService;

    @PersistenceContext
    private EntityManager entityManager;

    public List<File> getAllMovieFiles() {
        return this.movieRepository.getAllMovieFiles();
    }

    @Transactional
    public void insertMovie(Info videoInfo, com.google.api.services.drive.model.File remoteFile, String language) {
        try {
            MovieResponse movie = tmdbService.getMovie(videoInfo.tmdbId(), language);

            String imdbId = movie.external_ids().getOrDefault("imdb_id", null);
            if (imdbId == null) {
                LOGGER.error("imdbId not found for movie: {}", videoInfo.fileName());
                return;
            }

            OmdbMovieResponse omdbMovie = omdbService.getMovie(imdbId);
            if (omdbMovie == null) {
                LOGGER.error("OMDB response not found for movie: {} with imdbId: {}", videoInfo.fileName(), imdbId);
                return;
            }

            File dbFile = File.builder()
                    .id(remoteFile.getId())
                    .name(remoteFile.getName())
                    .size(remoteFile.getSize())
                    .modifiedTime(remoteFile.getModifiedTime().getValue())
                    .build();

            Movie dbMovie = Movie.builder()
                    .id(movie.id())
                    .title(omdbMovie.Title())
                    .collectionId(movie.belongs_to_collection() != null ? movie.belongs_to_collection().id() : null)
                    .file(dbFile)
                    .imdbId(imdbId)
                    .imdbRating(parseOmdbRating(omdbMovie.imdbRating()))
                    .imdbVotes(parseOmdbVotes(omdbMovie.imdbVotes()))
                    .releaseDate(parseReleasedDate(omdbMovie.Released()))
                    .releaseYear(Integer.parseInt(omdbMovie.Year()))
                    .parentalRating(parseParentalRating(omdbMovie.Rated()))
                    .runtime(parseRuntime(omdbMovie.Runtime()))
                    .posterPath(movie.poster_path())
                    .backdropPath(movie.backdrop_path())
                    .logoImage(movie.getBestLogoImage())
                    .trailerLink(movie.getOfficialTrailer())
                    .tagline(movie.tagline())
                    .plot(omdbMovie.Plot())
                    .director(movie.getDirectorName())
                    .build();

            dbMovie.setGenres(resolveGenres(movie.getGenres()));
            dbMovie.setStudios(resolveStudios(movie.getStudios()));


            dbMovie.setCasts(movie.getCasts(dbMovie));
            dbMovie.setCrews(movie.getCrews(dbMovie));
            dbMovie.setExternalLinks(movie.getExternalLinks(dbMovie));

            entityManager.persist(dbMovie);
            LOGGER.info("Added movie: {}", dbMovie.getTitle());

        } catch (Exception e) {
            LOGGER.error("Error saving movie: {}", videoInfo.fileName(), e);
        }
    }

    private Set<Genre> resolveGenres(Set<Genre> genres) {
        return genres.stream()
                .map(g -> entityManager.find(Genre.class, g.getId()) != null
                        ? entityManager.getReference(Genre.class, g.getId())
                        : g)
                .collect(Collectors.toSet());
    }

    private Set<Studio> resolveStudios(Set<Studio> studios) {
        return studios.stream()
                .map(s -> entityManager.find(Studio.class, s.getId()) != null
                        ? entityManager.getReference(Studio.class, s.getId())
                        : s)
                .collect(Collectors.toSet());
    }

}
