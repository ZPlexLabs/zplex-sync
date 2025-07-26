package zechs.zplex.sync.data.remote;

import com.google.api.services.drive.model.File;
import zechs.zplex.sync.data.remote.model.DrivePathFile;

import java.io.IOException;
import java.util.List;

public interface DriveApi {

    List<File> getFiles(String folderId, Boolean foldersOnly) throws IOException;

    List<DrivePathFile> getAllFilesRecursively(String folderId);

}
