package com.ncmusic.core.player.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.repository.LoginRepository
import com.ncmusic.core.data.repository.PlaylistRepository
import com.ncmusic.core.player.PlaybackController
import com.ncmusic.core.player.cache.AudioCache
import com.ncmusic.core.player.cache.PlaybackDataSourceFactory
import com.ncmusic.core.common.R
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.ncmusic.core.common.i18n.Str

/**
 * 后台播放服务（MediaSessionService）：
 * 通知栏媒体控制、锁屏控制、音频焦点打断自动暂停都由 Media3 原生能力提供。
 *
 * 通知栏按钮：
 * - 上一首 / 播放暂停 / 下一首：由系统按「可用命令」自动生成。因为队列由
 *   [PlaybackController] 自己维护，ExoPlayer 时间线里只有当前一首，所以这里
 *   重写 [ForwardingPlayer.getAvailableCommands] 把可切歌能力暴露给系统。
 * - 喜欢（红心）：自定义 SessionCommand，通过 [MediaSession.setMediaButtonPreferences]
 *   加到通知栏，点击后调用网易云喜欢接口并同步图标状态。
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class NcmusicPlayerService : MediaSessionService() {

    @Inject
    lateinit var playbackController: PlaybackController

    @Inject
    lateinit var audioCache: AudioCache

    @Inject
    lateinit var loginRepository: LoginRepository

    @Inject
    lateinit var playlistRepository: PlaylistRepository

    private var mediaSession: MediaSession? = null
    private var notificationController: ListenableFuture<MediaController>? = null
    private lateinit var exoPlayer: ExoPlayer

    /** 交给会话的播放器包装（锁屏歌词改的是它暴露的元数据，不碰 ExoPlayer 本体） */
    private var forwardingPlayer: ForwardingPlayer? = null

    /** 注册到会话包装上的监听器（歌词换句时手动触发一次元数据变更，让通知栏 / 锁屏刷新） */
    private val sessionListeners = CopyOnWriteArrayList<Player.Listener>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 已喜欢的歌曲 id 缓存（通知栏红心状态用） */
    private val likedIds = mutableSetOf<Long>()
    private var likedLoaded = false

    private val likeCommand = SessionCommand(LIKE_ACTION, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()

        // 在线播放边听边缓存；离线时由 PlaybackController 用 ncm-cache:// 虚拟地址直接读缓存
        val cache = audioCache.cache
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(this))
            .setCacheKeyFactory(CacheKeyFactory { dataSpec -> audioCache.cacheKeyFor(dataSpec) })
            .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(cache))
            // 缓存写满 / 磁盘异常时不要让播放失败，直接回退网络
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val dataSourceFactory = PlaybackDataSourceFactory(
            cacheFactory = cacheDataSourceFactory,
            localFactory = DefaultDataSource.Factory(this),
            cacheEnabled = { audioCache.isEnabled() },
        )

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // 锁屏 / 后台时网络会被系统短暂节流：多缓冲一段，避免一起播就卡顿
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 15_000,
                        /* maxBufferMs = */ 60_000,
                        /* bufferForPlaybackMs = */ 2_500,
                        /* bufferForPlaybackAfterRebufferMs = */ 5_000,
                    )
                    .build(),
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        // 把系统（通知栏/锁屏/耳机）的上一首/下一首映射到控制器队列逻辑
        val forwarding = object : ForwardingPlayer(exoPlayer) {
            override fun seekToNext() = playbackController.next()

            override fun seekToPrevious() = playbackController.previous()

            override fun seekToNextMediaItem() = playbackController.next()

            override fun seekToPreviousMediaItem() = playbackController.previous()

            override fun hasNextMediaItem(): Boolean = playbackController.hasNext()

            override fun hasPreviousMediaItem(): Boolean = playbackController.hasPrevious()

            /**
             * ExoPlayer 的时间线里只有当前这一首，默认不会暴露「切歌」命令，
             * 导致通知栏缺少上一首/下一首按钮。这里按控制器队列补上。
             */
            override fun getAvailableCommands(): Player.Commands {
                val builder = Player.Commands.Builder().addAll(super.getAvailableCommands())
                if (playbackController.hasNext()) {
                    builder.add(Player.COMMAND_SEEK_TO_NEXT)
                    builder.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                }
                if (playbackController.hasPrevious()) {
                    builder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    builder.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                }
                return builder.build()
            }

            /**
             * 通知栏 / 锁屏媒体卡片的第二行：有歌词时显示当前这一句，否则保持歌手名。
             * 只改会话看到的那份元数据，ExoPlayer 自己的 MediaItem 不动（不会重新加载音频）。
             */
            override fun getMediaMetadata(): MediaMetadata {
                val base = super.getCurrentMediaItem()?.mediaMetadata ?: MediaMetadata.EMPTY
                val line = playbackController.lockLyric.value
                if (line.isNullOrBlank() || line == base.artist?.toString()) return base
                return base.buildUpon().setArtist(line).build()
            }

            // 记录会话注册的监听器，供下面「歌词换句」时手动发一次元数据变更
            override fun addListener(listener: Player.Listener) {
                super.addListener(listener)
                sessionListeners.addIfAbsent(listener)
            }

            override fun removeListener(listener: Player.Listener) {
                super.removeListener(listener)
                sessionListeners.remove(listener)
            }
        }
        forwardingPlayer = forwarding

        val sessionBuilder = MediaSession.Builder(this, forwarding)
            .setCallback(sessionCallback)
        launchIntent()?.let { sessionBuilder.setSessionActivity(it) }
        mediaSession = sessionBuilder.build()

        // 歌词换句：手动发一次「元数据变了」，让通知栏 / 锁屏（含系统媒体卡片）刷新副标题。
        // 只改会话展示的元数据，不碰 ExoPlayer，播放不受影响。
        scope.launch {
            playbackController.lockLyric.collect { refreshSessionMetadata() }
        }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.ncmusic_playback_channel)
                .build(),
        )

        // 旧版本用过独立的「波浪进度」通知（已移除），顺手删掉系统里残留的频道
        getSystemService(NotificationManager::class.java)
            ?.deleteNotificationChannel(LEGACY_WAVE_CHANNEL_ID)

        // 切歌时刷新红心状态
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    refreshHeartButton()
                }
            },
        )

        refreshHeartButton()
        ensureLikedLoaded()

        // MediaSessionService 只有在有「服务型」MediaController 客户端连接（即绑定服务并
        // 触发 onGetSession -> addSession）时才会启用媒体通知。App 界面直接使用 ExoPlayer，
        // 因此这里用服务 token 保持一个常驻控制器连接，驱动通知栏 / 锁屏 / 状态栏播放控制。
        // 注意：用 session.token 建控制器是进程内直连，不会触发 addSession，通知不会出现。
        notificationController = MediaController.Builder(
            this,
            SessionToken(this, ComponentName(this, NcmusicPlayerService::class.java)),
        ).buildAsync()

        playbackController.attachPlayer(exoPlayer)
    }

    /** 会话回调：放通自定义「喜欢」命令 */
    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val availableCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(likeCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == LIKE_ACTION) {
                val songId = session.player.currentMediaItem?.mediaId?.toLongOrNull()
                if (songId != null) {
                    scope.launch { toggleLike(songId) }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private fun heartButton(liked: Boolean): CommandButton = CommandButton.Builder(
        if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
    )
        .setDisplayName(if (liked) Str.res(R.string.unlike) else Str.res(R.string.like))
        .setSessionCommand(likeCommand)
        .build()

    private fun refreshHeartButton() {
        val session = mediaSession ?: return
        val songId = session.player.currentMediaItem?.mediaId?.toLongOrNull()
        val liked = songId != null && songId in likedIds
        runCatching { session.setMediaButtonPreferences(listOf(heartButton(liked))) }
    }

    /** 把当前歌词同步到会话元数据（通知栏 / 锁屏刷新用；只发元数据事件，不打断播放） */
    private fun refreshSessionMetadata() {
        val forwarding = forwardingPlayer ?: return
        val metadata = forwarding.mediaMetadata
        sessionListeners.toList().forEach { listener ->
            runCatching { listener.onMediaMetadataChanged(metadata) }
        }
    }

    private fun ensureLikedLoaded() {
        if (likedLoaded) return
        likedLoaded = true
        scope.launch {
            val ids = loginRepository.account().getOrNull()?.account?.id
                ?.let { playlistRepository.likedSongIds(it).getOrNull() }
            if (ids == null) {
                likedLoaded = false
                return@launch
            }
            likedIds.clear()
            likedIds.addAll(ids)
            refreshHeartButton()
        }
    }

    private suspend fun toggleLike(songId: Long) {
        if (!loginRepository.isLoggedInLocally()) return
        ensureLikedLoaded()
        val liked = songId in likedIds
        when (playlistRepository.likeSong(songId, !liked)) {
            is AppResult.Success -> {
                if (liked) likedIds.remove(songId) else likedIds.add(songId)
                playbackController.notifyLikeChanged(songId, !liked)
                refreshHeartButton()
            }

            is AppResult.Failure -> Unit
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        playbackController.detachPlayer()
        notificationController?.let { MediaController.releaseFuture(it) }
        notificationController = null
        mediaSession?.release()
        mediaSession = null
        exoPlayer.release()
        super.onDestroy()
    }

    private fun launchIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val CHANNEL_ID = "ncmusic_playback"
        private const val LEGACY_WAVE_CHANNEL_ID = "ncmusic_wave_progress"
        private const val LIKE_ACTION = "com.ncmusic.action.LIKE"

        fun intent(context: Context): Intent = Intent(context, NcmusicPlayerService::class.java)
    }
}
