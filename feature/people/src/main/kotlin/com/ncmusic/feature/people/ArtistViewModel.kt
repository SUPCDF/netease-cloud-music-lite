package com.ncmusic.feature.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.data.repository.ArtistRepository
import com.ncmusic.core.network.model.Album
import com.ncmusic.core.network.model.Artist
import com.ncmusic.core.network.model.Song
import com.ncmusic.core.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 歌手页状态 */
data class ArtistUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val artist: Artist? = null,
    val hotSongs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val albumsLoading: Boolean = false,
    val albumsMore: Boolean = true,
)

/** 歌手页：资料 / 热门歌曲 / 专辑列表 */
@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val artistRepository: ArtistRepository,
    val playbackController: PlaybackController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val artistId: Long = savedStateHandle[ARG_ARTIST_ID] ?: 0L

    private val _state = MutableStateFlow(ArtistUiState())
    val state: StateFlow<ArtistUiState> = _state.asStateFlow()

    val currentSong = playbackController.currentSong

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = artistRepository.detail(artistId)) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            loading = false,
                            artist = result.data.artist,
                            hotSongs = result.data.hotSongs,
                        )
                    }
                    loadAlbums(reset = true)
                }

                is AppResult.Failure -> {
                    _state.update { it.copy(loading = false, error = result.error.userMessage) }
                }
            }
        }
    }

    /** 专辑分页加载（reset=false 时追加下一页） */
    fun loadAlbums(reset: Boolean = false) {
        val snapshot = _state.value
        if (snapshot.albumsLoading) return
        if (!reset && !snapshot.albumsMore) return
        viewModelScope.launch {
            _state.update { it.copy(albumsLoading = true) }
            val offset = if (reset) 0 else snapshot.albums.size
            when (val result = artistRepository.albums(artistId, offset)) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            albumsLoading = false,
                            albums = if (reset) result.data.albums else it.albums + result.data.albums,
                            albumsMore = result.data.more,
                        )
                    }
                }

                is AppResult.Failure -> {
                    _state.update { it.copy(albumsLoading = false) }
                }
            }
        }
    }

    // ---------------- 播放 ----------------

    fun playAll() {
        val songs = _state.value.hotSongs
        if (songs.isNotEmpty()) playbackController.playQueue(songs, 0)
    }

    fun playSong(song: Song) {
        playbackController.playSong(song, _state.value.hotSongs)
    }

    fun togglePlayPause() {
        playbackController.togglePlayPause()
    }

    companion object {
        const val ARG_ARTIST_ID = "artistId"
    }
}
