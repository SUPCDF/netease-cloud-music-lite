package com.ncmusic.feature.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.data.download.DownloadRepository
import com.ncmusic.core.data.download.DownloadedSong
import com.ncmusic.core.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 我的下载页状态 */
data class DownloadUiState(
    val loading: Boolean = true,
    val songs: List<DownloadedSong> = emptyList(),
    val currentSongId: Long? = null,
    val toast: String? = null,
)

/** 我的下载页 ViewModel：本地离线歌曲列表 */
@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            downloadRepository.downloads.collect { list ->
                _state.update { it.copy(loading = false, songs = list) }
            }
        }
        viewModelScope.launch {
            playbackController.currentSong.collect { song ->
                _state.update { it.copy(currentSongId = song?.id) }
            }
        }
    }

    /** 播放本地文件（不消耗流量，离线可用） */
    fun play(index: Int) {
        val list = _state.value.songs
        if (index !in list.indices) return
        playbackController.registerLocalPaths(list.associate { it.songId to it.filePath })
        playbackController.playQueue(list.map { it.toSong() }, index)
    }

    fun delete(songId: Long) {
        viewModelScope.launch {
            downloadRepository.delete(songId)
            _state.update { it.copy(toast = Str.res(R.string.download_deleted)) }
        }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }
}
