package com.ncmusic.core.data.repository

import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.common.model.SearchType
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.map
import com.ncmusic.core.data.model.Paged
import com.ncmusic.core.data.model.SearchResultData
import com.ncmusic.core.network.api.NeteaseApi
import com.ncmusic.core.network.api.WeapiBody
import com.ncmusic.core.network.model.Song
import com.ncmusic.core.network.util.apiCall
import com.ncmusic.core.network.util.requireCode
import com.ncmusic.core.network.util.jsonOf
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 搜索仓库 */
@Singleton
class SearchRepository @Inject constructor(
    private val api: NeteaseApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** 分类搜索 */
    suspend fun search(
        keywords: String,
        type: Int = SearchType.SONG,
        offset: Int = 0,
        limit: Int = 30,
    ): AppResult<SearchResultData> = apiCall(io) {
        val resp = api.search(
            WeapiBody(
                jsonOf(
                    "s" to keywords,
                    "type" to type,
                    "limit" to limit,
                    "offset" to offset,
                ),
            ),
        )
        requireCode(resp.code, Str.res(R.string.search_failed))
        val result = resp.result ?: throw com.ncmusic.core.common.result.AppError.Api(resp.code ?: -1, Str.res(R.string.search_failed))
        val more = when (type) {
            SearchType.SONG -> (result.songCount ?: 0) > offset + limit
            SearchType.ALBUM -> (result.albumCount ?: 0) > offset + limit
            SearchType.ARTIST -> (result.artistCount ?: 0) > offset + limit
            SearchType.PLAYLIST -> (result.playlistCount ?: 0) > offset + limit
            SearchType.USER -> (result.userprofileCount ?: 0) > offset + limit
            else -> false
        }
        SearchResultData(
            songs = result.songs ?: emptyList(),
            artists = result.artists ?: emptyList(),
            albums = result.albums ?: emptyList(),
            playlists = result.playlists ?: emptyList(),
            users = result.userprofiles ?: emptyList(),
            more = more,
        )
    }

    /** 搜索建议（单曲聚合） */
    suspend fun suggest(keywords: String): AppResult<List<Song>> = apiCall(io) {
        val resp = api.suggest(WeapiBody(jsonOf("s" to keywords, "limit" to 10)))
        resp.result?.songs ?: emptyList()
    }
}
