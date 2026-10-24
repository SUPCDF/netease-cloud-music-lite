package com.ncmusic.core.player.lyric

/** 一行歌词 */
data class LrcLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
)

/**
 * LRC 歌词解析器。
 * 支持一行多时间戳（[00:01.00][00:05.00] 歌词）、毫秒精度、翻译歌词按时间戳合并。
 */
object LrcParser {

    private val TIME_REGEX = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(lyric: String?, translation: String? = null): List<LrcLine> {
        if (lyric.isNullOrBlank()) return emptyList()
        val translationMap = parseTimeMap(translation)

        val lines = mutableListOf<LrcLine>()
        lyric.lines().forEach { raw ->
            val matches = TIME_REGEX.findAll(raw).toList()
            if (matches.isEmpty()) return@forEach
            val contentStart = matches.last().range.last + 1
            if (contentStart >= raw.length) return@forEach
            val text = raw.substring(contentStart).trim()
            if (text.isEmpty()) return@forEach

            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val millisRaw = match.groupValues[3]
                val millis = when {
                    millisRaw.isEmpty() -> 0L
                    millisRaw.length == 1 -> millisRaw.toLong() * 100
                    millisRaw.length == 2 -> millisRaw.toLong() * 10
                    else -> millisRaw.toLong()
                }
                val timeMs = minutes * 60_000 + seconds * 1_000 + millis
                lines += LrcLine(
                    timeMs = timeMs,
                    text = text,
                    translation = translationMap[timeMs],
                )
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun parseTimeMap(lyric: String?): Map<Long, String> {
        if (lyric.isNullOrBlank()) return emptyMap()
        val map = mutableMapOf<Long, String>()
        lyric.lines().forEach { raw ->
            val matches = TIME_REGEX.findAll(raw).toList()
            if (matches.isEmpty()) return@forEach
            val contentStart = matches.last().range.last + 1
            if (contentStart >= raw.length) return@forEach
            val text = raw.substring(contentStart).trim()
            if (text.isEmpty()) return@forEach
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val millisRaw = match.groupValues[3]
                val millis = when {
                    millisRaw.isEmpty() -> 0L
                    millisRaw.length == 1 -> millisRaw.toLong() * 100
                    millisRaw.length == 2 -> millisRaw.toLong() * 10
                    else -> millisRaw.toLong()
                }
                map[minutes * 60_000 + seconds * 1_000 + millis] = text
            }
        }
        return map
    }
}

/**
 * 二分查找当前进度对应的歌词行下标。
 * 返回最后一个 timeMs <= positionMs 的行；position 在第一行之前时返回 -1。
 */
fun List<LrcLine>.indexAt(positionMs: Long): Int {
    if (isEmpty()) return -1
    if (positionMs < first().timeMs) return -1
    var low = 0
    var high = size - 1
    var result = 0
    while (low <= high) {
        val mid = (low + high) / 2
        if (this[mid].timeMs <= positionMs) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}
