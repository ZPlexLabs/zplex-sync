package zechs.zplex.sync.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zechs.zplex.sync.data.local.model.File;
import zechs.zplex.sync.data.local.model.movie.Movie;
import zechs.zplex.sync.repository.queries.MovieSqlQueries;

import java.util.List;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Integer> {

    @Modifying
    @Transactional
    @NativeQuery(value = MovieSqlQueries.DELETE_MOVIE_RELATIONS)
    void deleteMovieAndRelationsByFileId(@Param("fileId") String fileId);


    @NativeQuery(value = MovieSqlQueries.GET_ALL_MOVIE_FILES)
    List<File> getAllMovieFiles();

}
