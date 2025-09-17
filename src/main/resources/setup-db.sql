-- Database: V1

-- Create files table
CREATE TABLE files
(
    id   TEXT PRIMARY KEY NOT NULL,
    name TEXT             NOT NULL,
    size BIGINT           NOT NULL
);

-- Create movies table
CREATE TABLE movies
(
    id           INTEGER PRIMARY KEY NOT NULL,
    title        TEXT                NOT NULL,
    poster_path  TEXT,
    vote_average NUMERIC(3, 1)       NOT NULL,
    year         INTEGER             NOT NULL,
    file_id      TEXT REFERENCES files (id) ON DELETE CASCADE
);

-- Create shows table
CREATE TABLE shows
(
    id           INTEGER PRIMARY KEY NOT NULL,
    name         TEXT                NOT NULL,
    poster_path  TEXT,
    vote_average NUMERIC(3, 1)       NOT NULL
);

-- Create seasons table
CREATE TABLE seasons
(
    id            INTEGER PRIMARY KEY NOT NULL,
    name          TEXT                NOT NULL,
    season_number INTEGER             NOT NULL,
    poster_path   TEXT,
    show_id       INTEGER REFERENCES shows (id) ON DELETE CASCADE
);

-- Create episodes table
CREATE TABLE episodes
(
    id             INTEGER PRIMARY KEY NOT NULL,
    title          TEXT,
    episode_number INTEGER             NOT NULL,
    season_number  INTEGER             NOT NULL,
    still_path     TEXT,
    season_id      INTEGER REFERENCES seasons (id) ON DELETE CASCADE,
    file_id        TEXT REFERENCES files (id) ON DELETE CASCADE
);

-- Create trigger function to delete seasons if no episodes present
CREATE OR REPLACE FUNCTION delete_season_if_no_episodes()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM episodes WHERE season_id = OLD.season_id) THEN
        DELETE FROM seasons WHERE id = OLD.season_id;
    END IF;
    RETURN OLD;
END;
$$
    LANGUAGE plpgsql;


-- Create trigger to delete seasons if no episodes present
CREATE TRIGGER check_episodes_before_season_delete
    AFTER DELETE
    ON episodes
    FOR EACH ROW
EXECUTE FUNCTION delete_season_if_no_episodes();

-- Create trigger function to delete shows if no seasons present
CREATE OR REPLACE FUNCTION delete_show_if_no_seasons()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM seasons WHERE show_id = OLD.show_id) THEN
        DELETE FROM shows WHERE id = OLD.show_id;
    END IF;
    RETURN OLD;
END;
$$
    LANGUAGE plpgsql;

-- Create trigger to delete shows if no seasons present
CREATE TRIGGER check_seasons_before_show_delete
    AFTER DELETE
    ON seasons
    FOR EACH ROW
EXECUTE FUNCTION delete_show_if_no_seasons();

-- Database Migration: V1 -> V2

-- Add column modified_time to files table
ALTER TABLE files
    ADD COLUMN modified_time BIGINT NOT NULL DEFAULT 0;

-- Add column modified_time to shows table
ALTER TABLE shows
    ADD COLUMN modified_time BIGINT NOT NULL DEFAULT 0;


-- Database Migration: V2 -> V3
DROP TABLE IF EXISTS movies;

-- Table: movies
CREATE TABLE movies
(
    id              INT   NOT NULL PRIMARY KEY,
    title           TEXT  NOT NULL,
    collection_id   INT,
    file_id         TEXT  NOT NULL,
    imdb_id         TEXT  NOT NULL,
    imdb_rating     DOUBLE PRECISION,
    imdb_votes      INT   NOT NULL,
    release_date    BIGINT,
    release_year    INT   NOT NULL,
    parental_rating TEXT,
    runtime         INT,
    poster_path     TEXT,
    backdrop_path   TEXT,
    logo_image      TEXT,
    trailer_link    TEXT,
    tagline         TEXT,
    plot            TEXT,
    director        TEXT,
    genres          INT[] NOT NULL,
    studios         INT[] NOT NULL,
    FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE
);

-- Table: casts
CREATE TABLE movie_casts
(
    id     INT  NOT NULL,
    image  TEXT,
    name   TEXT NOT NULL,
    role   TEXT,
    gender TEXT NOT NULL,
    FOREIGN KEY (id) REFERENCES movies (id) ON DELETE CASCADE
);

-- Table: crews
CREATE TABLE movie_crews
(
    id    INT  NOT NULL,
    image TEXT,
    name  TEXT NOT NULL,
    job   TEXT,
    FOREIGN KEY (id) REFERENCES movies (id) ON DELETE CASCADE
);

-- Table: genres
CREATE TABLE genres
(
    id   INT  NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL
);

-- Table: studios
CREATE TABLE studios
(
    id             INT  NOT NULL PRIMARY KEY,
    logo_path      TEXT,
    name           TEXT NOT NULL,
    origin_country TEXT NOT NULL
);

-- Table: external_links
CREATE TABLE movie_external_links
(
    id   INT  NOT NULL,
    name TEXT NOT NULL,
    url  TEXT NOT NULL,
    FOREIGN KEY (id) REFERENCES movies (id) ON DELETE CASCADE
);

-- Create trigger function to delete related data when a movie is deleted
CREATE OR REPLACE FUNCTION delete_related_movie_data()
    RETURNS TRIGGER AS
$$
BEGIN
    -- Delete related cast entries
    DELETE FROM movie_casts WHERE id = OLD.id;

    -- Delete related crew entries
    DELETE FROM movie_crews WHERE id = OLD.id;

    -- Delete related studio entries
    DELETE FROM studios WHERE id = OLD.id;

    -- Delete related external links entries
    DELETE FROM movie_external_links WHERE id = OLD.id;

    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to delete related data when a movie is deleted
CREATE TRIGGER delete_related_movie_data_trigger
    AFTER DELETE
    ON movies
    FOR EACH ROW
EXECUTE FUNCTION delete_related_movie_data();

-- Database Migration: V3 -> V4
DROP TABLE IF EXISTS episodes;
DROP TABLE IF EXISTS seasons;
DROP TABLE IF EXISTS shows;

-- Table: shows
CREATE TABLE shows
(
    id              INT    NOT NULL PRIMARY KEY,
    title           TEXT   NOT NULL,
    imdb_id         TEXT   NOT NULL,
    imdb_rating     DOUBLE PRECISION,
    imdb_votes      INT    NOT NULL,
    release_date    BIGINT,
    release_year    INT    NOT NULL,
    release_year_to BIGINT,
    parental_rating TEXT,
    poster_path     TEXT,
    backdrop_path   TEXT,
    logo_image      TEXT,
    trailer_link    TEXT,
    plot            TEXT,
    director        TEXT,
    genres          INT[]  NOT NULL,
    studios         INT[]  NOT NULL,
    modified_time   BIGINT NOT NULL
);

-- Table: casts
CREATE TABLE show_casts
(
    id     INT  NOT NULL,
    image  TEXT,
    name   TEXT NOT NULL,
    role   TEXT,
    gender TEXT NOT NULL,
    FOREIGN KEY (id) REFERENCES shows (id) ON DELETE CASCADE
);

-- Table: crews
CREATE TABLE show_crews
(
    id    INT  NOT NULL,
    image TEXT,
    name  TEXT NOT NULL,
    job   TEXT,
    FOREIGN KEY (id) REFERENCES shows (id) ON DELETE CASCADE
);
-- Table: external_links
CREATE TABLE show_external_links
(
    id   INT  NOT NULL,
    name TEXT NOT NULL,
    url  TEXT NOT NULL,
    FOREIGN KEY (id) REFERENCES shows (id) ON DELETE CASCADE
);

-- Create seasons table
CREATE TABLE seasons
(
    id            INTEGER PRIMARY KEY NOT NULL,
    name          TEXT                NOT NULL,
    season_number INTEGER             NOT NULL,
    release_date  BIGINT,
    release_year  INT                 NOT NULL,
    poster_path   TEXT,
    show_id       INTEGER REFERENCES shows (id) ON DELETE CASCADE
);

-- Create episodes table
CREATE TABLE episodes
(
    id             INTEGER PRIMARY KEY NOT NULL,
    title          TEXT,
    episode_number INTEGER             NOT NULL,
    season_number  INTEGER             NOT NULL,
    overview       TEXT,
    runtime        INT,
    airdate        BIGINT,
    still_path     TEXT,
    season_id      INTEGER REFERENCES seasons (id) ON DELETE CASCADE,
    file_id        TEXT REFERENCES files (id) ON DELETE CASCADE
);

-- Create trigger function to delete seasons if no episodes present
CREATE OR REPLACE FUNCTION delete_season_if_no_episodes()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM episodes WHERE season_id = OLD.season_id) THEN
        DELETE FROM seasons WHERE id = OLD.season_id;
    END IF;
    RETURN OLD;
END;
$$
    LANGUAGE plpgsql;


-- Create trigger to delete seasons if no episodes present
CREATE TRIGGER check_episodes_before_season_delete
    AFTER DELETE
    ON episodes
    FOR EACH ROW
EXECUTE FUNCTION delete_season_if_no_episodes();

-- Create trigger function to delete shows if no seasons present
CREATE OR REPLACE FUNCTION delete_show_if_no_seasons()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM seasons WHERE show_id = OLD.show_id) THEN
        DELETE FROM shows WHERE id = OLD.show_id;
    END IF;
    RETURN OLD;
END;
$$
    LANGUAGE plpgsql;

-- Create trigger to delete shows if no seasons present
CREATE TRIGGER check_seasons_before_show_delete
    AFTER DELETE
    ON seasons
    FOR EACH ROW
EXECUTE FUNCTION delete_show_if_no_seasons();

-- Create trigger function to delete related data when a show is deleted
CREATE OR REPLACE FUNCTION delete_related_show_data()
    RETURNS TRIGGER AS
$$
BEGIN
    -- Delete related cast entries
    DELETE FROM show_casts WHERE id = OLD.id;

    -- Delete related crew entries
    DELETE FROM show_crews WHERE id = OLD.id;

    -- Delete related studio entries
    DELETE FROM studios WHERE id = OLD.id;

    -- Delete related external links entries
    DELETE FROM show_external_links WHERE id = OLD.id;

    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to delete related data when a movie is deleted
CREATE TRIGGER delete_related_show_data_trigger
    AFTER DELETE
    ON shows
    FOR EACH ROW
EXECUTE FUNCTION delete_related_show_data();

-- Suggestions API
CREATE OR REPLACE FUNCTION fetch_titles_for_today(limit_value INT)
    RETURNS TABLE
            (
                tmdbId        INT,
                title         TEXT,
                poster_path   TEXT,
                backdrop_path TEXT,
                release       TEXT,
                type          TEXT
            )
AS
$$
DECLARE
    now BIGINT;
BEGIN
    now := EXTRACT(EPOCH FROM CURRENT_DATE)::BIGINT;
    RETURN QUERY
        WITH combined_titles AS (SELECT m.id,
                                        m.title,
                                        m.poster_path,
                                        m.backdrop_path,
                                        m.release_year::TEXT as release,
                                        'movie'        AS type
                                 FROM movies m
                                 UNION ALL
                                 SELECT s.id,
                                        s.title,
                                        s.poster_path,
                                        s.backdrop_path,
                                        CASE
                                            WHEN s.release_year_to = 2147483647
                                                THEN CONCAT(s.release_year, ' - Present')
                                            WHEN s.release_year_to IS NULL THEN s.release_year::TEXT
                                            ELSE s.release_year || ' - ' || s.release_year_to
                                            END AS release,
                                        'show'  AS type
                                 FROM shows s)
        SELECT *
        FROM combined_titles c
        ORDER BY (c.id + now) % (SELECT COUNT(*) FROM combined_titles)
        LIMIT limit_value;
END;
$$ LANGUAGE plpgsql;

-- Search Media Function
CREATE OR REPLACE FUNCTION search_media(
    p_table_name text, -- 'movies' or 'shows'
    p_studios int[] DEFAULT NULL,
    p_parental_ratings text[] DEFAULT NULL,
    p_release_years int[] DEFAULT NULL,
    p_genres int[] DEFAULT NULL,
    p_order_by text DEFAULT 'title',
    p_order_dir text DEFAULT 'ASC',
    p_include_nulls boolean DEFAULT true,
    p_page int DEFAULT 1,
    p_page_size int DEFAULT 50
)
    RETURNS TABLE
            (
                tmdbId     int,
                title      text,
                posterPath text,
                imdbRating double precision,
                release    text
            )
AS
$$
DECLARE
    sql_query        text;
    sql_release_expr text;
    sql_offset       int;
    sql_order_expr   text;
    sql_join_files   text := '';
BEGIN
    -- Validate table name
    IF p_table_name NOT IN ('movies', 'shows') THEN
        RAISE EXCEPTION 'Invalid table name: %', p_table_name;
    END IF;

    -- Validate order by column
    IF p_order_by NOT IN ('title', 'imdb_rating', 'release_year', 'release_date', 'date_added') THEN
        RAISE EXCEPTION 'Invalid order by column: %', p_order_by;
    END IF;

    -- Validate order direction
    IF UPPER(p_order_dir) NOT IN ('ASC', 'DESC') THEN
        RAISE EXCEPTION 'Invalid order direction: %', p_order_dir;
    END IF;

    -- Ensure page and page_size are positive
    IF p_page < 1 THEN
        RAISE EXCEPTION 'Page must be >= 1';
    END IF;
    IF p_page_size < 1 THEN
        RAISE EXCEPTION 'Page size must be >= 1';
    END IF;

    -- Calculate offset
    sql_offset := (p_page - 1) * p_page_size;

    -- Build release year expression
    IF p_table_name = 'movies' THEN
        sql_release_expr := 'release_year::TEXT AS release';
    ELSE
        sql_release_expr := 'CASE
                                WHEN release_year_to = 2147483647 THEN release_year::TEXT || '' - Present''
                                WHEN release_year_to IS NULL THEN release_year::TEXT
                                ELSE release_year::TEXT || '' - '' || release_year_to::TEXT
                             END AS release';
    END IF;

    -- Build ORDER BY expression and optional join for date_added
    IF p_order_by = 'date_added' THEN
        IF p_table_name = 'movies' THEN
            sql_order_expr := 'f.modified_time';
            sql_join_files := 'JOIN files f ON file_id = f.id';
        ELSE
            sql_order_expr := 'modified_time';
        END IF;
    ELSE
        sql_order_expr := format('%I', p_order_by);
    END IF;

    -- Build dynamic SQL safely with optional NULL filtering, LIMIT and OFFSET
    sql_query := format(
            'SELECT t.id AS tmdbId, title, poster_path AS posterPath, imdb_rating AS imdbRating, %s
             FROM %I t
             %s
             WHERE ($1::int[] IS NULL OR studios && $1)
               AND ($2::text[] IS NULL OR parental_rating = ANY($2))
               AND ($3::int[] IS NULL OR release_year = ANY($3))
               AND ($4::int[] IS NULL OR genres && $4)
               %s
             ORDER BY %s %s
             LIMIT %s OFFSET %s',
            sql_release_expr,
            p_table_name,
            sql_join_files,
            CASE WHEN NOT p_include_nulls THEN 'AND ' || sql_order_expr || ' IS NOT NULL' ELSE '' END,
            sql_order_expr,
            UPPER(p_order_dir),
            p_page_size,
            sql_offset
                 );

    -- Log the query as a single line
    RAISE LOG 'Dynamic search_media query: %', sql_query;

    -- Execute the dynamic query
    RETURN QUERY EXECUTE sql_query
        USING p_studios, p_parental_ratings, p_release_years, p_genres;
END;
$$ LANGUAGE plpgsql;

-- =========================================================
-- INDEXES FOR MOVIES
-- =========================================================

CREATE INDEX IF NOT EXISTS idx_movies_studios_gin
    ON movies USING GIN (studios);

CREATE INDEX IF NOT EXISTS idx_movies_genres_gin
    ON movies USING GIN (genres);

CREATE INDEX IF NOT EXISTS idx_movies_parental_rating
    ON movies (parental_rating);

CREATE INDEX IF NOT EXISTS idx_movies_release_year
    ON movies (release_year);

CREATE INDEX IF NOT EXISTS idx_movies_imdb_rating
    ON movies (imdb_rating);

-- ORDER BY date_added (via files join)
CREATE INDEX IF NOT EXISTS idx_files_modified_time
    ON files (id, modified_time);

-- =========================================================
-- INDEXES FOR SHOWS
-- =========================================================

CREATE INDEX IF NOT EXISTS idx_shows_studios_gin
    ON shows USING GIN (studios);

CREATE INDEX IF NOT EXISTS idx_shows_genres_gin
    ON shows USING GIN (genres);

CREATE INDEX IF NOT EXISTS idx_shows_parental_rating
    ON shows (parental_rating);

CREATE INDEX IF NOT EXISTS idx_shows_release_year
    ON shows (release_year);

CREATE INDEX IF NOT EXISTS idx_shows_imdb_rating
    ON shows (imdb_rating);

-- Ordering by modified_time (shows table has its own column)
CREATE INDEX IF NOT EXISTS idx_shows_modified_time
    ON shows (modified_time);


-- Materialized view for movie details
CREATE MATERIALIZED VIEW movie_details_mv AS
SELECT m.id,
       m.title,
       m.collection_id,
       m.file_id,
       m.imdb_id,
       TO_CHAR(m.imdb_rating, 'FM9.0')                            AS imdb_rating,
       m.imdb_votes,
       to_char(to_timestamp(m.release_date / 1000), 'DD/MM/YYYY') AS release_date,
       m.release_year,
       m.parental_rating,
       m.runtime,
       m.poster_path,
       m.backdrop_path,
       m.logo_image,
       m.trailer_link,
       m.tagline,
       m.plot,
       m.director,
       g.genres,
       s.studios,
       c.cast,
       cr.crew,
       coll.collections
FROM movies m
         LEFT JOIN LATERAL (
    SELECT json_agg(
                   jsonb_build_object('id', g.id, 'name', g.name)
                   ORDER BY
                       g.name
           ) AS genres
    FROM unnest(m.genres) gid
             JOIN genres g ON g.id = gid
    ) g ON TRUE
         LEFT JOIN LATERAL (
    SELECT json_agg(
                   jsonb_build_object('id', s.id, 'name', s.name, 'logo_path',
                                      s.logo_path, 'origin_country', s.origin_country)
                   ORDER BY
                       s.name
           ) AS studios
    FROM unnest(m.studios) sid
             JOIN studios s ON s.id = sid
    ) s ON TRUE
         LEFT JOIN LATERAL (
    SELECT json_agg(
                   jsonb_build_object(
                           'name', c.name, 'image', c.image, 'gender',
                           c.gender, 'role', c.role
                   )
                   ORDER BY
                       c.role
           ) AS "cast"
    FROM movie_casts c
    WHERE c.id = m.id
    ) c ON TRUE
         LEFT JOIN LATERAL (
    SELECT json_agg(
                   jsonb_build_object(
                           'name', cr.name, 'image', cr.image,
                           'job', cr.job
                   )
                   ORDER BY
                       cr.job
           ) AS crew
    FROM movie_crews cr
    WHERE cr.id = m.id
    ) cr ON TRUE
         LEFT JOIN LATERAL (
    SELECT CASE
               WHEN COUNT(*) > 1 THEN
                   json_agg(
                           jsonb_build_object('id', mm.id, 'title', mm.title)
                           ORDER BY mm.release_date
                   )
               ELSE NULL
               END AS collections
    FROM movies mm
    WHERE mm.collection_id = m.collection_id
    ) coll ON TRUE;


CREATE UNIQUE INDEX idx_movie_details_id ON movie_details_mv (id);


-- Materialized view for tvshows details
CREATE MATERIALIZED VIEW shows_details_mv AS
SELECT s.id,
       s.title,
       s.imdb_id,
       TO_CHAR(s.imdb_rating, 'FM9.0')                            AS imdb_rating,
       s.imdb_votes,
       to_char(to_timestamp(s.release_date / 1000), 'DD/MM/YYYY') AS release_date,
       CASE
           WHEN s.release_year_to = 2147483647 THEN s.release_year || ' - Present'
           WHEN s.release_year_to IS NULL THEN s.release_year::TEXT
           ELSE s.release_year || ' - ' || s.release_year_to
           END                                                    AS release,
       s.parental_rating,
       s.poster_path,
       s.backdrop_path,
       s.logo_image,
       s.trailer_link,
       s.plot,
       s.director,

       -- Genres
       (SELECT json_agg(jsonb_build_object('id', g.id, 'name', g.name) ORDER BY g.name)
        FROM genres g
        WHERE g.id = ANY (s.genres))                              AS genres,

       -- Studios
       (SELECT json_agg(jsonb_build_object('id', st.id, 'name', st.name, 'logo_path', st.logo_path, 'origin_country',
                                           st.origin_country) ORDER BY st.name)
        FROM studios st
        WHERE st.id = ANY (s.studios))                            AS studios,

       -- Cast
       (SELECT json_agg(jsonb_build_object('name', c.name, 'image', c.image, 'gender', c.gender, 'role', c.role)
                        ORDER BY c.role)
        FROM show_casts c
        WHERE c.id = s.id)                                        AS cast,

       -- Crew
       (SELECT json_agg(jsonb_build_object('name', cr.name, 'image', cr.image, 'job', cr.job) ORDER BY cr.job)
        FROM show_crews cr
        WHERE cr.id = s.id)                                       AS crew,

       -- Latest season with episodes count
       (SELECT jsonb_build_object(
                       'id', se.id,
                       'name', se.name,
                       'season_number', se.season_number,
                       'release_date', to_char(to_timestamp(se.release_date / 1000), 'DD/MM/YYYY'),
                       'release_year', se.release_year,
                       'poster_path', se.poster_path,
                       'episodes_count', COALESCE(ec.episodes_count, 0)
               )
        FROM seasons se
                 LEFT JOIN (SELECT season_id, COUNT(*) AS episodes_count
                            FROM episodes
                            GROUP BY season_id) ec ON ec.season_id = se.id
        WHERE se.show_id = s.id
        ORDER BY se.season_number DESC
        LIMIT 1)                                                  AS latest_season

FROM shows s;

CREATE UNIQUE INDEX idx_show_details_id ON shows_details_mv (id);

-- Set parallel workers
SET max_parallel_workers_per_gather = 4;
