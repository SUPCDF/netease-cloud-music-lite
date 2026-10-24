package com.ncmusic.lite

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.core.common.i18n.LanguageManager
import com.ncmusic.core.common.model.ThemeMode
import com.ncmusic.core.data.settings.SettingsRepository
import com.ncmusic.designsystem.theme.NcmusicTheme
import com.ncmusic.lite.navigation.AppRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 单 Activity 架构：所有页面均由 Compose Navigation 承载 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()

        setContent {
            // 主题模式 / 动态取色来自设置（DataStore），修改后立即生效
            val themeMode by settingsRepository.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dynamicColor by settingsRepository.dynamicColor
                .collectAsStateWithLifecycle(initialValue = true)

            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // 状态栏 / 导航栏图标跟着「App 内」的主题走（系统深色 + App 浅色时也要用深色图标，
            // 否则浅色页面上会出现一排白色图标，看起来像「控件都是白的」）
            val view = LocalView.current
            SideEffect {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            NcmusicTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                AppRoot()
            }
        }
    }

    /** 播放通知需要通知权限（Android 13+），首次启动主动申请一次 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
