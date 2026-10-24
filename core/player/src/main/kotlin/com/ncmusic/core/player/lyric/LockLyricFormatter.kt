package com.ncmusic.core.player.lyric

/**
 * 把逐句歌词格式化成「通知栏 / 锁屏媒体卡片」能稳定显示的文本。
 *
 * 系统媒体卡片宽度有限：一句太长会自己换行，卡片高度跟着变，切句时看起来一跳一跳。
 * 这里按**整首歌**预先算好要不要两行，并对每一句：
 *  - 单行放得下 → 一行；
 *  - 整首歌需要两行 → 每句都固定两行，长句在标点 / 空格附近断开，
 *    短句用一行空白补齐，保证整首歌里卡片高度不变；
 *  - 两行仍放不下 → 第二行末尾省略号截断（不会冒出第三行）。
 *
 * 宽度单位：一个汉字记 1.0，英文 / 数字约 0.55，空格约 0.35。
 */
object LockLyricFormatter {

    /** 单行安全宽度（汉字个数），再按系统字体缩放调整。适当放宽，让锁屏歌词能显示更完整的长句。 */
    private const val BASE_LINE_UNITS = 20.0

    /** 补位用的空白行（全角空格，保证这一行确实占高度又看不见） */
    private const val BLANK_LINE = "\u3000"

    /** 断行优先选择的位置（断在这些字符之后） */
    private const val SOFT_BREAKS = " ，,、。.！!？?；;：:…～~-—/"

    /**
     * @param lines 整首歌的歌词（已按时间排序）
     * @param fontScale 系统字体缩放（媒体卡片的字号跟着系统走，安全宽度相应变小）
     * @return 与 [lines] 一一对应的展示文本；需要两行时用 `\n` 分隔，短句第二行是空白
     */
    fun format(lines: List<String>, fontScale: Float = 1f): List<String> {
        if (lines.isEmpty()) return emptyList()
        val scale = fontScale.coerceIn(0.8f, 1.4f)
        val safe = (BASE_LINE_UNITS / scale).coerceAtLeast(10.0)

        var longest = 0.0
        for (line in lines) {
            val units = units(line.trim())
            if (units > longest) longest = units
        }
        // 整首歌最长的一句放不下一行 → 全曲统一两行，短句补空行，避免切句时高度跳动
        val twoLine = longest > safe

        return lines.map { raw ->
            val text = raw.trim()
            if (!twoLine) {
                if (units(text) <= safe) text else truncate(text, safe)
            } else {
                twoLines(text, safe).joinToString("\n")
            }
        }
    }

    /** 一首歌固定两行：第一行尽量在中间附近断开，第二行放不下就截断，空句补空白行 */
    private fun twoLines(text: String, safe: Double): List<String> {
        if (text.isEmpty()) return listOf(BLANK_LINE, BLANK_LINE)
        val total = units(text)
        if (total <= safe) return listOf(text, BLANK_LINE)

        val cut = findCut(text, minOf(total / 2, safe)).coerceIn(1, text.length)
        var first = text.substring(0, cut).trim()
        var rest = text.substring(cut).trim()

        if (first.isEmpty()) {
            // 断点异常靠前（整句都是空白 / 标点）：直接按宽度截断，保证只占一行
            first = truncate(text, safe)
            rest = ""
        }
        if (rest.isEmpty()) return listOf(first, BLANK_LINE)
        if (units(rest) > safe) rest = truncate(rest, safe)
        return listOf(first, rest)
    }

    /**
     * 找到断行位置：先按宽度走到目标位置，再往前找最近的标点 / 空格，
     * 找到就断在标点之后，找不到就硬断（中文歌词硬断也能读）。
     * 只往前找，保证第一行一定不超过目标宽度。
     */
    private fun findCut(text: String, targetUnits: Double): Int {
        if (text.isEmpty()) return 0
        var acc = 0.0
        var index = text.length
        for (i in text.indices) {
            acc += units(text[i])
            if (acc > targetUnits) {
                index = i
                break
            }
        }
        if (index <= 0) return 1.coerceAtMost(text.length)
        if (index >= text.length) return text.length

        for (delta in 1..5) {
            val before = index - delta
            if (before in 1 until text.length && text[before - 1] in SOFT_BREAKS) {
                return before
            }
        }
        return index
    }

    /** 按宽度截断并加省略号 */
    private fun truncate(text: String, safe: Double): String {
        val builder = StringBuilder()
        var acc = 0.0
        for (ch in text) {
            val unit = units(ch)
            // 给省略号留大约一个汉字的位置
            if (acc + unit > safe - 0.9) break
            acc += unit
            builder.append(ch)
        }
        val kept = builder.toString().trimEnd()
        return if (kept.isEmpty()) "…" else "$kept…"
    }

    private fun units(text: String): Double {
        var sum = 0.0
        for (ch in text) sum += units(ch)
        return sum
    }

    private fun units(ch: Char): Double = when {
        ch == ' ' || ch == '\t' -> 0.35
        ch.code < 0x80 -> 0.55
        ch.code in 0x2E80..0x9FFF -> 1.0 // 汉字 / 假名
        ch.code in 0xAC00..0xD7AF -> 1.0 // 韩文
        ch.code in 0x3000..0x303F -> 1.0 // 中文标点
        ch.code in 0xFF00..0xFF60 -> 1.0 // 全角字符
        else -> 0.8
    }
}
