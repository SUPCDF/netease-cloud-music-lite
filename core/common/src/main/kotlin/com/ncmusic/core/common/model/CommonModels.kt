package com.ncmusic.core.common.model

import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str
/** 主题模式 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 播放模式：顺序 / 列表循环 / 单曲循环 / 随机 */
enum class PlayMode { ORDER, LOOP_ALL, LOOP_ONE, SHUFFLE }

/** 音质等级（实际返回以账号权限为准） */
object SoundQuality {
    const val STANDARD = "standard"
    const val HIGHER = "higher"
    const val EXHIGH = "exhigh"
    const val LOSSLESS = "lossless"

    val all = listOf(STANDARD, HIGHER, EXHIGH, LOSSLESS)

    fun displayName(level: String): String = when (level) {
        STANDARD -> Str.res(R.string.standard)
        HIGHER -> Str.res(R.string.high)
        EXHIGH -> Str.res(R.string.very_high)
        LOSSLESS -> Str.res(R.string.lossless)
        else -> level
    }
}

/** 搜索类型（/search/get 的 type 参数） */
object SearchType {
    const val SONG = 1
    const val ALBUM = 10
    const val ARTIST = 100
    const val PLAYLIST = 1000
    const val USER = 1002
}

/** 评论 threadId 拼装 */
object CommentThread {
    fun song(songId: Long): String = "R_SO_4_$songId"

    /** MV 详情接口会返回 commentThreadId，这里仅作为缺失时的兜底 */
    fun mv(mvId: Long): String = "R_MV_5_$mvId"
}
