package com.ncmusic.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.repository.LoginRepository
import com.ncmusic.core.network.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 登录方式 */
enum class LoginMode { QR, PHONE }

/** 登录页状态 */
data class LoginUiState(
    val mode: LoginMode = LoginMode.QR,
    val qrKey: String? = null,
    val qrStatusText: String = Str.res(R.string.getting_qr_code),
    val qrExpired: Boolean = false,
    val scanned: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val phone: String = "",
    val password: String = "",
    val captcha: String = "",
    val useCaptcha: Boolean = false,
    val captchaCooldown: Int = 0,
    val loggedIn: Boolean = false,
    val profile: Profile? = null,
)

/**
 * 登录 ViewModel：
 * - 二维码登录：unikey -> 展示二维码 -> 轮询状态（801 待扫 / 802 待确认 / 803 成功 / 800 过期）
 * - 手机号登录：密码（本地 md5）或短信验证码
 * 登录成功后 Cookie（MUSIC_U）由网络层自动持久化。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginRepository: LoginRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    private var qrJob: Job? = null
    private var cooldownJob: Job? = null

    init {
        // 先认本地缓存，弱网时登录页也能立即显示已登录资料（不会先闪未登录）
        loginRepository.cachedProfile()?.let { profile ->
            _state.update { it.copy(loggedIn = true, profile = profile) }
        }
        checkLoginStatus()
        refreshQr()
    }

    fun checkLoginStatus() {
        viewModelScope.launch {
            val resp = loginRepository.account().getOrNull() ?: return@launch
            if (resp.account != null) {
                _state.update { it.copy(loggedIn = true, profile = resp.profile) }
            }
        }
    }

    fun switchMode(mode: LoginMode) {
        _state.update { it.copy(mode = mode, error = null) }
    }

    // ---------------- 二维码 ----------------

    fun refreshQr() {
        qrJob?.cancel()
        _state.update {
            it.copy(
                qrKey = null,
                qrStatusText = Str.res(R.string.getting_qr_code),
                qrExpired = false,
                scanned = false,
                error = null,
            )
        }
        qrJob = viewModelScope.launch {
            val key = loginRepository.qrUnikey().getOrNull()
            if (key == null) {
                _state.update { it.copy(error = Str.res(R.string.failed_to_get_qr_code_check_your_network_and_ret)) }
                return@launch
            }
            _state.update { it.copy(qrKey = key, qrStatusText = Str.res(R.string.open_the_netease_cloud_music_app_and_scan_the_qr)) }
            pollQr(key)
        }
    }

    private suspend fun pollQr(key: String) {
        while (currentCoroutineContext().isActive) {
            delay(2500)
            val resp = loginRepository.qrCheck(key).getOrNull() ?: continue
            when (resp.code) {
                800 -> {
                    _state.update { it.copy(qrExpired = true, qrStatusText = Str.res(R.string.qr_code_expired_tap_to_refresh)) }
                    return
                }

                801 -> _state.update { it.copy(qrStatusText = Str.res(R.string.open_the_netease_cloud_music_app_and_scan_the_qr), scanned = false) }

                802 -> _state.update { it.copy(qrStatusText = Str.res(R.string.scanned_confirm_login_on_your_phone), scanned = true) }

                803 -> {
                    val account = loginRepository.account().getOrNull()
                    _state.update {
                        it.copy(
                            loggedIn = true,
                            profile = account?.profile,
                            qrStatusText = Str.res(R.string.logged_in),
                        )
                    }
                    return
                }
            }
        }
    }

    // ---------------- 手机号 ----------------

    fun updatePhone(value: String) {
        _state.update { it.copy(phone = value.filter { c -> c.isDigit() }.take(11), error = null) }
    }

    fun updatePassword(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    fun updateCaptcha(value: String) {
        _state.update { it.copy(captcha = value.filter { c -> c.isDigit() }.take(6), error = null) }
    }

    fun setUseCaptcha(useCaptcha: Boolean) {
        _state.update { it.copy(useCaptcha = useCaptcha, error = null) }
    }

    fun sendCaptcha() {
        val phone = _state.value.phone
        if (phone.length != 11) {
            _state.update { it.copy(error = Str.res(R.string.enter_an_11_digit_phone_number)) }
            return
        }
        if (_state.value.captchaCooldown > 0) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = loginRepository.sendCaptcha(phone)) {
                is AppResult.Success -> {
                    _state.update { it.copy(loading = false) }
                    startCooldown()
                }

                is AppResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.error.userMessage)
                }
            }
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (second in 60 downTo 1) {
                _state.update { it.copy(captchaCooldown = second) }
                delay(1000)
            }
            _state.update { it.copy(captchaCooldown = 0) }
        }
    }

    fun login() {
        val current = _state.value
        if (current.phone.length != 11) {
            _state.update { it.copy(error = Str.res(R.string.enter_an_11_digit_phone_number)) }
            return
        }
        if (current.useCaptcha && current.captcha.length < 4) {
            _state.update { it.copy(error = Str.res(R.string.enter_the_sms_code)) }
            return
        }
        if (!current.useCaptcha && current.password.isBlank()) {
            _state.update { it.copy(error = Str.res(R.string.enter_your_password)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = if (current.useCaptcha) {
                loginRepository.loginByCaptcha(current.phone, current.captcha)
            } else {
                loginRepository.loginByPassword(current.phone, current.password)
            }
            when (result) {
                is AppResult.Success -> _state.update {
                    it.copy(loading = false, loggedIn = true, profile = result.data)
                }

                is AppResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.error.userMessage)
                }
            }
        }
    }
}
