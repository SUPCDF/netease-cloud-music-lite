package com.ncmusic.feature.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.result.AppError
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.repository.LoginRepository
import com.ncmusic.core.data.repository.PlaylistRepository
import com.ncmusic.core.data.repository.UserRepository
import com.ncmusic.core.network.model.Playlist
import com.ncmusic.core.network.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 用户主页状态 */
data class UserProfileUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val profile: Profile? = null,
    val playlists: List<Playlist> = emptyList(),
    val isSelf: Boolean = false,
    /** 是否已关注（null = 未知，默认按未关注展示） */
    val following: Boolean? = null,
    val followBusy: Boolean = false,
    val toast: String? = null,
    /** 网易风控要求完成安全验证时的提示文案（非 null 时弹窗） */
    val verifyTip: String? = null,
    /** 安全验证地址（可在浏览器 / 官方 App 打开） */
    val verifyUrl: String? = null,
)

/** 用户主页：资料 + TA 的歌单 */
@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val playlistRepository: PlaylistRepository,
    private val loginRepository: LoginRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val uid: Long = savedStateHandle[ARG_UID] ?: 0L
    private var myUid: Long? = null
    private var loaded = false

    private val _state = MutableStateFlow(UserProfileUiState())
    val state: StateFlow<UserProfileUiState> = _state.asStateFlow()

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
            when (val result = userRepository.profile(uid)) {
                is AppResult.Success -> {
                    val playlists = playlistRepository.userPlaylists(uid).getOrNull() ?: emptyList()
                    // 等级：detail 顶层 level 为目标用户等级；看自己且缺失时用 user/level 兜底
                    val profile = if ((result.data.level ?: 0) > 0) {
                        result.data
                    } else if (myUid != null && myUid == uid) {
                        result.data.copy(level = userRepository.myLevel())
                    } else {
                        result.data
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            profile = profile,
                            playlists = playlists.filter { p -> p.specialType != 5 },
                            isSelf = myUid != null && myUid == uid,
                            following = profile.followed ?: it.following,
                        )
                    }
                }

                is AppResult.Failure -> {
                    _state.update { it.copy(loading = false, error = result.error.userMessage) }
                }
            }
        }
    }

    /** 关注 / 取消关注 */
    fun toggleFollow() {
        val snapshot = _state.value
        if (snapshot.followBusy || snapshot.isSelf || uid <= 0) return
        val target = snapshot.following == true
        viewModelScope.launch {
            _state.update { it.copy(followBusy = true) }
            val result = if (target) userRepository.unfollow(uid) else userRepository.follow(uid)
            when (result) {
                is AppResult.Success -> {
                    _state.update { it.copy(followBusy = false, following = !target) }
                }

                is AppResult.Failure -> {
                    val error = result.error
                    if (error is AppError.VerificationRequired) {
                        _state.update {
                            it.copy(
                                followBusy = false,
                                verifyTip = error.userMessage,
                                verifyUrl = error.verifyUrl,
                            )
                        }
                    } else {
                        _state.update { it.copy(followBusy = false, toast = error.userMessage) }
                    }
                }
            }
        }
    }

    /** 关闭安全验证弹窗 */
    fun dismissVerify() {
        _state.update { it.copy(verifyTip = null, verifyUrl = null) }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    companion object {
        const val ARG_UID = "uid"
    }
}
