package com.ncmusic.core.common.result

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/**
 * 应用内统一的错误类型。
 * 网络层 / 数据层捕获到底层异常后统一转换为 AppError，
 * 保证底层异常（IOException、序列化异常等）不会泄漏到 UI 层。
 *
 * @param userMessage 可直接展示给用户的中文提示
 */
sealed class AppError(val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause) {

    /** 网络不可用 / 连接失败（例如接口地址填错、后端没启动） */
    class Network(cause: Throwable? = null) : AppError(Str.res(R.string.network_connection_failed_check_your_network_or), cause)

    /** 请求超时 */
    class Timeout(cause: Throwable? = null) : AppError(Str.res(R.string.request_timed_out_please_retry), cause)

    /** HTTP 状态码异常（404/500 等） */
    class Http(val code: Int, cause: Throwable? = null) : AppError(Str.res(R.string.server_error_http, code), cause)

    /** 业务错误码（NeteaseCloudMusicApi 统一返回 code 字段，如 301 未登录） */
    class Api(val code: Int, val apiMessage: String? = null) : AppError(apiMessage ?: Str.res(R.string.api_error_code, code))

    /** 未登录 / 登录态失效 */
    object NotLoggedIn : AppError(Str.res(R.string.session_expired_please_log_in_again))

    /**
     * 网易风控要求先完成安全验证（关注 / 取关等写操作会触发，code -462 或 250）。
     *
     * @param tip      服务端返回的说明（如「绑定手机号或短信验证成功后，可进行下一步操作」）
     * @param verifyUrl 服务端返回的验证地址（可在浏览器 / 官方 App 中打开完成验证）
     */
    class VerificationRequired(
        val tip: String?,
        val verifyUrl: String?,
    ) : AppError(tip?.takeIf { it.isNotBlank() } ?: Str.res(R.string.this_action_was_blocked_by_netease_security_comp))

    /** 数据解析失败（接口结构与预期不一致时兜底，避免直接崩溃） */
    class Parse(cause: Throwable? = null) : AppError(Str.res(R.string.failed_to_parse_data), cause)

    /** 未知错误 */
    class Unknown(cause: Throwable? = null) : AppError(cause?.message ?: Str.res(R.string.unknown_error), cause)
}

/** 把任意异常转换为 AppError */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is UnknownHostException -> AppError.Network(this)
    is SocketTimeoutException -> AppError.Timeout(this)
    is IOException -> AppError.Network(this)
    else -> AppError.Unknown(this)
}

/** 约定：接口 code == 200 才视为成功；301 表示需要登录 */
fun apiCodeToError(code: Int, message: String? = null): AppError = when (code) {
    301 -> AppError.NotLoggedIn
    else -> AppError.Api(code, message)
}
