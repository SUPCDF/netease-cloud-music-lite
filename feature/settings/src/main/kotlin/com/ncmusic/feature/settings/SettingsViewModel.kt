package com.ncmusic.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.common.model.PlayMode
import com.ncmusic.core.common.model.SoundQuality
import com.ncmusic.core.common.model.ThemeMode
import com.ncmusic.core.data.cache.LyricCache
import com.ncmusic.core.data.cache.PlaylistCache
import com.ncmusic.core.data.repository.PlayHistoryRepository
import com.ncmusic.core.data.settings.SettingsRepository
import com.ncmusic.core.player.PlaybackController
import com.ncmusic.core.player.cache.AudioCache
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 设置页 ViewModel：主题 / 动态取色 / 音质 / 倍速 / 播放模式 / 清理数据 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val playHistoryRepository: PlayHistoryRepository,
    private val audioCache: AudioCache,
    private val lyricCache: LyricCache,
    private val playlistCache: PlaylistCache,
    private val playbackController: PlaybackController,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val dynamicColor: StateFlow<Boolean> = settingsRepository.dynamicColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val soundQuality: StateFlow<String> = settingsRepository.soundQuality
        .stateIn(viewModelScope, SharingStarted.Eagerly, SoundQuality.EXHIGH)

    val playbackSpeed: StateFlow<Float> = settingsRepository.playbackSpeed
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)

    val playMode: StateFlow<PlayMode> = settingsRepository.playMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayMode.LOOP_ALL)

    /** 灵魂鼓点闪光（播放页背景随鼓点脉动） */
    val beatGlowEnabled: StateFlow<Boolean> = settingsRepository.beatGlowEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setBeatGlowEnabled(enable: Boolean) {
        viewModelScope.launch { settingsRepository.setBeatGlowEnabled(enable) }
    }

    /** 锁屏 / 通知栏歌词（媒体卡片第二行显示当前歌词） */
    val lockLyrics: StateFlow<Boolean> = settingsRepository.lockLyrics
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setLockLyrics(enable: Boolean) {
        viewModelScope.launch { settingsRepository.setLockLyrics(enable) }
    }

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    // ---------------- 缓存 ----------------

    private val _cacheLimitMb = MutableStateFlow(audioCache.limitMb())
    val cacheLimitMb: StateFlow<Int> = _cacheLimitMb.asStateFlow()

    private val _cacheUsedBytes = MutableStateFlow(0L)
    val cacheUsedBytes: StateFlow<Long> = _cacheUsedBytes.asStateFlow()

    fun refreshCacheInfo() {
        _cacheLimitMb.value = audioCache.limitMb()
        viewModelScope.launch {
            _cacheUsedBytes.value = withContext(io) { audioCache.usedBytes() }
        }
    }

    fun setCacheLimitMb(mb: Int) {
        _cacheLimitMb.value = mb
        audioCache.setLimitMb(mb)
        viewModelScope.launch {
            // 淘汰在后台进行；大缓存可能需要一点时间，连续刷新几次占用
            repeat(6) {
                delay(300)
                _cacheUsedBytes.value = withContext(io) { audioCache.usedBytes() }
            }
        }
    }

    fun clearCache() {
        // 先停播，释放当前歌曲对缓存文件的锁
        playbackController.stopForCacheClear()
        viewModelScope.launch {
            withContext(io) {
                audioCache.clear()
                lyricCache.clear()
                playlistCache.clear()
            }
            _cacheUsedBytes.value = withContext(io) { audioCache.usedBytes() }
            _toast.value = Str.res(R.string.cache_cleared)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enable: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enable) }
    }

    fun setSoundQuality(level: String) {
        viewModelScope.launch { settingsRepository.setSoundQuality(level) }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch { settingsRepository.setPlaybackSpeed(speed) }
    }

    fun setPlayMode(mode: PlayMode) {
        viewModelScope.launch { settingsRepository.setPlayMode(mode) }
    }

    fun clearPlayHistory() {
        viewModelScope.launch {
            playHistoryRepository.clear()
            _toast.value = Str.res(R.string.play_history_cleared)
        }
    }

    fun consumeToast() {
        _toast.value = null
    }

    companion object {
        /** 播放模式展示名 */
        fun playModeName(mode: PlayMode): String = when (mode) {
            PlayMode.ORDER -> Str.res(R.string.play_in_order)
            PlayMode.LOOP_ALL -> Str.res(R.string.repeat_all)
            PlayMode.LOOP_ONE -> Str.res(R.string.repeat_one)
            PlayMode.SHUFFLE -> Str.res(R.string.shuffle)
        }

        /** 主题模式展示名 */
        fun themeModeName(mode: ThemeMode): String = when (mode) {
            ThemeMode.SYSTEM -> Str.res(R.string.follow_system)
            ThemeMode.LIGHT -> Str.res(R.string.light)
            ThemeMode.DARK -> Str.res(R.string.dark)
        }

        val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    }
}
