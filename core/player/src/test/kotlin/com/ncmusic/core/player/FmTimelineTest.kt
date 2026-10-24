package com.ncmusic.core.player

import com.ncmusic.core.network.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FmTimelineTest {

    private fun song(id: Long) = Song(id = id, name = "song-$id")

    @Test
    fun `previous then next returns to the song that was left`() {
        val timeline = FmTimeline()
        timeline.reset(song(1))
        timeline.append(song(2))
        timeline.append(song(3))
        timeline.append(song(4))

        assertEquals(3L, timeline.previous()?.id)
        assertEquals(2L, timeline.previous()?.id)
        assertEquals(3L, timeline.next()?.id)
        assertEquals(4L, timeline.next()?.id)
        assertNull(timeline.next())
        assertEquals(4L, timeline.current?.id)
    }

    @Test
    fun `next at the end asks caller for a new song and appends it`() {
        val timeline = FmTimeline()
        timeline.reset(song(1))
        timeline.append(song(2))
        assertEquals(2L, timeline.current?.id)
        assertNull(timeline.next())

        timeline.append(song(3))
        assertEquals(3L, timeline.current?.id)
        assertEquals(2L, timeline.previous()?.id)
        assertEquals(3L, timeline.next()?.id)
    }

    @Test
    fun `previous at the first song returns null`() {
        val timeline = FmTimeline()
        timeline.reset(song(1))
        assertNull(timeline.previous())
        assertEquals(1L, timeline.current?.id)
    }

    @Test
    fun `timeline keeps at most fifty songs`() {
        val timeline = FmTimeline()
        timeline.reset(song(0))
        for (id in 1L..60L) {
            timeline.append(song(id))
        }
        assertEquals(60L, timeline.current?.id)

        var steps = 0
        while (timeline.previous() != null) {
            steps += 1
        }
        assertEquals(49, steps)
        assertEquals(11L, timeline.current?.id)
    }
}
