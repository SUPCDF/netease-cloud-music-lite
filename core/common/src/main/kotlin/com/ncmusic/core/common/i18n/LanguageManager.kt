package com.ncmusic.core.common.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用内语言（默认跟随系统）。
 *
 * - Application / Activity 在 `attachBaseContext` 里调用 [wrap]，让整个进程按当前语言取资源；
 * - [set] 保存选择并刷新本地化 Context，调用方随后 `recreate()` Activity 让界面生效。
 */
object LanguageManager {

    const val TAG_SYSTEM = "system"
    const val TAG_ZH = "zh"
    const val TAG_ZH_TW = "zh-TW"
    const val TAG_EN = "en"
    const val TAG_JA = "ja"
    const val TAG_KO = "ko"
    const val TAG_ES = "es"
    const val TAG_FR = "fr"
    const val TAG_DE = "de"
    const val TAG_RU = "ru"

    /** 设置页展示顺序 */
    val SUPPORTED_TAGS = listOf(
        TAG_SYSTEM,
        TAG_ZH,
        TAG_ZH_TW,
        TAG_EN,
        TAG_JA,
        TAG_KO,
        TAG_ES,
        TAG_FR,
        TAG_DE,
        TAG_RU,
    )

    private const val PREFS = "app_language"
    private const val KEY = "language"

    private var baseContext: Context? = null
    private var systemLocale: Locale = Locale.getDefault()

    @Volatile
    private var localizedContext: Context? = null

    private val _language = MutableStateFlow(TAG_SYSTEM)

    /** 当前选择的语言（system / 语言标签） */
    val language: StateFlow<String> = _language.asStateFlow()

    /** 在 Application.attachBaseContext 里、super 之前调用，记录未包装的 base */
    fun attachBase(base: Context) {
        if (baseContext == null) {
            systemLocale = Locale.getDefault()
            baseContext = base
        }
    }

    /** 在 Application.onCreate 里调用 */
    fun init() {
        val base = baseContext ?: return
        _language.value = readTag(base)
        rebuild()
    }

    /** attachBaseContext 用：把 base 包上一层语言配置 */
    fun wrap(base: Context): Context {
        val locale = localeOf(readTag(base)) ?: return base
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** 保存语言选择（system / 语言标签） */
    fun set(tag: String) {
        val base = baseContext ?: return
        val safe = if (tag in SUPPORTED_TAGS) tag else TAG_SYSTEM
        base.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, safe)
            .apply()
        _language.value = safe
        rebuild()
    }

    /** 当前语言对应的 Context（用于取字符串资源） */
    fun localizedContext(): Context = localizedContext ?: baseContext
        ?: throw IllegalStateException("LanguageManager is not initialized")

    /** 当前生效的语言标签，例如 zh、zh-TW、en、ja */
    val languageTag: String
        get() = localizedContext().resources.configuration.locales[0].toLanguageTag()

    val isEnglish: Boolean
        get() = languageTag.startsWith("en")

    private fun rebuild() {
        val base = baseContext ?: return
        val tag = _language.value
        val locale = localeOf(tag)
        if (locale == null) {
            Locale.setDefault(systemLocale)
            localizedContext = base
        } else {
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            localizedContext = base.createConfigurationContext(config)
        }
    }

    private fun readTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, TAG_SYSTEM)
            ?.takeIf { it in SUPPORTED_TAGS }
            ?: TAG_SYSTEM

    private fun localeOf(tag: String): Locale? = when (tag) {
        TAG_SYSTEM -> null
        else -> if (tag in SUPPORTED_TAGS) Locale.forLanguageTag(tag) else null
    }
}
