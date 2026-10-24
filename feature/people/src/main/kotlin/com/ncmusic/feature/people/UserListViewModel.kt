package com.ncmusic.feature.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.data.account.AccountCache
import com.ncmusic.core.data.repository.LoginRepository
import com.ncmusic.core.data.repository.UserRepository
import com.ncmusic.core.network.model.UserBrief
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 用户列表（关注 / 粉丝）状态 */
data class UserListUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val users: List<UserBrief> = emptyList(),
    val more: Boolean = false,
    val loadingMore: Boolean = false,
    /** 取关操作中的用户 id */
    val busyIds: Set<Long> = emptySet(),
    val toast: String? = null,
)

/** 关注 / 粉丝列表页 */
@HiltViewModel
class UserListViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val loginRepository: LoginRepository,
    private val accountCache: AccountCache,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val uid: Long = savedStateHandle[ARG_UID] ?: 0L
    private val type: String = savedStateHandle[ARG_TYPE] ?: TYPE_FOLLOWS

    /** true = 粉丝列表 */
    val isFollowers: Boolean = type == TYPE_FOLLOWEDS

    /** 只有查看自己的关注 / 粉丝时才读写本地缓存 */
    private val cacheEnabled: Boolean =
        uid > 0L && uid == (loginRepository.cachedAccount()?.id ?: 0L)

    private val _state = MutableStateFlow(UserListUiState())
    val state: StateFlow<UserListUiState> = _state.asStateFlow()

    init {
        // 先展示上次缓存的名单，联网成功后再静默替换
        if (cacheEnabled) {
            val cached = if (isFollowers) accountCache.followers() else accountCache.following()
            if (!cached.isNullOrEmpty()) {
                _state.value = UserListUiState(loading = false, users = cached)
            }
        }
        load(reset = true)
    }

    fun load(reset: Boolean = false) {
        val snapshot = _state.value
        if (!reset && (!snapshot.more || snapshot.loadingMore)) return
        viewModelScope.launch {
            _state.update {
                if (reset) {
                    // 有缓存时不要清空列表，也不要盖一层加载页
                    it.copy(loading = it.users.isEmpty(), error = null, more = false)
                } else {
                    it.copy(loadingMore = true)
                }
            }
            val offset = if (reset) 0 else snapshot.users.size
            val result = if (isFollowers) {
                userRepository.followeds(uid, offset)
            } else {
                userRepository.follows(uid, offset)
            }
            when (result) {
                is AppResult.Success -> {
                    val data = result.data
                    val list = (if (isFollowers) data.followeds else data.follow) ?: emptyList()
                    _state.update {
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            users = if (reset) list else it.users + list,
                            more = data.more ?: false,
                        )
                    }
                    if (reset && cacheEnabled) {
                        if (isFollowers) {
                            accountCache.saveFollowers(list)
                        } else {
                            accountCache.saveFollowing(list)
                        }
                    }
                }

                is AppResult.Failure -> {
                    _state.update {
                        if (reset) {
                            // 有缓存就继续显示缓存名单，离线不至于整页报错
                            it.copy(
                                loading = false,
                                error = if (it.users.isEmpty()) result.error.userMessage else null,
                            )
                        } else {
                            it.copy(loadingMore = false)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ARG_UID = "uid"
        const val ARG_TYPE = "type"
        const val TYPE_FOLLOWS = "follows"
        const val TYPE_FOLLOWEDS = "followeds"
    }

    /** 取消关注（「我的关注」列表内直接取关） */
    fun unfollow(targetUid: Long) {
        if (targetUid in _state.value.busyIds) return
        viewModelScope.launch {
            _state.update { it.copy(busyIds = it.busyIds + targetUid) }
            when (val result = userRepository.unfollow(targetUid)) {
                is AppResult.Success -> {
                    _state.update {
                        val updated = it.users.filterNot { u -> u.userId == targetUid }
                        if (cacheEnabled && !isFollowers) accountCache.saveFollowing(updated)
                        it.copy(
                            busyIds = it.busyIds - targetUid,
                            users = updated,
                            toast = Str.res(R.string.unfollowed),
                        )
                    }
                }

                is AppResult.Failure -> {
                    _state.update {
                        it.copy(busyIds = it.busyIds - targetUid, toast = result.error.userMessage)
                    }
                }
            }
        }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }
}
