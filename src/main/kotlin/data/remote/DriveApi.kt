package zechs.zplex.sync.data.remote

import com.google.api.services.drive.model.File
import data.model.DrivePathFile

interface DriveApi {

    fun getFiles(folderId: String, foldersOnly: Boolean, modifiedTime: Boolean): List<File>
    fun getAllFilesRecursively(folderId: String): List<DrivePathFile>

}