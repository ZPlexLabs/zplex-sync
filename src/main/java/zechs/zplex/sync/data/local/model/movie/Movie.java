package zechs.zplex.sync.data.local.model.movie;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;
import zechs.zplex.sync.data.local.model.File;
import zechs.zplex.sync.data.local.model.Genre;
import zechs.zplex.sync.data.local.model.Studio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "movies")
public class Movie {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;  // TMDB ID as PK

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "collection_id")
    private Integer collectionId;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private File file;

    @Column(name = "imdb_id", nullable = false, unique = true, columnDefinition = "TEXT")
    private String imdbId;

    @Column(name = "imdb_rating", precision = 3, scale = 1)
    private BigDecimal imdbRating;

    @Column(name = "imdb_votes", nullable = false)
    private Integer imdbVotes = 0;

    @Column(name = "release_date")
    private Long releaseDate;

    @Column(name = "release_year", nullable = false)
    private Integer releaseYear;

    @Column(name = "parental_rating", columnDefinition = "TEXT")
    private String parentalRating;

    @Column(name = "runtime")
    private Integer runtime;

    @Column(name = "poster_path", columnDefinition = "TEXT")
    private String posterPath;

    @Column(name = "backdrop_path", columnDefinition = "TEXT")
    private String backdropPath;

    @Column(name = "logo_image", columnDefinition = "TEXT")
    private String logoImage;

    @Column(name = "trailer_link", columnDefinition = "TEXT")
    private String trailerLink;

    @Column(name = "tagline", columnDefinition = "TEXT")
    private String tagline;

    @Column(name = "plot", columnDefinition = "TEXT")
    private String plot;

    @Column(name = "director", columnDefinition = "TEXT")
    private String director;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "_movie_to_genre",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private Set<Genre> genres = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "_movie_to_studios",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "studio_id")
    )
    private Set<Studio> studios = new HashSet<>();

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<MovieCrew> crews = new HashSet<>();

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<MovieCast> casts = new HashSet<>();

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<MovieExternalLink> externalLinks = new HashSet<>();

    public void setImdbRating(BigDecimal imdbRating) {
        this.imdbRating = (imdbRating != null) ? imdbRating.setScale(1, RoundingMode.HALF_UP) : null;
    }
}
