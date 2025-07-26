package zechs.zplex.sync.data.local.model.movie;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "movie_casts")
public class MovieCast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // auto-generated PK

    @Column(name = "cast_id")
    private Integer castId; // Original cast ID

    @Column(name = "image", columnDefinition = "TEXT")
    private String image;

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(name = "role", columnDefinition = "TEXT")
    private String role;

    @Column(name = "gender", nullable = false, columnDefinition = "TEXT")
    private String gender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;
}
