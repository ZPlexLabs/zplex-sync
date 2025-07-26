package zechs.zplex.sync.repository;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import zechs.zplex.sync.data.remote.DriveApi;
import zechs.zplex.sync.data.remote.model.DrivePathFile;
import zechs.zplex.sync.utils.DriveApiQueryBuilder;
import zechs.zplex.sync.utils.ParallelTaskExecutor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;

@Repository
public class DriveRepository implements DriveApi {

    private static final String FILES_LIST_FIELDS = "nextPageToken, files(id, name, size, mimeType, modifiedTime)";
    private static final String DRIVE_ORDER_BY = "folder, name";
    private static final int PAGE_SIZE = 1000;

    private final Drive driveService;
    private final ParallelTaskExecutor parallelTaskExecutor;

    @Autowired
    public DriveRepository(Drive driveService, ParallelTaskExecutor parallelTaskExecutor) {
        this.driveService = driveService;
        this.parallelTaskExecutor = parallelTaskExecutor;
    }

    @Override
    public List<File> getFiles(String folderId, Boolean foldersOnly) throws IOException {
        DriveApiQueryBuilder query = new DriveApiQueryBuilder()
                .inParents(folderId)
                .trashed(false);

        if (foldersOnly) {
            query.mimeTypeEquals("application/vnd.google-apps.folder");
        }

        List<File> files = new ArrayList<>();
        String pageToken = null;
        do {
            FileList response = getFiles(query.build(), pageToken);
            if (response != null) {
                files.addAll(response.getFiles());
                pageToken = response.getNextPageToken();
            }
        } while (pageToken != null);

        return files;
    }

    @Override
    public List<DrivePathFile> getAllFilesRecursively(String folderId) {
        return parallelTaskExecutor.invokeForkJoinPool(getAllFilesRecursive(folderId, ""));
    }

    private RecursiveTask<List<DrivePathFile>> getAllFilesRecursive(String folderId, String parentPath) {
        return new RecursiveTask<>() {
            @Override
            protected List<DrivePathFile> compute() {
                List<DrivePathFile> files = new ArrayList<>();

                List<File> items;
                try {
                    items = getFiles(folderId, false);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to list files in folder: " + folderId, e);
                }

                List<RecursiveTask<List<DrivePathFile>>> subTasks = items.stream()
                        .filter(file -> "application/vnd.google-apps.folder".equals(file.getMimeType()))
                        .map(file -> {
                            String subPath = Path.of(parentPath, file.getName()).toString();
                            return getAllFilesRecursive(file.getId(), subPath);
                        })
                        .toList();

                invokeAll(subTasks);

                items.stream()
                        .filter(file -> !"application/vnd.google-apps.folder".equals(file.getMimeType()))
                        .map(file -> new DrivePathFile(Path.of(parentPath, file.getName()).toString(), file))
                        .forEach(files::add);

                subTasks.stream()
                        .map(RecursiveTask::join)
                        .forEach(files::addAll);

                return files;
            }
        };
    }


    private FileList getFiles(String query, String pageToken) throws IOException {
        return driveService.files().list()
                .setPageSize(PAGE_SIZE)
                .setQ(query)
                .setOrderBy(DRIVE_ORDER_BY)
                .setPageToken(pageToken)
                .setFields(FILES_LIST_FIELDS)
                .execute();
    }
}
