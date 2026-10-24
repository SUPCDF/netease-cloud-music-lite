package com.ncmusic.core.data.repository

import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.common.result.AppError
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.network.api.NeteaseApi
import com.ncmusic.core.network.api.WeapiBody
import com.ncmusic.core.network.model.Playlist
import com.ncmusic.core.network.util.apiCall
import com.ncmusic.core.network.util.jsonOf
import com.ncmusic.core.network.util.requireCode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 歌单仓库 */
@Singleton
class PlaylistRepository @Inject constructor(
    private val api: NeteaseApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** 歌单详情（含全部歌曲） */
    suspend fun playlistDetail(id: Long): AppResult<Playlist> = apiCall(io) {
        val resp = api.playlistDetail(WeapiBody(jsonOf("id" to id, "n" to 1000, "s" to 8)))
        if (resp.code != 200) throw AppError.Api(resp.code ?: -1, Str.res(R.string.failed_to_load_playlist))
        val playlist = resp.playlist ?: throw AppError.Api(resp.code ?: -1, Str.res(R.string.playlist_not_found))
        playlist.withAllTracks()
    }

    /**
     * 补全歌曲列表。
     *
     * v6 歌单详情在部分歌单（尤其是「收藏的歌单」）下只会返回前 20 首，
     * `n` 参数并不总是生效。这里以 `trackIds` 为准，缺多少就按 500 首一批
     * 调 song/detail 补齐，保证详情页能看到完整歌曲。
     */
    private suspend fun Playlist.withAllTracks(): Playlist {
        val ids = trackIds?.map { it.id }.orEmpty()
        val current = tracks.orEmpty()
        if (ids.isEmpty() || current.size >= ids.size) return this
        val byId = current.associateBy { it.id }.toMutableMap()
        ids.filterNot { byId.containsKey(it) }
            .chunked(SONG_DETAIL_BATCH)
            .forEach { chunk ->
                val songs = runCatching {
                    val c = chunk.joinToString(",", "[", "]") { "{\"id\":$it}" }
                    api.songDetail(
                        WeapiBody(jsonOf("c" to c, "ids" to "[${chunk.joinToString(",")}]")),
                    ).songs
                }.getOrNull().orEmpty()
                songs.forEach { byId[it.id] = it }
            }
        return copy(tracks = ids.mapNotNull { byId[it] })
    }

    /** 用户歌单（我创建的 / 收藏的） */
    suspend fun userPlaylists(uid: Long): AppResult<List<Playlist>> = apiCall(io) {
        val resp = api.userPlaylists(
            WeapiBody(jsonOf("uid" to uid, "limit" to 1000, "offset" to 0, "includeVideo" to true)),
        )
        resp.playlist ?: emptyList()
    }

    /** 我喜欢的音乐 id 集合 */
    suspend fun likedSongIds(uid: Long): AppResult<Set<Long>> = apiCall(io) {
        val resp = api.likelist(WeapiBody(jsonOf("uid" to uid)))
        (resp.ids ?: emptyList()).toSet()
    }

    /** 喜欢 / 取消喜欢单曲 */
    suspend fun likeSong(songId: Long, like: Boolean): AppResult<Unit> = apiCall(io) {
        val resp = api.likeSong(
            WeapiBody(
                jsonOf(
                    "alg" to "itembased",
                    "trackId" to songId,
                    "like" to like,
                    "time" to "3",
                ),
            ),
        )
        requireCode(resp.code, if (like) Str.res(R.string.failed_to_like) else Str.res(R.string.failed_to_unlike))
        Unit
    }

    /** 收藏 / 取消收藏歌单（网易的取消收藏是独立接口，不是 subscribe + t=2） */
    suspend fun subscribePlaylist(playlistId: Long, subscribe: Boolean): AppResult<Unit> = apiCall(io) {
        val resp = if (subscribe) {
            api.subscribePlaylist(WeapiBody(jsonOf("id" to playlistId)))
        } else {
            api.unsubscribePlaylist(WeapiBody(jsonOf("id" to playlistId)))
        }
        requireCode(resp.code, if (subscribe) Str.res(R.string.failed_to_favorite) else Str.res(R.string.failed_to_unfavorite))
        Unit
    }

    /** 创建歌单，返回新歌单 id */
    suspend fun createPlaylist(name: String): AppResult<Long> = apiCall(io) {
        val resp = api.createPlaylist(WeapiBody(jsonOf("name" to name, "privacy" to 0)))
        resp.playlist?.id ?: resp.id ?: throw AppError.Api(resp.code ?: -1, Str.res(R.string.failed_to_create_playlist))
    }

    /** 歌单重命名 */
    suspend fun renamePlaylist(playlistId: Long, name: String): AppResult<Unit> = apiCall(io) {
        val resp = api.renamePlaylist(WeapiBody(jsonOf("id" to playlistId, "name" to name)))
        requireCode(resp.code, Str.res(R.string.failed_to_rename))
        Unit
    }

    /** 删除歌单 */
    suspend fun deletePlaylist(playlistId: Long): AppResult<Unit> = apiCall(io) {
        val resp = api.deletePlaylist(WeapiBody(jsonOf("pid" to playlistId)))
        requireCode(resp.code, Str.res(R.string.delete_failed))
        Unit
    }

    /** 歌单加歌 */
    suspend fun addTracks(playlistId: Long, songIds: List<Long>): AppResult<Unit> = apiCall(io) {
        val trackIds = songIds.joinToString(",", "[", "]")
        val resp = api.manipulateTracks(
            WeapiBody(jsonOf("op" to "add", "pid" to playlistId, "trackIds" to trackIds)),
        )
        requireCode(resp.code, Str.res(R.string.failed_to_add))
        Unit
    }

    /** 歌单删歌 */
    suspend fun removeTracks(playlistId: Long, songIds: List<Long>): AppResult<Unit> = apiCall(io) {
        val trackIds = songIds.joinToString(",", "[", "]")
        val resp = api.manipulateTracks(
            WeapiBody(jsonOf("op" to "del", "pid" to playlistId, "trackIds" to trackIds)),
        )
        requireCode(resp.code, Str.res(R.string.failed_to_remove))
        Unit
    }

    private companion object {
        /** song/detail 单批最大数量 */
        const val SONG_DETAIL_BATCH = 500
    }
}
