package zechs.zplex.sync.data.local

import data.model.DriveFile

interface FileDao {

    fun getAllFiles(): List<DriveFile>
    fun getAllMoviesFiles(): List<DriveFile>
    fun getAllEpisodesFiles(): List<DriveFile>
    fun getFileById(id: String): DriveFile?
    fun deleteFileById(id: String)

}