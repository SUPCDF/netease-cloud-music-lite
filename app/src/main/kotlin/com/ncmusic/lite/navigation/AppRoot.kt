package com.ncmusic.lite.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ncmusic.designsystem.component.AvatarViewerController
import com.ncmusic.designsystem.component.AvatarViewerDialog
import com.ncmusic.designsystem.component.LocalAvatarViewer
import com.ncmusic.feature.player.MiniPlayer
import com.ncmusic.feature.player.PlayerViewModel

/**
 * 应用根布局：
 * - 底部导航（3 个一级 tab），仅一级页面显示
 * - 迷你播放条固定在底部导航上方（全屏播放器等页面隐藏）
 * - 全局消费播放错误提示（Snackbar）
 */
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = TopLevelDestination.entries.any { it.route == currentRoute }
    val showMiniPlayer = currentRoute != Route.PLAYER && currentRoute != Route.MV

    // 全局播放错误提示
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playbackError by playerViewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(playbackError) {
        val message = playbackError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            playerViewModel.consumeError()
        }
    }

    // 全局头像查看（评论 / 好友 / 用户页点头像都能全屏看大图）
    val avatarViewerState = remember { mutableStateOf<String?>(null) }
    val avatarViewer = remember {
        AvatarViewerController { url -> avatarViewerState.value = url }
    }

    CompositionLocalProvider(LocalAvatarViewer provides avatarViewer) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            // 根 Scaffold 不消费系统栏 insets：让每个页面的 TopAppBar / 背景自己延伸到状态栏下，
            // 状态栏区域就不会再露出一条与页面底色不一致的「黑块」。
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = showMiniPlayer,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                    ) {
                        Box(
                            modifier = if (isTopLevel) {
                                Modifier
                            } else {
                                // 详情页没有底部一级导航，迷你播放条需自己避开系统导航栏
                                Modifier.navigationBarsPadding()
                            },
                        ) {
                            MiniPlayer(
                                onClick = {
                                    navController.navigate(Route.PLAYER) { launchSingleTop = true }
                                },
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = isTopLevel,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                    ) {
                        BottomTabBar(
                            currentRoute = currentRoute,
                            onSelect = { destination ->
                                navController.navigate(destination.route) {
                                    // 切换 tab 时保存/恢复各 tab 的滚动位置，避免堆栈无限增长
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            },
        ) { innerPadding ->
            AppNavHost(
                navController = navController,
                // 只消费根布局自身的内边距（底部导航高度），状态栏 inset 留给页面自己处理
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            )
        }
    }

    avatarViewerState.value?.let { url ->
        AvatarViewerDialog(url = url, onDismiss = { avatarViewerState.value = null })
    }
}

/**
 * 底部一级导航：只保留横向文字（无图标）。
 * 选中项用主色 + 半粗字重，未选中用次级文字色。
 */
@Composable
private fun BottomTabBar(
    currentRoute: String?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(50.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    // 切换 tab 时颜色渐变 + 选中项轻轻放大，不生硬
                    val tabColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 220),
                        label = "tabColor",
                    )
                    val tabScale by animateFloatAsState(
                        targetValue = if (selected) 1.08f else 1f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
                        label = "tabScale",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelect(destination) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = tabColor,
                            modifier = Modifier.graphicsLayer {
                                scaleX = tabScale
                                scaleY = tabScale
                            },
                        )
                    }
                }
            }
        }
    }
}
