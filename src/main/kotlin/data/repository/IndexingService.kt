package zechs.zplex.sync.data.repository

import com.google.api.services.drive.model.File
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import data.model.DriveFile
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import zechs.zplex.sync.data.model.Episode
import zechs.zplex.sync.data.model.Movie
import zechs.zplex.sync.data.model.Season
import zechs.zplex.sync.data.model.SeasonEpisode
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
    private val driveRepository = DriveRepository(GoogleDrive("ZPlex Agent"))
    private val tmdbRepository = TmdbRepository(tmdbApi)

    operator fun invoke() {
        try {
            println("Indexing service started.")
            indexMovies()
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

            val showsFolders = driveRepository.getFiles(
                folderId = System.getenv("SHOWS_FOLDER"),
                foldersOnly = true
            )

            // Processing shows
            processShows(showsFolders)
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
    private fun processShows(showsFolder: List<File>) {
        val showIdsInDatabase = tmdbRepository.getAllShowsIds()
        showsFolder
            .mapNotNull { parseFileName(it, extension = false) }
            .forEach { videoInfo ->
                try {
                    println()
                    println("Processing show: ${videoInfo.name}")
                    // does this tmdb-id exist in shows-table
                    val showId = showIdsInDatabase.find { it == videoInfo.tmdbId }
                    // then we only add/remove existing items
                    if (showId != null) {
                        val seasons = tmdbRepository.getSeasons(showId)
                        val remoteSeasons = driveRepository.getFiles(
                            folderId = videoInfo.fileId,
                            foldersOnly = true
                        )
                        println(
                            "[EXISTS SHOW] ${videoInfo.name} [showId=$showId] (${seasons.size} seasons saved, ${remoteSeasons.size} " +
                                    "seasons found)"
                        )
                        val deleteSeasons = seasons.filter { season ->
                            remoteSeasons.none { it.name == "Season ${season.seasonNumber}" }
                        }
                        println("Deleting ${deleteSeasons.size} seasons")
                        deleteSeasons.forEach { season ->
                            println("[DELETE SEASON] ${season.name} [${season.id}]")
                            tmdbRepository.deleteSeason(season.id)
                        }

                        val existingSeasons = findCommonSeasons(remoteSeasons, seasons)
                        println("Common seasons: ${existingSeasons.size}")
                        existingSeasons.forEach { existingSeason ->
                            checkExistingSeason(showId, remoteSeasons, existingSeason)
                        }

                        val newSeasonFolders = remoteSeasons.filter { season ->
                            seasons.none { it.seasonNumber == season.name.split(" ")[1].toInt() }
                        }

                        println("Adding ${newSeasonFolders.size} seasons")
                        newSeasonFolders.forEach { newSeasonFolder ->
                            addNewSeason(showId, newSeasonFolder)
                        }
                    } else {
                        println("[NOT EXIST SHOW] ${videoInfo.name}")

                        val tvShow = tmdbRepository.fetchShow(videoInfo.tmdbId)

                        val seasons = mutableListOf<Season>()
                        val episodes = mutableListOf<Episode>()
                        val showFolderId = videoInfo.fileId
                        val files = mutableListOf<DriveFile>()

                        val seasonFoldersFound = driveRepository.getFiles(
                            folderId = showFolderId,
                            foldersOnly = true
                        )

                        tvShow.seasons?.forEach { tvSeason ->
                            val expectedSeasonFolderName = "Season ${tvSeason.season_number}"
                            val seasonFolder = seasonFoldersFound.find { it.name == expectedSeasonFolderName }
                            if (seasonFolder == null) {
                                println("Season folder not found: $expectedSeasonFolderName of ${tvShow.name}")
                            } else {
                                seasons.add(
                                    Season(
                                        id = tvSeason.id,
                                        name = tvSeason.name,
                                        seasonNumber = tvSeason.season_number,
                                        posterPath = tvSeason.poster_path,
                                        showId = tvShow.id
                                    )
                                )
                                println("Season folder found: $expectedSeasonFolderName of ${tvShow.name}")
                                println("Processing episodes of $expectedSeasonFolderName")
                                val expectedSeason = tmdbRepository.fetchSeason(tvShow.id, tvSeason.season_number)
                                val episodesFolderFound = driveRepository.getFiles(
                                    folderId = seasonFolder.id,
                                    foldersOnly = false
                                ).filter { it.mimeType.startsWith("video/") }
                                    .map { DriveFile(it.id, it.name, it.getSize().toLong()) }
                                val episodeMap = buildEpisodeMap(episodesFolderFound)

                                var match = 0
                                expectedSeason.episodes?.forEach { episode ->
                                    val status = processEpisodes(tvSeason.id, episode, episodeMap, episodes, files)
                                    if (status) match++
                                }
                                println("Matched $match out of ${expectedSeason.episodes?.size ?: 0} episodes")
                            }
                        }
                        println(
                            "[INSERT NEW SHOW] ${tvShow.name} [${tvShow.id}] with ${seasons.size} seasons " +
                                    "and ${episodes.size} episodes (${files.size} files)"
                        )
                        tmdbRepository.upsertShow(tvShow.toShow(), seasons, episodes, files)
                    }
                } catch (e: Exception) {
                    println("Error processing show: ${videoInfo.name} (${e.message})")
                }
            }
    }

    private fun processEpisodes(
        seasonId: Int,
        episode: SeasonEpisode,
        episodeMap: Map<String, DriveFile>,
        episodes: MutableList<Episode>,
        files: MutableList<DriveFile>
    ): Boolean {
        findMatchingEpisode(episode, episodeMap)?.let { matchingEpisode ->
            episodes.add(
                Episode(
                    id = episode.id,
                    title = episode.name,
                    episodeNumber = episode.episode_number,
                    seasonNumber = episode.season_number,
                    seasonId = seasonId,
                    stillPath = episode.still_path,
                    fileId = matchingEpisode.id
                )
            )
            files.add(matchingEpisode)
            return true
        }
        return false
    }

    private fun checkExistingSeason(showId: Int, remoteSeasons: List<File>, existingSeason: Season) {
        val seasonFolder = remoteSeasons.find { it.name == "Season ${existingSeason.seasonNumber}" }!!

        val episodesFolder = driveRepository.getFiles(
            folderId = seasonFolder.id,
            foldersOnly = false
        ).filter { it.mimeType.startsWith("video/") }
            .map { DriveFile(it.id, it.name, it.getSize().toLong()) }
        val episodeMap = buildEpisodeMap(episodesFolder)
        val season = tmdbRepository.fetchSeason(showId, existingSeason.seasonNumber)

        println("Processing episodes of ${existingSeason.name}")
        val insertEpisodes = mutableMapOf<DriveFile, Episode>()
        var existCount = 0
        season.episodes?.forEach { episode ->
            val matchingEpisode = findMatchingEpisode(episode, episodeMap)
            if (matchingEpisode != null) {
                val doesExist = tmdbRepository.getEpisodeById(episode.id) != null
                if (!doesExist) {
                    val episodeEntity = Episode(
                        id = episode.id,
                        title = episode.name,
                        episodeNumber = episode.episode_number,
                        seasonNumber = episode.season_number,
                        seasonId = existingSeason.id,
                        stillPath = episode.still_path,
                        fileId = matchingEpisode.id
                    )
                    insertEpisodes[matchingEpisode] = episodeEntity
                } else {
                    existCount++
                }
            }
        }
        println("[EPISODE EXISTS] $existCount episodes")
        println("[ADD EPISODE] ${insertEpisodes.size} episodes to ${existingSeason.name}")
        if (insertEpisodes.isNotEmpty()) {
            tmdbRepository.addEpisodes(existingSeason.id, insertEpisodes)
        }
    }

    private fun addNewSeason(showId: Int, newSeasonFolder: File) {
        val seasonNumber = newSeasonFolder.name.split(" ")[1].toInt()
        val season = tmdbRepository.fetchSeason(showId, seasonNumber)
        val seasonEntity = Season(
            id = season.id!!,
            name = season.name?.ifEmpty { "Season $seasonNumber" } ?: "Season $seasonNumber",
            posterPath = season.poster_path,
            seasonNumber = season.season_number,
            showId = showId
        )
        tmdbRepository.addSeason(showId, seasonEntity)
        println("[ADD SEASON] ${seasonEntity.name} [${seasonEntity.id}]")

        val episodesFolder = driveRepository.getFiles(
            folderId = newSeasonFolder.id,
            foldersOnly = false
        ).filter { it.mimeType.startsWith("video/") }
            .map { DriveFile(it.id, it.name, it.getSize().toLong()) }
        val episodeMap = buildEpisodeMap(episodesFolder)
        val insertEpisodes = mutableMapOf<DriveFile, Episode>()
        season.episodes?.forEach { episode ->
            val matchingEpisode = findMatchingEpisode(episode, episodeMap)
            if (matchingEpisode != null) {
                val episodeEntity = Episode(
                    id = episode.id,
                    title = episode.name,
                    episodeNumber = episode.episode_number,
                    seasonNumber = episode.season_number,
                    seasonId = season.id,
                    stillPath = episode.still_path,
                    fileId = matchingEpisode.id
                )
                insertEpisodes[matchingEpisode] = episodeEntity
                println("[ADD EPISODE QUEUE] ${episodeEntity.title} [${episodeEntity.id}]")
            }
        }
        if (insertEpisodes.isNotEmpty()) {
            println("Adding ${insertEpisodes.size} episodes")
            tmdbRepository.addEpisodes(seasonEntity.id, insertEpisodes)
        }
    }

    private fun buildEpisodeMap(foundEpisodes: List<DriveFile>): Map<String, DriveFile> {
        val episodeMap = mutableMapOf<String, DriveFile>()
        for (file in foundEpisodes) {
            extractEpisodeFormat(file.name)?.let {
                episodeMap[it] = file
            }
        }
        return episodeMap
    }

    private fun extractEpisodeFormat(fileName: String): String? {
        try {
            val regex = Regex("""S(\d{2})E(\d+)""", RegexOption.IGNORE_CASE)
            val matchResult = regex.find(fileName)
            return matchResult?.value
        } catch (e: IndexOutOfBoundsException) {
            println("No match found for $fileName")
            e.printStackTrace()
        }
        return null
    }

    private fun findCommonSeasons(
        remoteSeasons: List<File>,
        seasons: List<Season>
    ): List<Season> {
        val remoteSeasonNumbers = remoteSeasons.map { it.name.split("Season ")[1].toInt() }
        val matchingSeasonNumbers = remoteSeasonNumbers.intersect(seasons.map { it.seasonNumber }.toSet())
        return seasons.filter { it.seasonNumber in matchingSeasonNumbers }
    }

    private fun findMatchingEpisode(
        episode: SeasonEpisode,
        episodeMap: Map<String, DriveFile>
    ): DriveFile? {
        val episodePattern = getEpisodePattern(episode)
        return episodeMap[episodePattern]
    }

    private fun getEpisodePattern(episode: SeasonEpisode): String {
        return "S%02dE%02d".format(episode.season_number, episode.episode_number)
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