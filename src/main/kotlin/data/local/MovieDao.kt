package zechs.zplex.sync.data.local

import data.model.DriveFile
import zechs.zplex.sync.data.model.Movie

interface MovieDao {

    fun upsertMovie(movie: Movie, file: DriveFile)
    fun getAllMovies(): List<Movie>
    fun getMovieById(id: Int): Movie?
    fun deleteMovieById(id: Int)

}