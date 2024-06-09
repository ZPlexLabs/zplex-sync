package zechs.zplex.sync.data.repository

import com.google.api.services.drive.model.File
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import data.model.DriveFile
import data.model.DrivePathFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Semaphore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import zechs.zplex.sync.data.model.Episode
import zechs.zplex.sync.data.model.Movie
import zechs.zplex.sync.data.model.Season
import zechs.zplex.sync.data.model.SeasonResponse
import zechs.zplex.sync.data.model.Show
import zechs.zplex.sync.data.model.TvResponse
import zechs.zplex.sync.data.remote.TmdbApi
import zechs.zplex.sync.utils.Constants.Companion.IS_DEBUG
import zechs.zplex.sync.utils.Constants.Companion.TMDB_API_KEY
import zechs.zplex.sync.utils.Constants.Companion.TMDB_API_URL
import zechs.zplex.sync.utils.GoogleDrive
import zechs.zplex.sync.utils.SynchronousCallAdapterFactory
import zechs.zplex.sync.utils.TmdbApiKeyInterceptor

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
            }
            .build()
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

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val semaphore = Semaphore(100)

    private val driveRepository = DriveRepository(
        drive = GoogleDrive(applicationName = "ZPlex Agent"),
        semaphore = semaphore,
        scope = coroutineScope
    )
    private val tmdbRepository = TmdbRepository(tmdbApi)

    operator fun invoke() {
        try {
            println("Indexing service started.")
            indexMovies()
            println()
            indexShows()
            println("Indexing service ended.")
        } finally {
            tmdbRepository.disconnect()
        }
    }

    private fun syncFiles(remoteFiles: List<File>, databaseFiles: List<DriveFile>): List<File> {
        println("Beginning file synchronization...")

        val newFiles = remoteFiles.filter { file -> databaseFiles.none { it.id == file.id } }
        val deleteFileId = databaseFiles
            .filter { dbFile -> remoteFiles.none { it.id == dbFile.id } }
            .map { it.id }

        println("Number of new files to be inserted: ${newFiles.size}")
        println("Number of files to be deleted: ${deleteFileId.size}")

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
            .mapNotNull { parseFileName(it) }
            .forEach { videoInfo ->
                try {
                    insertNewMovie(videoInfo)
                } catch (e: Exception) {
                    println("Error processing movie: ${videoInfo.name} (${e.message})")
                }
            }
    }

    private fun insertNewMovie(videoFile: Info) {
        println("New movie: ${videoFile.name}, inserting into the database.")

        val movieResponse = tmdbRepository.fetchMovie(videoFile.tmdbId)
        val movie = Movie(
            id = movieResponse.id,
            title = movieResponse.title ?: "No title",
            posterPath = movieResponse.poster_path,
            voteAverage = movieResponse.vote_average,
            releaseYear = videoFile.year,
            fileId = videoFile.fileId
        )
        val file = DriveFile(
            id = videoFile.fileId,
            name = videoFile.fileName,
            size = videoFile.fileSize!!
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
            println("File synchronization ended.")

            // Processing shows
            processShows(newFiles)
        } finally {
            println("Ended indexing shows")
        }
    }

    /**
     * Processes a list of DriveFiles representing TV shows by extracting relevant information
     * such as the show's name and TMDB ID. Each show is asynchronously processed using the
     * 'processSingleShow' method, which checks and updates the local database accordingly.
     *
     * If a show already exists in the local database, only the fileId field is updated.
     * If a show doesn't exist, it fetches details from TMDB and inserts it into the local database.
     *
     * After processing all shows, a sanitization process is performed to remove folders that exist
     * in the local database but no longer exist remotely.
     *
     * @param showsFolder A list of DriveFiles representing TV shows to be processed.
     */
    private fun processShows(showFiles: List<DrivePathFile>) {
        val mapOfTvResponse = mutableMapOf<Int, TvResponse>()
        val mapOfSeasonResponse = mutableMapOf<Int, SeasonResponse>()

        val showsToBeInserted = mutableSetOf<Show>()
        val seasonToBeInserted = mutableSetOf<Season>()
        val episodesToBeInserted = mutableSetOf<Episode>()
        val filesToBeInserted = mutableSetOf<DriveFile>()

        showFiles.forEach { (path, file) ->
            try {
                val showFolder = path.split("/")[0]
                val seasonFolder = path.split("/")[1]
                val episodeFile = path.split("/")[2]
                val showInfo = parseShowFolderName(showFolder) ?: return@forEach
                val seasonInfo = parseSeasonFolder(seasonFolder) ?: return@forEach
                val episodeInfo = parseEpisodeFile(episodeFile) ?: return@forEach

                val tvResponse = if (mapOfTvResponse.containsKey(showInfo.tmdbId)) {
                    mapOfTvResponse[showInfo.tmdbId]!!
                } else {
                    tmdbRepository.fetchShow(showInfo.tmdbId)
                        .also { mapOfTvResponse[showInfo.tmdbId] = it }
                }

                val show = tvResponse.toShow()
                tvResponse.seasons
                    ?.first { it.season_number == seasonInfo }
                    ?.let { foundSeason ->
                        val season = Season(
                            id = foundSeason.id,
                            name = foundSeason.name,
                            posterPath = foundSeason.poster_path,
                            seasonNumber = foundSeason.season_number,
                            showId = showInfo.tmdbId
                        )
                        val seasonResponse = if (mapOfSeasonResponse.containsKey(season.id)) {
                            mapOfSeasonResponse[season.id]!!
                        } else {
                            tmdbRepository.fetchSeason(showInfo.tmdbId, seasonInfo)
                                .also { mapOfSeasonResponse[season.id] = it }
                        }
                        seasonResponse.episodes
                            ?.firstOrNull { it.season_number == episodeInfo.seasonNumber && it.episode_number == episodeInfo.episodeNumber }
                            ?.let { foundEpisode ->
                                val episode = Episode(
                                    id = foundEpisode.id,
                                    title = foundEpisode.name,
                                    episodeNumber = foundEpisode.episode_number,
                                    seasonNumber = foundEpisode.season_number,
                                    stillPath = foundEpisode.still_path,
                                    seasonId = season.id,
                                    fileId = file.id
                                )
                                showsToBeInserted.add(show)
                                seasonToBeInserted.add(season)
                                episodesToBeInserted.add(episode)
                                filesToBeInserted.add(DriveFile(file.id, file.name, file.getSize()))
                                println("[ADD QUEUE] $path (${file.id})")
                            } ?: run { println("[ERROR] Episode not found: $path") }
                    }
            } catch (e: Exception) {
                println("[ERROR] $path -> ${e.message}")
                e.printStackTrace()
            }
        }

        if (showsToBeInserted.isNotEmpty()) {
            val databaseShows = tmdbRepository.getAllShowsIds()
            val showsActuallyNeedToBeInserted = showsToBeInserted
                .filter { show -> databaseShows.none { it == show.id } }
            println("[DATABASE EXECUTION] ${showsActuallyNeedToBeInserted.size} shows to be inserted.")
            tmdbRepository.batchAddShow(showsActuallyNeedToBeInserted)
        }
        if (seasonToBeInserted.isNotEmpty()) {
            val databaseSeasons = tmdbRepository.getAllSeasonIds()
            val seasonsActuallyNeedToBeInserted = seasonToBeInserted
                .filter { season -> databaseSeasons.none { it == season.id } }
            println("[DATABASE EXECUTION] ${seasonsActuallyNeedToBeInserted.size} seasons to be inserted.")
            tmdbRepository.batchAddSeason(seasonsActuallyNeedToBeInserted)
        }
        if (episodesToBeInserted.isNotEmpty() && filesToBeInserted.isNotEmpty()) {
            println("[DATABASE EXECUTION] ${episodesToBeInserted.size} episodes and ${filesToBeInserted.size} files to be inserted.")
            tmdbRepository.batchAddEpisodeAndFiles(episodesToBeInserted.toList(), filesToBeInserted.toList())
        }
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

    private fun doesMoviesFolderExist(): Boolean {
        return System.getenv("MOVIES_FOLDER") != null
    }

    private fun doesShowsFolderExist(): Boolean {
        return System.getenv("SHOWS_FOLDER") != null
    }

}