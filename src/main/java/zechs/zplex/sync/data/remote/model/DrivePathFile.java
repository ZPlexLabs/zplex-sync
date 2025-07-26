package zechs.zplex.sync.data.remote.model;

import com.google.api.services.drive.model.File;

public record DrivePathFile(
        String path,
        File file
) {
}
