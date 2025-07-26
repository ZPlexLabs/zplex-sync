package zechs.zplex.sync.repository.queries;

public final class MovieSqlQueries {

    public static final String GET_ALL_MOVIE_FILES = """
            SELECT f.id, f.name, f.size, f.modified_time
            FROM files f
            INNER JOIN movies m ON f.id = m.file_id
            ORDER BY f.id
            """;

    public static final String DELETE_MOVIE_RELATIONS = """
            -- 1. Delete related records from junction tables
            DELETE FROM _movie_to_genre WHERE movie_id IN (
                SELECT id FROM movies WHERE file_id = :fileId
            );
            DELETE FROM _movie_to_studios WHERE movie_id IN (
                SELECT id FROM movies WHERE file_id = :fileId
            );
            DELETE FROM _movie_to_crews WHERE movie_id IN (
                SELECT id FROM movies WHERE file_id = :fileId
            );
            DELETE FROM _movie_to_casts WHERE movie_id IN (
                SELECT id FROM movies WHERE file_id = :fileId
            );
            DELETE FROM _movie_to_external_links WHERE movie_id IN (
                SELECT id FROM movies WHERE file_id = :fileId
            );
            
            -- 2. Delete Movie ONLY if it has no remaining relations
            DELETE FROM movies
            WHERE file_id = :fileId
              AND id NOT IN (
                  SELECT DISTINCT movie_id FROM (
                      SELECT movie_id FROM _movie_to_genre
                      UNION ALL
                      SELECT movie_id FROM _movie_to_studios
                      UNION ALL
                      SELECT movie_id FROM _movie_to_crews
                      UNION ALL
                      SELECT movie_id FROM _movie_to_casts
                      UNION ALL
                      SELECT movie_id FROM _movie_to_external_links
                  ) AS remaining_relations
              );
            """;
}
