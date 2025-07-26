package zechs.zplex.sync.data.remote.model;

public record DriveFile(
        String id,
        String name,
        Long size,
        Long modifiedTime
) {
}

