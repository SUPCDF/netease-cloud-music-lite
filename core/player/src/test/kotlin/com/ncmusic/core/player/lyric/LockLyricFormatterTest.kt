package com.ncmusic.core.player.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockLyricFormatterTest {

    @Test
    fun `短句整首保持一行`() {
        val captions = LockLyricFormatter.format(listOf("第一句歌词", "第二句稍微长一点点", "短句"))
        assertEquals(3, captions.size)
        assertTrue(captions.none { it.contains('\n') })
    }

    @Test
    fun `整首有长句时全部固定两行且短句补空行`() {
        val long = "一二三四五六七八九十甲乙丙丁戊己庚辛壬癸子丑" // 22 个字，超过单行安全宽度
        val captions = LockLyricFormatter.format(listOf(long, "短句"))
        assertEquals(2, captions.size)
        captions.forEach { assertEquals(2, it.split('\n').size) }
        assertEquals("短句\n\u3000", captions[1])
    }

    @Test
    fun `断行优先落在标点之后`() {
        val line = "一二三四五六七八九，十甲乙丙丁戊己庚辛壬癸"
        val captions = LockLyricFormatter.format(listOf(line))
        val first = captions[0].substringBefore('\n')
        assertTrue("第一行应以标点结尾：$first", first.endsWith("，"))
    }

    @Test
    fun `超长歌词第二行截断且不出现第三行`() {
        val captions = LockLyricFormatter.format(listOf("一".repeat(50)))
        val lines = captions[0].split('\n')
        assertEquals(2, lines.size)
        assertTrue("第二行应以省略号结束：${lines[1]}", lines[1].endsWith("…"))
    }

    @Test
    fun `字体放大时安全宽度变小`() {
        // 15 个字：默认字号下一行放得下，1.4 倍字号下需要两行
        val line = "一".repeat(15)
        assertEquals(1, LockLyricFormatter.format(listOf(line), fontScale = 1f)[0].split('\n').size)
        assertEquals(2, LockLyricFormatter.format(listOf(line), fontScale = 1.4f)[0].split('\n').size)
    }

    @Test
    fun `空歌词返回空列表`() {
        assertTrue(LockLyricFormatter.format(emptyList()).isEmpty())
    }
}
