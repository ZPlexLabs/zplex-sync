package zechs.zplex.sync.data.local

import zechs.zplex.sync.data.model.DriveFile
import zechs.zplex.sync.data.model.Genre
import zechs.zplex.sync.data.model.Movie

interface MovieDao {

    fun upsertMovie(movie: Movie, file: DriveFile)

    fun getCommonMovieGenres() : List<Genre>
    fun getCommonMovieStudios(): List<Pair<Int, String>>
    fun getCommonMovieParentalRatings(): List<String>
    fun getCommonMovieYears(): List<Int>
}