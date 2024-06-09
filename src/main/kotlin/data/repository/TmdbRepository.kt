package zechs.zplex.sync.data.repository

import data.model.DriveFile
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import zechs.zplex.sync.data.local.Database
import zechs.zplex.sync.data.local.FileDao
import zechs.zplex.sync.data.local.MovieDao
import zechs.zplex.sync.data.local.ShowDao
import zechs.zplex.sync.data.model.Episode
import zechs.zplex.sync.data.model.Movie
import zechs.zplex.sync.data.model.MovieResponse
import zechs.zplex.sync.data.model.Season
import zechs.zplex.sync.data.model.SeasonResponse
import zechs.zplex.sync.data.model.Show
import zechs.zplex.sync.data.model.TvResponse
import zechs.zplex.sync.data.remote.TmdbApi
import zechs.zplex.sync.utils.DatabaseConnector

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
        var fileStatement: PreparedStatement? = null
        var movieStatement: PreparedStatement? = null

        val insertFileQuery = """
            INSERT INTO files (id, name, size)
            VALUES (?, ?, ?)
        """.trimIndent()

        val insertMovieQuery = """
            INSERT INTO movies (id, title, poster_path, vote_average, year, file_id)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        try {
            println("Transaction started.")
            println("Inserting ${movie.title} with file ${file.name} into the database.")
            connection.autoCommit = false

            // Insert data into the files table
            fileStatement = connection.prepareStatement(insertFileQuery)
            fileStatement.setString(1, file.id)
            fileStatement.setString(2, file.name)
            fileStatement.setLong(3, file.size)
            fileStatement.executeUpdate()

            movieStatement = connection.prepareStatement(insertMovieQuery)
            movieStatement.setInt(1, movie.id)
            movieStatement.setString(2, movie.title)
            movieStatement.setString(3, movie.posterPath)
            movieStatement.setDouble(4, movie.voteAverage ?: 0.0)
            movieStatement.setInt(5, movie.releaseYear)
            movieStatement.setString(6, file.id)
            movieStatement.executeUpdate()

            connection.commit()
            println("Transaction committed successfully.")
        } catch (e: SQLException) {
            try {
                connection.rollback() // Rollback the transaction if an error occurs
                println("Transaction rolled back due to an error.")
            } catch (ex: SQLException) {
                ex.printStackTrace()
            }
            e.printStackTrace()
        } finally {
            fileStatement?.close()
            movieStatement?.close()
            connection.autoCommit = true
        }
    }

    override fun getAllMovies(): List<Movie> {
        val movies = mutableListOf<Movie>()
        val query = "SELECT * FROM movies ORDER BY id"
        try {
            getConnection().createStatement().use { statement ->
                statement.executeQuery(query).use { resultSet ->
                    resultSet?.let {
                        while (resultSet.next()) {
                            movies.add(parseResultSetForMovie(it))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving movies: ${e.message}")
        }
        return movies
    }

    override fun getMovieById(id: Int): Movie? {
        val query = "SELECT * FROM movies WHERE id = ? LIMIT 1"
        try {
            getConnection().prepareStatement(query).use { statement ->
                statement.setInt(1, id)
                statement.executeQuery().use { resultSet ->
                    resultSet?.let {
                        if (resultSet.next()) {
                            return parseResultSetForMovie(resultSet)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            println("Error occurred while retrieving movie by id: ${e.message}")
        }
        return null
    }

    override fun deleteMovieById(id: Int) {
        val query = "DELETE FROM movies WHERE id = ?"
        try {
            getConnection().prepareStatement(query).use { statement ->
                statement.setInt(1, id)
                statement.executeUpdate()
            }
            println("Movie deleted: $id")
        } catch (e: SQLException) {
            println("Error occurred while deleting movie: ${e.message}")
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
                INSERT INTO files (id, name, size)
                VALUES (?, ?, ?)
            """.trimIndent()

            fileStatement = connection.prepareStatement(insertFileQuery)

            files.forEach { file ->
                fileStatement.setString(1, file.id)
                fileStatement.setString(2, file.name)
                fileStatement.setLong(3, file.size)
                fileStatement.executeUpdate()
            }

            // Insert or update the show
            val showQuery = """
                INSERT INTO shows (id, name, poster_path, vote_average)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
            """.trimIndent()
            showStatement = connection.prepareStatement(showQuery)
            showStatement.setInt(1, show.id)
            showStatement.setString(2, show.name)
            showStatement.setString(3, show.posterPath)
            showStatement.setDouble(4, show.voteAverage ?: 0.0)
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
                INSERT INTO shows (id, name, poster_path, vote_average)
                VALUES (?, ?, ?, ?)
            """.trimIndent()
            showStatement = connection.prepareStatement(showQuery)

            shows.forEach { show ->
                showStatement.setInt(1, show.id)
                showStatement.setString(2, show.name)
                showStatement.setString(3, show.posterPath)
                showStatement.setDouble(4, show.voteAverage ?: 0.0)
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
            INSERT INTO files (id, name, size)
            VALUES (?, ?, ?)
        """.trimIndent()

        try {
            val connection: Connection = getConnection()
            connection.autoCommit = false // Start a transaction


            val fileStatement = connection.prepareStatement(insertFileQuery)
            fileStatement.setString(1, file.id)
            fileStatement.setString(2, file.name)
            fileStatement.setLong(3, file.size)
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
            INSERT INTO files (id, name, size)
            VALUES (?, ?, ?)
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

    override fun batchAddEpisode(episodes: List<Episode>) {
        val connection: Connection = getConnection()
        var episodeStatement: PreparedStatement? = null

        try {
            connection.autoCommit = false // Start a transaction

            println("Starting batchAddEpisode method for ${episodes.size} episodes")

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
            episodeStatement.executeBatch()
            println("Episodes inserted or updated successfully")
            connection.commit()
            println("Transaction committed successfully")
        } catch (e: SQLException) {
            connection.rollback()
            e.printStackTrace()
        } finally {
            try {
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
        return getFiles("SELECT f.id, f.name, f.size FROM files f INNER JOIN movies m ON f.id = m.file_id ORDER BY f.id")
    }

    override fun getAllEpisodesFiles(): List<DriveFile> {
        return getFiles("SELECT f.id, f.name, f.size FROM files f INNER JOIN episodes e ON f.id = e.file_id ORDER BY f.id")
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

    override fun batchAddFiles(files: List<DriveFile>) {
        val connection: Connection = getConnection()
        var fileStatement: PreparedStatement? = null

        try {
            connection.autoCommit = false // Start a transaction

            println("Starting batchAddFiles method for ${files.size} files")

            // Insert or update the seasons
            val insertFileQuery = """
                INSERT INTO files (id, name, size)
                VALUES (?, ?, ?)
            """.trimIndent()

            fileStatement = connection.prepareStatement(insertFileQuery)

            files.forEach { file ->
                fileStatement.setString(1, file.id)
                fileStatement.setString(2, file.name)
                fileStatement.setLong(3, file.size)
                fileStatement.executeUpdate()
            }
            fileStatement.executeBatch()
            println("Files inserted successfully")
            connection.commit()
            println("Transaction committed successfully")
        } catch (e: SQLException) {
            connection.rollback()
            e.printStackTrace()
        } finally {
            try {
                fileStatement?.close()
            } catch (ex: SQLException) {
                ex.printStackTrace()
            }
            connection.autoCommit = true
        }
    }

    private fun parseResultSetForMovie(resultSet: ResultSet): Movie {
        return Movie(
            resultSet.getInt("id"),
            resultSet.getString("title"),
            resultSet.getString("poster_path"),
            resultSet.getDouble("vote_average"),
            resultSet.getInt("year"),
            resultSet.getString("file_id")
        )
    }

    private fun parseResultSetForShow(resultSet: ResultSet): Show {
        return Show(
            resultSet.getInt("id"),
            resultSet.getString("name"),
            resultSet.getString("poster_path"),
            resultSet.getDouble("vote_average")
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
            resultSet.getLong("size")
        )
    }

    fun fetchShow(id: Int): TvResponse {
        return tmdbApi.getShow(id)
    }

    fun fetchSeason(id: Int, seasonNumber: Int): SeasonResponse {
        return tmdbApi.getSeason(id, seasonNumber)
    }

    fun fetchMovie(id: Int): MovieResponse {
        return tmdbApi.getMovie(id)
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