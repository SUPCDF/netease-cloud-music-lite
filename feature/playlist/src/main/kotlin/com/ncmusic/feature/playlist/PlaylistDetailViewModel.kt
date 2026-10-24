package com.ncmusic.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.cache.PlaylistCache
import com.ncmusic.core.data.repository.LoginRepository
import com.ncmusic.core.data.repository.PlaylistRepository
import com.ncmusic.core.network.model.Playlist
import com.ncmusic.core.network.model.Song
import com.ncmusic.core.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 歌单详情页状态 */
data class PlaylistDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val playlist: Playlist? = null,
    val songs: List<Song> = emptyList(),
    val likedIds: Set<Long> = emptySet(),
    /** 当前正在播放的歌曲 id（用于歌单列表高亮） */
    val currentSongId: Long? = null,
    val loggedIn: Boolean = false,
    val myUid: Long? = null,
    val myPlaylists: List<Playlist> = emptyList(),
    val loadingMyPlaylists: Boolean = false,
    /** 当前展示的是本地缓存的歌单（离线） */
    val offline: Boolean = false,
    val toast: String? = null,
)

/** 歌单详情 ViewModel：播放全部 / 收藏 / 喜欢 / 加减歌曲 */
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val loginRepository: LoginRepository,
    private val playbackController: PlaybackController,
    private val playlistCache: PlaylistCache,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistDetailUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            playbackController.currentSong.collect { song ->
                _state.update { it.copy(currentSongId = song?.id) }
            }
        }
    }

    private var currentId = 0L
    private var loaded = false

    fun load(playlistId: Long, force: Boolean = false) {
        if (!force && loaded && currentId == playlistId) return
        currentId = playlistId
        loaded = true
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, offline = false) }
            when (val result = playlistRepository.playlistDetail(playlistId)) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            loading = false,
                            playlist = result.data,
                            songs = result.data.tracks ?: emptyList(),
                            offline = false,
                        )
                    }
                    playlistCache.save(result.data)
                }

                is AppResult.Failure -> {
                    // 断网时回退到上次打开过的歌单，曲目列表仍然完整
                    val cached = playlistCache.get(playlistId)
                    _state.update {
                        if (cached != null) {
                            it.copy(
                                loading = false,
                                error = null,
                                playlist = cached,
                                songs = cached.tracks ?: emptyList(),
                                offline = true,
                            )
                        } else {
                            it.copy(loading = false, error = result.error.userMessage)
                        }
                    }
                }
            }
            refreshUserState()
        }
    }

    private fun refreshUserState() {
        viewModelScope.launch {
            val uid = loginRepository.account().getOrNull()?.account?.id ?: run {
                _state.update { it.copy(loggedIn = false, myUid = null) }
                return@launch
            }
            _state.update { it.copy(loggedIn = true, myUid = uid) }
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

    fun playNext(song: Song) {
        playbackController.addToQueueNext(song)
        _state.update { it.copy(toast = Str.res(R.string.added_to_play_next)) }
    }

    fun addToQueue(song: Song) {
        playbackController.addToQueue(song)
        _state.update { it.copy(toast = Str.res(R.string.added_to_queue)) }
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

    // ---------------- 收藏歌单 ----------------

    fun toggleSubscribe() {
        val playlist = _state.value.playlist ?: return
        viewModelScope.launch {
            if (!loginRepository.isLoggedInLocally()) {
                _state.update { it.copy(toast = Str.res(R.string.please_log_in_first)) }
                return@launch
            }
            val target = !(playlist.subscribed ?: false)
            when (val result = playlistRepository.subscribePlaylist(playlist.id, target)) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        playlist = playlist.copy(subscribed = target),
                        toast = if (target) Str.res(R.string.playlist_favorited) else Str.res(R.string.removed_from_favorites),
                    )
                }

                is AppResult.Failure -> _state.update {
                    it.copy(toast = result.error.userMessage)
                }
            }
        }
    }

    // ---------------- 加歌 / 删歌 ----------------

    /** 加载「我的歌单」（添加到歌单用） */
    fun loadMyPlaylists() {
        if (_state.value.loadingMyPlaylists) return
        viewModelScope.launch {
            val uid = loginRepository.account().getOrNull()?.account?.id
            if (uid == null) {
                _state.update { it.copy(toast = Str.res(R.string.please_log_in_first)) }
                return@launch
            }
            _state.update { it.copy(loadingMyPlaylists = true) }
            val playlists = playlistRepository.userPlaylists(uid).getOrNull()
                ?.filter { it.userId == uid && it.specialType != 5 }
                ?: emptyList()
            _state.update { it.copy(loadingMyPlaylists = false, myPlaylists = playlists) }
        }
    }

    fun addToPlaylist(targetPlaylistId: Long, songId: Long) {
        viewModelScope.launch {
            when (val result = playlistRepository.addTracks(targetPlaylistId, listOf(songId))) {
                is AppResult.Success -> _state.update { it.copy(toast = Str.res(R.string.added_to_playlist)) }
                is AppResult.Failure -> _state.update { it.copy(toast = result.error.userMessage) }
            }
        }
    }

    fun createPlaylistAndAdd(name: String, songId: Long) {
        if (name.isBlank()) {
            _state.update { it.copy(toast = Str.res(R.string.playlist_name_can_t_be_empty)) }
            return
        }
        viewModelScope.launch {
            when (val created = playlistRepository.createPlaylist(name.trim())) {
                is AppResult.Success -> {
                    when (playlistRepository.addTracks(created.data, listOf(songId))) {
                        is AppResult.Success -> _state.update { it.copy(toast = Str.res(R.string.playlist_created_and_track_added)) }
                        is AppResult.Failure -> _state.update { it.copy(toast = Str.res(R.string.playlist_created_but_adding_the_track_failed)) }
                    }
                    loadMyPlaylists()
                }

                is AppResult.Failure -> _state.update { it.copy(toast = created.error.userMessage) }
            }
        }
    }

    /** 从当前歌单移除歌曲（仅自己的歌单可用） */
    fun removeTrack(songId: Long) {
        viewModelScope.launch {
            when (val result = playlistRepository.removeTracks(currentId, listOf(songId))) {
                is AppResult.Success -> _state.update { current ->
                    current.copy(
                        songs = current.songs.filterNot { it.id == songId },
                        playlist = current.playlist?.let { playlist ->
                            playlist.copy(trackCount = (playlist.trackCount ?: 1) - 1)
                        },
                        toast = Str.res(R.string.removed_from_playlist),
                    )
                }

                is AppResult.Failure -> _state.update { it.copy(toast = result.error.userMessage) }
            }
        }
    }

    /** 通用提示（分享 / 复制链接等本地操作） */
    fun notify(message: String) {
        _state.update { it.copy(toast = message) }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }
}
