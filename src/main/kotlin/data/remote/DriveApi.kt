package zechs.zplex.sync.data.remote

import com.google.api.services.drive.model.File
import zechs.zplex.sync.data.model.DrivePathFile

interface DriveApi {

    fun getFiles(folderId: String, foldersOnly: Boolean): List<File>
    fun getAllFilesRecursively(folderId: String): List<DrivePathFile>

}