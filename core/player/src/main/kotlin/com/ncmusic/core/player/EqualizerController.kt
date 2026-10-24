package com.ncmusic.core.player

import android.content.Context
import android.media.audiofx.Equalizer
import androidx.annotation.StringRes
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str
import com.ncmusic.core.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 单个频段信息 */
data class EqBand(
    val index: Short,
    val freqHz: Int,
    /** 当前增益（毫贝，100 毫贝 = 1 dB） */
    val levelMb: Int,
)

/** 均衡器状态（UI 观察） */
data class EqualizerState(
    val supported: Boolean = true,
    val enabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val minLevelMb: Int = -1500,
    val maxLevelMb: Int = 1500,
)

/** 预设曲线（按频段频率位置换算的 dB 增益） */
enum class EqPreset(@StringRes val labelRes: Int) {
    FLAT(R.string.flat),
    POP(R.string.pop),
    ROCK(R.string.rock),
    BASS(R.string.bass_boost),
    VOCAL(R.string.vocal),
    TREBLE(R.string.treble_boost),
    ;

    val label: String get() = Str.res(labelRes)
}

/**
 * 系统均衡器（audiofx.Equalizer）。
 *
 * 工作方式：播放器创建音频会话后调用 [attach]（幂等），
 * 之后所有开关 / 频段调整都会实时生效并持久化到 [SettingsRepository]。
 */
@Singleton
class EqualizerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    private var equalizer: Equalizer? = null
    private var sessionId = 0

    /** 已持久化的设置（attach 时应用） */
    private var savedEnabled = false
    private var savedLevels: List<Int> = emptyList()

    init {
        scope.launch {
            settingsRepository.eqEnabled.collect {
                savedEnabled = it
                runCatching { equalizer?.enabled = it }
                _state.value = _state.value.copy(enabled = it)
            }
        }
        scope.launch {
            settingsRepository.eqBands.collect {
                savedLevels = it
                applyLevels(it)
            }
        }
    }

    /** 挂到播放器音频会话（幂等；sessionId<=0 时忽略） */
    fun attach(newSessionId: Int) {
        if (newSessionId <= 0) return
        if (equalizer != null && sessionId == newSessionId) return
        release()
        val eq = runCatching { Equalizer(1000, newSessionId) }.getOrNull()
        if (eq == null) {
            _state.value = EqualizerState(supported = false)
            return
        }
        equalizer = eq
        sessionId = newSessionId
        runCatching {
            val range = eq.bandLevelRange
            val minMb = range[0].toInt()
            val maxMb = range[1].toInt()
            val bands = buildList {
                for (b in 0 until eq.numberOfBands) {
                    val band = b.toShort()
                    val freq = runCatching { eq.getBandFreqRange(band)?.let { (it[0] / 1000).toInt() } ?: 0 }
                        .getOrDefault(0)
                    add(EqBand(band, freq, eq.getBandLevel(band).toInt()))
                }
            }
            _state.value = EqualizerState(
                supported = true,
                enabled = savedEnabled,
                bands = bands,
                minLevelMb = minMb,
                maxLevelMb = maxMb,
            )
            applyLevels(savedLevels)
            eq.enabled = savedEnabled
        }.onFailure {
            release()
            _state.value = EqualizerState(supported = false)
        }
    }

    fun detach() {
        release()
    }

    /** 开关均衡器（持久化） */
    fun setEnabled(enable: Boolean) {
        scope.launch { settingsRepository.setEqEnabled(enable) }
    }

    /** 调整单个频段（毫贝；持久化） */
    fun setBandLevel(index: Short, levelMb: Int) {
        val eq = equalizer ?: return
        val min = _state.value.minLevelMb
        val max = _state.value.maxLevelMb
        val clamped = levelMb.coerceIn(min, max)
        runCatching { eq.setBandLevel(index, clamped.toShort()) }
        val levels = currentLevels().toMutableList()
        if (index in levels.indices) levels[index.toInt()] = clamped
        scope.launch { settingsRepository.setEqBands(levels) }
        _state.value = _state.value.copy(
            bands = _state.value.bands.map { if (it.index == index) it.copy(levelMb = clamped) else it },
        )
    }

    /** 应用预设 */
    fun applyPreset(preset: EqPreset) {
        val bands = _state.value.bands
        if (bands.isEmpty()) return
        val levels = bands.map { presetLevel(preset, it.freqHz) }
        val eq = equalizer
        if (eq != null) {
            bands.forEachIndexed { i, band ->
                runCatching { eq.setBandLevel(band.index, levels[i].toShort()) }
            }
        }
        scope.launch { settingsRepository.setEqBands(levels) }
        _state.value = _state.value.copy(
            bands = bands.mapIndexed { i, band -> band.copy(levelMb = levels[i]) },
        )
    }

    /** 还原全部频段为 0 */
    fun resetAll() = applyPreset(EqPreset.FLAT)

    private fun applyLevels(levels: List<Int>) {
        val eq = equalizer ?: return
        val bands = _state.value.bands
        if (bands.isEmpty()) return
        bands.forEachIndexed { i, band ->
            val level = levels.getOrNull(i) ?: 0
            runCatching { eq.setBandLevel(band.index, level.toShort()) }
        }
        _state.value = _state.value.copy(
            bands = bands.mapIndexed { i, band -> band.copy(levelMb = levels.getOrNull(i) ?: 0) },
        )
    }

    private fun currentLevels(): List<Int> = _state.value.bands.map { it.levelMb }

    private fun release() {
        runCatching { equalizer?.enabled = false }
        runCatching { equalizer?.release() }
        equalizer = null
        sessionId = 0
    }

    /** 按频率简单模拟预设曲线（低频 <300Hz / 中频 300-3kHz / 高频 >3kHz），单位毫贝 */
    private fun presetLevel(preset: EqPreset, freqHz: Int): Int {
        val low = freqHz in 1 until 300
        val high = freqHz >= 3000
        return when (preset) {
            EqPreset.FLAT -> 0
            EqPreset.POP -> when {
                low -> 200
                high -> 150
                else -> 0
            }

            EqPreset.ROCK -> when {
                low -> 350
                high -> 300
                else -> -100
            }

            EqPreset.BASS -> when {
                low -> 600
                high -> 0
                else -> -150
            }

            EqPreset.VOCAL -> when {
                low -> -250
                high -> -100
                else -> 400
            }

            EqPreset.TREBLE -> when {
                low -> -150
                high -> 600
                else -> 0
            }
        }
    }
}
