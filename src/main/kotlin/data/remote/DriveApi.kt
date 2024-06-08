package zechs.zplex.sync.data.remote

import com.google.api.services.drive.model.File

interface DriveApi {

    fun getFiles(folderId: String, foldersOnly: Boolean): List<File>

}