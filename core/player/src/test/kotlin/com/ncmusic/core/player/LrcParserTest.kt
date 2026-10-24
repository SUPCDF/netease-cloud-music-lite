package com.ncmusic.core.player

import com.ncmusic.core.player.lyric.LrcParser
import com.ncmusic.core.player.lyric.indexAt
import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {

    private val sample = """
        [00:00.000] 作词 : 测试
        [00:01.50]第一句歌词
        [00:03.000]第二句歌词
        [00:05.000][00:07.000]重复歌词
        [00:09.00]最后一句
    """.trimIndent()

    private val translation = """
        [00:01.50]First line
        [00:03.00]Second line
    """.trimIndent()

    @Test
    fun `解析时间戳与内容`() {
        val lines = LrcParser.parse(sample, translation)

        assertEquals(6, lines.size)
        assertEquals(0L, lines[0].timeMs)
        assertEquals("作词 : 测试", lines[0].text)
        assertEquals(1500L, lines[1].timeMs)
        assertEquals("第一句歌词", lines[1].text)
        assertEquals("First line", lines[1].translation)
        assertEquals(3000L, lines[2].timeMs)
        assertEquals("Second line", lines[2].translation)
        // 一行双时间戳
        assertEquals(5000L, lines[3].timeMs)
        assertEquals(7000L, lines[4].timeMs)
        assertEquals("重复歌词", lines[4].text)
        assertEquals(9000L, lines[5].timeMs)
    }

    @Test
    fun `查找逻辑边界`() {
        val lines = LrcParser.parse(sample)

        // 第一行时间是 0ms，0ms 应命中下标 0
        assertEquals(0, lines.indexAt(0))
        assertEquals(0, lines.indexAt(999))
        assertEquals(0, lines.indexAt(1000))
        assertEquals(1, lines.indexAt(1500))
        assertEquals(2, lines.indexAt(4999))
        assertEquals(3, lines.indexAt(5000))
        assertEquals(3, lines.indexAt(6000))
        assertEquals(5, lines.indexAt(999999))
    }

    @Test
    fun `空歌词返回空列表`() {
        assertEquals(0, LrcParser.parse(null).size)
        assertEquals(0, LrcParser.parse("").size)
        assertEquals(0, LrcParser.parse("没有时间戳的文本").size)
    }
}
