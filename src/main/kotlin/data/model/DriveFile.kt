package data.model

import com.google.api.services.drive.model.File

data class DriveFile(
    val id: String,
    val name: String,
    val size: Long,
    val modifiedTime: Long
)


data class DrivePathFile(
    val path: String,
    val file: File
)