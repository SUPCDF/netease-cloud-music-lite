package com.ncmusic.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.model.SoundQuality
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.download.DownloadRepository
import com.ncmusic.core.data.repository.LoginRepository
import com.ncmusic.core.data.repository.PlaylistRepository
import com.ncmusic.core.data.repository.SongRepository
import com.ncmusic.core.data.settings.SettingsRepository
import com.ncmusic.core.network.model.Song
import com.ncmusic.core.player.PlaybackController
import com.ncmusic.core.player.lyric.LrcLine
import com.ncmusic.core.player.lyric.LrcParser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/**
 * 播放器页面状态（迷你播放条与全屏播放器共用）。
 * 播放行为转发给全局单例 [PlaybackController]；歌词 / 喜欢状态在本 VM 内维护。
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    val controller: PlaybackController,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val loginRepository: LoginRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    // ---------------- 播放状态透传 ----------------

    val currentSong = controller.currentSong
    val queue = controller.queue
    val currentIndex = controller.currentIndex
    val isPlaying = controller.isPlaying
    val isBuffering = controller.isBuffering
    val positionMs = controller.positionMs
    val durationMs = controller.durationMs
    val playMode = controller.playMode
    val isFmMode = controller.isFmMode
    val speed = controller.speed
    val quality = controller.quality
    val errorMessage = controller.errorMessage

    /** 定时停止剩余毫秒（null 表示未开启） */
    val sleepTimerRemainingMs = controller.sleepTimerRemainingMs

    /** 灵魂鼓点闪光设置（播放页背景取封面颜色随鼓点脉动） */
    val beatGlowEnabled: StateFlow<Boolean> = settingsRepository.beatGlowEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 鼓点能量 0..1 */
    val beatEnergy: StateFlow<Float> = controller.beatEnergy

    /** 播放页在前台可见时才允许采集频谱 */
    fun setBeatVisualizerVisible(visible: Boolean) = controller.setBeatVisualizerVisible(visible)

    // ---------------- 歌词 ----------------

    private val _lyricLines = MutableStateFlow<List<LrcLine>>(emptyList())
    val lyricLines: StateFlow<List<LrcLine>> = _lyricLines.asStateFlow()

    /** 歌词是否显示翻译（设置项） */
    val lyricsTranslation: StateFlow<Boolean> = settingsRepository.lyricsTranslation
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private var lyricJob: Job? = null

    fun toggleLyricsTranslation() {
        viewModelScope.launch {
            settingsRepository.setLyricsTranslation(!lyricsTranslation.value)
        }
    }

    fun setSleepTimer(minutes: Int?) = controller.setSleepTimer(minutes)

    // ---------------- 喜欢状态 ----------------

    private val _likedIds = MutableStateFlow<Set<Long>>(emptySet())
    val likedIds: StateFlow<Set<Long>> = _likedIds.asStateFlow()

    private var likedIdsLoaded = false

    /** 本地已下载歌曲 id（用于下载按钮状态） */
    private val _downloadedIds = MutableStateFlow<Set<Long>>(emptySet())
    val downloadedIds: StateFlow<Set<Long>> = _downloadedIds.asStateFlow()

    /** 下载进度（songId -> 0..100） */
    val downloadProgress = downloadRepository.progress

    /** 一次性提示（喜欢/取消喜欢/需要登录等） */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        // 跟随当前歌曲加载歌词
        viewModelScope.launch {
            controller.currentSong.collect { song -> loadLyric(song?.id) }
        }
        // 通知栏红心切换 → 同步应用内喜欢状态
        viewModelScope.launch {
            controller.likeEvents.collect { (songId, liked) ->
                _likedIds.value = if (liked) {
                    _likedIds.value + songId
                } else {
                    _likedIds.value - songId
                }
            }
        }
        // 已下载列表
        viewModelScope.launch {
            downloadRepository.downloads.collect { list ->
                _downloadedIds.value = list.map { it.songId }.toSet()
            }
        }
    }

    private fun loadLyric(songId: Long?) {
        lyricJob?.cancel()
        if (songId == null) {
            _lyricLines.value = emptyList()
            return
        }
        lyricJob = viewModelScope.launch {
            val resp = songRepository.lyric(songId).getOrNull() ?: return@launch
            val lines = withContext(Dispatchers.Default) {
                LrcParser.parse(resp.lrc?.lyric, resp.tlyric?.lyric)
            }
            _lyricLines.value = lines
        }
    }

    // ---------------- 播放控制 ----------------

    fun togglePlayPause() = controller.togglePlayPause()

    /** 打开 MV 前先暂停歌曲，避免和视频声音重叠 */
    fun pause() = controller.pause()

    fun next() = controller.next()

    fun previous() = controller.previous()

    fun seekToFraction(fraction: Float) = controller.seekToFraction(fraction)

    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    fun cyclePlayMode() = controller.cyclePlayMode()

    fun setPlayMode(mode: com.ncmusic.core.common.model.PlayMode) = controller.setPlayMode(mode)

    fun setSpeed(speed: Float) = controller.setSpeed(speed)

    fun setQuality(level: String) = controller.setQuality(level)

    fun playAt(index: Int) {
        controller.playAt(index)
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) = controller.playQueue(songs, startIndex)

    fun playSong(song: Song, queue: List<Song> = emptyList()) = controller.playSong(song, queue)

    fun addToQueue(song: Song) {
        controller.addToQueue(song)
        _toast.value = Str.res(R.string.added_to_queue)
    }

    fun removeAt(index: Int) = controller.removeAt(index)

    fun clearQueue() = controller.clearQueue()

    fun consumeError() = controller.consumeError()

    fun consumeToast() {
        _toast.value = null
    }

    /** 通用提示（分享 / 复制链接等本地操作） */
    fun notify(message: String) {
        _toast.value = message
    }

    // ---------------- 喜欢 / 收藏 ----------------

    /** 懒加载「我喜欢的音乐」id 集合 */
    fun ensureLikedIds() {
        if (likedIdsLoaded) return
        likedIdsLoaded = true
        viewModelScope.launch {
            val uid = loginRepository.account().getOrNull()?.account?.id ?: run {
                likedIdsLoaded = false
                return@launch
            }
            val ids = playlistRepository.likedSongIds(uid).getOrNull() ?: run {
                likedIdsLoaded = false
                return@launch
            }
            _likedIds.value = ids
        }
    }

    fun toggleLike(songId: Long) {
        viewModelScope.launch {
            val uid = loginRepository.account().getOrNull()?.account?.id
            if (uid == null) {
                _toast.value = Str.res(R.string.please_log_in)
                return@launch
            }
            val liked = songId in _likedIds.value
            when (val result = playlistRepository.likeSong(songId, !liked)) {
                is AppResult.Success -> {
                    _likedIds.value = if (liked) {
                        _likedIds.value - songId
                    } else {
                        _likedIds.value + songId
                    }
                    _toast.value = if (liked) Str.res(R.string.removed_from_liked) else Str.res(R.string.added_to_liked)
                }

                is AppResult.Failure -> _toast.value = result.error.userMessage
            }
        }
    }

    /** 音质展示名 */
    fun qualityName(level: String): String = SoundQuality.displayName(level)

    // ---------------- 下载 ----------------

    /** 下载当前歌曲（使用当前音质） */
    fun download(song: Song) {
        if (song.id in _downloadedIds.value) {
            _toast.value = Str.res(R.string.already_in_downloads)
            return
        }
        val level = controller.quality.value
        _toast.value = Str.res(R.string.downloading, song.name)
        viewModelScope.launch {
            when (val result = downloadRepository.download(song, level)) {
                is AppResult.Success -> _toast.value = Str.res(R.string.downloaded_2, song.name)
                is AppResult.Failure -> _toast.value = result.error.userMessage
            }
        }
    }
}
