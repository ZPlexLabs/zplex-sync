package zechs.zplex.sync.data.local

import zechs.zplex.sync.data.model.DriveFile
import zechs.zplex.sync.data.model.Episode
import zechs.zplex.sync.data.model.Genre
import zechs.zplex.sync.data.model.Season
import zechs.zplex.sync.data.model.Show

interface ShowDao {
    fun upsertShow(show: Show, seasons: List<Season>, episodes: List<Episode>, files: List<DriveFile>)
    fun getAllShows(): List<Show>
    fun getAllSeasonIds(): List<Int>

    fun updateShowsModifiedTime(shows: List<Show>)

    fun getCommonShowGenres(): List<Genre>
    fun getCommonShowStudios(): List<Pair<Int, String>>
    fun getCommonShowParentalRatings(): List<String>
    fun getCommonShowYears(): List<Int>
}