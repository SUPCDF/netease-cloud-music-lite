package com.ncmusic.feature.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.repository.LoginRepository
import com.ncmusic.core.data.repository.UserRepository
import com.ncmusic.core.network.model.RecordItem
import com.ncmusic.core.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 听歌排行页状态 */
data class RankingUiState(
    val loading: Boolean = true,
    val error: String? = null,
    /** 1 = 最近一周，0 = 所有时间 */
    val recordsType: Int = 1,
    val records: List<RecordItem> = emptyList(),
)

/** 听歌排行（独立页面）：最近一周 / 所有时间 */
@HiltViewModel
class RankingViewModel @Inject constructor(
    private val loginRepository: LoginRepository,
    private val userRepository: UserRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val _state = MutableStateFlow(RankingUiState())
    val state: StateFlow<RankingUiState> = _state.asStateFlow()

    private var myUid: Long? = null
    private var loaded = false

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            if (!loaded) {
                loaded = true
                myUid = loginRepository.account().getOrNull()?.account?.id
            }
            val uid = myUid
            if (uid == null) {
                _state.update { it.copy(loading = false, records = emptyList(), error = Str.res(R.string.log_in_to_view_listening_stats)) }
                return@launch
            }
            when (val result = userRepository.playRecord(uid, _state.value.recordsType)) {
                is AppResult.Success -> {
                    _state.update { it.copy(loading = false, records = result.data) }
                }

                is AppResult.Failure -> {
                    _state.update { it.copy(loading = false, error = result.error.userMessage) }
                }
            }
        }
    }

    /** type: 1 最近一周 / 0 所有时间 */
    fun loadRecords(type: Int) {
        if (type == _state.value.recordsType && _state.value.records.isNotEmpty() && _state.value.error == null) return
        _state.update { it.copy(recordsType = type) }
        load()
    }

    fun play(item: RecordItem) {
        val song = item.song ?: return
        playbackController.playSong(song, _state.value.records.mapNotNull { it.song })
    }
}
