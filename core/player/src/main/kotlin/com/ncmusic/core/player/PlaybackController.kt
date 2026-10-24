package com.ncmusic.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.common.model.PlayMode
import com.ncmusic.core.common.model.SoundQuality
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.repository.PlayHistoryRepository
import com.ncmusic.core.data.repository.SongRepository
import com.ncmusic.core.data.settings.SettingsRepository
import com.ncmusic.core.network.model.Song
import com.ncmusic.core.player.cache.AudioCache
import com.ncmusic.core.player.lyric.LrcLine
import com.ncmusic.core.player.lyric.LrcParser
import com.ncmusic.core.player.lyric.LockLyricFormatter
import com.ncmusic.core.player.lyric.indexAt
import com.ncmusic.core.player.service.NcmusicPlayerService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/**
 * 播放控制器（全应用单例）。
 *
 * 设计说明：
 * - 队列 / 播放模式 / 播放顺序由本控制器维护，ExoPlayer 只承载「当前这一首」
 * - 切歌时先通过 API 解析真实播放地址，再交给 ExoPlayer 播放
 * - 通知栏 / 锁屏 / 耳机线控通过 MediaSession + ForwardingPlayer 转发到本控制器
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val playHistoryRepository: PlayHistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val equalizerController: EqualizerController,
    private val audioCache: AudioCache,
    @IoDispatcher private val io: kotlinx.coroutines.CoroutineDispatcher,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ---------------- 状态 ----------------

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    val currentSong: StateFlow<Song?> = combine(_queue, _currentIndex) { queue, index ->
        queue.getOrNull(index)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _globalPlayMode = MutableStateFlow(PlayMode.LOOP_ALL)

    /** 队列级播放模式覆盖（私人FM 默认顺序播放，不受全局随机模式影响） */
    private val _queuePlayMode = MutableStateFlow<PlayMode?>(null)
    val playMode: StateFlow<PlayMode> =
        combine(_globalPlayMode, _queuePlayMode) { global, override -> override ?: global }
            .stateIn(scope, SharingStarted.Eagerly, _globalPlayMode.value)

    private fun effectivePlayMode(): PlayMode = _queuePlayMode.value ?: _globalPlayMode.value

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _quality = MutableStateFlow(SoundQuality.EXHIGH)
    val quality: StateFlow<String> = _quality.asStateFlow()

    /** 错误提示（UI 消费后调用 [consumeError] 清除） */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** 定时停止剩余时间（毫秒；null 表示未开启） */
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()

    /** 红心状态变化事件（通知栏红心 → 应用内喜欢状态同步） */
    private val _likeEvents = MutableSharedFlow<Pair<Long, Boolean>>(extraBufferCapacity = 16)
    val likeEvents: SharedFlow<Pair<Long, Boolean>> = _likeEvents.asSharedFlow()

    /** 锁屏 / 通知栏媒体卡片第二行的当前歌词（null = 不显示，保持歌手名） */
    private val _lockLyric = MutableStateFlow<String?>(null)
    val lockLyric: StateFlow<String?> = _lockLyric.asStateFlow()

    /** 本地已下载歌曲的播放地址（songId -> filePath） */
    private val localPaths = mutableMapOf<Long, String>()

    /**
     * 私人FM：队列里永远只有当前一首。
     * 播完/点下一首时通过这个 provider 取新歌，避免列表顺序、随机模式互相干扰。
     */
    private var fmNextProvider: (suspend () -> Song?)? = null
    private var fmLoading = false
    private var queueGeneration = 0

    /**
     * 私人FM 本次会话的播放时间线（最多 50 首）。
     * 播放器队列里始终只有当前一首；这里只用于上一首/下一首在已播放歌曲间前后移动。
     */
    private val fmTimeline = FmTimeline()

    private val _isFmMode = MutableStateFlow(false)
    val isFmMode: StateFlow<Boolean> = _isFmMode.asStateFlow()

    private val beatDetector = BeatDetector(scope)

    /** 灵魂鼓点能量 0..1（播放页背景动效用；设置里开启后才工作） */
    val beatEnergy: StateFlow<Float> = beatDetector.energy

    /** 是否开启鼓点闪光（设置项） */
    private var beatGlowEnabled = false
    /** 播放页是否在前台可见 */
    private var beatVisualizerVisible = false
    /** 当前是否正在播放 */
    private var beatPlaying = false

    private var sleepTimerJob: Job? = null

    // ---------------- 内部状态 ----------------

    private var player: ExoPlayer? = null
    private var playerReady = MutableStateFlow(false)
    private var tickerJob: Job? = null
    private var preparedIndex = -1
    private var consecutiveErrors = 0
    private val scrobbledIds = mutableSetOf<Long>()
    private var shuffleOrder: List<Int> = emptyList()

    /** 锁屏歌词：当前歌曲的歌词行 / 展示文本（按全曲算好行数与安全宽度）/ 开关状态 */
    private var lockLyricLines: List<LrcLine> = emptyList()
    private var lockLyricCaptions: List<String> = emptyList()
    private var lockLyricEnabled = false

    init {
        // 设置项驱动播放行为（倍速全局记忆 / 播放模式 / 音质）
        scope.launch {
            settingsRepository.playMode.collect { mode ->
                _globalPlayMode.value = mode
                applyPlayMode()
            }
        }
        scope.launch {
            settingsRepository.playbackSpeed.collect { speed ->
                _speed.value = speed
                player?.setPlaybackParameters(PlaybackParameters(speed, 1.0f))
            }
        }
        scope.launch {
            settingsRepository.soundQuality.collect { _quality.value = it }
        }
        // 灵魂鼓点闪光（设置项驱动频谱采集）
        scope.launch {
            settingsRepository.beatGlowEnabled.collect { enabled ->
                beatGlowEnabled = enabled
                updateBeatDetector()
            }
        }
        // 锁屏 / 通知栏歌词：开关变化 + 跟随当前歌曲加载歌词，按播放进度取当前这一句
        scope.launch {
            settingsRepository.lockLyrics.collect { enabled ->
                lockLyricEnabled = enabled
                if (enabled) {
                    loadLockLyric(_queue.value.getOrNull(_currentIndex.value))
                } else {
                    lockLyricLines = emptyList()
                    _lockLyric.value = null
                }
            }
        }
        scope.launch {
            currentSong.collectLatest { song -> loadLockLyric(song) }
        }
        scope.launch {
            _positionMs.collect { position -> updateLockLyric(position) }
        }
    }

    // ---------------- 服务 / 播放器挂载 ----------------

    /**
     * 播放页可见性变化。只有播放页在前台、且正在播放、且开启鼓点闪光时才采集频谱；
     * 退到后台、锁屏或离开播放页立即停止，避免无意义的 CPU / 电量消耗。
     */
    fun setBeatVisualizerVisible(visible: Boolean) {
        if (beatVisualizerVisible == visible) return
        beatVisualizerVisible = visible
        updateBeatDetector()
    }

    private fun updateBeatDetector() {
        if (beatGlowEnabled && beatVisualizerVisible && beatPlaying) {
            beatDetector.start()
        } else {
            beatDetector.stop()
        }
    }

    fun attachPlayer(exoPlayer: ExoPlayer) {
        player = exoPlayer
        exoPlayer.addListener(playerListener)
        exoPlayer.repeatMode = if (effectivePlayMode() == PlayMode.LOOP_ONE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
        exoPlayer.setPlaybackParameters(PlaybackParameters(_speed.value, 1.0f))
        playerReady.value = true
        attachEqualizer(exoPlayer)
        beatPlaying = exoPlayer.isPlaying
        beatDetector.onAudioSessionChanged(exoPlayer.audioSessionId)
        updateBeatDetector()
    }

    fun detachPlayer() {
        player?.removeListener(playerListener)
        player = null
        playerReady.value = false
        equalizerController.detach()
        beatPlaying = false
        beatDetector.stop()
        stopTicker()
        _isPlaying.value = false
    }

    /** 把当前生效的播放模式应用到 ExoPlayer / 随机顺序 */
    private fun applyPlayMode() {
        val mode = effectivePlayMode()
        player?.repeatMode = if (mode == PlayMode.LOOP_ONE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
        if (mode == PlayMode.SHUFFLE) rebuildShuffleOrder()
    }

    private fun attachEqualizer(exoPlayer: ExoPlayer) {
        runCatching { equalizerController.attach(exoPlayer.audioSessionId) }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            beatPlaying = isPlaying
            player?.let { attachEqualizer(it) }
            player?.let { beatDetector.onAudioSessionChanged(it.audioSessionId) }
            updateBeatDetector()
            if (isPlaying) startTicker() else stopTicker()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _isBuffering.value = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) {
                _durationMs.value = (player?.duration ?: 0L).coerceAtLeast(0L)
                player?.let { attachEqualizer(it) }
                player?.let { beatDetector.onAudioSessionChanged(it.audioSessionId) }
                updateBeatDetector()
            }
            if (playbackState == Player.STATE_ENDED) {
                onTrackEnded()
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _speed.value = playbackParameters.speed
        }

        override fun onPlayerError(error: PlaybackException) {
            _errorMessage.value = Str.res(R.string.playback_error, error.errorCodeName)
            advanceAfterError()
        }
    }

    // ---------------- 对外动作 ----------------

    /** 用给定队列开始播放（例如点开歌单/搜索结果） */
    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        ensureServiceStarted()
        _isFmMode.value = false
        fmNextProvider = null
        fmLoading = false
        clearFmTimeline()
        _queuePlayMode.value = null
        queueGeneration += 1
        _queue.value = songs
        _currentIndex.value = startIndex.coerceIn(0, songs.lastIndex)
        preparedIndex = -1
        consecutiveErrors = 0
        scrobbledIds.clear()
        rebuildShuffleOrder()
        applyPlayMode()
        scope.launch { prepareCurrent() }
    }

    /**
     * 私人FM：播放器队列里只放当前这一首。
     * [nextProvider] 在播完或手动点下一首时调用，返回一首新歌。
     */
    fun playPersonalFm(song: Song, nextProvider: suspend () -> Song?) {
        ensureServiceStarted()
        queueGeneration += 1
        _isFmMode.value = true
        fmNextProvider = nextProvider
        fmLoading = false
        clearFmTimeline()
        fmTimeline.reset(song)
        // FM 永远是单曲顺序播放，不受全局随机模式影响
        _queuePlayMode.value = PlayMode.LOOP_ALL
        _queue.value = listOf(song)
        _currentIndex.value = 0
        preparedIndex = -1
        consecutiveErrors = 0
        scrobbledIds.clear()
        rebuildShuffleOrder()
        applyPlayMode()
        scope.launch { prepareCurrent() }
    }

    /** 播放单曲（可选带上下文队列） */
    fun playSong(song: Song, queue: List<Song> = emptyList()) {
        val list = if (queue.isEmpty()) listOf(song) else queue
        val index = list.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0
        playQueue(list, index)
    }

    /** 暂停当前歌曲（进入 MV 播放页等场景使用） */
    fun pause() {
        player?.pause()
    }

    fun togglePlayPause() {
        val p = player ?: run {
            // 没有播放实例时，尝试恢复当前歌曲
            if (_currentIndex.value >= 0) {
                ensureServiceStarted()
                scope.launch { prepareCurrent() }
            }
            return
        }
        if (p.isPlaying) p.pause() else p.play()
    }

    fun next() {
        if (fmNextProvider != null) {
            requestFmNext()
            return
        }
        val target = nextIndex()
        if (target == null) {
            player?.pause()
            player?.seekTo(0)
            return
        }
        _currentIndex.value = target
        scope.launch { prepareCurrent() }
    }

    /** 在当前队列内跳转到指定位置 */
    fun playAt(index: Int) {
        if (index !in _queue.value.indices) return
        _currentIndex.value = index
        scope.launch { prepareCurrent() }
    }

    fun previous() {
        // 私人FM：上一首直接回到本次 FM 播放过的上一首；没有历史就重播当前这首
        if (fmNextProvider != null) {
            playPreviousFm()
            return
        }
        val p = player
        if (p != null && p.currentPosition > 3_000) {
            p.seekTo(0)
            return
        }
        val target = previousIndex() ?: return
        _currentIndex.value = target
        scope.launch { prepareCurrent() }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0))
        _positionMs.value = positionMs.coerceAtLeast(0)
    }

    fun seekToFraction(fraction: Float) {
        val duration = _durationMs.value
        if (duration > 0) seekTo((duration * fraction).toLong())
    }

    fun setPlayMode(mode: PlayMode) {
        // 私人FM 是单曲流，永远顺序播放，不允许随机/单曲循环
        if (_isFmMode.value) return
        if (_queuePlayMode.value != null) {
            // 其他需要独立播放模式的队列：只影响当前队列，不覆盖用户全局偏好
            _queuePlayMode.value = mode
            applyPlayMode()
        } else {
            _globalPlayMode.value = mode
            applyPlayMode()
            scope.launch { settingsRepository.setPlayMode(mode) }
        }
    }

    fun cyclePlayMode() {
        val next = when (effectivePlayMode()) {
            PlayMode.ORDER -> PlayMode.LOOP_ALL
            PlayMode.LOOP_ALL -> PlayMode.LOOP_ONE
            PlayMode.LOOP_ONE -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.ORDER
        }
        setPlayMode(next)
    }

    fun setSpeed(speed: Float) {
        scope.launch { settingsRepository.setPlaybackSpeed(speed) }
    }

    /** 切换音质：保持当前进度重新解析地址 */
    fun setQuality(level: String) {
        if (_quality.value == level) return
        scope.launch {
            settingsRepository.setSoundQuality(level)
            val position = player?.currentPosition ?: 0L
            if (_currentIndex.value >= 0 && player?.isPlaying == true) {
                resolveAndPlay(_currentIndex.value, startPositionMs = position)
            }
        }
    }

    fun addToQueue(song: Song) {
        _queue.value = _queue.value + song
        rebuildShuffleOrder()
    }

    fun addToQueueNext(song: Song) {
        val list = _queue.value.toMutableList()
        val insertAt = (_currentIndex.value + 1).coerceIn(0, list.size)
        list.add(insertAt, song)
        _queue.value = list
        rebuildShuffleOrder()
    }

    fun removeAt(index: Int) {
        val list = _queue.value.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        _queue.value = list

        when {
            list.isEmpty() -> {
                player?.stop()
                player?.clearMediaItems()
                _currentIndex.value = -1
                _positionMs.value = 0
                _durationMs.value = 0
                _isFmMode.value = false
                fmNextProvider = null
                fmLoading = false
                clearFmTimeline()
                queueGeneration += 1
                _queuePlayMode.value = null
            }

            index < _currentIndex.value -> _currentIndex.value -= 1

            index == _currentIndex.value -> {
                val target = index.coerceAtMost(list.lastIndex)
                _currentIndex.value = target
                scope.launch { prepareCurrent() }
            }
        }
        rebuildShuffleOrder()
    }

    fun move(from: Int, to: Int) {
        val list = _queue.value.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
        _queue.value = list

        val current = _currentIndex.value
        _currentIndex.value = when {
            current == from -> to
            from < current && to >= current -> current - 1
            from > current && to <= current -> current + 1
            else -> current
        }
        rebuildShuffleOrder()
    }

    fun clearQueue() {
        player?.stop()
        player?.clearMediaItems()
        _queue.value = emptyList()
        _currentIndex.value = -1
        _positionMs.value = 0
        _durationMs.value = 0
        shuffleOrder = emptyList()
        _isFmMode.value = false
        fmNextProvider = null
        fmLoading = false
        clearFmTimeline()
        queueGeneration += 1
        _queuePlayMode.value = null
    }

    fun hasNext(): Boolean = fmNextProvider != null || nextIndex() != null

    fun hasPrevious(): Boolean = fmNextProvider != null || _queue.value.size > 1

    /** 通知栏红心切换后广播，供应用内 UI 同步 */
    fun notifyLikeChanged(songId: Long, liked: Boolean) {
        _likeEvents.tryEmit(songId to liked)
    }

    /** 标记 songId 使用本地文件播放（离线播放） */
    fun registerLocalPaths(paths: Map<Long, String>) {
        localPaths.putAll(paths)
    }

    /** 清音频缓存前调用：停止播放并释放当前歌曲占用的缓存文件锁 */
    fun stopForCacheClear() {
        player?.stop()
        _isPlaying.value = false
        _isBuffering.value = false
    }

    fun consumeError() {
        _errorMessage.value = null
    }

    // ---------------- 定时停止 ----------------

    /** 设置定时停止（分钟；null 表示取消）。到点后自动暂停播放。 */
    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minutes == null || minutes <= 0) {
            _sleepTimerRemainingMs.value = null
            return
        }
        val totalMs = minutes * 60_000L
        _sleepTimerRemainingMs.value = totalMs
        sleepTimerJob = scope.launch {
            val endAt = System.currentTimeMillis() + totalMs
            while (isActive) {
                val remain = endAt - System.currentTimeMillis()
                if (remain <= 0) break
                _sleepTimerRemainingMs.value = remain
                delay(1_000)
            }
            player?.pause()
            _sleepTimerRemainingMs.value = null
        }
    }

    // ---------------- 内部逻辑 ----------------

    private fun ensureServiceStarted() {
        runCatching { context.startService(NcmusicPlayerService.intent(context)) }
    }

    private suspend fun prepareCurrent() {
        val index = _currentIndex.value
        if (index < 0) return
        resolveAndPlay(index)
    }

    private suspend fun resolveAndPlay(index: Int, startPositionMs: Long = 0L) {
        val song = _queue.value.getOrNull(index) ?: return
        val exoPlayer = awaitPlayer() ?: run {
            _isBuffering.value = false
            _errorMessage.value = Str.res(R.string.player_failed_to_start_please_retry)
            return
        }

        // 已下载的歌曲直接播放本地文件，不再请求在线地址
        val localUrl = localPaths[song.id]
            ?.takeIf { java.io.File(it).exists() }
            ?.let { android.net.Uri.fromFile(java.io.File(it)).toString() }

        val url = localUrl ?: run {
            val result = songRepository.getSongUrl(song.id, _quality.value)
            val urlItem = (result as? AppResult.Success)?.data
            val fetched = urlItem?.url
            if (fetched != null) {
                // 登记地址对应缓存 key，播完这首歌离线也能听；关闭缓存时不登记
                if (audioCache.isEnabled()) {
                    audioCache.registerUrl(fetched, song.id, _quality.value)
                    audioCache.recordSize(song.id, _quality.value, urlItem.size ?: 0L)
                }
                fetched
            } else {
                // 取地址失败（离线 / 版权限制）：启用缓存且有完整缓存时直接读缓存
                val cachedKey = audioCache.findCachedKey(song.id, _quality.value)
                if (cachedKey != null) {
                    audioCache.syntheticUri(cachedKey)
                } else {
                    _isBuffering.value = false
                    _errorMessage.value = (result as? AppResult.Failure)?.error?.userMessage
                        ?: Str.res(R.string.can_t_play, song.name)
                    advanceAfterError()
                    return
                }
            }
        }

        consecutiveErrors = 0
        preparedIndex = index
        exoPlayer.setMediaItem(song.toMediaItem(url))
        exoPlayer.prepare()
        exoPlayer.play()
        if (startPositionMs > 0) exoPlayer.seekTo(startPositionMs)

        _durationMs.value = song.durationMs
        _positionMs.value = startPositionMs
        playHistoryRepository.record(song)
    }

    private suspend fun awaitPlayer(): ExoPlayer? {
        player?.let { return it }
        withTimeoutOrNull(3_000) {
            playerReady.first { it }
        }
        return player
    }

    private fun onTrackEnded() {
        if (fmNextProvider != null) {
            requestFmNext()
            return
        }
        val target = nextIndex()
        if (target == null) {
            // 顺序播放到末尾：停下并回到第一首
            player?.seekTo(0)
            _positionMs.value = 0
            return
        }
        _currentIndex.value = target
        scope.launch { prepareCurrent() }
    }

    private fun advanceAfterError() {
        if (fmNextProvider != null) {
            if (consecutiveErrors >= 3) {
                player?.pause()
                return
            }
            consecutiveErrors += 1
            requestFmNext()
            return
        }
        if (consecutiveErrors >= 3) {
            player?.pause()
            return
        }
        consecutiveErrors += 1
        val target = nextIndex() ?: return
        _currentIndex.value = target
        scope.launch { prepareCurrent() }
    }

    /**
     * 私人FM 下一首：
     * - 如果之前点过上一首，先沿本次 FM 时间线前进，回到离开的那首；
     * - 只有已经在时间线末尾时，才向 provider 请求一首新歌。
     */
    private fun requestFmNext() {
        val provider = fmNextProvider ?: return

        fmTimeline.next()?.let { song ->
            playFmSong(song, provider)
            return
        }

        if (fmLoading) return
        fmLoading = true
        val generation = queueGeneration
        scope.launch {
            var started = false
            _isBuffering.value = true
            try {
                val next = try {
                    provider()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    null
                }
                if (generation != queueGeneration || fmNextProvider == null) return@launch
                if (next == null) {
                    _errorMessage.value = Str.res(R.string.failed_to_load_personal_fm)
                    return@launch
                }
                appendFmSong(next, provider)
                started = true
            } finally {
                fmLoading = false
                if (!started) _isBuffering.value = false
            }
        }
    }

    /** 私人FM 的上一首：沿时间线回退一首，队列仍然只放这一首 */
    private fun playPreviousFm() {
        val provider = fmNextProvider ?: return
        fmTimeline.previous()?.let { song ->
            playFmSong(song, provider)
            return
        }
        // 已经是本次 FM 的第一首：按一次回到当前歌曲开头
        player?.seekTo(0)
        _positionMs.value = 0
    }

    /** 请求到新歌后追加到时间线末尾 */
    private fun appendFmSong(song: Song, provider: suspend () -> Song?) {
        fmTimeline.append(song)
        playFmSong(song, provider)
    }

    private fun clearFmTimeline() {
        fmTimeline.clear()
    }

    /**
     * FM 单曲切换：队列始终只保留这一首，不建列表、不参与随机。
     * 时间线的增删由调用方负责，这里只负责把指定歌曲放进单曲队列并播放。
     */
    private fun playFmSong(song: Song, provider: suspend () -> Song?) {
        queueGeneration += 1
        fmNextProvider = provider
        _isFmMode.value = true
        _queuePlayMode.value = PlayMode.LOOP_ALL
        _queue.value = listOf(song)
        _currentIndex.value = 0
        preparedIndex = -1
        scrobbledIds.clear()
        rebuildShuffleOrder()
        applyPlayMode()
        scope.launch { prepareCurrent() }
    }

    private fun nextIndex(): Int? {
        val queue = _queue.value
        if (queue.isEmpty()) return null
        val current = _currentIndex.value
        return when (effectivePlayMode()) {
            PlayMode.ORDER -> if (current + 1 < queue.size) current + 1 else null
            PlayMode.LOOP_ALL, PlayMode.LOOP_ONE -> if (current + 1 < queue.size) current + 1 else 0
            PlayMode.SHUFFLE -> shuffleNeighbor(current, forward = true)
        }
    }

    private fun previousIndex(): Int? {
        val queue = _queue.value
        if (queue.size <= 1) return null
        val current = _currentIndex.value
        return when (effectivePlayMode()) {
            PlayMode.ORDER -> (current - 1).coerceAtLeast(0)
            PlayMode.LOOP_ALL, PlayMode.LOOP_ONE -> if (current - 1 >= 0) current - 1 else queue.lastIndex
            PlayMode.SHUFFLE -> shuffleNeighbor(current, forward = false)
        }
    }

    // 随机播放顺序：当前歌曲打头，其余乱序
    private fun rebuildShuffleOrder() {
        val indices = _queue.value.indices.toList()
        val current = _currentIndex.value
        shuffleOrder = if (current in indices) {
            listOf(current) + indices.filter { it != current }.shuffled()
        } else {
            indices.shuffled()
        }
    }

    private fun shuffleNeighbor(current: Int, forward: Boolean): Int? {
        val order = shuffleOrder.ifEmpty { rebuildShuffleOrder(); shuffleOrder }
        val pos = order.indexOf(current)
        if (pos == -1) return order.firstOrNull()
        val nextPos = if (forward) (pos + 1) % order.size else (pos - 1 + order.size) % order.size
        return order.getOrNull(nextPos)
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                val p = player
                if (p != null) {
                    _positionMs.value = p.currentPosition.coerceAtLeast(0)
                    _durationMs.value = (p.duration).takeIf { it > 0 } ?: _durationMs.value
                    maybeScrobble(p.currentPosition)
                }
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
        _positionMs.value = player?.currentPosition?.coerceAtLeast(0) ?: _positionMs.value
    }

    /** 播放达到要求后自动打卡（10 秒 或 一半时长） */
    private fun maybeScrobble(positionMs: Long) {
        val song = _queue.value.getOrNull(_currentIndex.value) ?: return
        if (song.id in scrobbledIds) return
        val threshold = max(10_000L, _durationMs.value / 2)
        if (positionMs >= threshold) {
            scrobbledIds += song.id
            scope.launch {
                runCatching { songRepository.scrobble(song.id, (positionMs / 1000).toInt()) }
            }
        }
    }

    // ---------------- 锁屏 / 通知栏歌词 ----------------

    /**
     * 拉取当前歌曲的歌词，供通知栏 / 锁屏媒体卡片显示。
     *
     * 系统媒体卡片只能显示「标题 + 一行副标题」，这里把副标题（歌手位）换成当前这一句歌词；
     * 没有歌词 / 关闭开关时保持 null，仍然显示歌手名。
     */
    private suspend fun loadLockLyric(song: Song?) {
        lockLyricLines = emptyList()
        lockLyricCaptions = emptyList()
        _lockLyric.value = null
        if (song == null || !lockLyricEnabled) return
        val resp = songRepository.lyric(song.id).getOrNull() ?: return
        val lines = LrcParser.parse(resp.lrc?.lyric, resp.tlyric?.lyric)
        if (!lockLyricEnabled) return
        if (_queue.value.getOrNull(_currentIndex.value)?.id != song.id) return
        lockLyricLines = lines
        // 按整首歌最长的一句决定一行还是两行；短句补空行，切句时卡片高度不跳
        lockLyricCaptions = LockLyricFormatter.format(
            lines.map { it.text },
            context.resources.configuration.fontScale,
        )
        updateLockLyric(_positionMs.value)
    }

    /** 按播放进度更新锁屏歌词（只在整句变化时改值，避免频繁刷新通知） */
    private fun updateLockLyric(positionMs: Long) {
        val lines = lockLyricLines
        val caption = if (lines.isEmpty()) null else lockLyricCaptions.getOrNull(lines.indexAt(positionMs))
        val text = caption?.takeIf { it.isNotBlank() }
        if (text != _lockLyric.value) _lockLyric.value = text
    }
}

/** Song -> MediaItem 映射（通知栏 / 锁屏使用元数据展示） */
fun Song.toMediaItem(url: String): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(url)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(name)
            .setArtist(artistText)
            .setAlbumTitle(albumName)
            .setArtworkUri(coverUrl?.let { Uri.parse(it) })
            .setIsPlayable(true)
            .build(),
    )
    .build()
