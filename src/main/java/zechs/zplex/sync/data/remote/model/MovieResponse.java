package zechs.zplex.sync.data.remote.model;

import zechs.zplex.sync.data.local.model.Studio;
import zechs.zplex.sync.data.local.model.movie.Movie;
import zechs.zplex.sync.data.local.model.movie.MovieCast;
import zechs.zplex.sync.data.local.model.movie.MovieCrew;
import zechs.zplex.sync.data.local.model.movie.MovieExternalLink;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record MovieResponse(
        String backdrop_path,
        BelongsToCollection belongs_to_collection,
        Credits credits,
        Map<String, String> external_ids,
        List<Genre> genres,
        int id,
        Images images,
        String poster_path,
        List<ProductionCompany> production_companies,
        String tagline,
        String title,
        Videos videos
) {
    public String getBestLogoImage() {
        return Optional.ofNullable(images)
                .map(Images::logos)
                .flatMap(logos -> logos.stream()
                        .max(Comparator.comparingDouble(a -> Optional.ofNullable(a.vote_average()).orElse(0.0)))
                        .map(Image::file_path))
                .orElse(null);
    }

    public String getOfficialTrailer() {
        return Optional.ofNullable(videos)
                .map(Videos::results)
                .flatMap((results) -> results.stream()
                        .filter(video -> "Trailer".equals(video.type()) && "YouTube".equals(video.site()))
                        .sorted((a, b) -> Boolean.compare(b.official(), a.official()))
                        .max(Comparator.comparingLong(a -> Instant.parse(a.published_at()).toEpochMilli()))
                        .map(Video::key)
                )
                .map(key -> "https://www.youtube.com/watch?v=" + key)
                .orElse(null);
    }

    public String getDirectorName() {
        return Optional.ofNullable(credits)
                .map(Credits::crew)
                .flatMap(crew -> crew.stream()
                        .filter(member -> "Director".equals(member.job()))
                        .map(Crew::name)
                        .findFirst())
                .orElse(null);
    }

    public Set<MovieExternalLink> getExternalLinks(Movie dbMovie) {
        if (external_ids == null || external_ids.isEmpty()) {
            return Collections.emptySet();
        }

        return external_ids.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> MovieExternalLink.builder()
                        .name(entry.getKey())
                        .url(entry.getValue())
                        .movie(dbMovie)
                        .build()
                )
                .collect(Collectors.toSet());
    }


    public Set<zechs.zplex.sync.data.local.model.Genre> getGenres() {
        if (genres == null || genres.isEmpty()) {
            return Collections.emptySet();
        }

        return genres.stream()
                .map(genre -> new zechs.zplex.sync.data.local.model.Genre(genre.id(), genre.name(), "movie"))
                .collect(Collectors.toSet());
    }

    public Set<Studio> getStudios() {
        if (production_companies == null || production_companies.isEmpty()) {
            return Collections.emptySet();
        }

        return production_companies.stream()
                .map(company -> new Studio(company.id(), company.logo_path(), company.name(), company.origin_country()))
                .collect(Collectors.toSet());
    }

    public Set<MovieCrew> getCrews(Movie dbMovie) {
        if (credits == null || credits.crew() == null || credits.crew().isEmpty()) {
            return Collections.emptySet();
        }

        return credits.crew().stream()
                .map(person -> MovieCrew.builder()
                        .crewId(person.id())
                        .name(person.name())
                        .image(person.profile_path())
                        .job(person.job())
                        .movie(dbMovie)
                        .build())
                .collect(Collectors.toSet());
    }

    public Set<MovieCast> getCasts(Movie dbMovie) {
        if (credits == null || credits.cast() == null || credits.cast().isEmpty()) {
            return Collections.emptySet();
        }

        return credits.cast().stream()
                .map(person -> MovieCast.builder()
                        .castId(person.id())
                        .name(person.name())
                        .image(person.profile_path())
                        .role(person.character())
                        .gender(person.getGender().name())
                        .movie(dbMovie)
                        .build())
                .collect(Collectors.toSet());
    }
}