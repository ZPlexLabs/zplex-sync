package zechs.zplex.sync.data.local.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostRemove;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import zechs.zplex.sync.config.ApplicationContextProvider;
import zechs.zplex.sync.repository.MovieRepository;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(name = "File")
@Table(name = "files")
public class File {

    @Id
    @Column(name = "id", nullable = false, length = Integer.MAX_VALUE)
    private String id;

    @Column(name = "name", nullable = false, length = Integer.MAX_VALUE)
    private String name;

    @Column(name = "size", nullable = false)
    private Long size;

    @ColumnDefault("0")
    @Column(name = "modified_time", nullable = false)
    private Long modifiedTime;

    @PostRemove
    public void deleteAssociatedMovie() {
        MovieRepository movieRepository = ApplicationContextProvider.getBean(MovieRepository.class);
        movieRepository.deleteMovieAndRelationsByFileId(this.id);
    }

}
