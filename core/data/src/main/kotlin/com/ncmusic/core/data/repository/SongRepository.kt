package com.ncmusic.core.data.repository

import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.common.result.AppError
import com.ncmusic.core.data.cache.LyricCache
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.network.api.EapiBody
import com.ncmusic.core.network.api.NeteaseApi
import com.ncmusic.core.network.api.WeapiBody
import com.ncmusic.core.network.model.LyricResp
import com.ncmusic.core.network.model.Song
import com.ncmusic.core.network.model.SongUrlItem
import com.ncmusic.core.network.util.apiCall
import com.ncmusic.core.network.util.apiCodeToError
import com.ncmusic.core.network.util.jsonOf
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 歌曲仓库：播放地址 / 详情 / 歌词 / 打卡 */
@Singleton
class SongRepository @Inject constructor(
    private val api: NeteaseApi,
    private val lyricCache: LyricCache,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 获取播放地址。
     * 优先 weapi，失败自动降级 eapi；两者都拿不到时返回友好错误。
     */
    suspend fun getSongUrl(songId: Long, level: String, encodeType: String = "flac"): AppResult<SongUrlItem> =
        apiCall(io) {
            val primary = runCatching {
                api.songUrl(
                    WeapiBody(
                        jsonOf(
                            "ids" to "[$songId]",
                            "level" to level,
                            "encodeType" to encodeType,
                        ),
                    ),
                )
            }.getOrNull()

            val primaryItem = primary?.data?.firstOrNull()
            if (primaryItem?.url != null) return@apiCall primaryItem

            // eapi 备用通道（digest 路径用 /api 形式）
            val eapiResult = runCatching {
                api.songUrlEapi(
                    "https://interface.music.163.com/eapi/song/enhance/player/url/v1",
                    EapiBody(
                        digestPath = "/api/song/enhance/player/url/v1",
                        data = jsonOf(
                            "ids" to "[$songId]",
                            "level" to level,
                            "encodeType" to encodeType,
                        ),
                    ),
                )
            }.getOrNull()

            val eapiItem = eapiResult?.data?.firstOrNull()
            if (eapiItem?.url != null) return@apiCall eapiItem

            // 都拿不到：给出可读的错误提示
            val code = eapiItem?.code ?: primaryItem?.code ?: primary?.code ?: eapiResult?.code ?: -1
            throw when (code) {
                -110 -> AppError.Api(-110, Str.res(R.string.this_song_requires_vip_access_and_can_t_be_playe))
                404 -> AppError.Api(404, Str.res(R.string.can_t_play_this_song_right_now_copyright_restric))
                else -> apiCodeToError(code, Str.res(R.string.can_t_get_the_playback_url))
            }
        }

    /** 歌曲详情（批量） */
    suspend fun songDetail(ids: List<Long>): AppResult<List<Song>> = apiCall(io) {
        if (ids.isEmpty()) return@apiCall emptyList()
        val c = ids.joinToString(",", "[", "]") { "{\"id\":$it}" }
        val resp = api.songDetail(WeapiBody(jsonOf("c" to c, "ids" to "[${ids.joinToString(",")}]")))
        resp.songs ?: emptyList()
    }

    /** 歌词（原文 + 翻译）：优先本地缓存，离线时也能显示已听过的歌词 */
    suspend fun lyric(songId: Long): AppResult<LyricResp> {
        lyricCache.get(songId)?.let { cached -> return AppResult.Success(cached) }
        val result = apiCall(io) {
            api.lyric(WeapiBody(jsonOf("id" to songId, "lv" to -1, "kv" to -1, "tv" to -1)))
        }
        if (result is AppResult.Success) {
            lyricCache.put(songId, result.data)
        }
        return result
    }

    /** 播放打卡（播放进度达到要求后上报） */
    suspend fun scrobble(songId: Long, playedSeconds: Int): AppResult<Unit> = apiCall(io) {
        val logs = json.encodeToString<List<PlayLog>>(
            listOf(
                PlayLog(
                    action = "play",
                    json = PlayLogJson(
                        download = 0,
                        end = "playend",
                        id = songId,
                        sourceId = songId,
                        time = playedSeconds,
                        type = "song",
                        wifi = 0,
                    ),
                ),
            ),
        )
        api.weblog(WeapiBody(jsonOf("logs" to logs)))
        Unit
    }
}

@kotlinx.serialization.Serializable
private data class PlayLog(val action: String, val json: PlayLogJson)

@kotlinx.serialization.Serializable
private data class PlayLogJson(
    val download: Int,
    val end: String,
    val id: Long,
    val sourceId: Long,
    val time: Int,
    val type: String,
    val wifi: Int,
)
