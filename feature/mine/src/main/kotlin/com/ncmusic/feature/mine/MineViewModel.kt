package com.ncmusic.feature.mine

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.account.AccountCache
import com.ncmusic.core.data.repository.AvatarRepository
import com.ncmusic.core.data.repository.LoginRepository
import com.ncmusic.core.data.repository.PlaylistRepository
import com.ncmusic.core.data.repository.UserRepository
import com.ncmusic.core.network.model.Playlist
import com.ncmusic.core.network.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 我的页状态 */
data class MineUiState(
    val loading: Boolean = false,
    val loggedIn: Boolean = false,
    val profile: Profile? = null,
    val likedPlaylistId: Long? = null,
    val createdPlaylists: List<Playlist> = emptyList(),
    val subscribedPlaylists: List<Playlist> = emptyList(),
    val uploadingAvatar: Boolean = false,
    val toast: String? = null,
)

/** 我的页 ViewModel：账号资料 / 我的歌单 / 退出登录（最近播放、听歌排行为独立页面） */
@HiltViewModel
class MineViewModel @Inject constructor(
    private val loginRepository: LoginRepository,
    private val playlistRepository: PlaylistRepository,
    private val userRepository: UserRepository,
    private val avatarRepository: AvatarRepository,
    private val accountCache: AccountCache,
) : ViewModel() {

    // 首帧直接用本地缓存的账号资料 + 歌单，避免每次打开「我的」页都先闪一下未登录 / 空白
    private val _state = MutableStateFlow(
        run {
            val cachedUid = loginRepository.cachedAccount()?.id ?: 0L
            val cachedPlaylists = accountCache.playlists().orEmpty()
            MineUiState(
                loggedIn = loginRepository.isLoggedInLocally(),
                profile = loginRepository.cachedProfile(),
                likedPlaylistId = cachedPlaylists.firstOrNull { it.specialType == 5 }?.id,
                createdPlaylists = cachedPlaylists.filter {
                    it.userId == cachedUid && it.specialType != 5
                },
                subscribedPlaylists = cachedPlaylists.filter { it.userId != cachedUid },
            )
        },
    )
    val state = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            // 先把缓存 / 本地登录态显示出来，再静默刷新
            val cachedProfile = loginRepository.cachedProfile()
            _state.update { current ->
                current.copy(
                    loading = current.profile == null,
                    loggedIn = current.loggedIn || loginRepository.isLoggedInLocally(),
                    profile = current.profile ?: cachedProfile,
                )
            }

            when (val accountResult = loginRepository.account()) {
                is AppResult.Success -> {
                    val account = accountResult.data.account
                    if (account == null) {
                        // 服务端明确返回未登录：才清空页面
                        _state.value = MineUiState()
                        return@launch
                    }

                    _state.update {
                        it.copy(
                            loggedIn = true,
                            profile = it.profile
                                ?: accountResult.data.profile
                                ?: loginRepository.cachedProfile(),
                        )
                    }

                    val profile = userRepository.profile(account.id).getOrNull()
                        ?.let { p -> if ((p.level ?: 0) > 0) p else p.copy(level = userRepository.myLevel()) }
                    profile?.let(loginRepository::cacheProfile)

                    // 歌单请求成功就整份缓存；失败则用上次缓存，离线时页面内容不变
                    val playlists = playlistRepository.userPlaylists(account.id).getOrNull()
                    if (playlists != null) accountCache.savePlaylists(playlists)
                    val fallbackPlaylists = if (playlists == null) {
                        accountCache.playlists().orEmpty()
                    } else {
                        emptyList()
                    }

                    _state.update {
                        val source = playlists ?: fallbackPlaylists.takeIf { cached -> cached.isNotEmpty() }
                        it.copy(
                            loading = false,
                            loggedIn = true,
                            profile = profile ?: it.profile,
                            likedPlaylistId = source
                                ?.firstOrNull { p -> p.specialType == 5 }?.id
                                ?: it.likedPlaylistId,
                            createdPlaylists = source
                                ?.filter { p -> p.userId == account.id && p.specialType != 5 }
                                ?: it.createdPlaylists,
                            subscribedPlaylists = source
                                ?.filter { p -> p.userId != account.id }
                                ?: it.subscribedPlaylists,
                        )
                    }
                }

                is AppResult.Failure -> {
                    // 离线 / 请求失败：只要本地还有登录态（Cookie 或缓存），就保持已登录和已有内容
                    _state.update {
                        if (loginRepository.isLoggedInLocally() || it.profile != null) {
                            it.copy(loading = false)
                        } else {
                            MineUiState()
                        }
                    }
                }
            }
        }
    }

    // ---------------- 头像 ----------------

    /** 上传并替换当前账号头像；优先用接口返回的新 URL 立即刷新显示 */
    fun changeAvatar(uri: Uri) {
        if (_state.value.uploadingAvatar) return
        viewModelScope.launch {
            _state.update { it.copy(uploadingAvatar = true) }
            when (val result = avatarRepository.updateAvatar(uri)) {
                is AppResult.Success -> {
                    val newAvatarUrl = result.data
                    val current = _state.value.profile
                    if (!newAvatarUrl.isNullOrBlank() && current != null) {
                        // 接口会直接返回新头像 URL，立即更新，省掉一次资料请求
                        val updated = current.copy(avatarUrl = newAvatarUrl)
                        loginRepository.cacheProfile(updated)
                        _state.update {
                            it.copy(
                                uploadingAvatar = false,
                                profile = updated,
                                toast = Str.res(R.string.avatar_updated),
                            )
                        }
                    } else {
                        val uid = current?.userId
                            ?: loginRepository.account().getOrNull()?.account?.id
                        val freshProfile = uid?.let { userRepository.profile(it).getOrNull() }
                        val updated = freshProfile?.let { profile ->
                            if (newAvatarUrl.isNullOrBlank()) {
                                profile
                            } else {
                                profile.copy(avatarUrl = newAvatarUrl)
                            }
                        }
                        updated?.let(loginRepository::cacheProfile)
                        _state.update {
                            it.copy(
                                uploadingAvatar = false,
                                profile = updated ?: it.profile,
                                toast = Str.res(R.string.avatar_updated),
                            )
                        }
                    }
                }

                is AppResult.Failure -> _state.update {
                    it.copy(uploadingAvatar = false, toast = result.error.userMessage)
                }
            }
        }
    }

    // ---------------- 退出 ----------------

    fun logout() {
        viewModelScope.launch {
            loginRepository.logout()
            _state.update { MineUiState() }
            load()
        }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }
}
