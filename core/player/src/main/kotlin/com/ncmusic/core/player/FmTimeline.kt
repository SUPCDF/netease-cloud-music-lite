package com.ncmusic.core.player

import com.ncmusic.core.network.model.Song

/**
 * 私人FM 本次会话的播放时间线（不进入可见播放队列）。
 *
 * 播放队列始终只有当前一首；时间线只负责上一首/下一首在已播放歌曲间前后移动。
 * 只有位于时间线末尾、再次请求下一首时，才需要向接口取新歌。
 */
internal class FmTimeline(private val maxSize: Int = 50) {

    private val songs = mutableListOf<Song>()

    var index: Int = -1
        private set

    val current: Song?
        get() = songs.getOrNull(index)

    val canGoNext: Boolean
        get() = index < songs.lastIndex

    val canGoPrevious: Boolean
        get() = index > 0

    fun reset(song: Song) {
        songs.clear()
        songs.add(song)
        index = 0
    }

    fun clear() {
        songs.clear()
        index = -1
    }

    /** 沿时间线前进一首；已经在末尾时返回 null，由调用方请求新歌 */
    fun next(): Song? {
        if (!canGoNext) return null
        index += 1
        return songs[index]
    }

    /** 沿时间线回退一首；已经是第一首时返回 null */
    fun previous(): Song? {
        if (!canGoPrevious) return null
        index -= 1
        return songs[index]
    }

    /** 请求到新歌后追加到末尾，并让当前指针停在末尾 */
    fun append(song: Song) {
        songs.add(song)
        while (songs.size > maxSize) {
            songs.removeAt(0)
        }
        index = songs.lastIndex
    }
}
