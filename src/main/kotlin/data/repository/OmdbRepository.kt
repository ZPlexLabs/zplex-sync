package zechs.zplex.sync.data.repository

import zechs.zplex.sync.data.model.omdb.OmdbMovieResponse
import zechs.zplex.sync.data.model.omdb.OmdbTvResponse
import zechs.zplex.sync.data.remote.OmdbApi

// The point of this class is to handle weird responses from the API, and
// 200 - Not Found response. (weird...)
class OmdbRepository(
    private val omdbApi: OmdbApi
) {

    fun fetchMovieById(imdbId: String): OmdbMovieResponse? {
        return try {
            omdbApi.fetchMovieById(imdbId)
        } catch (e: Exception) {
            null
        }
    }

    fun fetchTvById(imdbId: String): OmdbTvResponse? {
        return try {
            omdbApi.fetchTvById(imdbId)
        } catch (e: Exception) {
            null
        }
    }

    fun fetchMovieByName(title: String, year: Int): OmdbMovieResponse? {
        return try {
            omdbApi.fetchMovieByName(title, year)
        } catch (e: Exception) {
            null
        }
    }

    fun fetchTvByName(title: String, year: Int): OmdbTvResponse? {
        return try {
            omdbApi.fetchTvByName(title, year)
        } catch (e: Exception) {
            null
        }
    }

}