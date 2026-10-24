package com.ncmusic.feature.settings

import androidx.lifecycle.ViewModel
import com.ncmusic.core.player.EqPreset
import com.ncmusic.core.player.EqualizerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 均衡器 ViewModel：状态与操作全部转发给全局 [EqualizerController] */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val equalizerController: EqualizerController,
) : ViewModel() {

    val state = equalizerController.state

    fun setEnabled(enabled: Boolean) = equalizerController.setEnabled(enabled)

    fun setBandLevel(index: Short, levelMb: Int) =
        equalizerController.setBandLevel(index, levelMb)

    fun applyPreset(preset: EqPreset) = equalizerController.applyPreset(preset)

    fun resetAll() = equalizerController.resetAll()
}
