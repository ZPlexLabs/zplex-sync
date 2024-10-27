package zechs.zplex.sync.data.repository

import com.google.api.services.drive.model.File
import zechs.zplex.sync.data.local.Database
import zechs.zplex.sync.data.local.FileDao
import zechs.zplex.sync.data.local.MovieDao
import zechs.zplex.sync.data.local.ShowDao
import zechs.zplex.sync.data.model.DriveFile
import zechs.zplex.sync.data.model.Episode
import zechs.zplex.sync.data.model.Genre
import zechs.zplex.sync.data.model.Movie
import zechs.zplex.sync.data.model.Season
import zechs.zplex.sync.data.model.Show
import zechs.zplex.sync.data.model.tmdb.MovieResponse
import zechs.zplex.sync.data.model.tmdb.SeasonResponse
import zechs.zplex.sync.data.model.tmdb.TvResponse
import zechs.zplex.sync.data.remote.TmdbApi
import zechs.zplex.sync.utils.DatabaseConnector
import java.sql.Array
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

class TmdbRepository(
    private val tmdbApi: TmdbApi
) : MovieDao, ShowDao, FileDao, Database {

    private val database by lazy {
        DatabaseConnector(System.getenv("ZPLEX_DATABASE_URL"))
    }

    init {
        connect()
        displayStatistics()
    }

    fun getAllGenres(): List<Genre> {
        val genres = mutableListOf<Genre>()
        val query = "SELECT * FROM genres ORDER BY id"
        try {
            getConnection().createStatement().use { statement ->
                statement.executeQuery(query).use { resultSet ->
                    resultSet?.let {
                        while (resultSet.next()) {
                            genres.add(parseResultSetForGenre(it))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving genres: ${e.message}")
        }
        return genres
    }

    fun batchAddGenres(genres: List<Genre>, type: String) {
        val connection = getConnection()
        var insertGenreStmt: PreparedStatement? = null
        try {
            println("Transaction started.")
            connection.autoCommit = false // Start transaction
            println("Inserting ${genres.size} genres for $type into the database.")

            val insertGenreSQL = """
            INSERT INTO genres (id, name, type) 
            VALUES (?, ?, ?)
            ON CONFLICT DO NOTHING 
            """.trimIndent()
            insertGenreStmt = connection.prepareStatement(insertGenreSQL)
            genres.forEach { genre ->
                insertGenreStmt.apply {
                    setInt(1, genre.id)
                    setString(2, genre.name)
                    setString(3, type)
                    addBatch()
                    clearParameters()
                }
            }
            insertGenreStmt.executeBatch()
            connection.commit()
            println("Transaction committed successfully")
        } catch (e: SQLException) {
            connection.rollback()
            e.printStackTrace()
        } finally {
            try {
                insertGenreStmt?.close()
            } catch (ex: SQLException) {
                ex.printStackTrace()
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /**
     * Retrieves the row count of a specified table in the database.
     *
     * @param connection The database connection.
     * @param tableName The name of the table to get the row count from.
     * @return The row count of the specified table.
     */
    private fun getTableRowCount(connection: Connection, tableName: String): Int {
        var rowCount = 0
        val query = "SELECT COUNT(*) AS row_count FROM $tableName"
        try {
            connection.createStatement().use { statement ->
                statement.executeQuery(query).use { resultSet ->
                    if (resultSet.next()) {
                        rowCount = resultSet.getInt("row_count")
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving row count of table \"$tableName\" : ${e.message}")
        }
        return rowCount
    }

    private fun displayStatistics() {
        val connection = getConnection()
        println("Movies: ${getTableRowCount(connection, "movies")}")
        println("Shows: ${getTableRowCount(connection, "shows")}")
    }

    override fun upsertMovie(movie: Movie, file: DriveFile) {
        val connection = getConnection()

        var insertMovieStmt: PreparedStatement? = null
        var insertFileStmt: PreparedStatement? = null
        var insertStudioStmt: PreparedStatement? = null
        var insertCastStmt: PreparedStatement? = null
        var insertCrewStmt: PreparedStatement? = null
        var insertExternalLinkStmt: PreparedStatement? = null

        try {
            println("Transaction started.")
            connection.autoCommit = false // Start transaction
            println("Inserting ${movie.title} with file id ${file.id} into the database.")

            // ======= INSERT files =======
            val insertFileSQL = """
            INSERT INTO files (id, name, size, modified_time)
            VALUES (?, ?, ?, ?)
            """.trimIndent()
            insertFileStmt = connection.prepareStatement(insertFileSQL).apply {
                setString(1, file.id)
                setString(2, file.name)
                setLong(3, file.size)
                setLong(4, file.modifiedTime)
            }
            insertFileStmt.executeUpdate()

            // ======= INSERT studios =======
            val insertStudioSQL = """
            INSERT INTO studios (id, logo_path, name, origin_country) 
            VALUES (?, ?, ?, ?)
            ON CONFLICT DO NOTHING 
            """.trimIndent()
            insertStudioStmt = connection.prepareStatement(insertStudioSQL)
            movie.studios.forEach { studio ->
                insertStudioStmt.apply {
                    setInt(1, studio.id)
                    setObject(2, studio.logo)
                    setString(3, studio.name)
                    setString(4, studio.country)
                    addBatch()
                    clearParameters()
                }
            }
            insertStudioStmt.executeBatch()

            // ======= INSERT movies =======
            val insertMovieSQL = """
            INSERT INTO movies (
                id, title, collection_id, file_id, imdb_id, imdb_rating, imdb_votes, release_date, release_year,
                parental_rating, runtime, poster_path, backdrop_path, logo_image, trailer_link, tagline, plot,
                director, genres, studios
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            insertMovieStmt = connection.prepareStatement(insertMovieSQL).apply {
                setInt(1, movie.id)
                setString(2, movie.title)
                setObject(3, movie.collectionId)
                setString(4, movie.fileId)
                setString(5, movie.imdbId)
                setObject(6, movie.imdbRating)
                setObject(7, movie.imdbVotes)
                setObject(8, movie.releaseDate)
                setObject(9, movie.releaseYear)
                setObject(10, movie.parentalRating)
                setObject(11, movie.runtime)
                setObject(12, movie.posterPath)
                setObject(13, movie.backdropPath)
                setObject(14, movie.logoImage)
                setObject(15, movie.trailerLink)
                setObject(16, movie.tagLine)
                setObject(17, movie.plot)
                setObject(18, movie.director)
                setObject(19, createIntegerArray(movie.genres.map { genre -> genre.id }))
                setArray(20, createIntegerArray(movie.studios.map { studio -> studio.id }))
            }
            insertMovieStmt.executeUpdate()

            // ======= INSERT casts =======
            val insertCastSQL = """
            INSERT INTO movie_casts (id, image, name, role, gender)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
            insertCastStmt = connection.prepareStatement(insertCastSQL)
            movie.cast.forEach { cast ->
                insertCastStmt.apply {
                    setInt(1, cast.id)
                    setObject(2, cast.image)
                    setString(3, cast.name)
                    setObject(4, cast.role)
                    setString(5, cast.gender.name)
                    addBatch()
                    clearParameters()
                }
            }
            insertCastStmt.executeBatch()

            // ======= INSERT crews =======
            val insertCrewSQL = """
            INSERT INTO movie_crews (id, image, name, job)
            VALUES (?, ?, ?, ?)
            """.trimIndent()
            insertCrewStmt = connection.prepareStatement(insertCrewSQL)
            movie.crew.forEach { crew ->
                insertCrewStmt.apply {
                    setInt(1, crew.id)
                    setObject(2, crew.image)
                    setString(3, crew.name)
                    setObject(4, crew.job)
                    addBatch()
                    clearParameters()
                }
            }
            insertCrewStmt.executeBatch()

            // ======= INSERT external links =======
            val insertExternalLinkSQL = """
            INSERT INTO movie_external_links (id, name, url)
            VALUES (?, ?, ?)
            """.trimIndent()
            insertExternalLinkStmt = connection.prepareStatement(insertExternalLinkSQL)
            movie.externalLinks.forEach { link ->
                insertExternalLinkStmt.apply {
                    setInt(1, link.id)
                    setString(2, link.name)
                    setString(3, link.url)
                    addBatch()
                    clearParameters()
                }
            }
            insertExternalLinkStmt.executeBatch()
            connection.commit()

            println("Transaction committed successfully")
        } catch (e: SQLException) {
            connection.rollback()
            e.printStackTrace()
        } finally {
            try {
                insertMovieStmt?.close()
                insertFileStmt?.close()
                insertStudioStmt?.close()
                insertCastStmt?.close()
                insertCrewStmt?.close()
                insertExternalLinkStmt?.close()
            } catch (ex: SQLException) {
                ex.printStackTrace()
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun getCommonMovieGenres(): List<Genre> {
        return getCommonGenres(MediaType.MOVIE)
    }

    override fun getCommonMovieStudios(): List<Pair<Int, String>> {
        return getCommonStudios(MediaType.MOVIE)
    }

    override fun getCommonMovieParentalRatings(): List<String> {
        return getCommonParentalRatings(MediaType.MOVIE)
    }

    override fun getCommonMovieYears(): List<Int> {
        return getCommonYears(MediaType.MOVIE)
    }

    private fun createIntegerArray(list: List<Int>): Array {
        return getConnection().createArrayOf("INTEGER", list.toTypedArray())
    }

    override fun updateModifiedTime(files: List<File>) {
        val query = """
            UPDATE files
            SET modified_time = ?
            WHERE id = ?
        """.trimIndent()
        val connection = getConnection()
        val updateStatement = connection.prepareStatement(query)
        try {
            connection.autoCommit = false
            for (file in files) {
                updateStatement.setLong(1, file.modifiedTime.value)
                updateStatement.setString(2, file.id)
                updateStatement.addBatch()
            }
            updateStatement.executeBatch()
            connection.commit()
            println("Updated ${files.size} files")
        } catch (e: SQLException) {
            println("Error occurred while updating movies: ${e.message}")
            try {
                connection.rollback() // Rollback the transaction if an error occurs
                println("Transaction rolled back due to an error.")
            } catch (ex: SQLException) {
                ex.printStackTrace()
            }
        } finally {
            try {
                updateStatement?.close()
            } catch (ex: SQLException) {
                ex.printStackTrace()
            }
            connection.autoCommit = true
        }
    }

    override fun upsertShow(show: Show, seasons: List<Season>, episodes: List<Episode>, files: List<DriveFile>) {
        val connection: Connection = getConnection()

        var insertShowStmt: PreparedStatement? = null
        var insertFileStmt: PreparedStatement? = null
        var insertStudioStmt: PreparedStatement? = null
        var insertCastStmt: PreparedStatement? = null
        var insertCrewStmt: PreparedStatement? = null
        var insertExternalLinkStmt: PreparedStatement? = null
        var insertSeasonStmt: PreparedStatement? = null
        var insertEpisodeStmt: PreparedStatement? = null

        try {
            println("Transaction started.")
            connection.autoCommit = false // Start a transaction

            println("Inserting ${show.title} with ${seasons.size} seasons and ${episodes.size} episodes into the database.")

            // ======= INSERT files =======
            val insertFileSQL = """
            INSERT INTO files (id, name, size, modified_time)
            VALUES (?, ?, ?, ?)
            """.trimIndent()
            insertFileStmt = connection.prepareStatement(insertFileSQL)

            files.forEach { file ->
                insertFileStmt.apply {
                    setString(1, file.id)
                    setString(2, file.name)
                    setLong(3, file.size)
                    setLong(4, file.modifiedTime)
                    addBatch()
                    clearParameters()
                }
            }
            insertFileStmt.executeBatch()

            // ======= INSERT studios =======
            val insertStudioSQL = """
            INSERT INTO studios (id, logo_path, name, origin_country) 
            VALUES (?, ?, ?, ?)
            ON CONFLICT DO NOTHING 
            """.trimIndent()
            insertStudioStmt = connection.prepareStatement(insertStudioSQL)
            show.studios.forEach { studio ->
                insertStudioStmt.apply {
                    setInt(1, studio.id)
                    setObject(2, studio.logo)
                    setString(3, studio.name)
                    setString(4, studio.country)
                    addBatch()
                    clearParameters()
                }
            }
            insertStudioStmt.executeBatch()

            // ======= INSERT shows =======
            val insertShowSQL = """
            INSERT INTO shows (
                id, title, imdb_id, imdb_rating, imdb_votes, release_date, release_year, release_year_to,
                parental_rating, poster_path, backdrop_path, logo_image, trailer_link, plot,
                director, genres, studios, modified_time
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO NOTHING
            """.trimIndent()
            insertShowStmt = connection.prepareStatement(insertShowSQL).apply {
                setInt(1, show.id)
                setString(2, show.title)
                setString(3, show.imdbId)
                setObject(4, show.imdbRating)
                setObject(5, show.imdbVotes)
                setObject(6, show.releaseDate)
                setInt(7, show.releasedYear)
                setObject(8, show.releaseYearTo)
                setObject(9, show.parentalRating)
                setObject(10, show.posterPath)
                setObject(11, show.backdropPath)
                setObject(12, show.logoImage)
                setObject(13, show.trailerLink)
                setObject(14, show.plot)
                setObject(15, show.director)
                setObject(16, createIntegerArray(show.genres.map { genre -> genre.id }))
                setArray(17, createIntegerArray(show.studios.map { studio -> studio.id }))
                setLong(18, show.modifiedTime)
            }
            insertShowStmt.executeUpdate()

            // ======= INSERT seasons =======
            val insertSeasonSQL = """
                INSERT INTO seasons (
                id, name, season_number, release_date, release_year, poster_path, show_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
            """.trimIndent()
            insertSeasonStmt = connection.prepareStatement(insertSeasonSQL)
            seasons.forEach { season ->
                insertSeasonStmt.apply {
                    setInt(1, season.id)
                    setString(2, season.name)
                    setInt(3, season.seasonNumber)
                    setObject(4, season.releaseDate)
                    setObject(5, season.releaseYear)
                    setString(6, season.posterPath)
                    setInt(7, season.showId)
                    addBatch()
                    clearParameters()
                }
            }
            insertSeasonStmt.executeBatch()

            // ======= INSERT episodes =======
            val insertEpisodeSQL = """
                INSERT INTO episodes (
                id, title, episode_number, season_number, overview, runtime, airdate, still_path, season_id, file_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            insertEpisodeStmt = connection.prepareStatement(insertEpisodeSQL)
            episodes.forEach { episode ->
                insertEpisodeStmt.apply {
                    setInt(1, episode.id)
                    setString(2, episode.title)
                    setInt(3, episode.episodeNumber)
                    setInt(4, episode.seasonNumber)
                    setObject(5, episode.overview)
                    setObject(6, episode.runtime)
                    setObject(7, episode.airdate)
                    setString(8, episode.stillPath)
                    setInt(9, episode.seasonId)
                    setString(10, episode.fileId)
                    addBatch()
                    clearParameters()
                }
            }
            insertEpisodeStmt.executeBatch()

            // ======= INSERT casts =======
            if (getRowCountForShow(show.id, "show_casts") != show.cast.size) {
                val deleteCastSQL = "DELETE FROM show_casts WHERE id = ?"
                val deleteCastStmt = connection.prepareStatement(deleteCastSQL)
                deleteCastStmt.setInt(1, show.id)
                deleteCastStmt.executeUpdate()

                val insertCastSQL = """
                INSERT INTO show_casts (id, image, name, role, gender)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent()
                insertCastStmt = connection.prepareStatement(insertCastSQL)
                show.cast.forEach { cast ->
                    insertCastStmt.apply {
                        setInt(1, cast.id)
                        setObject(2, cast.image)
                        setString(3, cast.name)
                        setObject(4, cast.role)
                        setString(5, cast.gender.name)
                        addBatch()
                        clearParameters()
                    }
                }
                insertCastStmt.executeBatch()
            }

            // ======= INSERT crews =======
            if (getRowCountForShow(show.id, "show_crews") != show.cast.size) {
                val deleteCastSQL = "DELETE FROM show_crews WHERE id = ?"
                val deleteCastStmt = connection.prepareStatement(deleteCastSQL)
                deleteCastStmt.setInt(1, show.id)
                deleteCastStmt.executeUpdate()

                val insertCrewSQL = """
                INSERT INTO show_crews (id, image, name, job)
                VALUES (?, ?, ?, ?)
                """.trimIndent()
                insertCrewStmt = connection.prepareStatement(insertCrewSQL)
                show.crew.forEach { crew ->
                    insertCrewStmt.apply {
                        setInt(1, crew.id)
                        setObject(2, crew.image)
                        setString(3, crew.name)
                        setObject(4, crew.job)
                        addBatch()
                        clearParameters()
                    }
                }
                insertCrewStmt.executeBatch()
            }

            // ======= INSERT external links =======
            if (getRowCountForShow(show.id, "show_external_links") != show.cast.size) {
                val deleteCastSQL = "DELETE FROM show_external_links WHERE id = ?"
                val deleteCastStmt = connection.prepareStatement(deleteCastSQL)
                deleteCastStmt.setInt(1, show.id)
                deleteCastStmt.executeUpdate()
                val insertExternalLinkSQL = """
                INSERT INTO show_external_links (id, name, url)
                VALUES (?, ?, ?)
                """.trimIndent()
                insertExternalLinkStmt = connection.prepareStatement(insertExternalLinkSQL)
                show.externalLinks.forEach { link ->
                    insertExternalLinkStmt.apply {
                        setInt(1, link.id)
                        setString(2, link.name)
                        setString(3, link.url)
                        addBatch()
                        clearParameters()
                    }
                }
                insertExternalLinkStmt.executeBatch()
            }

            connection.commit()
            println("Transaction committed successfully")
        } catch (e: SQLException) {
            connection.rollback()
            e.printStackTrace()
        } finally {
            try {
                insertShowStmt?.close()
                insertSeasonStmt?.close()
                insertEpisodeStmt?.close()
                insertFileStmt?.close()
                insertStudioStmt?.close()
                insertCastStmt?.close()
                insertCrewStmt?.close()
                insertExternalLinkStmt?.close()
            } catch (ex: SQLException) {
                ex.printStackTrace()
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun getAllShows(): List<Show> {
        val shows = mutableListOf<Show>()
        val query = "SELECT * FROM shows ORDER BY id"
        try {
            getConnection().createStatement().use { statement ->
                statement.executeQuery(query).use { resultSet ->
                    resultSet?.let {
                        while (resultSet.next()) {
                            // shows.add(parseResultSetForShow(it))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving shows: ${e.message}")
        }
        return shows
    }


    override fun updateShowsModifiedTime(shows: List<Show>) {
        val queryUpdateShow = """
            UPDATE shows
            SET modified_time = ?
            WHERE id = ?
        """.trimIndent()
        val connection = getConnection()
        val updateStatement = connection.prepareStatement(queryUpdateShow)
        try {
            connection.autoCommit = false
            for (show in shows) {
                updateStatement.setLong(1, show.modifiedTime)
                updateStatement.setInt(2, show.id)
                updateStatement.addBatch()
            }
            updateStatement.executeBatch()
            connection.commit()
            println("Updated ${shows.size} shows")
        } catch (e: SQLException) {
            println("Error occurred while updating movies: ${e.message}")
            try {
                connection.rollback() // Rollback the transaction if an error occurs
                println("Transaction rolled back due to an error.")
            } catch (ex: SQLException) {
                ex.printStackTrace()
            }
        } finally {
            try {
                updateStatement?.close()
            } catch (ex: SQLException) {
                ex.printStackTrace()
            }
            connection.autoCommit = true
        }
    }

    override fun getCommonShowGenres(): List<Genre> {
        return getCommonGenres(MediaType.SHOW)
    }

    override fun getCommonShowStudios(): List<Pair<Int, String>> {
        return getCommonStudios(MediaType.SHOW)
    }

    override fun getCommonShowParentalRatings(): List<String> {
        return getCommonParentalRatings(MediaType.SHOW)
    }

    override fun getCommonShowYears(): List<Int> {
        return getCommonYears(MediaType.SHOW)
    }

    private fun getCommonGenres(type: MediaType): List<Genre> {
        val genres = mutableListOf<Genre>()
        val primaryTable = when (type) {
            MediaType.MOVIE -> "movies"
            MediaType.SHOW -> "shows"
        }
        val query = """
        SELECT DISTINCT g.id, g.name
        FROM genres g
        JOIN (
            SELECT DISTINCT UNNEST(genres) AS genre_id
            FROM $primaryTable
        ) AS unique_genre_ids ON g.id = unique_genre_ids.genre_id
        WHERE g.type IN ('both', '${if (type == MediaType.MOVIE) "movie" else "show"}')
        ORDER BY g.name;
        """.trimIndent()

        try {
            getConnection().createStatement().use { statement ->
                statement.executeQuery(query).use { resultSet ->
                    resultSet?.let {
                        while (resultSet.next()) {
                            genres.add(
                                Genre(
                                    id = resultSet.getInt("id"),
                                    name = resultSet.getString("name")
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving common genres from shows table: ${e.message}")
        }
        return genres.toList()
    }

    private fun getCommonStudios(type: MediaType): List<Pair<Int, String>> {
        val studios = mutableListOf<Pair<Int, String>>()
        val primaryTable = when (type) {
            MediaType.MOVIE -> "movies"
            MediaType.SHOW -> "shows"
        }
        val query = """SELECT DISTINCT s.id, s.name
        FROM studios s
             JOIN (SELECT DISTINCT studios[1] AS studio_id FROM $primaryTable)
             AS unique_studio_ids ON s.id = unique_studio_ids.studio_id
        ORDER BY s.name;
        """.trimIndent()
        try {
            getConnection().createStatement().use { statement ->
                statement.executeQuery(query).use { resultSet ->
                    resultSet?.let {
                        while (resultSet.next()) {
                            studios.add(
                                Pair(
                                    resultSet.getInt("id"),
                                    resultSet.getString("name")
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving common parental ratings from shows table: ${e.message}")
        }
        return studios.toList()
    }

    private enum class MediaType {
        MOVIE, SHOW
    }

    private fun getCommonParentalRatings(type: MediaType): List<String> {
        val ratings = mutableListOf<String>()
        val primaryTable = when (type) {
            MediaType.MOVIE -> "movies"
            MediaType.SHOW -> "shows"
        }
        val query = """SELECT DISTINCT parental_rating FROM $primaryTable 
        WHERE parental_rating IS NOT NULL 
        ORDER BY parental_rating
        """.trimIndent()
        try {
            getConnection().createStatement().use { statement ->
                statement.executeQuery(query).use { resultSet ->
                    resultSet?.let {
                        while (resultSet.next()) {
                            ratings.add(resultSet.getString("parental_rating"))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving common parental ratings from shows table: ${e.message}")
        }
        return ratings.toList()
    }

    private fun getCommonYears(type: MediaType): List<Int> {
        val years = mutableListOf<Int>()
        val primaryTable = when (type) {
            MediaType.MOVIE -> "movies"
            MediaType.SHOW -> "shows"
        }
        val query = "SELECT DISTINCT release_year FROM $primaryTable ORDER BY release_year"
        try {
            getConnection().createStatement().use { statement ->
                statement.executeQuery(query).use { resultSet ->
                    resultSet?.let {
                        while (resultSet.next()) {
                            years.add(resultSet.getInt("release_year"))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving common years: ${e.message}")
        }
        return years.toList()
    }

    override fun getAllSeasonIds(): List<Int> {
        val seasonIds = mutableListOf<Int>()
        val query = "SELECT id FROM seasons ORDER BY id"
        try {
            getConnection().createStatement().use { statement ->
                statement.executeQuery(query).use { resultSet ->
                    resultSet?.let {
                        while (resultSet.next()) {
                            seasonIds.add(resultSet.getInt("id"))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving shows: ${e.message}")
        }
        return seasonIds
    }

    private fun getFiles(query: String): List<DriveFile> {
        val files = mutableListOf<DriveFile>()
        try {
            getConnection().prepareStatement(query).use { statement ->
                statement.executeQuery().use { resultSet ->
                    resultSet?.let {
                        while (resultSet.next()) {
                            files.add(parseResultSetForDriveFile(it))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving files: ${e.message}")
        }
        return files
    }

    override fun getAllFiles(): List<DriveFile> {
        return getFiles("SELECT * FROM files ORDER BY id")
    }

    override fun getAllMoviesFiles(): List<DriveFile> {
        return getFiles("SELECT f.id, f.name, f.size, f.modified_time FROM files f INNER JOIN movies m ON f.id = m.file_id ORDER BY f.id")
    }

    override fun getAllEpisodesFiles(): List<DriveFile> {
        return getFiles("SELECT f.id, f.name, f.size, f.modified_time FROM files f INNER JOIN episodes e ON f.id = e.file_id ORDER BY f.id")
    }

    override fun getFileById(id: String): DriveFile? {
        val query = "SELECT * FROM files WHERE id = ? LIMIT 1"
        try {
            getConnection().prepareStatement(query).use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        resultSet?.let { return parseResultSetForDriveFile(resultSet) }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving episode by id: ${e.message}")
        }
        return null
    }

    override fun deleteFileById(id: String) {
        val query = "DELETE FROM files WHERE id = ?"
        try {
            getConnection().prepareStatement(query).use { statement ->
                statement.setString(1, id)
                statement.executeUpdate()
            }
            println("File deleted: $id")
        } catch (e: SQLException) {
            println("Error occurred while deleting file: ${e.message}")
        }
    }

    private fun parseResultSetForDriveFile(resultSet: ResultSet): DriveFile {
        return DriveFile(
            resultSet.getString("id"),
            resultSet.getString("name"),
            resultSet.getLong("size"),
            resultSet.getLong("modified_time")
        )
    }

    private fun parseResultSetForGenre(resultSet: ResultSet): Genre {
        return Genre(
            resultSet.getInt("id"),
            resultSet.getString("name")
        )
    }

    fun fetchShow(id: Int): TvResponse {
        val extras = listOf("images", "external_ids", "credits", "videos")
        return tmdbApi.getShow(id, append_to_response = extras.joinToString(","))
    }

    fun fetchSeason(id: Int, seasonNumber: Int): SeasonResponse {
        return tmdbApi.getSeason(id, seasonNumber)
    }

    fun fetchMovie(id: Int): MovieResponse {
        val extras = listOf("images", "external_ids", "credits", "videos")
        return tmdbApi.getMovie(id, append_to_response = extras.joinToString(","))
    }

    override fun connect() {
        if (database.connection == null) {
            database.connect()
        }
    }

    override fun disconnect() {
        database.disconnect()
    }

    override fun getConnection(): Connection {
        if (database.connection == null) connect()
        if (database.connection!!.isClosed) connect()
        return database.connection!!
    }

    fun deleteFiles(deleteIds: List<String>) {
        val query = "DELETE FROM files WHERE id = ?"
        try {
            getConnection().prepareStatement(query).use { statement ->
                for (id in deleteIds) {
                    statement.setString(1, id)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
            println("Deleted ${deleteIds.size} files")
        } catch (e: SQLException) {
            println("Error occurred while deleting files: ${e.message}")
            e.nextException
        }
    }

    private fun getRowCountForShow(showId: Int, tableName: String): Int {
        val countSQL = "SELECT COUNT(*) FROM $tableName WHERE id = ?"
        var count = 0
        getConnection().prepareStatement(countSQL).use { preparedStatement ->
            preparedStatement.setInt(1, showId)
            preparedStatement.executeQuery().use { resultSet ->
                if (resultSet.next()) {
                    count = resultSet.getInt(1)
                }
            }
        }
        return count
    }

}