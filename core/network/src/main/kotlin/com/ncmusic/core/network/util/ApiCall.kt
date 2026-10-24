package com.ncmusic.core.network.util

import com.ncmusic.core.common.result.AppError
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.toAppError
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/**
 * 统一 API 调用封装：
 * - 捕获所有网络/解析异常并转换为 [AppError]
 * - 保证底层异常不会泄漏到 UI 层
 */
suspend fun <T> apiCall(
    dispatcher: CoroutineDispatcher,
    block: suspend () -> T,
): AppResult<T> = withContext(dispatcher) {
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        AppResult.Failure(AppError.Http(e.code()))
    } catch (e: IOException) {
        AppResult.Failure(e.toAppError())
    } catch (e: SerializationException) {
        // 接口返回体不符合预期（空响应 / 结构变化）时给出友好提示，
        // 不把原始解析异常信息直接暴露给用户
        AppResult.Failure(AppError.Parse(e))
    } catch (e: Throwable) {
        AppResult.Failure(e.toAppError())
    }
}

/** 业务码校验：code != 200 时抛出对应 AppError */
fun requireCode(code: Int?, message: String? = null) {
    if (code != 200) throw apiCodeToError(code ?: -1, message)
}

/** 业务码 -> AppError（对已知错误码给出友好提示） */
fun apiCodeToError(code: Int, message: String? = null): AppError = when (code) {
    301, 302 -> AppError.NotLoggedIn
    -462 -> AppError.Api(code, Str.res(R.string.risky_network_environment_retry_later_or_switch))
    -110 -> AppError.Api(code, Str.res(R.string.this_song_requires_vip_access_and_can_t_be_playe))
    -200 -> AppError.Api(code, Str.res(R.string.action_failed_200_please_retry))
    250 -> AppError.Api(code, Str.res(R.string.log_in_to_use_this_feature))
    else -> if (!message.isNullOrBlank()) AppError.Api(code, message) else AppError.Api(code)
}
