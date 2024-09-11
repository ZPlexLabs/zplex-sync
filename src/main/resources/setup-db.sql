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
    id              INT    NOT NULL PRIMARY KEY,
    title           TEXT   NOT NULL,
    collection_id   INT,
    file_id         TEXT   NOT NULL,
    imdb_id         TEXT   NOT NULL,
    imdb_rating     DOUBLE PRECISION,
    imdb_votes      INT    NOT NULL,
    release_date    BIGINT,
    release_year    BIGINT NOT NULL,
    parental_rating TEXT,
    runtime         BIGINT,
    poster_path     TEXT,
    backdrop_path   TEXT,
    logo_image      TEXT,
    trailer_link    TEXT,
    tagline         TEXT,
    plot            TEXT,
    director        TEXT,
    genres          INT[]  NOT NULL,
    studios         INT[]  NOT NULL,
    FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE
);

-- Table: casts
CREATE TABLE casts
(
    id     INT  NOT NULL,
    image  TEXT,
    name   TEXT NOT NULL,
    role   TEXT,
    gender TEXT NOT NULL,
    FOREIGN KEY (id) REFERENCES movies (id) ON DELETE CASCADE
);

-- Table: crews
CREATE TABLE crews
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
CREATE TABLE external_links
(
    id   INT  NOT NULL,
    name TEXT NOT NULL,
    url  TEXT NOT NULL,
    FOREIGN KEY (id) REFERENCES movies (id) ON DELETE CASCADE
);

-- -- Create trigger function to delete related data when a movie is deleted
CREATE OR REPLACE FUNCTION delete_related_movie_data()
    RETURNS TRIGGER AS
$$
BEGIN
    -- Delete related cast entries
    DELETE FROM casts WHERE id = OLD.id;

    -- Delete related crew entries
    DELETE FROM crews WHERE id = OLD.id;

    -- Delete related studio entries
    DELETE FROM studios WHERE id = OLD.id;

    -- Delete related external links entries
    DELETE FROM external_links WHERE id = OLD.id;

    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

-- -- Create trigger to delete related data when a movie is deleted
CREATE TRIGGER delete_related_movie_data_trigger
    AFTER DELETE
    ON movies
    FOR EACH ROW
EXECUTE FUNCTION delete_related_movie_data();