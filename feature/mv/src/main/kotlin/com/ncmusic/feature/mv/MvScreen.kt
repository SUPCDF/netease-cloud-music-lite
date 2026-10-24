package com.ncmusic.feature.mv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str
import com.ncmusic.core.common.model.CommentThread
import com.ncmusic.core.common.util.TimeUtils
import com.ncmusic.designsystem.component.CoverImage
import com.ncmusic.designsystem.component.NcError
import com.ncmusic.designsystem.component.NcLoading
import com.ncmusic.designsystem.component.NcTopBar
import com.ncmusic.feature.comment.CommentPane
import com.ncmusic.feature.comment.CommentViewModel
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * MV 播放页：
 * - 顶部视频、下方评论（可边看边刷评论）
 * - 倍速、下载、全屏
 * - 全屏时隐藏评论区与系统栏，退出全屏恢复
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MvScreen(
    mvId: Long,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit,
    viewModel: MvViewModel = hiltViewModel(),
    commentViewModel: CommentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val commentState by commentViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val commentListState = rememberLazyListState()
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var controlsInteraction by remember { mutableStateOf(0) }
    var controlsBusy by remember { mutableStateOf(false) }

    val detail = state.detail
    val threadId = detail?.commentThreadId?.takeIf { it.isNotBlank() }
        ?: detail?.let { CommentThread.mv(it.id) }

    LaunchedEffect(mvId) { viewModel.load(mvId) }
    LaunchedEffect(state.toast) {
        val toast = state.toast
        if (toast != null) {
            snackbarHostState.showSnackbar(toast)
            viewModel.consumeToast()
        }
    }

    // 播放中 3 秒无操作自动隐藏控件；暂停、缓冲、拖动进度或打开倍速面板时保持显示
    LaunchedEffect(
        showControls,
        state.isPlaying,
        state.isBuffering,
        controlsInteraction,
        controlsBusy,
        showSpeed,
        fullscreen,
    ) {
        if (!showControls || !state.isPlaying || state.isBuffering || controlsBusy || showSpeed) {
            return@LaunchedEffect
        }
        delay(MV_CONTROLS_AUTO_HIDE_MS)
        showControls = false
    }

    BackHandler(enabled = fullscreen) { fullscreen = false }

    LaunchedEffect(fullscreen) {
        if (fullscreen) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
        val act = activity ?: return@LaunchedEffect
        val window = act.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            // 非全屏时固定竖屏，保证「视频在上、评论在下」始终有足够空间
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.let {
                WindowCompat.getInsetsController(it.window, it.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (!fullscreen) {
                NcTopBar(
                    title = detail?.name ?: "MV",
                    onBack = onBack,
                )
            }

            when {
                detail == null && state.loading -> NcLoading(Modifier.weight(1f))

                detail == null -> NcError(
                    message = state.error ?: Str.res(R.string.mv_load_failed),
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.weight(1f),
                )

                else -> {
                    MvVideoArea(
                        state = state,
                        player = viewModel.player,
                        fullscreen = fullscreen,
                        showControls = showControls,
                        onToggleControls = { showControls = !showControls },
                        onPlayPause = {
                            controlsInteraction++
                            viewModel.togglePlayPause()
                        },
                        onSeek = {
                            controlsInteraction++
                            viewModel.seekToFraction(it)
                        },
                        onSpeedClick = {
                            controlsInteraction++
                            showSpeed = true
                        },
                        onDownload = {
                            controlsInteraction++
                            viewModel.download()
                        },
                        onFullscreen = {
                            controlsInteraction++
                            fullscreen = !fullscreen
                        },
                        onExitFullscreen = {
                            controlsInteraction++
                            fullscreen = false
                        },
                        onControlsBusyChange = { controlsBusy = it },
                        onRetry = {
                            showControls = true
                            viewModel.retryVideo()
                        },
                        modifier = if (fullscreen) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        },
                    )

                    if (!fullscreen) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = Str.res(
                                    R.string.comments,
                                    commentState.total.takeIf { it > 0 } ?: (detail.commentCount ?: 0L),
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = detail.artistName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (threadId != null) {
                            CommentPane(
                                threadId = threadId,
                                viewModel = commentViewModel,
                                snackbarHostState = snackbarHostState,
                                onUserClick = onUserClick,
                                modifier = Modifier.weight(1f),
                                listState = commentListState,
                            )
                        } else {
                            NcLoading(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (showSpeed) {
        ModalBottomSheet(onDismissRequest = { showSpeed = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                Text(
                    text = Str.res(R.string.playback_speed),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                MV_SPEED_OPTIONS.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setSpeed(option)
                                showSpeed = false
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatMvSpeed(option),
                            modifier = Modifier.weight(1f),
                        )
                        if (abs(option - state.speed) < 0.001f) {
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

@Composable
private fun MvVideoArea(
    state: MvUiState,
    player: ExoPlayer,
    fullscreen: Boolean,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedClick: () -> Unit,
    onDownload: () -> Unit,
    onFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    onControlsBusyChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(Color.Black)) {
        CoverImage(
            url = state.detail?.cover,
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            contentScale = ContentScale.Crop,
        )

        if (state.videoUrl != null) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(com.ncmusic.feature.mv.R.layout.view_mv_player, null, false) as PlayerView).apply {
                        this.player = player
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 点击视频显示 / 隐藏控件
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleControls,
                ),
        )

        if (showControls || state.videoError != null) {
            if (fullscreen) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                            ),
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onExitFullscreen) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = Str.res(R.string.exit_fullscreen),
                            tint = Color.White,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = state.detail?.name.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.detail?.artistName.orEmpty(),
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            when {
                state.videoError != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = state.videoError,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        TextButton(onClick = onRetry) {
                            Text(Str.res(R.string.retry), color = Color.White)
                        }
                    }
                }

                state.videoLoading || state.isBuffering -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                    )
                }

                else -> {
                    FilledIconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(58.dp),
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) {
                                Str.res(R.string.pause)
                            } else {
                                Str.res(R.string.play)
                            },
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            if (state.videoUrl != null) {
                MvBottomControls(
                    state = state,
                    onSeek = onSeek,
                    onSpeedClick = onSpeedClick,
                    onDownload = onDownload,
                    onFullscreen = onFullscreen,
                    onDraggingChange = onControlsBusyChange,
                    fullscreen = fullscreen,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun MvBottomControls(
    state: MvUiState,
    onSeek: (Float) -> Unit,
    onSpeedClick: () -> Unit,
    onDownload: () -> Unit,
    onFullscreen: () -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs
    val fraction = if (duration > 0) {
        (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    val bufferedFraction = if (duration > 0) {
        (state.bufferedPositionMs.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    val displayed = dragFraction ?: fraction
    val displayedMs = if (duration > 0) (displayed * duration).toLong() else state.positionMs

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.28f to Color.Black.copy(alpha = 0.38f),
                    1f to Color.Black.copy(alpha = 0.90f),
                ),
            )
            // 点在控件空白处时不要穿透到视频层去切换控件显示
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(start = 16.dp, end = 12.dp, top = 22.dp, bottom = 8.dp),
    ) {
        MvSeekBar(
            fraction = displayed,
            bufferedFraction = bufferedFraction,
            onDrag = { dragFraction = it },
            onDragEnd = {
                dragFraction?.let(onSeek)
                dragFraction = null
            },
            onDraggingChange = onDraggingChange,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = TimeUtils.formatDuration(displayedMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = " / ${TimeUtils.formatDuration(duration)}",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onSpeedClick)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = Str.res(R.string.playback_speed),
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = formatMvSpeed(state.speed),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 3.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDownload),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = Str.res(R.string.download_mv),
                    tint = Color.White,
                    modifier = Modifier.size(21.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onFullscreen),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    contentDescription = if (fullscreen) {
                        Str.res(R.string.exit_fullscreen)
                    } else {
                        Str.res(R.string.fullscreen)
                    },
                    tint = Color.White,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

/**
 * MV 自绘进度条：
 * - 细轨道 + 小圆点，播放进度白色、缓冲进度半透明
 * - 拖动时圆点稍微放大，松手才真正 seek
 */
@Composable
private fun MvSeekBar(
    fraction: Float,
    bufferedFraction: Float,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var barWidth by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val thumbRadius by animateDpAsState(
        targetValue = if (dragging) 7.dp else 5.dp,
        label = "mvSeekThumb",
    )
    val activeFraction = fraction.coerceIn(0f, 1f)
    val buffered = bufferedFraction.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .height(32.dp)
            .padding(horizontal = 6.dp)
            .onSizeChanged { barWidth = it.width.toFloat() }
            .pointerInput(barWidth) {
                fun fractionAt(x: Float): Float =
                    if (barWidth > 0f) (x / barWidth).coerceIn(0f, 1f) else 0f

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    onDraggingChange(true)
                    onDrag(fractionAt(down.position.x))
                    try {
                        drag(down.id) { change ->
                            onDrag(fractionAt(change.position.x))
                            change.consume()
                        }
                    } finally {
                        dragging = false
                        onDraggingChange(false)
                        onDragEnd()
                    }
                }
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(activeFraction, 0f..1f)
                setProgress { target ->
                    onDrag(target.coerceIn(0f, 1f))
                    onDragEnd()
                    true
                }
            },
    ) {
        val centerY = size.height / 2f
        val trackHeight = 3.dp.toPx()
        val start = Offset(0f, centerY)
        drawLine(
            color = Color.Black.copy(alpha = 0.18f),
            start = start,
            end = Offset(size.width, centerY),
            strokeWidth = trackHeight + 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White.copy(alpha = 0.24f),
            start = start,
            end = Offset(size.width, centerY),
            strokeWidth = trackHeight,
            cap = StrokeCap.Round,
        )
        if (buffered > 0.001f) {
            drawLine(
                color = Color.White.copy(alpha = 0.38f),
                start = start,
                end = Offset(size.width * buffered, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
        }
        if (activeFraction > 0.001f) {
            drawLine(
                color = Color.White,
                start = start,
                end = Offset(size.width * activeFraction, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
        }

        val thumbX = size.width * activeFraction
        val radius = thumbRadius.toPx()
        drawCircle(
            color = Color.White.copy(alpha = if (dragging) 0.22f else 0.12f),
            radius = radius + 4.dp.toPx(),
            center = Offset(thumbX, centerY),
        )
        drawCircle(
            color = Color.White,
            radius = radius,
            center = Offset(thumbX, centerY),
        )
    }
}

private val MV_SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private const val MV_CONTROLS_AUTO_HIDE_MS = 3_000L

private fun formatMvSpeed(speed: Float): String {
    val value = if (abs(speed - speed.toInt().toFloat()) < 0.001f) {
        String.format(Locale.US, "%.1f", speed)
    } else {
        String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
    }
    return "${value}x"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
