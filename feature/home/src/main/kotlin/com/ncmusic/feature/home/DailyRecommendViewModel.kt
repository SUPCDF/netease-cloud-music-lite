package com.ncmusic.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.repository.DiscoverRepository
import com.ncmusic.core.data.repository.LoginRepository
import com.ncmusic.core.network.model.Song
import com.ncmusic.core.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 每日推荐页状态 */
data class DailyUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val needLogin: Boolean = false,
    val songs: List<Song> = emptyList(),
    val dateText: String = "",
)

/** 每日推荐 ViewModel（需登录） */
@HiltViewModel
class DailyRecommendViewModel @Inject constructor(
    private val discoverRepository: DiscoverRepository,
    private val loginRepository: LoginRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val _state = MutableStateFlow(DailyUiState())
    val state = _state.asStateFlow()

    private var loaded = false

    fun load(force: Boolean = false) {
        if (loaded && !force) return
        loaded = true
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, needLogin = false) }
            if (!loginRepository.isLoggedInLocally()) {
                _state.update { it.copy(loading = false, needLogin = true) }
                return@launch
            }
            when (val result = discoverRepository.dailyRecommendSongs()) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        songs = result.data,
                        dateText = todayText(),
                    )
                }

                is AppResult.Failure -> _state.update {
                    // 登录态过期时接口返回 301/302
                    it.copy(
                        loading = false,
                        error = result.error.userMessage,
                        needLogin = !loginRepository.isLoggedInLocally(),
                    )
                }
            }
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

    private fun todayText(): String {
        val calendar = Calendar.getInstance()
        return Str.res(R.string.date_md, calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
    }
}
