package com.ncmusic.lite.navigation

import androidx.annotation.StringRes
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 底部导航的一级页面（只显示横向文字标签，无图标） */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
) {
    HOME(Route.HOME, R.string.discover),
    MINE(Route.MINE, R.string.me),
    ;

    val label: String get() = Str.res(labelRes)
}
