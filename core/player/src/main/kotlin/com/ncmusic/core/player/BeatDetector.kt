package com.ncmusic.core.player

import android.media.audiofx.Visualizer
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 鼓点检测：读取当前播放音频的频谱，取低频能量作为「鼓点」强度（0..1）。
 *
 * - 优先用 [Visualizer] 挂在 ExoPlayer 的 audioSessionId 上（需要录音权限）
 * - 设备不支持 / 没有权限 / 初始化失败时自动退回模拟节拍，保证动效不会中断
 *
 * 只在本地分析频谱用于绘制动效，不录制、不上传任何音频。
 */
class BeatDetector(private val scope: CoroutineScope) {

    private var visualizer: Visualizer? = null
    private var fallbackJob: Job? = null

    private val _energy = MutableStateFlow(0f)

    /** 鼓点强度 0..1（0 = 静音 / 未开启） */
    val energy: StateFlow<Float> = _energy.asStateFlow()

    private var running = false
    private var sessionId = 0

    // 自动增益 / 缓慢呼吸包络状态
    private var peak = 0.15f
    private var baseline = 0f
    private var level = 0f

    /** 设置项打开时调用 */
    fun start() {
        if (running) return
        running = true
        if (sessionId > 0) attach(sessionId) else startFallback()
    }

    /** 设置项关闭 / 播放器解绑时调用 */
    fun stop() {
        running = false
        detach()
        peak = 0.15f
        baseline = 0f
        level = 0f
        _energy.value = 0f
    }

    /** ExoPlayer 的 audioSessionId 变化 / 开始播放时调用 */
    fun onAudioSessionChanged(audioSessionId: Int) {
        if (audioSessionId == sessionId && visualizer != null) return
        sessionId = audioSessionId
        if (!running) return
        if (audioSessionId > 0) attach(audioSessionId) else startFallback()
    }

    private fun attach(audioSessionId: Int) {
        detach()
        val attached = runCatching {
            val v = Visualizer(audioSessionId)
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int,
                    ) = Unit

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int,
                    ) {
                        if (fft == null) return
                        _energy.value = pulseFromFft(fft)
                    }
                },
                Visualizer.getMaxCaptureRate(),
                false,
                true,
            )
            v.enabled = true
            visualizer = v
            true
        }.getOrDefault(false)
        if (!attached) startFallback()
    }

    private fun detach() {
        fallbackJob?.cancel()
        fallbackJob = null
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
    }

    /**
     * 把 FFT 低频能量变成「跟着鼓点律动」的强度（0..1）：
     *
     * 1. **自动增益**：按本曲峰值归一化（峰值约 10s 衰减），切歌 / 安静段落都能马上建立基准；
     * 2. **起拍量**：减去约 2s 慢基线，得到「这一拍比刚才响多少」，抬升 0.12 就算满格 —— 灵敏度较高，
     *    轻的鼓点也能点亮；
     * 3. **包络**：起拍约 50ms 贴着鼓点亮起，回落约 0.25s —— 拍与拍之间能重新暗下去，
     *    律动清晰但过渡仍是柔和的，不是爆闪。
     */
    private fun pulseFromFft(fft: ByteArray): Float {
        val raw = lowFrequencyLevel(fft)
        peak = maxOf(raw, peak * 0.995f)
        val norm = (raw / maxOf(peak, 0.06f)).coerceIn(0f, 1f)

        baseline += (norm - baseline) * 0.024f
        val relative = ((norm - baseline) / 0.12f).coerceIn(0f, 1f)
        val target = maxOf(relative, norm * 0.10f)

        level = if (target > level) {
            level + (target - level) * 0.62f
        } else {
            level + (target - level) * 0.17f
        }
        val shaped = level.coerceIn(0f, 1f)
        return shaped * shaped * (3f - 2f * shaped)
    }

    /**
     * 低频能量：FFT 数据每两个字节是一个复数（实部、虚部），
     * 取最低几个 bin（≈0~260Hz，底鼓 / 贝斯）的平均模值。
     */
    private fun lowFrequencyLevel(fft: ByteArray): Float {
        val bins = 6
        var sum = 0.0
        var count = 0
        for (i in 1..bins) {
            val index = i * 2
            if (index + 1 >= fft.size) break
            val real = fft[index].toInt()
            val imag = fft[index + 1].toInt()
            sum += sqrt((real * real + imag * imag).toDouble())
            count++
        }
        if (count == 0) return 0f
        return (sum / count / 90.0).coerceIn(0.0, 1.0).toFloat()
    }

    /** 模拟节拍（约 125 BPM）：没有权限 / 设备不支持频谱采集时的兜底 */
    private fun startFallback() {
        fallbackJob?.cancel()
        fallbackJob = scope.launch {
            while (isActive) {
                val period = 480L
                val phase = (System.currentTimeMillis() % period).toFloat() / period
                val pulse = (1f - phase) * (1f - phase)
                _energy.value = 0.06f + 0.94f * pulse
                delay(33)
            }
        }
    }
}
