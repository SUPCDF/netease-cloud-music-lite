package com.ncmusic.core.common.util

import java.util.Locale
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.LanguageManager
import com.ncmusic.core.common.i18n.Str

/** 与时间/数字展示相关的工具方法 */
object TimeUtils {

    /** 毫秒 -> mm:ss（播放器进度、歌曲时长） */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.CHINA, "%02d:%02d", minutes, seconds)
    }

    /** 大数字 -> x万 / x亿（中文），其它语言用 K/M/B */
    fun formatCount(count: Long): String {
        val tag = LanguageManager.languageTag
        return when {
            tag.startsWith("zh-TW") || tag.startsWith("zh-HK") ||
                tag.startsWith("zh-MO") || tag.startsWith("zh-Hant") -> when {
                count >= 100_000_000 -> String.format(Locale.CHINA, "%.1f億", count / 100_000_000.0)
                count >= 10_000 -> String.format(Locale.CHINA, "%.1f萬", count / 10_000.0)
                else -> count.toString()
            }

            tag.startsWith("zh") -> when {
                count >= 100_000_000 -> String.format(Locale.CHINA, "%.1f亿", count / 100_000_000.0)
                count >= 10_000 -> String.format(Locale.CHINA, "%.1f万", count / 10_000.0)
                else -> count.toString()
            }

            else -> when {
                count >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", count / 1_000_000_000.0)
                count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
                count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
                else -> count.toString()
            }
        }
    }

    /** 时间戳（毫秒）-> 相对时间：刚刚 / x分钟前 / x小时前 / x天前 / x个月前 / x年前 */
    fun formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diff = (nowMs - timestampMs).coerceAtLeast(0)
        val minute = 60_000L
        val hour = 60 * minute
        val day = 24 * hour
        val month = 30 * day
        val year = 365 * day
        return when {
            diff < minute -> Str.res(R.string.just_now)
            diff < hour -> Str.res(R.string.min_ago, diff / minute)
            diff < day -> Str.res(R.string.h_ago, diff / hour)
            diff < month -> Str.res(R.string.d_ago, diff / day)
            diff < year -> Str.res(R.string.mo_ago, diff / month)
            else -> Str.res(R.string.y_ago, diff / year)
        }
    }
}
