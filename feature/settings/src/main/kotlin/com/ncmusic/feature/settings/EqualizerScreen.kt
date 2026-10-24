package com.ncmusic.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.core.player.EqBand
import com.ncmusic.core.player.EqPreset
import com.ncmusic.designsystem.component.NcTopBar
import java.util.Locale
import kotlin.math.roundToInt
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 均衡器页：开关 / 预设 / 各频段滑条 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { NcTopBar(title = Str.res(R.string.equalizer), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Str.res(R.string.enable_equalizer), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = Str.res(R.string.adjust_band_gains_applied_to_playback_in_real_ti),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = { viewModel.setEnabled(it) },
                )
            }

            when {
                !state.supported -> Text(
                    text = Str.res(R.string.this_device_or_audio_session_doesn_t_support_the),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                state.bands.isEmpty() -> Text(
                    text = Str.res(R.string.bands_attach_automatically_once_playback_starts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                else -> {
                    Text(
                        text = Str.res(R.string.presets),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        EqPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.applyPreset(preset) },
                                label = { Text(preset.label) },
                            )
                        }
                    }

                    Text(
                        text = Str.res(R.string.band_gain),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    state.bands.forEach { band ->
                        BandSlider(
                            band = band,
                            enabled = state.enabled,
                            minMb = state.minLevelMb,
                            maxMb = state.maxLevelMb,
                            onChange = { index, levelMb ->
                                viewModel.setBandLevel(index, levelMb)
                            },
                        )
                    }
                    TextButton(
                        onClick = viewModel::resetAll,
                        enabled = state.enabled,
                    ) {
                        Text(Str.res(R.string.reset_all))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BandSlider(
    band: EqBand,
    enabled: Boolean,
    minMb: Int,
    maxMb: Int,
    onChange: (Short, Int) -> Unit,
) {
    val levelDb = band.levelMb / 100f
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = bandLabel(band),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = String.format(Locale.US, "%+.1f dB", levelDb),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = levelDb,
            onValueChange = { onChange(band.index, (it * 100).roundToInt()) },
            valueRange = (minMb / 100f)..(maxMb / 100f),
            enabled = enabled,
        )
    }
}

private fun bandLabel(band: EqBand): String = when {
    band.freqHz <= 0 -> Str.res(R.string.band, band.index + 1)
    band.freqHz >= 1000 -> "${band.freqHz / 1000} kHz"
    else -> "${band.freqHz} Hz"
}
