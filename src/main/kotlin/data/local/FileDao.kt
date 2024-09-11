package zechs.zplex.sync.data.local

import com.google.api.services.drive.model.File
import zechs.zplex.sync.data.model.DriveFile

interface FileDao {

    fun getAllFiles(): List<DriveFile>
    fun getAllMoviesFiles(): List<DriveFile>
    fun getAllEpisodesFiles(): List<DriveFile>
    fun getFileById(id: String): DriveFile?
    fun deleteFileById(id: String)
    fun updateModifiedTime(files: List<File>)

}