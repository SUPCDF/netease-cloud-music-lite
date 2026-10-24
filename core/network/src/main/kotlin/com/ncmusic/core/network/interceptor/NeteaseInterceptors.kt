package com.ncmusic.core.network.interceptor

import com.ncmusic.core.network.api.EapiBody
import com.ncmusic.core.network.api.WeapiBody
import com.ncmusic.core.network.cookie.CookieStore
import com.ncmusic.core.network.crypto.NeteaseCrypto
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/** 统一请求头：UA / Referer / Origin（网易对匿名请求会校验 UA 合法性） */
class NeteaseHeaderInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val newRequest = request.newBuilder()
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            )
            .header("Referer", "https://music.163.com")
            .header("Origin", "https://music.163.com")
            .build()
        return chain.proceed(newRequest)
    }
}

/**
 * 加密封装拦截器：
 * - 把 WeapiBody 明文替换为 form 表单 {params, encSecKey}
 * - 把 EapiBody 明文替换为 form 表单 {params}（十六进制密文）
 * 仓库层代码对加密完全无感知。
 */
class NeteaseEncryptInterceptor(
    private val cookieStore: CookieStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = request.body

        return when (body) {
            is WeapiBody -> {
                // csrf_token 始终携带（未登录时为空串）
                val data = JsonObject(
                    body.data.toMutableMap().apply {
                        put("csrf_token", JsonPrimitive(cookieStore.csrfToken()))
                    },
                )
                val encrypted = NeteaseCrypto.weapi(data.toString())
                val form = FormBody.Builder()
                    .add("params", encrypted.params)
                    .add("encSecKey", encrypted.encSecKey)
                    .build()
                chain.proceed(request.newBuilder().post(form).build())
            }

            is EapiBody -> {
                if (body.mobile) {
                    // 移动端报文由 MobileEapiInterceptor（网络拦截器）处理：
                    // 需要在 BridgeInterceptor 之后覆盖 Cookie / UA，这里原样放行
                    chain.proceed(request)
                } else {
                    val params = NeteaseCrypto.eapi(body.digestPath, body.data.toString())
                    val form = FormBody.Builder()
                        .add("params", params)
                        .build()
                    chain.proceed(request.newBuilder().post(form).build())
                }
            }

            else -> chain.proceed(request)
        }
    }
}

/**
 * 移动端 eapi 通道（评论发表等写操作必须使用）。
 *
 * 网易云对 weapi 的写操作会返回 code 250「请切换至移动端后重试」，
 * 移动端请求需要：
 * - data.header 携带 os / appver / deviceId / requestId 等设备信息
 * - Cookie 头为移动端字段组合（MUSIC_U、__csrf、os、appver …）
 * - UA 为官方客户端
 *
 * 必须在网络拦截器层完成：应用层的 Cookie 会被 BridgeInterceptor 用
 * CookieJar 的内容覆盖（评论接口需要临时替换为移动端 Cookie 组合）。
 */
class MobileEapiInterceptor(
    private val cookieStore: CookieStore,
) : Interceptor {

    /** 设备 id：进程内随机生成一次即可（接口只用来识别客户端，不校验真实性） */
    private val deviceId: String by lazy {
        (1..32).map { "0123456789abcdef".random() }.joinToString("")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = request.body
        if (body !is EapiBody || !body.mobile) return chain.proceed(request)

        val header = buildJsonObject {
            put("osver", JsonPrimitive("16.2"))
            put("deviceId", JsonPrimitive(deviceId))
            put("os", JsonPrimitive("iPhone OS"))
            put("appver", JsonPrimitive("9.0.90"))
            put("versioncode", JsonPrimitive("140"))
            put("mobilename", JsonPrimitive(""))
            put("buildver", JsonPrimitive((System.currentTimeMillis() / 1000).toString()))
            put("resolution", JsonPrimitive("1920x1080"))
            put("__csrf", JsonPrimitive(cookieStore.csrfToken()))
            put("channel", JsonPrimitive("distribution"))
            put(
                "requestId",
                JsonPrimitive(
                    "${System.currentTimeMillis()}_${(0..999).random().toString().padStart(4, '0')}",
                ),
            )
            cookieStore.value("MUSIC_U")?.let { put("MUSIC_U", JsonPrimitive(it)) }
        }

        val data = JsonObject(body.data.toMutableMap().apply { put("header", header) })
        val params = NeteaseCrypto.eapi(body.digestPath, data.toString())

        // 移动端 Cookie 组合直接作为请求头（替换 CookieJar 的默认 Cookie）
        val cookieHeader = header.entries.joinToString("; ") { (name, value) ->
            "$name=${(value as? JsonPrimitive)?.content ?: ""}"
        }

        val form = FormBody.Builder().add("params", params).build()
        val newRequest = request
            .withReplacedFormBody(form)
            .newBuilder()
            .header("Cookie", cookieHeader)
            .header("User-Agent", MOBILE_UA)
            .build()
        return chain.proceed(newRequest)
    }

    private companion object {
        const val MOBILE_UA = "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"
    }
}

/**
 * 关注 / 取关接口的 Cookie 补丁。
 *
 * 网易对 `weapi/user/follow/{uid}` 与 `weapi/user/delfollow/{uid}` 会做 checkToken
 * 校验（校验失败返回 code 250，客户端会误报「需要登录」）。参考实现的做法是让请求
 * 携带 `os` cookie 绕过该校验，这里在 Cookie 头里补一个 `os=pc`。
 */
class FollowCookieInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val isFollow = path.contains("/weapi/user/follow/") || path.contains("/weapi/user/delfollow/")
        if (!isFollow) return chain.proceed(request)
        val cookie = request.header("Cookie").orEmpty()
        if (cookie.contains("os=")) return chain.proceed(request)
        val patched = if (cookie.isBlank()) "os=pc" else "$cookie; os=pc"
        return chain.proceed(request.newBuilder().header("Cookie", patched).build())
    }
}

/**
 * 在网络拦截器阶段把请求体替换为新的 form 体，并同步修正相关请求头。
 *
 * 背景：BridgeInterceptor 在进入网络拦截器之前就已按**旧请求体**写好头部：
 * - `Content-Type: application/json`（来自 EapiBody）
 * - `Transfer-Encoding: chunked`（EapiBody 未声明 contentLength）
 *
 * 而 OkHttp（4.12）在替换 body 后不会自动更新这些头。若不同步修正，
 * 网易 eapi 服务器会收到「application/json + chunked 编码的 form 报文」，
 * 直接返回空响应（表现为评论发送失败，报 JSON EOF 解析错误）。
 */
internal fun Request.withReplacedFormBody(form: FormBody): Request =
    newBuilder()
        .post(form)
        .header("Content-Type", form.contentType().toString())
        .removeHeader("Transfer-Encoding")
        .header("Content-Length", form.contentLength().toString())
        .build()
