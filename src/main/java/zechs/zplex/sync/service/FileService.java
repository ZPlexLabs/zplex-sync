package zechs.zplex.sync.service;


import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zechs.zplex.sync.data.local.model.File;
import zechs.zplex.sync.repository.FileRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FileService {

    private final FileRepository fileRepository;

    @Autowired
    public FileService(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    @Transactional
    public void batchUpdateModifiedTime(List<com.google.api.services.drive.model.File> remoteFiles) {
        if (remoteFiles == null || remoteFiles.isEmpty()) {
            return;
        }

        List<String> fileIds = remoteFiles.stream()
                .map(com.google.api.services.drive.model.File::getId)
                .toList();

        List<File> dbFiles = fileRepository.findByIdIn(fileIds);

        Map<String, Long> fileModifiedTimeMap = remoteFiles.stream()
                .collect(Collectors.toMap(
                        com.google.api.services.drive.model.File::getId,
                        file -> file.getModifiedTime().getValue()
                ));

        for (File dbFile : dbFiles) {
            Long newModifiedTime = fileModifiedTimeMap.get(dbFile.getId());
            if (newModifiedTime != null) {
                dbFile.setModifiedTime(newModifiedTime);
            }
        }

        int batchSize = 100;
        for (int i = 0; i < dbFiles.size(); i += batchSize) {
            List<File> batch = dbFiles.subList(i, Math.min(i + batchSize, dbFiles.size()));
            fileRepository.saveAll(batch);
        }
    }

    @Transactional
    public void deleteFilesByIds(List<String> remoteFilesIds) {
        int batchSize = 100;
        for (int i = 0; i < remoteFilesIds.size(); i += batchSize) {
            List<String> batchIds = remoteFilesIds.subList(i, Math.min(i + batchSize, remoteFilesIds.size()));
            fileRepository.deleteByIds(batchIds);
        }
    }
}
