package com.ncmusic.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.repository.DiscoverRepository
import com.ncmusic.core.data.repository.LoginRepository
import com.ncmusic.core.data.repository.PersonalFmSource
import com.ncmusic.core.network.model.CatItem
import com.ncmusic.core.network.model.PlaylistBrief
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

/** 首页状态 */
data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val personalized: List<PlaylistBrief> = emptyList(),
    val newSongs: List<Song> = emptyList(),
    val toplists: List<PlaylistBrief> = emptyList(),
    val categories: List<CatItem> = emptyList(),
    val selectedCategory: String = "全部",
    val categoryPlaylists: List<PlaylistBrief> = emptyList(),
    val categoryLoading: Boolean = false,
    val loadingMore: Boolean = false,
    val categoryOffset: Int = 0,
    val categoryMore: Boolean = false,
    val toast: String? = null,
)

/** 首页（发现页）ViewModel */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val discoverRepository: DiscoverRepository,
    private val loginRepository: LoginRepository,
    private val personalFmSource: PersonalFmSource,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val personalized = discoverRepository.personalizedPlaylists()
            val newSongs = discoverRepository.newSongs()
            val toplists = discoverRepository.toplist()
            val catalogue = discoverRepository.catalogue()

            // 全部失败才展示错误页，否则展示可用的部分
            if (personalized is AppResult.Failure &&
                newSongs is AppResult.Failure &&
                toplists is AppResult.Failure
            ) {
                _state.update {
                    it.copy(loading = false, error = personalized.error.userMessage)
                }
                return@launch
            }

            _state.update {
                it.copy(
                    loading = false,
                    personalized = personalized.getOrNull() ?: emptyList(),
                    newSongs = newSongs.getOrNull() ?: emptyList(),
                    toplists = toplists.getOrNull() ?: emptyList(),
                    categories = catalogue.getOrNull() ?: emptyList(),
                )
            }
            loadCategory()
        }
    }

    // ---------------- 歌单广场 ----------------

    /** 下拉刷新：不显示整页 loading，只在顶部转圈；刷新失败保留原有内容 */
    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val personalized = discoverRepository.personalizedPlaylists()
            val newSongs = discoverRepository.newSongs()
            val toplists = discoverRepository.toplist()
            val catalogue = discoverRepository.catalogue()
            _state.update {
                it.copy(
                    refreshing = false,
                    personalized = personalized.getOrNull() ?: it.personalized,
                    newSongs = newSongs.getOrNull() ?: it.newSongs,
                    toplists = toplists.getOrNull() ?: it.toplists,
                    categories = catalogue.getOrNull() ?: it.categories,
                )
            }
            refreshCategory()
        }
    }

    /** 歌单广场第一页（刷新用，失败静默保留原列表） */
    private suspend fun refreshCategory() {
        val snapshot = _state.value
        when (val result = discoverRepository.topPlaylists(snapshot.selectedCategory, 0, PAGE_SIZE)) {
            is AppResult.Success -> _state.update {
                it.copy(
                    categoryPlaylists = result.data.items,
                    categoryOffset = result.data.items.size,
                    categoryMore = result.data.more,
                )
            }

            is AppResult.Failure -> Unit
        }
    }

    fun selectCategory(category: String) {
        if (category == _state.value.selectedCategory) return
        _state.update {
            it.copy(selectedCategory = category, categoryPlaylists = emptyList(), categoryOffset = 0)
        }
        loadCategory()
    }

    fun loadCategory(loadMore: Boolean = false) {
        val snapshot = _state.value
        if (loadMore && (snapshot.loadingMore || !snapshot.categoryMore)) return
        if (!loadMore && snapshot.categoryLoading) return

        val offset = if (loadMore) snapshot.categoryOffset else 0
        _state.update {
            if (loadMore) {
                it.copy(loadingMore = true)
            } else {
                it.copy(categoryLoading = true, categoryPlaylists = emptyList(), categoryOffset = 0, categoryMore = false)
            }
        }
        viewModelScope.launch {
            when (val result = discoverRepository.topPlaylists(snapshot.selectedCategory, offset, PAGE_SIZE)) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        categoryLoading = false,
                        loadingMore = false,
                        categoryPlaylists = if (loadMore) {
                            it.categoryPlaylists + result.data.items
                        } else {
                            result.data.items
                        },
                        categoryOffset = offset + result.data.items.size,
                        categoryMore = result.data.more,
                    )
                }

                is AppResult.Failure -> _state.update {
                    it.copy(
                        categoryLoading = false,
                        loadingMore = false,
                        toast = result.error.userMessage,
                    )
                }
            }
        }
    }

    // ---------------- 快速入口 ----------------

    /** 私人FM（需登录）：播放器队列始终只有当前一首，播完自动取下一首 */
    fun playPersonalFm(onNeedLogin: () -> Unit) {
        viewModelScope.launch {
            if (!loginRepository.isLoggedInLocally()) {
                onNeedLogin()
                return@launch
            }
            val first = personalFmSource.start()
            if (first == null) {
                _state.update { it.copy(toast = Str.res(R.string.no_playable_recommendations_right_now)) }
                return@launch
            }
            playbackController.playPersonalFm(first) { personalFmSource.next() }
            _state.update { it.copy(toast = Str.res(R.string.personal_fm_started)) }
        }
    }

    // ---------------- 播放 ----------------

    fun playNewSong(song: Song) {
        playbackController.playSong(song, _state.value.newSongs)
    }

    fun playPlaylistFirst(playlist: PlaylistBrief) {
        // 触发歌单详情页播放需要完整歌曲列表，这里交给详情页处理；
        // 首页只做导航，此方法保留给未来扩展。
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}
