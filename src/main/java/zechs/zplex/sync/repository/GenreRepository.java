package zechs.zplex.sync.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zechs.zplex.sync.data.local.model.Genre;

@Repository
public interface GenreRepository extends JpaRepository<Genre, Integer> {

}
