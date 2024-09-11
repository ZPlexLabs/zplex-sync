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
                setInt(9, movie.releaseYear)
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
            INSERT INTO casts (id, image, name, role, gender)
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
            INSERT INTO crews (id, image, name, job)
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
            INSERT INTO external_links (id, name, url)
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
        val fileStatement: PreparedStatement?
        var showStatement: PreparedStatement? = null
        var seasonStatement: PreparedStatement? = null
        var episodeStatement: PreparedStatement? = null

        try {
            connection.autoCommit = false // Start a transaction

            println("Starting upsertShow method")

            val insertFileQuery = """
                INSERT INTO files (id, name, size, modified_time)
                VALUES (?, ?, ?, ?)
            """.trimIndent()

            fileStatement = connection.prepareStatement(insertFileQuery)

            files.forEach { file ->
                fileStatement.setString(1, file.id)
                fileStatement.setString(2, file.name)
                fileStatement.setLong(3, file.size)
                fileStatement.setLong(4, file.modifiedTime)
                fileStatement.executeUpdate()
            }

            // Insert or update the show
            val showQuery = """
                INSERT INTO shows (id, name, poster_path, vote_average, modified_time)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
            """.trimIndent()
            showStatement = connection.prepareStatement(showQuery)
            showStatement.setInt(1, show.id)
            showStatement.setString(2, show.name)
            showStatement.setString(3, show.posterPath)
            showStatement.setDouble(4, show.voteAverage ?: 0.0)
            showStatement.setLong(5, show.modifiedTime)
            println("Executing showStatement: $showStatement")
            showStatement.executeUpdate()
            println("Show inserted or updated successfully")

            // Insert seasons
            val seasonQuery = """
                INSERT INTO seasons (id, name, poster_path, season_number, show_id)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
            """.trimIndent()
            seasonStatement = connection.prepareStatement(seasonQuery)
            for (season in seasons) {
                seasonStatement.setInt(1, season.id)
                seasonStatement.setString(2, season.name)
                seasonStatement.setString(3, season.posterPath)
                seasonStatement.setInt(4, season.seasonNumber)
                seasonStatement.setInt(5, season.showId)
                seasonStatement.addBatch()
            }
            println("Executing seasonStatement: $seasonStatement")
            seasonStatement.executeBatch()
            println("Seasons inserted successfully")

            // Insert episodes
            val episodeQuery = """
                INSERT INTO episodes (id, title, episode_number, season_number, still_path, season_id, file_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            episodeStatement = connection.prepareStatement(episodeQuery)
            for (episode in episodes) {
                episodeStatement.setInt(1, episode.id)
                episodeStatement.setString(2, episode.title)
                episodeStatement.setInt(3, episode.episodeNumber)
                episodeStatement.setInt(4, episode.seasonNumber)
                episodeStatement.setString(5, episode.stillPath)
                episodeStatement.setInt(6, episode.seasonId)
                episodeStatement.setString(7, episode.fileId)
                episodeStatement.addBatch()
            }
            println("Executing episodeStatement: $episodeStatement")
            episodeStatement.executeBatch()
            println("Episodes inserted successfully")

            connection.commit()
            println("Transaction committed successfully")
        } catch (e: SQLException) {
            connection.rollback()
            e.printStackTrace()
        } finally {
            try {
                showStatement?.close()
                seasonStatement?.close()
                episodeStatement?.close()
            } catch (ex: SQLException) {
                ex.printStackTrace()
            }
            connection.autoCommit = true
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
                            shows.add(parseResultSetForShow(it))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving shows: ${e.message}")
        }
        return shows
    }

    override fun getAllShowsIds(): List<Int> {
        val showIds = mutableListOf<Int>()
        val query = "SELECT id FROM shows ORDER BY id"
        try {
            getConnection().createStatement().use { statement ->
                statement.executeQuery(query).use { resultSet ->
                    resultSet?.let {
                        while (resultSet.next()) {
                            showIds.add(resultSet.getInt("id"))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving shows: ${e.message}")
        }
        return showIds
    }


    override fun getShowById(id: Int): Show? {
        val query = "SELECT * FROM shows WHERE id = ? LIMIT 1"
        try {
            getConnection().prepareStatement(query).use { statement ->
                statement.setInt(1, id)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        resultSet?.let { return parseResultSetForShow(resultSet) }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving show by id: ${e.message}")
            throw e
        }
        return null
    }

    override fun deleteShowById(id: Int) {
        val query = "DELETE FROM shows WHERE id = ?"
        try {
            getConnection().prepareStatement(query).use { statement ->
                statement.setInt(1, id)
                statement.executeUpdate()
            }
            println("Show deleted: $id")
        } catch (e: SQLException) {
            println("Error occurred while deleting show: ${e.message}")
        }
    }

    override fun batchAddShow(shows: List<Show>) {
        val connection: Connection = getConnection()
        var showStatement: PreparedStatement? = null

        try {
            connection.autoCommit = false // Start a transaction

            println("Starting batchAddShow method for ${shows.size} shows")

            // Insert or update the show
            val showQuery = """
                INSERT INTO shows (id, name, poster_path, vote_average, modified_time)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
            showStatement = connection.prepareStatement(showQuery)

            shows.forEach { show ->
                showStatement.setInt(1, show.id)
                showStatement.setString(2, show.name)
                showStatement.setString(3, show.posterPath)
                showStatement.setDouble(4, show.voteAverage ?: 0.0)
                showStatement.setLong(5, show.modifiedTime)
                showStatement.addBatch()
            }
            showStatement.executeBatch()
            println("Show inserted or updated successfully")
            connection.commit()
            println("Transaction committed successfully")
        } catch (e: SQLException) {
            connection.rollback()
            e.printStackTrace()
        } finally {
            try {
                showStatement?.close()
            } catch (ex: SQLException) {
                ex.printStackTrace()
            }
            connection.autoCommit = true
        }
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

    override fun addSeason(showId: Int, season: Season) {
        val query = """
            INSERT INTO seasons (id, name, poster_path, season_number, show_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
        """.trimIndent()
        try {
            getConnection().prepareStatement(query).use { statement ->
                statement.setInt(1, season.id)
                statement.setString(2, season.name)
                statement.setString(3, season.posterPath)
                statement.setInt(4, season.seasonNumber)
                statement.setInt(5, showId)
                statement.executeUpdate()
            }
            println("Season inserted: ${season.name}")
        } catch (e: SQLException) {
            println("Error occurred while inserting season: ${e.message}")
        }
    }

    override fun deleteSeason(seasonId: Int) {
        val query = "DELETE FROM seasons WHERE id = ?"
        try {
            getConnection().prepareStatement(query).use { statement ->
                statement.setInt(1, seasonId)
                statement.executeUpdate()
            }
            println("Season deleted: $seasonId")
        } catch (e: SQLException) {
            println("Error occurred while deleting season: ${e.message}")
        }
    }

    override fun getSeasons(showId: Int): List<Season> {
        val seasons = mutableListOf<Season>()
        val query = "SELECT * FROM seasons WHERE show_id = ? ORDER BY season_number"
        try {
            getConnection().prepareStatement(query).use { statement ->
                statement.setInt(1, showId)
                statement.executeQuery().use { resultSet ->
                    resultSet?.let {
                        while (resultSet.next()) {
                            seasons.add(parseResultSetForSeason(it))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving seasons: ${e.message}")
        }
        return seasons
    }

    override fun getSeasonById(seasonId: Int): Season? {
        val query = "SELECT * FROM seasons WHERE id = ? LIMIT 1"
        try {
            getConnection().prepareStatement(query).use { statement ->
                statement.setInt(1, seasonId)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        resultSet?.let { return parseResultSetForSeason(resultSet) }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving season by id: ${e.message}")
        }
        return null
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

    override fun batchAddSeason(seasons: List<Season>) {
        val connection: Connection = getConnection()
        var seasonStatement: PreparedStatement? = null

        try {
            connection.autoCommit = false // Start a transaction

            println("Starting batchAddSeason method for ${seasons.size} seasons")

            // Insert or update the seasons
            val seasonQuery = """
                INSERT INTO seasons (id, name, poster_path, season_number, show_id)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
            seasonStatement = connection.prepareStatement(seasonQuery)
            seasons.forEach { season ->
                seasonStatement.setInt(1, season.id)
                seasonStatement.setString(2, season.name)
                seasonStatement.setString(3, season.posterPath)
                seasonStatement.setInt(4, season.seasonNumber)
                seasonStatement.setInt(5, season.showId)
                seasonStatement.addBatch()
            }
            seasonStatement.executeBatch()
            println("Seasons inserted or updated successfully")
            connection.commit()
            println("Transaction committed successfully")
        } catch (e: SQLException) {
            connection.rollback()
            e.printStackTrace()
        } finally {
            try {
                seasonStatement?.close()
            } catch (ex: SQLException) {
                ex.printStackTrace()
            }
            connection.autoCommit = true
        }
    }

    override fun addEpisode(seasonId: Int, episode: Episode, file: DriveFile) {
        val episodeQuery = """
            INSERT INTO episodes (id, title, episode_number, season_number, still_path, season_id, file_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
        """.trimIndent()

        val insertFileQuery = """
            INSERT INTO files (id, name, size, modified_time)
            VALUES (?, ?, ?, ?)
        """.trimIndent()

        try {
            val connection: Connection = getConnection()
            connection.autoCommit = false // Start a transaction


            val fileStatement = connection.prepareStatement(insertFileQuery)
            fileStatement.setString(1, file.id)
            fileStatement.setString(2, file.name)
            fileStatement.setLong(3, file.size)
            fileStatement.setLong(4, file.modifiedTime)
            fileStatement.executeUpdate()

            val episodeStatement = connection.prepareStatement(episodeQuery)

            episodeStatement.setInt(1, episode.id)
            episodeStatement.setString(2, episode.title)
            episodeStatement.setInt(3, episode.episodeNumber)
            episodeStatement.setInt(4, episode.seasonNumber)
            episodeStatement.setString(5, episode.stillPath)
            episodeStatement.setInt(6, seasonId)
            episodeStatement.setString(7, file.id)
            episodeStatement.executeUpdate()
            connection.commit()

            println("Episode inserted: ${episode.title}")
        } catch (e: SQLException) {
            println("Error occurred while inserting episode: ${e.message}")
        }
    }

    override fun addEpisodes(seasonId: Int, episodes: MutableMap<DriveFile, Episode>) {
        val episodeQuery = """
            INSERT INTO episodes (id, title, episode_number, season_number, still_path, season_id, file_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
        """.trimIndent()

        val insertFileQuery = """
            INSERT INTO files (id, name, size, modified_time)
            VALUES (?, ?, ?, ?)
        """.trimIndent()

        val connection: Connection = getConnection()
        val fileStatement = connection.prepareStatement(insertFileQuery)
        val episodeStatement = connection.prepareStatement(episodeQuery)

        try {
            connection.autoCommit = false // Start a transaction

            for (episodeMap in episodes) {
                val episode = episodeMap.value
                val file = episodeMap.key
                fileStatement.setString(1, file.id)
                fileStatement.setString(2, file.name)
                fileStatement.setLong(3, file.size)
                fileStatement.setLong(4, file.modifiedTime)
                fileStatement.addBatch()

                episodeStatement.setInt(1, episode.id)
                episodeStatement.setString(2, episode.title)
                episodeStatement.setInt(3, episode.episodeNumber)
                episodeStatement.setInt(4, episode.seasonNumber)
                episodeStatement.setString(5, episode.stillPath)
                episodeStatement.setInt(6, seasonId)
                episodeStatement.setString(7, file.id)
                episodeStatement.addBatch()
            }
            fileStatement.executeBatch()
            episodeStatement.executeBatch()

            connection.commit()

            println("Inserted ${episodes.size} episodes.")
        } catch (e: SQLException) {
            connection.rollback()
            e.printStackTrace()
            println("Error occurred while inserting episode: ${e.message}")
        } finally {
            try {
                fileStatement?.close()
                episodeStatement?.close()
            } catch (ex: SQLException) {
                ex.printStackTrace()
            }
            connection.autoCommit = true
        }
    }

    override fun deleteEpisode(episodeId: Int) {
        val query = "DELETE FROM episodes WHERE id = ?"
        try {
            getConnection().prepareStatement(query).use { statement ->
                statement.setInt(1, episodeId)
                statement.executeUpdate()
            }
            println("Episode deleted: $episodeId")
        } catch (e: SQLException) {
            println("Error occurred while deleting episode: ${e.message}")
        }
    }

    override fun getEpisodes(seasonId: Int): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val query = "SELECT * FROM episodes WHERE season_id = ? ORDER BY episode_number"
        try {
            getConnection().prepareStatement(query).use { statement ->
                statement.setInt(1, seasonId)
                statement.executeQuery().use { resultSet ->
                    resultSet?.let {
                        while (resultSet.next()) {
                            episodes.add(parseResultSetForEpisode(it))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving episodes: ${e.message}")
        }
        return episodes
    }

    override fun getEpisodeById(episodeId: Int): Episode? {
        val query = "SELECT * FROM episodes WHERE id = ? LIMIT 1"
        try {
            getConnection().prepareStatement(query).use { statement ->
                statement.setInt(1, episodeId)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        resultSet?.let { return parseResultSetForEpisode(resultSet) }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving episode by id: ${e.message}")
        }
        return null
    }

    override fun batchAddEpisodeAndFiles(episodes: List<Episode>, files: List<DriveFile>) {
        val connection: Connection = getConnection()
        var fileStatement: PreparedStatement? = null
        var episodeStatement: PreparedStatement? = null

        try {
            connection.autoCommit = false // Start a transaction

            println("Starting batchAddEpisodeAndFiles method for ${episodes.size} episodes and ${files.size} files")

            // Insert files
            val insertFileQuery = """
                INSERT INTO files (id, name, size, modified_time)
                VALUES (?, ?, ?, ?)
            """.trimIndent()
            fileStatement = connection.prepareStatement(insertFileQuery)

            files.forEach { file ->
                fileStatement.setString(1, file.id)
                fileStatement.setString(2, file.name)
                fileStatement.setLong(3, file.size)
                fileStatement.setLong(4, file.modifiedTime)
                fileStatement.addBatch()
            }
            println("Executing batch files")
            fileStatement.executeBatch()
            println("Files inserted successfully")

            // Insert episodes
            val episodeQuery = """
                INSERT INTO episodes (id, title, episode_number, season_number, still_path, season_id, file_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            episodeStatement = connection.prepareStatement(episodeQuery)
            episodes.forEach { episode ->
                episodeStatement.setInt(1, episode.id)
                episodeStatement.setString(2, episode.title)
                episodeStatement.setInt(3, episode.episodeNumber)
                episodeStatement.setInt(4, episode.seasonNumber)
                episodeStatement.setString(5, episode.stillPath)
                episodeStatement.setInt(6, episode.seasonId)
                episodeStatement.setString(7, episode.fileId)
                episodeStatement.addBatch()
            }
            println("Executing batch episodes")
            episodeStatement.executeBatch()
            println("Episodes inserted or updated successfully")

            connection.commit()
            println("Transaction committed successfully")
        } catch (e: SQLException) {
            connection.rollback()
            e.printStackTrace()
        } finally {
            try {
                fileStatement?.close()
                episodeStatement?.close()
            } catch (ex: SQLException) {
                ex.printStackTrace()
            }
            connection.autoCommit = true
        }
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

    private fun parseResultSetForShow(resultSet: ResultSet): Show {
        return Show(
            resultSet.getInt("id"),
            resultSet.getString("name"),
            resultSet.getString("poster_path"),
            resultSet.getDouble("vote_average"),
            resultSet.getLong("modified_time")
        )
    }

    private fun parseResultSetForSeason(resultSet: ResultSet): Season {
        return Season(
            resultSet.getInt("id"),
            resultSet.getString("name"),
            resultSet.getString("poster_path"),
            resultSet.getInt("season_number"),
            resultSet.getInt("show_id")
        )
    }

    private fun parseResultSetForEpisode(resultSet: ResultSet): Episode {
        return Episode(
            resultSet.getInt("id"),
            resultSet.getString("title"),
            resultSet.getInt("episode_number"),
            resultSet.getInt("season_number"),
            resultSet.getString("still_path"),
            resultSet.getInt("season_id"),
            resultSet.getString("file_id")
        )
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

}