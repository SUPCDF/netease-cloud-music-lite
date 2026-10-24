package com.ncmusic.core.data.download

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.common.result.AppError
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.repository.SongRepository
import com.ncmusic.core.network.model.Album
import com.ncmusic.core.network.model.Artist
import com.ncmusic.core.network.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

private val Context.downloadDataStore by preferencesDataStore(name = "ncmusic_downloads")

/** 本地已下载歌曲（音频文件保存在应用专属外部目录，卸载即清理） */
@Serializable
data class DownloadedSong(
    val songId: Long,
    val name: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String? = null,
    val durationMs: Long = 0L,
    val quality: String = "",
    val filePath: String,
    val sizeBytes: Long = 0L,
    val downloadedAt: Long = 0L,
) {
    /** 还原成播放器可用的歌曲模型 */
    fun toSong(): Song = Song(
        id = songId,
        name = name,
        ar = listOf(Artist(id = 0, name = artist)),
        al = Album(id = 0, name = album, picUrl = coverUrl),
        dt = durationMs,
    )
}

/** 下载结果 */
sealed interface DownloadEvent {
    data class Success(val song: DownloadedSong) : DownloadEvent
    data class Failure(val message: String) : DownloadEvent
}

/**
 * 歌曲下载仓库：
 * - 通过 song/enhance/player/url 解析地址（与在线播放同音质）
 * - 用 HttpURLConnection 流式写入应用专属目录 `Android/data/<pkg>/files/downloads`
 * - 元数据以 JSON 形式持久化在独立 DataStore
 */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val key = stringPreferencesKey("downloaded_songs")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + io)

    /** 已下载列表（按下载时间倒序） */
    val downloads: Flow<List<DownloadedSong>> = context.downloadDataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<DownloadedSong>>(raw) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.downloadedAt }
    }

    /** 下载进度（songId -> 0..100） */
    private val _progress = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val progress: StateFlow<Map<Long, Int>> = _progress.asStateFlow()

    suspend fun isDownloaded(songId: Long): Boolean =
        downloads.first().any { it.songId == songId && File(it.filePath).exists() }

    /** 下载单曲（已下载则直接返回已有记录） */
    suspend fun download(song: Song, level: String): AppResult<DownloadedSong> {
        if (song.id <= 0L) return AppResult.Failure(AppError.Api(-1, Str.res(R.string.incomplete_song_info)))
        val existing = downloads.first().firstOrNull { it.songId == song.id }
        if (existing != null && File(existing.filePath).exists()) {
            return AppResult.Success(existing)
        }

        return withContext(io) {
            try {
                val item = songRepository.getSongUrl(song.id, level).getOrNull()
                val url = item?.url ?: return@withContext AppResult.Failure(
                    AppError.Api(-1, Str.res(R.string.can_t_get_the_download_url_possibly_copyright_re)),
                )
                val dir = File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }
                val ext = url.substringBefore('?').substringAfterLast('.', "mp3")
                    .takeIf { it.length in 1..4 }?.lowercase() ?: "mp3"
                val file = File(dir, "${song.id}.$ext")
                streamToFile(url, file) { percent ->
                    _progress.value = _progress.value + (song.id to percent)
                }.getOrThrow()
                val downloaded = DownloadedSong(
                    songId = song.id,
                    name = song.name,
                    artist = song.artistText,
                    album = song.albumName,
                    coverUrl = song.coverUrl,
                    durationMs = song.durationMs,
                    quality = item.level ?: level,
                    filePath = file.absolutePath,
                    sizeBytes = file.length(),
                    downloadedAt = System.currentTimeMillis(),
                )
                save(downloaded)
                // 顺手预取歌词，之后离线播放下载歌曲也能看到歌词
                scope.launch { runCatching { songRepository.lyric(song.id) } }
                AppResult.Success(downloaded)
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Api(-1, t.message ?: Str.res(R.string.download_failed)))
            } finally {
                _progress.value = _progress.value - song.id
            }
        }
    }

    /** 删除下载（同时删除本地文件） */
    suspend fun delete(songId: Long) = withContext(io) {
        val current = downloads.first()
        current.firstOrNull { it.songId == songId }?.let { runCatching { File(it.filePath).delete() } }
        persist(current.filterNot { it.songId == songId })
        Unit
    }

    private suspend fun save(item: DownloadedSong) {
        val current = downloads.first().filterNot { it.songId == item.songId }
        persist(listOf(item) + current)
    }

    private suspend fun persist(list: List<DownloadedSong>) {
        context.downloadDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(list)
        }
    }

    /** 流式下载到文件，回调百分比进度 */
    private fun streamToFile(
        url: String,
        target: File,
        onProgress: (Int) -> Unit,
    ): Result<Unit> = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                error(Str.res(R.string.download_failed_http, connection.responseCode))
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var written = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            onProgress(((written * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
