package zechs.zplex.sync.data.repository

import com.google.api.services.drive.model.File
import com.google.gson.GsonBuilder
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Semaphore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import redis.clients.jedis.JedisPooled
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import zechs.zplex.sync.data.model.DriveFile
import zechs.zplex.sync.data.model.DrivePathFile
import zechs.zplex.sync.data.model.Episode
import zechs.zplex.sync.data.model.Movie
import zechs.zplex.sync.data.model.Season
import zechs.zplex.sync.data.model.Show
import zechs.zplex.sync.data.model.omdb.OmdbTvResponse
import zechs.zplex.sync.data.model.tmdb.SeasonEpisode
import zechs.zplex.sync.data.model.tmdb.TvResponse
import zechs.zplex.sync.data.model.tmdb.TvSeason
import zechs.zplex.sync.data.remote.OmdbApi
import zechs.zplex.sync.data.remote.TmdbApi
import zechs.zplex.sync.utils.Constants.Companion.IS_DEBUG
import zechs.zplex.sync.utils.Constants.Companion.OMDB_API_KEY
import zechs.zplex.sync.utils.Constants.Companion.OMDB_API_URL
import zechs.zplex.sync.utils.Constants.Companion.TMDB_API_KEY
import zechs.zplex.sync.utils.Constants.Companion.TMDB_API_URL
import zechs.zplex.sync.utils.GoogleDrive
import zechs.zplex.sync.utils.OmdbApiKeyInterceptor
import zechs.zplex.sync.utils.SynchronousCallAdapterFactory
import zechs.zplex.sync.utils.TmdbApiKeyInterceptor
import zechs.zplex.sync.utils.ext.nullIfNA
import zechs.zplex.sync.utils.ext.nullIfNAOrElse
import java.text.SimpleDateFormat
import java.util.*


class IndexingService {

    private data class Info(
        val name: String,
        val year: Int,
        val tmdbId: Int,
        val fileSize: Long?,
        val fileName: String,
        val fileId: String
    )

    private val moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val httpLogging by lazy {
        HttpLoggingInterceptor()
            .setLevel(HttpLoggingInterceptor.Level.BODY)
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .also {
                if (IS_DEBUG) {
                    it.addInterceptor(httpLogging)
                }
                it.addInterceptor(TmdbApiKeyInterceptor(TMDB_API_KEY))
                it.addInterceptor(OmdbApiKeyInterceptor(OMDB_API_KEY))
            }.build()
    }

    private val tmdbApi by lazy<TmdbApi> {
        Retrofit.Builder()
            .baseUrl(TMDB_API_URL)
            .client(client)
            .addCallAdapterFactory(SynchronousCallAdapterFactory())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TmdbApi::class.java)
    }

    private val omdbApi by lazy<OmdbApi> {
        Retrofit.Builder()
            .baseUrl(OMDB_API_URL)
            .client(client)
            .addCallAdapterFactory(SynchronousCallAdapterFactory())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OmdbApi::class.java)
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val semaphore = Semaphore(100)

    private val driveRepository = DriveRepository(
        drive = GoogleDrive(applicationName = "ZPlex Agent"),
        semaphore = semaphore,
        scope = coroutineScope
    )
    private val tmdbRepository = TmdbRepository(tmdbApi)
    private val omdbRepository = OmdbRepository(omdbApi)

    private val gson by lazy {
        GsonBuilder().create()
    }

    private val redis by lazy {
        JedisPooled(
            System.getenv("REDIS_HOST"),
            System.getenv("REDIS_PORT").toInt(),
            System.getenv("REDIS_USERNAME"),
            System.getenv("REDIS_PASSWORD")
        )
    }

    operator fun invoke() {
        try {
            println("Indexing service started.")
            updateGenreList()
            println()
            indexMovies()
            println()
            indexShows()
            println()
            updateFiltersCache()
            println("Indexing service ended.")
        } finally {
            redis.close()
            tmdbRepository.disconnect()
        }
    }

    private fun updateGenreList() {
        println("Beginning genre synchronization...")
        try {
            val existingGenres = tmdbRepository.getAllGenres()
            val movieGenres = tmdbApi.getMovieGenres().genres
            val showGenres = tmdbApi.getShowGenres().genres

            // Eliminate existing genres
            val newMovieGenres = movieGenres.filter { movieGenre ->
                existingGenres.none { existingGenre -> existingGenre.id == movieGenre.id }
            }

            val newShowGenres = showGenres.filter { showGenre ->
                existingGenres.none { existingGenre -> existingGenre.id == showGenre.id }
            }

            if (newMovieGenres.isEmpty() && newShowGenres.isEmpty()) {
                println("No new genres found.")
                return
            }

            // Prepare 3 lists
            val uniqueMovieGenres = newMovieGenres.filter { movieGenre ->
                showGenres.none { showGenre -> showGenre.id == movieGenre.id }
            }

            val uniqueShowGenres = newShowGenres.filter { showGenre ->
                movieGenres.none { movieGenre -> movieGenre.id == showGenre.id }
            }

            val commonGenres = newMovieGenres.filter { movieGenre ->
                showGenres.any { showGenre -> showGenre.id == movieGenre.id }
            }

            tmdbRepository.batchAddGenres(uniqueMovieGenres, "movie")
            tmdbRepository.batchAddGenres(uniqueShowGenres, "show")
            tmdbRepository.batchAddGenres(commonGenres, "both")
        } catch (e: Exception) {
            println("Error updating genre list: ${e.message}")
            throw e
        }
        println("Genre synchronization ended.")
    }

    private fun syncFiles(remoteFiles: List<File>, databaseFiles: List<DriveFile>): List<File> {
        println("Beginning file synchronization...")

        val commonFiles = remoteFiles.filter { file -> databaseFiles.any { it.id == file.id } }
        val newFiles = remoteFiles.filter { file -> databaseFiles.none { it.id == file.id } }
        val deleteFileId = databaseFiles
            .filter { dbFile -> remoteFiles.none { it.id == dbFile.id } }
            .map { it.id }

        val updateFiles = commonFiles.filter { remoteFile ->
            val dbFile = databaseFiles.first { it.id == remoteFile.id }
            dbFile.modifiedTime != remoteFile.modifiedTime.value
        }
        println("Number of files to be updated: ${updateFiles.size}")
        println("Number of new files to be inserted: ${newFiles.size}")
        println("Number of files to be deleted: ${deleteFileId.size}")

        tmdbRepository.updateModifiedTime(updateFiles)
        tmdbRepository.deleteFiles(deleteFileId)
        println("File synchronization ended.")

        return newFiles
    }

    private fun indexMovies() {
        try {
            println("Beginning indexing movies...")

            if (!doesMoviesFolderExist()) {
                println("Movies folder does not exist, skipping show processing.")
                return
            }

            val movieFiles = driveRepository.getFiles(
                folderId = System.getenv("MOVIES_FOLDER"),
                foldersOnly = false
            ).filter { it.mimeType.startsWith("video/") }

            // Sync files
            val newFiles = syncFiles(
                remoteFiles = movieFiles,
                databaseFiles = tmdbRepository.getAllMoviesFiles()
            )
            // Processing movies
            processMovies(newFiles)
        } finally {
            println("Ended indexing movies")
        }
    }

    /**
     * Processes a list of DriveFiles representing movies by extracting relevant information
     * such as video details. Each movie is asynchronously processed using the 'processSingleMovie' method,
     * which checks and updates the local database accordingly.
     *
     * If a movie already exists in the local database, only the fileId field is updated.
     * If a movie doesn't exist, it fetches details from TMDB and inserts it into the local database.
     *
     * After processing all movies, a sanitization process is performed to remove files that exist
     * in the local database but no longer exist remotely.
     *
     * @param driveFiles A list of DriveFiles representing movies to be processed.
     * @throws Exception If an error occurs during movie processing or sanitization.
     */
    private fun processMovies(driveFiles: List<File>) {
        driveFiles
            .mapNotNull {
                val parse = parseFileName(it)
                if (parse != null) Pair(parse, it)
                else null
            }
            .forEach { (videoInfo, driveFile) ->
                try {
                    insertNewMovie(videoInfo, driveFile)
                } catch (e: Exception) {
                    println("Error processing movie: ${videoInfo.name} (${e.message})")
                }
            }
    }

    private fun insertNewMovie(videoFile: Info, driveFile: File) {
        println("New movie: ${videoFile.name}, inserting into the database.")

        val movieResponse = tmdbRepository.fetchMovie(videoFile.tmdbId)
        val imdbId = movieResponse.external_ids
            ?.getOrDefault("imdb_id", null)
            ?: run {
                println("[ERROR] IMDB ID not found for - ${movieResponse.title}")
                return
            }

        val omdbResponse = omdbRepository.fetchMovieById(imdbId)
            ?: run {
                println("[ERROR] OMDB response not found for - ${movieResponse.title}")
                return
            }

        val movie = Movie(
            id = movieResponse.id,
            imdbId = omdbResponse.imdbID,
            imdbRating = omdbResponse.imdbRating?.nullIfNAOrElse { it.replace(",", "").toDouble() },
            imdbVotes = omdbResponse.imdbVotes.replace(",", "").toInt(),
            releaseDate = omdbResponse.Released.nullIfNAOrElse { parseDateToEpoch(it) },
            releaseYear = omdbResponse.Year.toInt(),
            parentalRating = omdbResponse.Rated?.nullIfNA(),
            runtime = omdbResponse.Runtime.nullIfNAOrElse { it.split(" ")[0].toInt() },
            posterPath = movieResponse.poster_path,
            backdropPath = movieResponse.backdrop_path,
            logoImage = movieResponse.getBestLogoImage(),
            trailerLink = movieResponse.getOfficialTrailer(),
            tagLine = movieResponse.tagline,
            plot = omdbResponse.Plot,
            director = movieResponse.getDirectorName(),
            cast = movieResponse.credits.cast?.map { it.toCast(tmdbId = movieResponse.id) } ?: emptyList(),
            crew = movieResponse.credits.crew?.map { it.toCrew(tmdbId = movieResponse.id) } ?: emptyList(),
            genres = movieResponse.genres,
            studios = movieResponse.production_companies?.map { it.toStudio() } ?: emptyList(),
            externalLinks = movieResponse.getExternalLinks(),
            title = omdbResponse.Title,
            collectionId = movieResponse.belongs_to_collection?.id,
            fileId = videoFile.fileId
        )
        val file = DriveFile(
            id = videoFile.fileId,
            name = videoFile.fileName,
            size = videoFile.fileSize!!,
            modifiedTime = driveFile.modifiedTime.value
        )
        tmdbRepository.upsertMovie(movie, file)
    }

    private fun indexShows() {
        try {
            println("Beginning indexing shows...")

            if (!doesShowsFolderExist()) {
                println("Shows folder does not exist, skipping show processing.")
                return
            }

            val remoteFiles = driveRepository.getAllFilesRecursively(folderId = System.getenv("SHOWS_FOLDER"))
            println("[GOOGLE DRIVE] Number of files found: ${remoteFiles.size} in the shows folder.")

            // Sync files
            println("Beginning file synchronization...")

            val databaseFiles = tmdbRepository.getAllEpisodesFiles()
            val newFiles = remoteFiles.filter { file -> databaseFiles.none { it.id == file.file.id } }
            val deleteFileId = databaseFiles
                .filter { dbFile -> remoteFiles.none { it.file.id == dbFile.id } }
                .map { it.id }
            println("Number of new files to be inserted: ${newFiles.size}")
            println("Number of files to be deleted: ${deleteFileId.size}")

            if (deleteFileId.isNotEmpty()) {
                print("[DATABASE EXECUTION] Deleting ${deleteFileId.size} files from the database.")
                tmdbRepository.deleteFiles(deleteFileId)
            }
            val updateTheseFiles = remoteFiles.filter { remoteFile ->
                val dbFile = databaseFiles.firstOrNull { it.id == remoteFile.file.id }
                dbFile?.modifiedTime != remoteFile.file.modifiedTime.value
            }.map { it.file }
            println("[DATABASE EXECUTION] ${updateTheseFiles.size} files need to be updated.")
            tmdbRepository.updateModifiedTime(updateTheseFiles)
            syncModifiedTime()
            println("File synchronization ended.")

            // Processing shows
            processShows(newFiles)
        } finally {
            println("Ended indexing shows")
        }
    }

    private fun syncModifiedTime() {
        val remoteShowFolders = driveRepository.getFiles(
            folderId = System.getenv("SHOWS_FOLDER"),
            foldersOnly = true
        )
        val databaseShows = tmdbRepository.getAllShows()
        val updateTheseShows = mutableListOf<Show>()
        remoteShowFolders.forEach { remoteFolder ->
            val dbShow = databaseShows.firstOrNull { show ->
                parseShowFolderName(remoteFolder.name)?.tmdbId == show.id
            }
            if (dbShow != null && dbShow.modifiedTime != remoteFolder.modifiedTime.value) {
                updateTheseShows.add(dbShow.copy(modifiedTime = remoteFolder.modifiedTime.value))
            }
        }
        println("[DATABASE EXECUTION] ${updateTheseShows.size} shows need to be updated.")
        tmdbRepository.updateShowsModifiedTime(updateTheseShows)
    }

    /**
     * The `processShows` function processes a list of TV show files from Google Drive, grouping them by show and season,
     * and updates the local database with metadata from TMDB and OMDB.
     *
     * 1. Fetches the remote show folders from Google Drive using the `SHOWS_FOLDER` env variable.
     * 2. Groups and validates incoming files based on their folder structure (show/season/episode).
     * 3. Matches each show folder with corresponding metadata from TMDB and OMDB via the TMDB ID and IMDb ID.
     * 4. If any metadata is missing (show folder, IMDb ID, etc.), it logs an error and skips the show.
     * 5. Groups files by season, retrieves season and episode details from TMDB, and processes each episode.
     * 6. Builds `Show`, `Season`, `Episode`, and `DriveFile` objects with associated metadata.
     * 7. Inserts new seasons, episodes, and file metadata into the database, skipping any duplicates.
     * 8. Logs key events and errors throughout the process while ensuring all valid data is processed.
     */
    private fun processShows(showFiles: List<DrivePathFile>) {
        val remoteShowFolders = driveRepository.getFiles(
            folderId = System.getenv("SHOWS_FOLDER"),
            foldersOnly = true
        )

        showFiles
            .filter { isValidShowPath(it) }
            .groupBy { getShowFolderName(it) }
            .mapNotNull { (showFolderPath, files) ->
                parseShowFolderName(showFolderPath)?.let { showFolder -> showFolder to files }
            }
            .sortedBy { (showFolder, _) -> showFolder.title }
            .forEach { (showFolder, files) ->
                val driveShowFolder = findDriveShowFolder(showFolder, remoteShowFolders)
                    ?: run {
                        logError("Show folder not found", showFolder.title, showFolder.year, showFolder.tmdbId)
                        return@forEach
                    }

                val tvResponse = tmdbRepository.fetchShow(showFolder.tmdbId)
                val imdbId = tvResponse.external_ids?.get("imdb_id") ?: run {
                    logError("IMDB ID not found", tvResponse.name)
                    return@forEach
                }

                val omdbResponse = omdbRepository.fetchTvById(imdbId) ?: run {
                    logError("OMDB response not found", tvResponse.name, "($imdbId)")
                    return@forEach
                }

                val show = createShow(tvResponse, omdbResponse, driveShowFolder)
                val (seasonsToBeInserted, episodesToBeInserted, filesToBeInserted) =
                    processSeasonsAndEpisodes(tvResponse, omdbResponse, files, showFolder.tmdbId)

                upsertShowData(show, seasonsToBeInserted, episodesToBeInserted, filesToBeInserted)
            }
    }

    private fun isValidShowPath(file: DrivePathFile): Boolean {
        return file.path.split("/").size >= 3
    }

    private fun getShowFolderName(file: DrivePathFile): String {
        return file.path.split("/")[0]
    }

    private fun getSeasonFolderName(file: DrivePathFile): String {
        return file.path.split("/")[1]
    }

    private fun getEpisodeInfo(file: DrivePathFile): EpisodeInfo? {
        val episodeFileName = file.path.split("/")[2]
        return parseEpisodeFile(episodeFileName)
    }

    private fun findDriveShowFolder(showFolder: ShowInfo, remoteShowFolders: List<File>): File? {
        return remoteShowFolders.find {
            it.name == "${showFolder.title} (${showFolder.year}) [${showFolder.tmdbId}]"
        }
    }

    private fun processSeasonsAndEpisodes(
        tvResponse: TvResponse,
        omdbResponse: OmdbTvResponse,
        showFiles: List<DrivePathFile>,
        tmdbId: Int
    ): Triple<Set<Season>, Set<Episode>, Set<DriveFile>> {

        val seasonsToBeInserted = mutableSetOf<Season>()
        val episodesToBeInserted = mutableSetOf<Episode>()
        val filesToBeInserted = mutableSetOf<DriveFile>()

        showFiles
            .groupBy { getSeasonFolderName(it) }
            .toSortedMap()
            .forEach seasonLoop@{ (seasonText, episodeFiles) ->
                val seasonNumber = parseSeasonFolder(seasonText) ?: run {
                    logError("Season number not found", seasonText)
                    return@seasonLoop
                }

                val tmdbSeason = tmdbRepository.fetchSeason(tmdbId, seasonNumber)
                if (tmdbSeason.episodes == null) {
                    logError("Season not found", seasonText)
                    return@seasonLoop
                }

                val episodeMap =
                    tmdbSeason.episodes.associateBy { formatSeasonEpisodeLabel(it.season_number, it.episode_number) }

                episodeFiles.forEach episodeLoop@{ file ->
                    val episodeInfo = getEpisodeInfo(file) ?: return@episodeLoop
                    val foundEpisode =
                        episodeMap[formatSeasonEpisodeLabel(episodeInfo.seasonNumber, episodeInfo.episodeNumber)]

                    foundEpisode?.let {
                        val episode = createEpisode(foundEpisode, tmdbSeason.id, file)
                        episodesToBeInserted.add(episode)
                        filesToBeInserted.add(createDriveFile(file))
                    } ?: logError("Episode not found", file.path)
                }

                tvResponse.seasons?.firstOrNull { it.season_number == seasonNumber }?.let { foundSeason ->
                    val season = createSeason(foundSeason, omdbResponse.Title, tmdbId)
                    seasonsToBeInserted.add(season)
                }
            }

        return Triple(seasonsToBeInserted, episodesToBeInserted, filesToBeInserted)
    }

    private fun createEpisode(
        foundEpisode: SeasonEpisode,
        seasonId: Int,
        file: DrivePathFile
    ): Episode {
        return Episode(
            id = foundEpisode.id,
            title = foundEpisode.name,
            episodeNumber = foundEpisode.episode_number,
            seasonNumber = foundEpisode.season_number,
            stillPath = foundEpisode.still_path,
            seasonId = seasonId,
            overview = foundEpisode.overview,
            airdate = foundEpisode.air_date?.nullIfNAOrElse { parseDateToEpoch(it, "yyyy-MM-dd") },
            runtime = foundEpisode.runtime,
            fileId = file.file.id
        )
    }

    private fun createSeason(
        foundSeason: TvSeason,
        showName: String,
        tmdbId: Int
    ): Season {
        return Season(
            id = foundSeason.id,
            name = foundSeason.name,
            posterPath = foundSeason.poster_path,
            overview = foundSeason.getOverview(showName),
            releaseYear = foundSeason.air_date?.substringBefore("-")?.toIntOrNull() ?: 0,
            releaseDate = foundSeason.air_date?.nullIfNAOrElse { parseDateToEpoch(it, "yyyy-MM-dd") },
            seasonNumber = foundSeason.season_number,
            showId = tmdbId
        )
    }

    private fun createDriveFile(file: DrivePathFile): DriveFile {
        return DriveFile(
            id = file.file.id,
            name = file.file.name,
            size = file.file.getSize(),
            modifiedTime = file.file.modifiedTime.value
        )
    }

    private fun formatSeasonEpisodeLabel(seasonNumber: Int, episodeNumber: Int): String {
        return "S%02dE%02d".format(seasonNumber, episodeNumber)
    }

    private fun logError(error: String, vararg details: Any?) {
        println("[ERROR] $error: ${details.joinToString(", ")}")
    }

    private fun upsertShowData(
        show: Show,
        seasons: Set<Season>,
        episodes: Set<Episode>,
        files: Set<DriveFile>
    ) {
        val databaseSeasons = tmdbRepository.getAllSeasonIds()
        val seasonsToInsert = seasons.filter { season -> databaseSeasons.none { it == season.id } }

        println("[DATABASE EXECUTION] Inserting show: ${show.title}")
        println("[DATABASE EXECUTION] ${seasonsToInsert.size} seasons, ${episodes.size} episodes, and ${files.size} files to be inserted.")
        tmdbRepository.upsertShow(show, seasonsToInsert.toList(), episodes.toList(), files.toList())
        println()
    }

    private fun createShow(
        tvResponse: TvResponse,
        omdbResponse: OmdbTvResponse,
        driveShowFolder: File
    ): Show {
        return Show(
            id = tvResponse.id,
            imdbId = omdbResponse.imdbID,
            imdbRating = omdbResponse.imdbRating?.nullIfNAOrElse { it.replace(",", "").toDouble() },
            imdbVotes = omdbResponse.imdbVotes.replace(",", "").toInt(),
            releaseDate = omdbResponse.Released.nullIfNAOrElse { parseDateToEpoch(it) },
            releasedYear = omdbResponse.Year.substringBefore("–").toInt(),
            releaseYearTo = omdbResponse.getLatestYear(),
            parentalRating = omdbResponse.Rated?.nullIfNA(),
            posterPath = tvResponse.poster_path,
            backdropPath = tvResponse.backdrop_path,
            logoImage = tvResponse.getBestLogoImage(),
            trailerLink = tvResponse.getOfficialTrailer(),
            plot = omdbResponse.Plot,
            director = tvResponse.getDirectorName(),
            cast = tvResponse.credits.cast?.map { it.toCast(tmdbId = tvResponse.id) }
                ?: emptyList(),
            crew = tvResponse.credits.crew?.map { it.toCrew(tmdbId = tvResponse.id) }
                ?: emptyList(),
            genres = tvResponse.genres,
            studios = tvResponse.production_companies?.map { it.toStudio() } ?: emptyList(),
            externalLinks = tvResponse.getExternalLinks(),
            title = omdbResponse.Title,
            modifiedTime = driveShowFolder.modifiedTime.value
        )
    }

    private data class ShowInfo(
        val title: String,
        val year: Int,
        val tmdbId: Int,
    )

    private data class EpisodeInfo(
        val seasonNumber: Int,
        val episodeNumber: Int,
    )

    private fun updateFiltersCache() {
        updateShowGenresCache()
        updateShowStudiosCache()
        updateShowParentalRatingsCache()
        updateShowYearsCache()
        updateMovieGenresCache()
        updateMovieStudiosCache()
        updateMovieParentalRatingsCache()
        updateMovieYearsCache()
    }

    private fun updateShowGenresCache() {
        val commonShowGenres = tmdbRepository.getCommonShowGenres()
        val key = "commonShowGenres"
        redis.jsonSet(key, gson.toJson(commonShowGenres.map { mapOf("id" to it.id, "name" to it.name) }))
        println("[REDIS] Updated Show Genres: ${commonShowGenres.size} items")
    }

    private fun updateShowStudiosCache() {
        val commonShowStudios = tmdbRepository.getCommonShowStudios()
        val key = "commonShowStudios"
        redis.jsonSet(key, gson.toJson(commonShowStudios.map { mapOf("id" to it.first, "name" to it.second) }))
        println("[REDIS] Updated Show Studios: ${commonShowStudios.size} items")
    }

    private fun updateShowParentalRatingsCache() {
        val ratings = tmdbRepository.getCommonShowParentalRatings()
        val (added, removed) = updateListCache("commonShowParentalRatings", ratings)
        println("[REDIS] Updated Show Parental Ratings: ${ratings.size} items, Added: $added, Removed: $removed")
    }

    private fun updateShowYearsCache() {
        val years = tmdbRepository.getCommonShowYears()
        val (added, removed) = updateListCache("commonShowYears", years)
        println("[REDIS] Updated Show Years: ${years.size} items, Added: $added, Removed: $removed")
    }

    private fun updateMovieGenresCache() {
        val commonMovieGenres = tmdbRepository.getCommonMovieGenres()
        val key = "commonMovieGenres"
        redis.jsonSet(key, gson.toJson(commonMovieGenres.map { mapOf("id" to it.id, "name" to it.name) }))
        println("[REDIS] Updated Movie Genres: ${commonMovieGenres.size} items")
    }

    private fun updateMovieStudiosCache() {
        val commonMovieStudios = tmdbRepository.getCommonMovieStudios()
        val key = "commonMovieStudios"
        redis.jsonSet(key, gson.toJson(commonMovieStudios.map { mapOf("id" to it.first, "name" to it.second) }))
        println("[REDIS] Updated Movie Studios: ${commonMovieStudios.size} items")
    }

    private fun updateMovieParentalRatingsCache() {
        val ratings = tmdbRepository.getCommonMovieParentalRatings()
        val (added, removed) = updateListCache("commonMovieParentalRatings", ratings)
        println("[REDIS] Updated Movie Parental Ratings: ${ratings.size} items, Added: $added, Removed: $removed")
    }

    private fun updateMovieYearsCache() {
        val years = tmdbRepository.getCommonMovieYears()
        val (added, removed) = updateListCache("commonMovieYears", years)
        println("[REDIS] Updated Movie Years: ${years.size} items, Added: $added, Removed: $removed")
    }

    private fun updateListCache(key: String, list: List<Any>): Pair<Int, Int> {
        val existing = redis.lrange(key, 0, -1).toSet()

        val toAdd = list.map { it.toString() }.filterNot { existing.contains(it) }
        val toRemove = existing.filterNot { list.map { item -> item.toString() }.contains(it) }

        toRemove.forEach { redis.lrem(key, 0, it) }

        if (toAdd.isNotEmpty()) {
            redis.lpush(key, *toAdd.toTypedArray())
        }
        return Pair(toAdd.size, toRemove.size)
    }

    private fun parseShowFolderName(folderName: String): ShowInfo? {
        val regex = """^(.+) \((\d{4})\) \[(\d+)]$"""
            .toRegex()
        return try {
            val matchResult = regex.matchEntire(folderName)
            if (matchResult != null) {
                val (name, year, tmdbId) = matchResult.destructured
                ShowInfo(name, year.toInt(), tmdbId.toInt())
            } else null
        } catch (e: Exception) {
            println("parseShowFolderName Error parsing file name: $folderName")
            e.printStackTrace()
            null
        }
    }

    private fun parseSeasonFolder(seasonFolderName: String): Int? {
        return try {
            val pattern = "^Season (\\d+)$".toRegex()
            val matchResult = pattern.matchEntire(seasonFolderName)
            matchResult?.groups?.get(1)?.value?.toInt()
        } catch (e: Exception) {
            println("parseSeasonFolder Error parsing file name: $seasonFolderName")
            e.printStackTrace()
            null
        }
    }

    private fun parseEpisodeFile(fileName: String): EpisodeInfo? {
        try {
            val regex = Regex("""S(\d{2})E(\d+)""", RegexOption.IGNORE_CASE)
            val matchResult = regex.find(fileName)
            return matchResult?.let {
                val (season, episode) = it.destructured
                EpisodeInfo(season.toInt(), episode.toInt())
            }
        } catch (e: IndexOutOfBoundsException) {
            println("parseEpisodeFile No match found for $fileName")
            e.printStackTrace()
        }
        return null
    }

    private fun parseFileName(
        driveFile: File,
        extension: Boolean = true
    ): Info? {
        val regex = """^(.+) \((\d{4})\) \[(\d+)]${if (extension) "(\\.mkv|\\.mp4)?" else ""}$"""
            .toRegex()

        return try {
            val matchResult = regex.matchEntire(driveFile.name)
            if (matchResult != null) {
                val (name, year, tmdbId) = matchResult.destructured
                Info(name, year.toInt(), tmdbId.toInt(), driveFile.getSize(), driveFile.name, driveFile.id)
            } else null
        } catch (e: Exception) {
            println("Error parsing file name: ${driveFile.name}")
            e.printStackTrace()
            null
        }
    }

    private fun parseDateToEpoch(dateStr: String, pattern: String = "dd MMM yyyy"): Long? {
        val dateFormat = SimpleDateFormat(pattern, Locale.ENGLISH)
        return try {
            val date: Date? = dateFormat.parse(dateStr)
            date?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun doesMoviesFolderExist(): Boolean {
        return System.getenv("MOVIES_FOLDER") != null
    }

    private fun doesShowsFolderExist(): Boolean {
        return System.getenv("SHOWS_FOLDER") != null
    }

}