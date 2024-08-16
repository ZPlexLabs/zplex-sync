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
ALTER TABLE files ADD COLUMN modified_time BIGINT NOT NULL DEFAULT 0;

-- Add column modified_time to shows table
ALTER TABLE shows ADD COLUMN modified_time BIGINT NOT NULL DEFAULT 0;