package com.ncmusic.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.model.SearchType
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.repository.SearchRepository
import com.ncmusic.core.data.settings.SettingsRepository
import com.ncmusic.core.network.model.PlaylistBrief
import com.ncmusic.core.network.model.SearchAlbum
import com.ncmusic.core.network.model.SearchArtist
import com.ncmusic.core.network.model.Song
import com.ncmusic.core.network.model.UserBrief
import com.ncmusic.core.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ncmusic.core.common.R

/** 搜索页状态 */
data class SearchUiState(
    val keyword: String = "",
    val submitted: Boolean = false,
    val history: List<String> = emptyList(),
    val suggestions: List<Song> = emptyList(),
    val tabIndex: Int = 0,
    val songs: List<Song> = emptyList(),
    val artists: List<SearchArtist> = emptyList(),
    val albums: List<SearchAlbum> = emptyList(),
    val playlists: List<PlaylistBrief> = emptyList(),
    val users: List<UserBrief> = emptyList(),
    val more: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val playingSongId: Long? = null,
)

/** 搜索页 ViewModel：搜索建议 + 分类结果 + 历史记录 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val settingsRepository: SettingsRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state = _state.asStateFlow()

    private var suggestJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.searchHistory.collect { history ->
                _state.update { it.copy(history = history) }
            }
        }
        viewModelScope.launch {
            playbackController.currentSong.collect { song ->
                _state.update { it.copy(playingSongId = song?.id) }
            }
        }
    }

    /** 输入关键词：防抖请求搜索建议 */
    fun updateKeyword(value: String) {
        _state.update { it.copy(keyword = value) }
        suggestJob?.cancel()
        if (value.isBlank()) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(300)
            val songs = searchRepository.suggest(value).getOrNull() ?: return@launch
            _state.update { it.copy(suggestions = songs) }
        }
    }

    /** 提交搜索（默认先搜单曲） */
    fun submit(keyword: String = _state.value.keyword) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        _state.update {
            it.copy(
                keyword = trimmed,
                submitted = true,
                suggestions = emptyList(),
                loading = true,
                error = null,
                tabIndex = 0,
                songs = emptyList(),
                artists = emptyList(),
                albums = emptyList(),
                playlists = emptyList(),
                users = emptyList(),
                more = false,
            )
        }
        viewModelScope.launch {
            settingsRepository.addSearchHistory(trimmed)
            runSearch(SearchType.SONG, offset = 0, append = false)
        }
    }

    /** 切换结果分类 tab；该分类还没有数据时自动请求 */
    fun selectTab(index: Int) {
        if (index == _state.value.tabIndex) return
        _state.update { it.copy(tabIndex = index) }
        val snapshot = _state.value
        val hasData = when (index) {
            0 -> snapshot.songs.isNotEmpty()
            1 -> snapshot.artists.isNotEmpty()
            2 -> snapshot.albums.isNotEmpty()
            3 -> snapshot.playlists.isNotEmpty()
            else -> snapshot.users.isNotEmpty()
        }
        if (!hasData && !snapshot.loading) {
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                runSearch(SEARCH_TABS[index].type, offset = 0, append = false)
            }
        }
    }

    /** 单曲结果加载更多 */
    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loadingMore || snapshot.loading || !snapshot.more || snapshot.tabIndex != 0) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            runSearch(SearchType.SONG, offset = snapshot.songs.size, append = true)
        }
    }

    private suspend fun runSearch(type: Int, offset: Int, append: Boolean) {
        when (val result = searchRepository.search(_state.value.keyword, type, offset, PAGE_SIZE)) {
            is AppResult.Success -> _state.update { current ->
                current.copy(
                    loading = false,
                    loadingMore = false,
                    error = null,
                    songs = if (type == SearchType.SONG) {
                        if (append) current.songs + result.data.songs else result.data.songs
                    } else {
                        current.songs
                    },
                    artists = if (type == SearchType.ARTIST) result.data.artists else current.artists,
                    albums = if (type == SearchType.ALBUM) result.data.albums else current.albums,
                    playlists = if (type == SearchType.PLAYLIST) result.data.playlists else current.playlists,
                    users = if (type == SearchType.USER) result.data.users else current.users,
                    more = if (type == SearchType.SONG) result.data.more else current.more,
                )
            }

            is AppResult.Failure -> _state.update {
                it.copy(
                    loading = false,
                    loadingMore = false,
                    error = result.error.userMessage,
                )
            }
        }
    }

    // ---------------- 播放 / 历史 ----------------

    fun playSong(song: Song) {
        playbackController.playSong(song, _state.value.songs)
    }

    fun removeHistory(keyword: String) {
        viewModelScope.launch { settingsRepository.removeSearchHistory(keyword) }
    }

    fun clearHistory() {
        viewModelScope.launch { settingsRepository.clearSearchHistory() }
    }

    fun clearKeyword() {
        _state.update {
            it.copy(
                keyword = "",
                submitted = false,
                suggestions = emptyList(),
                error = null,
            )
        }
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}

/** 结果分类（顺序与 Tab 一致） */
val SEARCH_TABS = listOf(
    SearchTab(R.string.songs, SearchType.SONG),
    SearchTab(R.string.artist, SearchType.ARTIST),
    SearchTab(R.string.album, SearchType.ALBUM),
    SearchTab(R.string.playlist, SearchType.PLAYLIST),
    SearchTab(R.string.user, SearchType.USER),
)

/** 标题保存资源 id，切换语言后重建 Activity 能立即生效 */
data class SearchTab(val titleRes: Int, val type: Int)
