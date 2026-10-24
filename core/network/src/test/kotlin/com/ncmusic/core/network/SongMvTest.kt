package com.ncmusic.core.network

import com.ncmusic.core.network.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

/** MV 入口门槛：兼容不同接口返回的 mv / mvid 字段。 */
class SongMvTest {

    @Test
    fun prefersPositiveMv() {
        assertEquals(123L, Song(mv = 123L, mvid = 456L).musicVideoId)
    }

    @Test
    fun fallsBackToMvidWhenMvMissingOrZero() {
        assertEquals(456L, Song(mv = null, mvid = 456L).musicVideoId)
        assertEquals(456L, Song(mv = 0L, mvid = 456L).musicVideoId)
    }

    @Test
    fun noMvWhenBothMissingOrZero() {
        assertEquals(0L, Song().musicVideoId)
        assertEquals(0L, Song(mv = 0L, mvid = 0L).musicVideoId)
    }
}
