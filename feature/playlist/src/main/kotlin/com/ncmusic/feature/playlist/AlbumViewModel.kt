package com.ncmusic.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.repository.AlbumRepository
import com.ncmusic.core.data.repository.LoginRepository
import com.ncmusic.core.data.repository.PlaylistRepository
import com.ncmusic.core.network.model.Album
import com.ncmusic.core.network.model.Song
import com.ncmusic.core.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 专辑详情页状态 */
data class AlbumUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val likedIds: Set<Long> = emptySet(),
    val toast: String? = null,
)

/** 专辑详情 ViewModel：播放全部 / 逐曲播放 / 喜欢 */
@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val playlistRepository: PlaylistRepository,
    private val loginRepository: LoginRepository,
    private val playbackController: PlaybackController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val albumId: Long = savedStateHandle[ARG_ALBUM_ID] ?: 0L

    private val _state = MutableStateFlow(AlbumUiState())
    val state = _state.asStateFlow()

    val currentSong = playbackController.currentSong

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = albumRepository.detail(albumId)) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            loading = false,
                            album = result.data.album,
                            songs = result.data.songs,
                        )
                    }
                    loadLikedIds()
                }

                is AppResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.error.userMessage)
                }
            }
        }
    }

    private fun loadLikedIds() {
        viewModelScope.launch {
            val uid = loginRepository.account().getOrNull()?.account?.id ?: return@launch
            val ids = playlistRepository.likedSongIds(uid).getOrNull() ?: return@launch
            _state.update { it.copy(likedIds = ids) }
        }
    }

    // ---------------- 播放 ----------------

    fun playAll() {
        val songs = _state.value.songs
        if (songs.isNotEmpty()) playbackController.playQueue(songs, 0)
    }

    fun playSong(song: Song) {
        playbackController.playSong(song, _state.value.songs)
    }

    fun addToQueue(song: Song) {
        playbackController.addToQueue(song)
        _state.update { it.copy(toast = Str.res(R.string.added_to_queue)) }
    }

    fun playNext(song: Song) {
        playbackController.addToQueueNext(song)
        _state.update { it.copy(toast = Str.res(R.string.added_to_play_next)) }
    }

    // ---------------- 喜欢 ----------------

    fun toggleLike(songId: Long) {
        viewModelScope.launch {
            if (!loginRepository.isLoggedInLocally()) {
                _state.update { it.copy(toast = Str.res(R.string.please_log_in_first)) }
                return@launch
            }
            val liked = songId in _state.value.likedIds
            when (val result = playlistRepository.likeSong(songId, !liked)) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        likedIds = if (liked) it.likedIds - songId else it.likedIds + songId,
                        toast = if (liked) Str.res(R.string.removed_from_liked) else Str.res(R.string.added_to_liked),
                    )
                }

                is AppResult.Failure -> _state.update {
                    it.copy(toast = result.error.userMessage)
                }
            }
        }
    }

    fun notify(message: String) {
        _state.update { it.copy(toast = message) }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    companion object {
        const val ARG_ALBUM_ID = "albumId"
    }
}
