package zechs.zplex.sync.data.local

import zechs.zplex.sync.data.model.DriveFile
import zechs.zplex.sync.data.model.Movie

interface MovieDao {

    fun upsertMovie(movie: Movie, file: DriveFile)

}