package com.ncmusic.feature.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.repository.PlayHistoryRepository
import com.ncmusic.core.network.model.Song
import com.ncmusic.core.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 最近播放（本地记录）页状态 */
data class RecentPlayUiState(
    val loading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val currentSongId: Long? = null,
)

/** 最近播放页 ViewModel：本地播放历史列表 */
@HiltViewModel
class RecentPlayViewModel @Inject constructor(
    private val playHistoryRepository: PlayHistoryRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val _state = MutableStateFlow(RecentPlayUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            playbackController.currentSong.collect { song ->
                _state.update { it.copy(currentSongId = song?.id) }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            val recent = playHistoryRepository.recentSongs(100).getOrNull() ?: emptyList()
            _state.update { it.copy(loading = false, songs = recent) }
        }
    }

    fun play(index: Int) {
        val songs = _state.value.songs
        val song = songs.getOrNull(index) ?: return
        playbackController.playSong(song, songs)
    }
}
