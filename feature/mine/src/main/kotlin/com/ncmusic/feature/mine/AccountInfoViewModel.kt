package com.ncmusic.feature.mine

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.repository.AvatarRepository
import com.ncmusic.core.data.repository.LoginRepository
import com.ncmusic.core.data.repository.UserRepository
import com.ncmusic.core.network.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 账号信息页状态 */
data class AccountInfoUiState(
    val loading: Boolean = true,
    val profile: Profile? = null,
    val nickname: String = "",
    val signature: String = "",
    val uploadingAvatar: Boolean = false,
    val saving: Boolean = false,
    val toast: String? = null,
) {
    /** 昵称或简介有改动时才允许保存 */
    val changed: Boolean
        get() = profile != null && (
            nickname.trim() != profile.nickname ||
                signature.trim() != profile.signature.orEmpty()
            )

    val canSave: Boolean
        get() = !saving && !uploadingAvatar && nickname.isNotBlank() && changed
}

/** 账号信息页 ViewModel：改头像 / 昵称 / 简介 */
@HiltViewModel
class AccountInfoViewModel @Inject constructor(
    private val loginRepository: LoginRepository,
    private val userRepository: UserRepository,
    private val avatarRepository: AvatarRepository,
) : ViewModel() {

    // 首帧直接用缓存资料，离线也能正常打开和查看
    private val _state = MutableStateFlow(
        loginRepository.cachedProfile()?.let { profile ->
            AccountInfoUiState(
                loading = false,
                profile = profile,
                nickname = profile.nickname,
                signature = profile.signature.orEmpty(),
            )
        } ?: AccountInfoUiState(),
    )
    val state = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val cached = loginRepository.cachedProfile()
            _state.update {
                if (it.profile == null) {
                    it.copy(
                        loading = cached == null,
                        profile = cached,
                        nickname = cached?.nickname ?: it.nickname,
                        signature = cached?.signature.orEmpty() ?: it.signature,
                    )
                } else {
                    it.copy(loading = false)
                }
            }

            val uid = loginRepository.account().getOrNull()?.account?.id
                ?: cached?.userId?.takeIf { it > 0L }
            val profile = uid?.let { userRepository.profile(it).getOrNull() } ?: cached
            if (profile == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            loginRepository.cacheProfile(profile)

            // 用户已经开始编辑时不要用网络结果覆盖输入框
            _state.update {
                val editing = it.profile != null && (
                    it.nickname != it.profile?.nickname ||
                        it.signature != it.profile?.signature.orEmpty()
                    )
                if (editing) {
                    it.copy(loading = false)
                } else {
                    it.copy(
                        loading = false,
                        profile = profile,
                        nickname = profile.nickname,
                        signature = profile.signature.orEmpty(),
                    )
                }
            }
        }
    }

    fun updateNickname(value: String) {
        _state.update { it.copy(nickname = value.take(MAX_NICKNAME)) }
    }

    fun updateSignature(value: String) {
        _state.update { it.copy(signature = value.take(MAX_SIGNATURE)) }
    }

    /** 上传并替换头像；优先用接口返回的新 URL 立即刷新显示 */
    fun changeAvatar(uri: Uri) {
        if (_state.value.uploadingAvatar) return
        viewModelScope.launch {
            _state.update { it.copy(uploadingAvatar = true) }
            when (val result = avatarRepository.updateAvatar(uri)) {
                is AppResult.Success -> {
                    val newAvatarUrl = result.data
                    val updated = _state.value.profile?.let { profile ->
                        if (newAvatarUrl.isNullOrBlank()) profile
                        else profile.copy(avatarUrl = newAvatarUrl)
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

                is AppResult.Failure -> _state.update {
                    it.copy(uploadingAvatar = false, toast = result.error.userMessage)
                }
            }
        }
    }

    /** 保存昵称 / 简介；性别 / 生日 / 省市按当前资料原样带回，避免被服务端清空 */
    fun save() {
        val current = _state.value
        val profile = current.profile ?: return
        if (current.saving) return
        val nickname = current.nickname.trim()
        if (nickname.isBlank()) {
            _state.update { it.copy(toast = Str.res(R.string.nickname_required)) }
            return
        }
        val signature = current.signature.trim()
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            when (
                val result = userRepository.updateProfile(
                    nickname = nickname,
                    signature = signature,
                    gender = profile.gender,
                    birthday = profile.birthday,
                    province = profile.province,
                    city = profile.city,
                )
            ) {
                is AppResult.Success -> {
                    val updated = _state.value.profile?.copy(
                        nickname = nickname,
                        signature = signature,
                    )
                    updated?.let(loginRepository::cacheProfile)
                    _state.update {
                        it.copy(
                            saving = false,
                            profile = updated ?: it.profile,
                            toast = Str.res(R.string.profile_saved),
                        )
                    }
                }

                is AppResult.Failure -> _state.update {
                    it.copy(saving = false, toast = result.error.userMessage)
                }
            }
        }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    private companion object {
        const val MAX_NICKNAME = 20
        const val MAX_SIGNATURE = 100
    }
}
