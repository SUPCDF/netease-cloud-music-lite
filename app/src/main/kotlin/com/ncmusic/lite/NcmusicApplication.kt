package com.ncmusic.lite

import android.app.Application
import android.content.Context
import com.ncmusic.core.common.i18n.LanguageManager
import com.ncmusic.core.network.cookie.CookieStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/** 应用入口，Hilt 依赖注入的宿主 */
@HiltAndroidApp
class NcmusicApplication : Application() {

    @Inject
    lateinit var cookieStore: CookieStore

    override fun attachBaseContext(newBase: Context) {
        LanguageManager.attachBase(newBase)
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        LanguageManager.init()
        // 启动时同步恢复持久化的 Cookie（登录态），避免首屏请求因缺少凭证失败。
        // DataStore 读取是一次本地文件 IO，耗时很短。
        runBlocking { cookieStore.init() }
    }
}
