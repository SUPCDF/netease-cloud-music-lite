package com.ncmusic.core.common.i18n

import androidx.annotation.StringRes

/** 全局字符串取用（跟随应用内语言设置） */
object Str {

    fun res(@StringRes id: Int, vararg args: Any): String {
        val context = LanguageManager.localizedContext()
        return if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
    }
}
