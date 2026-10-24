package com.ncmusic.core.data.repository

import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.network.model.Song
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 私人FM 单曲来源。
 *
 * 官方接口一次返回 3 首；这里只把其中一首交给播放器，
 * 播放器队列里始终只有当前这一首，播完或点下一首时再取下一首。
 * 这样不受队列/随机播放模式影响，也不会出现列表顺序反了的问题。
 */
@Singleton
class PersonalFmSource @Inject constructor(
    private val discoverRepository: DiscoverRepository,
) {

    private val buffer = ArrayDeque<Song>()
    private val recentIds = ArrayDeque<Long>()
    private val mutex = Mutex()

    /** 重新开始一轮 FM：清空缓存和最近记录，返回第一首 */
    suspend fun start(): Song? = mutex.withLock {
        buffer.clear()
        recentIds.clear()
        nextLocked()
    }

    /** 取下一首；缓存用尽时再调用接口 */
    suspend fun next(): Song? = mutex.withLock { nextLocked() }

    private suspend fun nextLocked(): Song? {
        while (buffer.isNotEmpty()) {
            val song = buffer.removeFirst()
            if (song.id !in recentIds) {
                remember(song.id)
                return song
            }
        }

        repeat(2) {
            val batch = discoverRepository.personalFm().getOrNull().orEmpty()
            if (batch.isEmpty()) return@repeat
            // 接口偶尔会回传刚播过的歌，优先过滤；全都重复时仍然返回一首，保证 FM 不断
            val candidates = batch.filter { it.id !in recentIds }.ifEmpty { batch }
            buffer.addAll(candidates.drop(1))
            val song = candidates.first()
            remember(song.id)
            return song
        }
        return null
    }

    private fun remember(id: Long) {
        if (id in recentIds) return
        recentIds.addLast(id)
        while (recentIds.size > 50) recentIds.removeFirst()
    }
}
