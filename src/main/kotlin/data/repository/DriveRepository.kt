package zechs.zplex.sync.data.repository

import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import zechs.zplex.sync.data.remote.DriveApi
import zechs.zplex.sync.utils.DriveApiQueryBuilder
import zechs.zplex.sync.utils.GoogleDrive

class DriveRepository(
    private val drive: GoogleDrive
) : DriveApi {

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