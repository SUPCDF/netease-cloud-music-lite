package com.ncmusic.feature.mv

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.data.repository.MvRepository
import com.ncmusic.core.network.model.MvDetail
import com.ncmusic.core.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** MV 播放页状态 */
data class MvUiState(
    val loading: Boolean = true,
    val detail: MvDetail? = null,
    val videoUrl: String? = null,
    val videoLoading: Boolean = false,
    val error: String? = null,
    val videoError: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val toast: String? = null,
)

/** MV 数据 / 播放器 / 下载 */
@HiltViewModel
class MvViewModel @Inject constructor(
    private val repository: MvRepository,
    private val playbackController: PlaybackController,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(MvUiState())
    val state = _state.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        .build()

    private var currentMvId = 0L
    private var tickerJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) startTicker() else {
                stopTicker()
                syncPosition()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val duration = player.duration.takeIf { it > 0 } ?: _state.value.durationMs
            _state.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    isPlaying = player.isPlaying,
                    durationMs = duration,
                )
            }
            if (playbackState == Player.STATE_READY) syncPosition()
            if (playbackState == Player.STATE_ENDED) {
                stopTicker()
                syncPosition()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            stopTicker()
            _state.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    videoError = Str.res(R.string.mv_unavailable),
                )
            }
        }
    }

    init {
        // 看 MV 时暂停歌曲，避免两路声音重叠
        playbackController.pause()
        player.addListener(playerListener)
    }

    fun load(mvId: Long, refresh: Boolean = false) {
        if (!refresh && currentMvId == mvId && _state.value.detail != null) return
        currentMvId = mvId
        player.stop()
        player.clearMediaItems()
        stopTicker()
        _state.value = MvUiState(loading = true)
        viewModelScope.launch {
            when (val result = repository.detail(mvId)) {
                is AppResult.Success -> {
                    if (mvId != currentMvId) return@launch
                    _state.update {
                        it.copy(
                            loading = false,
                            detail = result.data,
                            durationMs = result.data.duration,
                            videoLoading = true,
                        )
                    }
                    resolveVideo(mvId)
                }

                is AppResult.Failure -> {
                    if (mvId != currentMvId) return@launch
                    _state.update {
                        it.copy(loading = false, error = result.error.userMessage)
                    }
                }
            }
        }
    }

    fun retry() {
        if (currentMvId > 0) load(currentMvId, refresh = true)
    }

    fun retryVideo() {
        if (currentMvId <= 0 || _state.value.videoLoading) return
        viewModelScope.launch { resolveVideo(currentMvId) }
    }

    private suspend fun resolveVideo(mvId: Long) {
        if (mvId != currentMvId) return
        _state.update { it.copy(videoLoading = true, videoError = null) }
        when (val result = repository.videoUrl(mvId)) {
            is AppResult.Success -> {
                if (mvId != currentMvId) return
                val url = result.data.url
                if (url.isNullOrBlank()) {
                    _state.update {
                        it.copy(videoLoading = false, videoError = Str.res(R.string.mv_unavailable))
                    }
                    return
                }
                _state.update { it.copy(videoLoading = false, videoUrl = url) }
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.playWhenReady = true
            }

            is AppResult.Failure -> {
                if (mvId != currentMvId) return
                _state.update {
                    it.copy(videoLoading = false, videoError = result.error.userMessage)
                }
            }
        }
    }

    fun togglePlayPause() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekToFraction(fraction: Float) {
        val duration = player.duration.takeIf { it > 0 } ?: _state.value.durationMs
        if (duration > 0) {
            player.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
            syncPosition()
        }
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun pause() {
        player.pause()
    }

    fun download() {
        val detail = _state.value.detail ?: return
        val url = _state.value.videoUrl ?: run {
            _state.update { it.copy(toast = Str.res(R.string.mv_unavailable)) }
            return
        }
        val manager = context.getSystemService(DownloadManager::class.java)
        if (manager == null) {
            _state.update { it.copy(toast = Str.res(R.string.download_failed)) }
            return
        }
        val ok = runCatching {
            manager.enqueue(buildRequest(detail, url, publicDir = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q))
            true
        }.getOrElse {
            runCatching {
                manager.enqueue(buildRequest(detail, url, publicDir = false))
                true
            }.getOrDefault(false)
        }
        _state.update {
            it.copy(
                toast = Str.res(if (ok) R.string.mv_download_started else R.string.download_failed),
            )
        }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    private fun buildRequest(
        detail: MvDetail,
        url: String,
        publicDir: Boolean,
    ): DownloadManager.Request {
        val safeName = "${detail.name}-${detail.artistName}"
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .take(60)
            .ifBlank { "MV_${detail.id}" }
        val path = "NcmusicLite/$safeName.mp4"
        return DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("${detail.name} - MV")
            setDescription(detail.artistName)
            setMimeType("video/mp4")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            if (publicDir) {
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, path)
            } else {
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, path)
            }
        }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (isActive) {
                syncPosition()
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun syncPosition() {
        val duration = player.duration.takeIf { it > 0 } ?: _state.value.durationMs
        _state.update {
            it.copy(
                positionMs = player.currentPosition.coerceAtLeast(0L),
                bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
                durationMs = duration,
            )
        }
    }

    override fun onCleared() {
        stopTicker()
        player.removeListener(playerListener)
        player.release()
        super.onCleared()
    }
}
