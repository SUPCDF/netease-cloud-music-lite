package com.ncmusic.core.player.cache

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource

/**
 * 只对网络音频启用缓存。
 *
 * 已下载歌曲是通过 file:// 播放的，如果同样走 CacheDataSource，会把本地文件再复制
 * 一份进音频缓存，既浪费空间又永远不会被离线逻辑用到。这里按 scheme 分流：
 * - http/https / ncm-cache：CacheDataSource（边播边缓存 / 离线读缓存）
 * - file/content 等本地地址：DefaultDataSource，直接播放
 */
@OptIn(UnstableApi::class)
class PlaybackDataSourceFactory(
    private val cacheFactory: CacheDataSource.Factory,
    private val localFactory: DataSource.Factory,
    private val cacheEnabled: () -> Boolean,
) : DataSource.Factory {

    override fun createDataSource(): DataSource = SchemeAwareDataSource(
        cached = cacheFactory.createDataSource(),
        local = localFactory.createDataSource(),
        cacheEnabled = cacheEnabled,
    )
}

@OptIn(UnstableApi::class)
private class SchemeAwareDataSource(
    private val cached: DataSource,
    private val local: DataSource,
    private val cacheEnabled: () -> Boolean,
) : DataSource {

    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        cached.addTransferListener(transferListener)
        local.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val target = when (dataSpec.uri.scheme?.lowercase()) {
            "file", "content", "asset", "rawresource", "android.resource" -> local
            // 设置「不缓存」时，网络播放直接走 DefaultDataSource，不读也不写缓存
            "ncm-cache" -> cached
            else -> if (cacheEnabled()) cached else local
        }
        active = target
        return target.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val target = active ?: error("DataSource is not open")
        return target.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = active?.uri

    override fun close() {
        val target = active
        active = null
        target?.close()
    }
}
