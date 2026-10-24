package com.ncmusic.feature.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.ncmusic.core.common.model.PlayMode
import com.ncmusic.core.common.model.SoundQuality
import com.ncmusic.core.common.model.ThemeMode
import com.ncmusic.designsystem.component.NcTopBar
import java.util.Locale
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.LanguageManager
import com.ncmusic.core.common.i18n.Str

/** 设置页：外观 / 播放 / 数据 / 关于 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEqualizerClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val quality by viewModel.soundQuality.collectAsStateWithLifecycle()
    val speed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val playMode by viewModel.playMode.collectAsStateWithLifecycle()
    val beatGlow by viewModel.beatGlowEnabled.collectAsStateWithLifecycle()
    val lockLyrics by viewModel.lockLyrics.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val cacheLimitMb by viewModel.cacheLimitMb.collectAsStateWithLifecycle()
    val cacheUsedBytes by viewModel.cacheUsedBytes.collectAsStateWithLifecycle()

    var showQualitySheet by remember { mutableStateOf(false) }
    var showPlayModeSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showCacheSizeSheet by remember { mutableStateOf(false) }
    val appLanguage by LanguageManager.language.collectAsStateWithLifecycle()

    val context = LocalContext.current
    // 灵魂鼓点闪光需要录音权限读取音频频谱（仅本地分析，不录音；拒绝则退回模拟节拍）
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> Unit }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(toast) {
        val message = toast
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeToast()
        }
    }
    LaunchedEffect(Unit) { viewModel.refreshCacheInfo() }

    Scaffold(
        topBar = { NcTopBar(title = Str.res(R.string.settings), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                // ---------------- 外观 ----------------
                SettingsSectionTitle(Str.res(R.string.appearance))
                SettingsClickRow(
                    title = Str.res(R.string.theme),
                    value = SettingsViewModel.themeModeName(themeMode),
                    onClick = { showThemeSheet = true },
                )
                SettingsClickRow(
                    title = Str.res(R.string.language),
                    value = languageLabel(appLanguage),
                    onClick = { showLanguageSheet = true },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(Str.res(R.string.dynamic_color_material_you), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = Str.res(R.string.generate_app_colors_from_the_system_wallpaper_an),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = dynamicColor, onCheckedChange = { viewModel.setDynamicColor(it) })
                }

                // ---------------- 播放 ----------------
                SettingsSectionTitle(Str.res(R.string.playback))
                SettingsClickRow(
                    title = Str.res(R.string.default_audio_quality),
                    value = SoundQuality.displayName(quality) + " " + Str.res(R.string.depends_on_your_account),
                    onClick = { showQualitySheet = true },
                )
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(Str.res(R.string.default_speed), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            text = formatSpeed(speed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = speed,
                        onValueChange = { viewModel.setPlaybackSpeed(it) },
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                    )
                }
                SettingsClickRow(
                    title = Str.res(R.string.default_play_mode),
                    value = SettingsViewModel.playModeName(playMode),
                    onClick = { showPlayModeSheet = true },
                )
                SettingsClickRow(
                    title = Str.res(R.string.equalizer),
                    value = Str.res(R.string.adjust_band_gains_system_audio_effect),
                    onClick = onEqualizerClick,
                )
                SettingsSwitchRow(
                    title = Str.res(R.string.beat_glow),
                    subtitle = Str.res(R.string.player_background_takes_the_cover_color_and_puls),
                    checked = beatGlow,
                    onCheckedChange = { enabled ->
                        if (
                            enabled &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        viewModel.setBeatGlowEnabled(enabled)
                    },
                )
                SettingsSwitchRow(
                    title = Str.res(R.string.lock_screen_notification_lyrics),
                    subtitle = Str.res(R.string.shows_the_current_lyric_on_the_media_card_s_seco),
                    checked = lockLyrics,
                    onCheckedChange = { viewModel.setLockLyrics(it) },
                )

                // ---------------- 后台 ----------------
                SettingsSectionTitle(Str.res(R.string.background))
                SettingsClickRow(
                    title = Str.res(R.string.background_playback_protection),
                    value = Str.res(R.string.whitelist_the_app_from_battery_optimization_to_a),
                    onClick = { openBatteryWhitelist(context) },
                )

                // ---------------- 存储 ----------------
                SettingsSectionTitle(Str.res(R.string.storage))
                SettingsClickRow(
                    title = Str.res(R.string.cache_size),
                    value = cacheLimitLabel(cacheLimitMb) + " · " +
                        Str.res(R.string.cache_used, formatCacheSize(cacheUsedBytes)),
                    onClick = { showCacheSizeSheet = true },
                )
                SettingsClickRow(
                    title = Str.res(R.string.clear_cache),
                    value = Str.res(R.string.clear_cache_desc),
                    onClick = { viewModel.clearCache() },
                )
                SettingsClickRow(
                    title = Str.res(R.string.clear_play_history),
                    value = Str.res(R.string.recent_plays_local_database),
                    onClick = { viewModel.clearPlayHistory() },
                )

                // ---------------- 关于 ----------------
                SettingsSectionTitle(Str.res(R.string.about))
                Text(
                    text = Str.res(R.string.netease_cloud_music_lite_v, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = Str.res(R.string.settings_about),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // ---------------- 音质选择 ----------------
    if (showQualitySheet) {
        ModalBottomSheet(onDismissRequest = { showQualitySheet = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                Text(
                    text = Str.res(R.string.default_audio_quality),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                SoundQuality.all.forEach { level ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setSoundQuality(level)
                                showQualitySheet = false
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = SoundQuality.displayName(level),
                            modifier = Modifier.weight(1f),
                        )
                        if (level == quality) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }

    // ---------------- 缓存大小 ----------------
    if (showCacheSizeSheet) {
        ModalBottomSheet(onDismissRequest = { showCacheSizeSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                Text(
                    text = Str.res(R.string.cache_size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                CACHE_LIMIT_OPTIONS.forEach { mb ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setCacheLimitMb(mb)
                                showCacheSizeSheet = false
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = cacheLimitLabel(mb),
                            modifier = Modifier.weight(1f),
                        )
                        if (mb == cacheLimitMb) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Text(
                    text = Str.res(R.string.cache_size_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }

    // ---------------- 语言选择 ----------------
    if (showThemeSheet) {
        ModalBottomSheet(onDismissRequest = { showThemeSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                Text(
                    text = Str.res(R.string.theme),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setThemeMode(mode)
                                showThemeSheet = false
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = SettingsViewModel.themeModeName(mode),
                            modifier = Modifier.weight(1f),
                        )
                        if (mode == themeMode) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
    if (showLanguageSheet) {
        ModalBottomSheet(onDismissRequest = { showLanguageSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                Text(
                    text = Str.res(R.string.language),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                LANGUAGE_OPTIONS.forEach { (tag, nativeName) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showLanguageSheet = false
                                if (tag != appLanguage) {
                                    LanguageManager.set(tag)
                                    (context as? Activity)?.recreate()
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = nativeName ?: Str.res(R.string.follow_system),
                            modifier = Modifier.weight(1f),
                        )
                        if (tag == appLanguage) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
    // ---------------- 播放模式选择 ----------------
    if (showPlayModeSheet) {
        ModalBottomSheet(onDismissRequest = { showPlayModeSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                Text(
                    text = Str.res(R.string.default_play_mode),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                PlayMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setPlayMode(mode)
                                showPlayModeSheet = false
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = SettingsViewModel.playModeName(mode),
                            modifier = Modifier.weight(1f),
                        )
                        if (mode == playMode) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val LANGUAGE_OPTIONS = listOf(
    LanguageManager.TAG_SYSTEM to null,
    LanguageManager.TAG_ZH to "简体中文",
    LanguageManager.TAG_ZH_TW to "繁體中文",
    LanguageManager.TAG_EN to "English",
    LanguageManager.TAG_JA to "日本語",
    LanguageManager.TAG_KO to "한국어",
    LanguageManager.TAG_ES to "Español",
    LanguageManager.TAG_FR to "Français",
    LanguageManager.TAG_DE to "Deutsch",
    LanguageManager.TAG_RU to "Русский",
)

private fun languageLabel(tag: String): String =
    LANGUAGE_OPTIONS.firstOrNull { it.first == tag }?.second
        ?: if (tag == LanguageManager.TAG_SYSTEM) Str.res(R.string.follow_system) else tag

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsClickRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 跳转到系统电池优化设置，让用户把应用加入白名单（后台播放保活） */
private fun openBatteryWhitelist(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    val started = runCatching { context.startActivity(request) }.isSuccess ||
        runCatching { context.startActivity(fallback) }.isSuccess
    if (!started) {
        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}

private fun formatSpeed(speed: Float): String = when {
    speed == speed.toInt().toFloat() -> String.format(Locale.US, "%.1fx", speed)
    else -> String.format(Locale.US, "%.2fx", speed).replace("0x", "x")
}

private val CACHE_LIMIT_OPTIONS = listOf(-1, 1024, 5120, 10240, 0)

private fun cacheLimitLabel(mb: Int): String = when {
    mb < 0 -> Str.res(R.string.cache_disabled)
    mb == 0 -> Str.res(R.string.cache_unlimited)
    else -> "${mb / 1024} GB"
}

private fun formatCacheSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L ->
        String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L ->
        String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
