package zechs.zplex.sync.data.local

import data.model.DriveFile
import zechs.zplex.sync.data.model.Episode
import zechs.zplex.sync.data.model.Season
import zechs.zplex.sync.data.model.Show

interface ShowDao {
    fun upsertShow(show: Show, seasons: List<Season>, episodes: List<Episode>, files: List<DriveFile>)
    fun getAllShows(): List<Show>
    fun getShowById(id: Int): Show?
    fun deleteShowById(id: Int)

    fun addSeason(showId: Int, season: Season)
    fun deleteSeason(seasonId: Int)
    fun getSeasons(showId: Int): List<Season>
    fun getSeasonById(seasonId: Int): Season?

    fun addEpisode(seasonId: Int, episode: Episode, file: DriveFile)
    fun addEpisodes(seasonId: Int, episodes: MutableMap<DriveFile, Episode>)
    fun deleteEpisode(episodeId: Int)
    fun getEpisodes(seasonId: Int): List<Episode>
    fun getEpisodeById(episodeId: Int): Episode?
}