package zechs.zplex.sync.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zechs.zplex.sync.data.local.model.File;

import java.util.List;

@Repository
public interface FileRepository extends JpaRepository<File, Integer> {

    List<File> findByIdIn(List<String> ids);

    @Modifying
    @Transactional
    @Query("DELETE FROM File f WHERE f.id IN :ids")
    void deleteByIds(@Param("ids") List<String> ids);

}
