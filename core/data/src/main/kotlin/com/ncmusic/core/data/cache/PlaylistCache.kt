package com.ncmusic.core.data.cache

import android.content.Context
import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.network.model.Playlist
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 歌单详情（含歌曲列表）本地缓存。
 *
 * 在线打开过的歌单会存一份元数据 + 歌曲列表；断网再打开时至少能看到完整曲目，
 * 其中完整播放过（已进音频缓存）或已下载的歌曲可以直接播放。
 * 只保留最近打开的一批歌单，避免无限增长。
 */
@Singleton
class PlaylistCache @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val dir = File(context.filesDir, "playlist_cache")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(playlistId: Long): Playlist? = withContext(io) {
        if (playlistId <= 0L) return@withContext null
        val file = File(dir, "$playlistId.json")
        if (!file.exists()) return@withContext null
        runCatching {
            json.decodeFromString<Playlist>(file.readText())
        }.getOrNull()
    }

    suspend fun save(playlist: Playlist) = withContext(io) {
        if (playlist.id <= 0L || playlist.tracks == null) return@withContext
        runCatching {
            dir.mkdirs()
            File(dir, "${playlist.id}.json").writeText(json.encodeToString(playlist))
            prune()
        }
    }

    suspend fun clear() = withContext(io) {
        runCatching { dir.deleteRecursively() }
        Unit
    }

    private fun prune() {
        val files = dir.listFiles { file -> file.isFile && file.extension == "json" }
            ?.sortedByDescending { file -> file.lastModified() }
            ?: return
        files.drop(MAX_PLAYLISTS).forEach { file -> runCatching { file.delete() } }
    }

    private companion object {
        const val MAX_PLAYLISTS = 40
    }
}
