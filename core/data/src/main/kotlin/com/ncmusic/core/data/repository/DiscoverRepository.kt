package com.ncmusic.core.data.repository

import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.data.model.Paged
import com.ncmusic.core.network.api.NeteaseApi
import com.ncmusic.core.network.api.WeapiBody
import com.ncmusic.core.network.model.Album
import com.ncmusic.core.network.model.CatItem
import com.ncmusic.core.network.model.PlaylistBrief
import com.ncmusic.core.network.model.Song
import com.ncmusic.core.network.util.apiCall
import com.ncmusic.core.network.util.jsonOf
import com.ncmusic.core.network.util.requireCode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 发现页 / 首页仓库 */
@Singleton
class DiscoverRepository @Inject constructor(
    private val api: NeteaseApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** 推荐歌单（匿名可用） */
    suspend fun personalizedPlaylists(limit: Int = 30): AppResult<List<PlaylistBrief>> = apiCall(io) {
        val resp = api.personalizedPlaylist(
            WeapiBody(jsonOf("limit" to limit, "total" to true, "n" to 1000)),
        )
        requireCode(resp.code, Str.res(R.string.failed_to_load_recommended_playlists))
        resp.result ?: emptyList()
    }

    /** 推荐新音乐（列表统一成 Song） */
    suspend fun newSongs(limit: Int = 12): AppResult<List<Song>> = apiCall(io) {
        val resp = api.personalizedNewsong(WeapiBody(jsonOf("limit" to limit, "type" to 0)))
        requireCode(resp.code, Str.res(R.string.failed_to_load_new_songs))
        resp.result?.mapNotNull { item ->
            item.song ?: if (item.id != 0L) {
                Song(
                    id = item.id,
                    name = item.name,
                    artists = item.artists,
                    al = Album(name = "", picUrl = item.picUrl),
                )
            } else {
                null
            }
        } ?: emptyList()
    }

    /** 每日推荐（需登录） */
    suspend fun dailyRecommendSongs(): AppResult<List<Song>> = apiCall(io) {
        val resp = api.recommendSongs(WeapiBody(jsonOf("offset" to 0, "total" to true, "limit" to 30)))
        requireCode(resp.code, Str.res(R.string.failed_to_load_daily_recommendations))
        resp.data?.dailySongs ?: emptyList()
    }

    /** 私人FM（需登录） */
    suspend fun personalFm(): AppResult<List<Song>> = apiCall(io) {
        val resp = api.personalFm(WeapiBody(jsonOf()))
        requireCode(resp.code, Str.res(R.string.failed_to_load_personal_fm))
        resp.data ?: emptyList()
    }

    /** 排行榜列表 */
    suspend fun toplist(): AppResult<List<PlaylistBrief>> = apiCall(io) {
        val resp = api.toplist(WeapiBody(jsonOf()))
        requireCode(resp.code, Str.res(R.string.failed_to_load_charts))
        resp.list ?: emptyList()
    }

    /** 歌单分类标签 */
    suspend fun catalogue(): AppResult<List<CatItem>> = apiCall(io) {
        val resp = api.playlistCatalogue(WeapiBody(jsonOf()))
        requireCode(resp.code, Str.res(R.string.failed_to_load_playlist_categories))
        resp.sub ?: emptyList()
    }

    /** 分类歌单（歌单广场） */
    suspend fun topPlaylists(
        cat: String = "全部",
        offset: Int = 0,
        limit: Int = 30,
    ): AppResult<Paged<PlaylistBrief>> = apiCall(io) {
        val resp = api.topPlaylist(
            WeapiBody(
                jsonOf(
                    "cat" to cat,
                    "order" to "hot",
                    "limit" to limit,
                    "offset" to offset,
                    "total" to true,
                ),
            ),
        )
        requireCode(resp.code, Str.res(R.string.failed_to_load_the_playlist_square))
        Paged(
            items = resp.playlists ?: emptyList(),
            more = resp.more ?: ((resp.total ?: 0) > offset + limit),
        )
    }
}
