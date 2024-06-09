package zechs.zplex.sync.data.repository

import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import data.model.DrivePathFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import zechs.zplex.sync.data.remote.DriveApi
import zechs.zplex.sync.utils.DriveApiQueryBuilder
import zechs.zplex.sync.utils.GoogleDrive

class DriveRepository(
    private val drive: GoogleDrive,
    private val semaphore: Semaphore,
    private val scope: CoroutineScope
) : DriveApi {

    override fun getAllFilesRecursively(folderId: String): List<DrivePathFile> = runBlocking {
        val filesList = mutableListOf<DrivePathFile>()
        getAllFilesRecursive(folderId, "", filesList)
        return@runBlocking filesList
    }

    private suspend fun getAllFilesRecursive(
        folderId: String,
        parentPath: String,
        filesList: MutableList<DrivePathFile>
    ) {
        val files = getFiles(folderId, false)

        files.map { file ->
            semaphore.withPermit {
                scope.async {
                    val filePath = parentPath + file.name
                    if (file.mimeType == "application/vnd.google-apps.folder") {
                        // If the file is a folder, recurse into it
                        getAllFilesRecursive(file.id, "$filePath/", filesList)
                    } else {
                        // If the file is not a folder, add its path to the list
                        synchronized(filesList) {
                            filesList.add(DrivePathFile(filePath, file))
                        }
                    }
                }
            }
        }.awaitAll()
    }

    override fun getFiles(folderId: String, foldersOnly: Boolean): List<File> {
        val query = DriveApiQueryBuilder()
            .inParents(folderId)
            .trashed(false)
            .also { if (foldersOnly) it.mimeTypeEquals("application/vnd.google-apps.folder") }
            .build()

        val files = mutableListOf<File>()
        var pageToken: String? = null
        do {
            val response = getFiles(
                query = query,
                pageToken = pageToken,
                pageSize = 1000
            )
            if (response != null) {
                response.files
                    ?.toTypedArray()
                    ?.let { files.addAll(it) }
                pageToken = response.nextPageToken
            }
        } while (pageToken != null)

        return files.sortedBy { it.name }.toList()
    }

    private fun getFiles(
        query: String,
        pageSize: Int = 25,
        pageToken: String?
    ): FileList? {
        return drive.service.files().list()
            .setPageSize(pageSize)
            .setQ(query)
            .setPageToken(pageToken)
            .setFields("nextPageToken, files(id, name, size, mimeType)")
            .execute()
    }
}