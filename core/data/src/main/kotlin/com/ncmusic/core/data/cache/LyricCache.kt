package com.ncmusic.core.data.cache

import android.content.Context
import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.network.model.LyricResp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 歌词本地缓存。
 *
 * 在线播放时把拿到的歌词顺手存一份，之后离线播放同一首歌时直接读本地，
 * 不再显示「暂无歌词」。数据量很小，统一放在应用私有目录的 lyrics 下。
 */
@Singleton
class LyricCache @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val dir = File(context.filesDir, "lyrics")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(songId: Long): LyricResp? = withContext(io) {
        if (songId <= 0L) return@withContext null
        val file = File(dir, "$songId.json")
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<LyricResp>(file.readText()) }.getOrNull()
    }

    suspend fun put(songId: Long, lyric: LyricResp) = withContext(io) {
        if (songId <= 0L || lyric.lrc?.lyric.isNullOrBlank()) return@withContext
        runCatching {
            dir.mkdirs()
            File(dir, "$songId.json").writeText(json.encodeToString(lyric))
        }
    }

    suspend fun clear() = withContext(io) {
        runCatching { dir.deleteRecursively() }
        Unit
    }
}
