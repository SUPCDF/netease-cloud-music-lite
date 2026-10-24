package com.ncmusic.core.network.cookie

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

private val Context.cookieDataStore by preferencesDataStore(name = "netease_cookies")

/**
 * Cookie 管理（网易接口的登录态核心）。
 *
 * 策略：
 * - 内存中保存全量 name->value（与域名无关，兼容 music.163.com / interface.music.163.com）
 * - 通过 OkHttp CookieJar 自动接收响应 Cookie、自动附加请求 Cookie
 * - 异步持久化到 DataStore，重启后依然保持登录
 *
 * MUSIC_U 是网易的登录凭证，__csrf 用于 weapi 的 csrf_token 参数。
 */
@Singleton
class CookieStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val keyCookies = stringPreferencesKey("cookies")

    /** 内存快照：所有 cookie 的 name -> value */
    @Volatile
    private var cookies: Map<String, String> = emptyMap()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 启动时调用一次，把持久化的 Cookie 读入内存 */
    suspend fun init() {
        val stored = context.cookieDataStore.data.first()[keyCookies] ?: return
        cookies = parseCookieHeader(stored)
    }

    /** OkHttp 的 CookieJar 实现 */
    fun cookieJar(): CookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (cookies.isEmpty()) return
            update(this@CookieStore.cookies + cookies.associate { it.name to it.value })
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookies.map { (name, value) ->
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(url.host)
                    .path("/")
                    .build()
            }
    }

    /** 以 "a=1; b=2" 形式导出 Cookie 请求头 */
    fun cookieHeader(): String = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    /** csrf_token 参数（weapi 需要，未登录时为空字符串） */
    fun csrfToken(): String = cookies["__csrf"] ?: ""

    /** 按名称取 cookie 值 */
    fun value(name: String): String? = cookies[name]

    /** 是否已登录（存在 MUSIC_U 即视为有登录态） */
    fun hasMusicU(): Boolean = cookies.containsKey("MUSIC_U")

    /** 清空所有 Cookie（退出登录） */
    suspend fun clear() {
        cookies = emptyMap()
        context.cookieDataStore.edit { it.remove(keyCookies) }
    }

    private fun update(newCookies: Map<String, String>) {
        cookies = newCookies
        scope.launch { persist() }
    }

    private suspend fun persist() {
        val snapshot = cookieHeader()
        context.cookieDataStore.edit { it[keyCookies] = snapshot }
    }

    private fun parseCookieHeader(header: String): Map<String, String> =
        header.split(";")
            .mapNotNull { part ->
                val trimmed = part.trim()
                val idx = trimmed.indexOf('=')
                if (idx <= 0) null else trimmed.substring(0, idx) to trimmed.substring(idx + 1)
            }
            .toMap()
}
