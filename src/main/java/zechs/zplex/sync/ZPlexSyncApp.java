package zechs.zplex.sync;

import com.google.api.services.drive.model.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import zechs.zplex.sync.data.local.model.Genre;
import zechs.zplex.sync.data.remote.model.GenreResponse;
import zechs.zplex.sync.repository.DriveRepository;
import zechs.zplex.sync.service.FileService;
import zechs.zplex.sync.service.GenreService;
import zechs.zplex.sync.service.MovieService;
import zechs.zplex.sync.service.TmdbService;
import zechs.zplex.sync.utils.Info;
import zechs.zplex.sync.utils.Pair;
import zechs.zplex.sync.utils.ParallelTaskExecutor;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ZPlexSyncApp implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZPlexSyncApp.class);
    private final String LANGUAGE_CODE = "en";

    private final ParallelTaskExecutor parallelTaskExecutor;

    private final MovieService movieService;
    private final TmdbService tmdbService;
    private final GenreService genreService;
    private final FileService fileService;

    private final DriveRepository driveRepository;

    @Autowired
    public ZPlexSyncApp(ParallelTaskExecutor parallelTaskExecutor,
                        MovieService movieService,
                        TmdbService tmdbService,
                        GenreService genreService,
                        FileService fileService,
                        DriveRepository driveRepository) {
        this.parallelTaskExecutor = parallelTaskExecutor;
        this.movieService = movieService;
        this.tmdbService = tmdbService;
        this.genreService = genreService;
        this.fileService = fileService;
        this.driveRepository = driveRepository;
    }

    @Override
    public void run(String... args) {
        updateGenreList();
        indexMovies();
    }

    private void updateGenreList() {
        LOGGER.info("Beginning genre synchronization...");
        List<Genre> existingGenres = genreService.getAllGenres();
        GenreResponse movieGenresResponse = tmdbService.getGenres(LANGUAGE_CODE);

        List<Genre> newMovieGenres = movieGenresResponse.genres()
                .stream()
                .filter(genre -> existingGenres.stream().map(Genre::getId).noneMatch(genre.id()::equals))
                .map(genre -> new Genre(genre.id(), genre.name(), "movie"))
                .toList();

        if (newMovieGenres.isEmpty()) {
            LOGGER.info("No new genres found");
            return;
        }
        LOGGER.info("Added {} new movie genres", newMovieGenres.size());

        genreService.saveGenres(newMovieGenres);
        LOGGER.info("Finished genre synchronization.");
    }

    private void indexMovies() {
        try {
            LOGGER.info("Beginning index movies...");
            if (System.getenv("MOVIES_FOLDER") == null) {
                LOGGER.error("MOVIES_FOLDER environment variable not set");
                return;
            }
            List<File> movieFiles = driveRepository.getFiles(System.getenv("MOVIES_FOLDER"), false)
                    .stream()
                    .filter((file -> file.getMimeType().startsWith("video/")))
                    .toList();

            List<zechs.zplex.sync.data.local.model.File> dbMovieFiles = movieService.getAllMovieFiles();
            List<File> newFiles = syncFiles(movieFiles, dbMovieFiles);
            processMovies(newFiles);
        } catch (IOException e) {
            LOGGER.error("Failed to index movies", e);
        } finally {
            LOGGER.info("Finished index movies.");
        }
    }


    public List<File> syncFiles(List<File> remoteFiles, List<zechs.zplex.sync.data.local.model.File> databaseFiles) {
        LOGGER.info("Beginning file synchronization...");

        List<File> commonFiles = remoteFiles.stream()
                .filter(file -> databaseFiles.stream().anyMatch(dbFile -> dbFile.getId().equals(file.getId())))
                .toList();

        List<File> newFiles = remoteFiles.stream()
                .filter(file -> databaseFiles.stream().noneMatch(dbFile -> dbFile.getId().equals(file.getId())))
                .toList();

        List<String> deleteFileId = databaseFiles.stream()
                .map(zechs.zplex.sync.data.local.model.File::getId)
                .filter(id -> remoteFiles.stream().noneMatch(file -> file.getId().equals(id)))
                .toList();

        List<File> updateFiles = commonFiles.stream()
                .filter(remoteFile -> {
                    zechs.zplex.sync.data.local.model.File dbFile = databaseFiles.stream()
                            .filter(file -> file.getId().equals(remoteFile.getId()))
                            .findFirst()
                            .orElse(null);
                    return dbFile != null && !dbFile.getModifiedTime().equals(remoteFile.getModifiedTime().getValue());
                })
                .toList();

        LOGGER.info("Number of files to be updated: {}", updateFiles.size());
        LOGGER.info("Number of new files to be inserted: {}", newFiles.size());
        LOGGER.info("Number of files to be deleted: {}", deleteFileId.size());

        // Assuming tmdbRepository is available
        fileService.batchUpdateModifiedTime(updateFiles);
        fileService.deleteFilesByIds(deleteFileId);

        LOGGER.info("File synchronization ended.");

        return newFiles;
    }

    private void processMovies(List<File> remoteFiles) {
//         remoteFiles.stream()
//                .map(file -> {
//                    Info parsed = parseFileName(file, true);
//                    if (parsed == null) {
//                        return null;
//                    } else {
//                        return new Pair<>(parsed, file);
//                    }
//                })
//                .filter(Objects::nonNull)
//                .forEach(pair -> {
//                    Info videoInfo = pair.getFirst();
//                    File remoteFile = pair.getSecond();
//                    insertNewMovie(videoInfo, remoteFile);
//                });
        AtomicInteger count = new AtomicInteger();
        for (File file : remoteFiles) {
            Info parsed = parseFileName(file, true);
            if (parsed == null) {
                continue; // skip this file
            } else {
                Pair<Info, File> pair = new Pair<>(parsed, file);
                Info videoInfo = pair.getFirst();
                File remoteFile = pair.getSecond();
                insertNewMovie(videoInfo, remoteFile);
                count.getAndIncrement();
                if (count.get() >= 1) {
                    break; // break after processing one movie
                }
            }
        }
    }

    private void insertNewMovie(Info videoInfo, File remoteFile) {
        LOGGER.info("New movie: {}, inserting into the database.", remoteFile.getName());
        parallelTaskExecutor.executeTask(() -> {
            movieService.insertMovie(videoInfo, remoteFile, LANGUAGE_CODE);
        });
    }

    private Info parseFileName(File driveFile, boolean extension) {
        String regexPattern = "^(.+) \\((\\d{4})\\) \\[(\\d+)]" + (extension ? "(\\.mkv|\\.mp4)?" : "") + "$";
        Pattern pattern = Pattern.compile(regexPattern);

        try {
            Matcher matcher = pattern.matcher(driveFile.getName());
            if (matcher.matches()) {
                String name = matcher.group(1);
                int year = Integer.parseInt(matcher.group(2));
                int tmdbId = Integer.parseInt(matcher.group(3));

                return new Info(name, year, tmdbId, Long.parseLong(String.valueOf(driveFile.size())),
                        driveFile.getName(), driveFile.getId());
            }
        } catch (Exception e) {
            System.out.println("Error parsing file name: " + driveFile.getName());
        }

        return null;
    }

}
