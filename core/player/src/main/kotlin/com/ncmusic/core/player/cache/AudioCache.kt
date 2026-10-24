package com.ncmusic.core.player.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.common.model.SoundQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 在线播放音频缓存。
 *
 * - 用 Media3 SimpleCache 边播边存，完整播放过的歌曲离线也能播；
 * - 上限可选择 1 GB / 5 GB / 10 GB / 不限制，超出后按最近使用时间淘汰；
 * - 缓存与「下载」相互独立：清缓存不会删除用户主动下载的歌曲。
 */
@OptIn(UnstableApi::class)
@Singleton
class AudioCache @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher io: CoroutineDispatcher,
) {

    private val prefs = context.getSharedPreferences("ncmusic_audio_cache", Context.MODE_PRIVATE)
    private val cacheDir = File(context.filesDir, "audio_cache")
    private val scope = CoroutineScope(SupervisorJob() + io)

    @Volatile
    private var limitBytes: Long = mbToBytes(prefs.getInt(KEY_LIMIT_MB, DEFAULT_LIMIT_MB))

    @Volatile
    private var enabled: Boolean = prefs.getInt(KEY_LIMIT_MB, DEFAULT_LIMIT_MB) >= 0

    @Volatile
    private var opened = false

    /** 播放地址 -> 缓存 key。地址每次解析都会变，key 固定用 songId@quality。 */
    private val urlKeys = object : LinkedHashMap<String, String>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
            size > MAX_URL_KEYS
    }

    private val evictor = DynamicLruEvictor { limitBytes }

    val cache: SimpleCache by lazy {
        SimpleCache(cacheDir, evictor).also { created ->
            opened = true
            // 上次可能设置了更小的上限，启动后先按新上限清理一次
            scope.launch { runCatching { evictor.trim(created) } }
        }
    }

    fun limitMb(): Int = prefs.getInt(KEY_LIMIT_MB, DEFAULT_LIMIT_MB)

    /** 是否启用在线播放缓存（「不缓存」时直接走网络，不读也不写缓存） */
    fun isEnabled(): Boolean = enabled

    fun setLimitMb(mb: Int) {
        // -1 = 不缓存，0 = 不限制，其余为 MB 上限
        val normalized = if (mb < 0) LIMIT_DISABLED else mb.coerceAtLeast(0)
        prefs.edit().putInt(KEY_LIMIT_MB, normalized).apply()
        enabled = normalized != LIMIT_DISABLED
        limitBytes = mbToBytes(normalized)
        if (enabled) {
            scope.launch { runCatching { evictor.trim(cache) } }
        }
    }

    /** 已缓存音频占用（字节） */
    fun usedBytes(): Long = runCatching {
        if (opened) cache.cacheSpace else directorySize(cacheDir)
    }.getOrDefault(0L)

    fun cacheKey(songId: Long, quality: String): String = "$songId@$quality"

    /** 把本次解析出的播放地址登记到缓存 key，CacheDataSource 写缓存时使用 */
    fun registerUrl(url: String, songId: Long, quality: String) {
        synchronized(urlKeys) {
            urlKeys[url] = cacheKey(songId, quality)
        }
    }

    /** 记录歌曲文件大小，用来判断缓存是否完整 */
    fun recordSize(songId: Long, quality: String, size: Long) {
        if (size <= 0L) return
        prefs.edit().putLong(sizeKey(cacheKey(songId, quality)), size).apply()
    }

    /**
     * 找一首离线可完整播放的缓存。
     * 优先当前音质，其次其他音质（例如之前用无损听过，现在设置成了极高）。
     */
    fun findCachedKey(songId: Long, preferredQuality: String): String? {
        if (!enabled) return null
        sequence {
            yield(preferredQuality)
            SoundQuality.all.forEach { if (it != preferredQuality) yield(it) }
        }.map { quality -> cacheKey(songId, quality) }
            .firstOrNull { isFullyCached(it) }
            ?.let { return it }
        return null
    }

    /** 离线播放时使用的虚拟地址；CacheDataSource 会直接读缓存，不会访问网络 */
    fun syntheticUri(key: String): String {
        val split = key.split('@', limit = 2)
        if (split.size != 2) return "ncm-cache://audio/$key"
        return "ncm-cache://audio/${split[0]}/${split[1]}"
    }

    /** CacheDataSource 的 CacheKeyFactory 入口 */
    fun cacheKeyFor(dataSpec: DataSpec): String {
        val uri = dataSpec.uri
        if (uri.scheme == CACHE_SCHEME) {
            val segments = uri.pathSegments
            if (segments.size >= 2) {
                return "${segments[segments.size - 2]}@${segments.last()}"
            }
            return uri.toString()
        }
        synchronized(urlKeys) {
            urlKeys[uri.toString()]?.let { return it }
        }
        return uri.toString()
    }

    /** 清空音频缓存（不删除「下载」页里的歌曲） */
    fun clear() {
        runCatching {
            val c = cache
            c.keys.toList().forEach { key -> c.removeResource(key) }
        }
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(SIZE_KEY_PREFIX) }.forEach { editor.remove(it) }
        editor.apply()
        synchronized(urlKeys) { urlKeys.clear() }
    }

    private fun isFullyCached(key: String): Boolean {
        val c = cache
        if (!c.keys.contains(key)) return false
        val size = prefs.getLong(sizeKey(key), -1L)
        if (size > 0L) return c.isCached(key, 0L, size)
        // 没拿到文件大小：退化为「从 0 开始有缓存数据」，完整播放过的歌曲一般可用
        return c.getCachedSpans(key).any { span -> span.position == 0L && span.length > 0L }
    }

    private fun sizeKey(cacheKey: String): String = SIZE_KEY_PREFIX + cacheKey

    private fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun mbToBytes(mb: Int): Long =
        if (mb <= 0) Long.MAX_VALUE else mb.toLong() * 1024L * 1024L

    private companion object {
        const val CACHE_SCHEME = "ncm-cache"
        const val KEY_LIMIT_MB = "limit_mb"
        const val SIZE_KEY_PREFIX = "size_"
        const val DEFAULT_LIMIT_MB = 5120
        const val MAX_URL_KEYS = 512
        const val LIMIT_DISABLED = -1
    }
}

/**
 * 上限可动态修改的 LRU 淘汰器。
 *
 * Media3 自带的 LeastRecentlyUsedCacheEvictor 的上限在构造时固定，改设置后必须重建
 * SimpleCache（会中断播放）。这里自己记录 key 的最近访问时间，上限每次实时读取。
 */
@OptIn(UnstableApi::class)
private class DynamicLruEvictor(
    private val limitBytes: () -> Long,
) : CacheEvictor {

    private val lastAccess = ConcurrentHashMap<String, Long>()
    private val evicting = AtomicBoolean(false)
    private val evictAgain = AtomicBoolean(false)

    /**
     * LRU 扫描 / 删文件都放在独立线程，避免在 CacheDataSource 写入回调里
     * 长时间持有缓存锁，导致锁屏或后台播放时音频读取被卡住。
     */
    private val evictExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ncmusic-cache-evict").apply { isDaemon = true }
    }

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        lastAccess[key] = System.currentTimeMillis()
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        lastAccess[span.key] = max(lastAccess[span.key] ?: 0L, span.lastTouchTimestamp)
        scheduleEvict(cache)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        if (!cache.keys.contains(span.key)) lastAccess.remove(span.key)
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        lastAccess[newSpan.key] = max(lastAccess[newSpan.key] ?: 0L, newSpan.lastTouchTimestamp)
    }

    fun trim(cache: Cache) {
        scheduleEvict(cache)
    }

    private fun scheduleEvict(cache: Cache) {
        val limit = limitBytes()
        if (limit == Long.MAX_VALUE || cache.cacheSpace <= limit) return
        if (!evicting.compareAndSet(false, true)) {
            // 正在淘汰：标记一下，当前一轮结束后再检查一次上限
            evictAgain.set(true)
            return
        }
        evictExecutor.execute {
            try {
                evict(cache)
            } catch (_: Throwable) {
                // 缓存异常不能影响播放；下一次 span 变化 / 设置变更会再尝试
            } finally {
                evicting.set(false)
                if (evictAgain.compareAndSet(true, false)) scheduleEvict(cache)
            }
        }
    }

    private fun evict(cache: Cache) {
        val limit = limitBytes()
        if (limit == Long.MAX_VALUE) return
        while (cache.cacheSpace > limit) {
            val keys = cache.keys
            if (keys.isEmpty()) return
            keys.forEach { key ->
                if (!lastAccess.containsKey(key)) {
                    lastAccess[key] = cache.getCachedSpans(key)
                        .maxOfOrNull { span -> span.lastTouchTimestamp } ?: 0L
                }
            }
            val candidate = keys.minByOrNull { key -> lastAccess[key] ?: 0L } ?: return
            val before = cache.cacheSpace
            cache.removeResource(candidate)
            if (!cache.keys.contains(candidate)) lastAccess.remove(candidate)
            // 候选正在播放 / 被锁定，删不掉：等下一次 span 变化再试
            if (cache.cacheSpace >= before) return
        }
    }
}
