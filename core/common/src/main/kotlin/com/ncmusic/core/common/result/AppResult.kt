package com.ncmusic.core.common.result

/**
 * 统一的结果封装：数据层（Repository）所有方法都返回 AppResult，
 * UI 层只需要处理 Success / Failure 两种情况，避免到处 try-catch。
 */
sealed interface AppResult<out T> {

    /** 调用成功 */
    data class Success<T>(val data: T) : AppResult<T>

    /** 调用失败，error 里携带可直接展示给用户的错误信息 */
    data class Failure(val error: AppError) : AppResult<Nothing>
}

/** 映射成功结果，失败原样透传 */
inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

/** 成功时返回数据，失败返回 null */
fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data

/** 成功时取出 body，失败时用 [fallback] 计算回退值 */
inline fun <T> AppResult<T>.getOrElse(fallback: (AppError) -> T): T = when (this) {
    is AppResult.Success -> data
    is AppResult.Failure -> fallback(error)
}

/** 把成功结果转换为 Failure(message)（配合 ?. 使用，便于链式兜底） */
inline fun <T, R> AppResult<T>.mapOrFailure(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(data)
    is AppResult.Failure -> this
}
