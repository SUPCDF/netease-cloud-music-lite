package com.ncmusic.feature.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Toc
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ncmusic.core.common.model.PlayMode
import com.ncmusic.core.common.model.SoundQuality
import com.ncmusic.core.common.util.TimeUtils
import com.ncmusic.core.player.lyric.LrcLine
import com.ncmusic.core.player.lyric.indexAt
import com.ncmusic.designsystem.component.CoverImage
import com.ncmusic.designsystem.component.NcEmpty
import com.ncmusic.designsystem.component.SongRow
import com.ncmusic.designsystem.util.copySongLink
import com.ncmusic.designsystem.util.shareSong
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale
import kotlinx.coroutines.launch
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/**
 * 全屏播放器：
 * - 封面 / 歌词双页切换（点击封面进歌词）
 * - 进度拖动、播放模式、上一首/下一首
 * - 音质切换、倍速切换、收藏、评论入口、播放队列
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenComments: (Long) -> Unit,
    onOpenMv: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenEqualizer: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val song by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by viewModel.isBuffering.collectAsStateWithLifecycle()
    val position by viewModel.positionMs.collectAsStateWithLifecycle()
    val duration by viewModel.durationMs.collectAsStateWithLifecycle()
    val playMode by viewModel.playMode.collectAsStateWithLifecycle()
    val isFmMode by viewModel.isFmMode.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val quality by viewModel.quality.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedIds.collectAsStateWithLifecycle()
    val lyricLines by viewModel.lyricLines.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val showTranslation by viewModel.lyricsTranslation.collectAsStateWithLifecycle()
    val sleepRemaining by viewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val beatGlow by viewModel.beatGlowEnabled.collectAsStateWithLifecycle()
    val beatEnergy by viewModel.beatEnergy.collectAsStateWithLifecycle()

    var showQueue by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showPlayerSettings by remember { mutableStateOf(false) }
    var showDirectory by remember { mutableStateOf(false) }
    var showFullCover by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var savingCover by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(toast) {
        val message = toast
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeToast()
        }
    }
    LaunchedEffect(song?.id) { viewModel.ensureLikedIds() }

    val current = song
    if (current == null) {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                PlayerTopBar(title = Str.res(R.string.player), subtitle = "", onBack = onBack)
                NcEmpty(Str.res(R.string.nothing_playing))
            }
        }
        return
    }

    // 频谱只服务播放页视觉效果：页面不可见（后台 / 锁屏 / 跳转别处）时立即停止采集。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setBeatVisualizerVisible(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setBeatVisualizerVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.setBeatVisualizerVisible(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setBeatVisualizerVisible(false)
        }
    }

    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val displayFraction = dragFraction ?: fraction
    val mvId = current.musicVideoId
    // 进度每 500ms 才推一次：补一层线性滑行，让进度条和时间连续走；
    // 拖动中或大幅跳转（点进度条、切歌）时立即归位，保证「跟手」。
    val glideFraction = remember { Animatable(fraction) }
    LaunchedEffect(displayFraction, dragFraction != null) {
        if (dragFraction != null || abs(displayFraction - glideFraction.value) > 0.03f) {
            glideFraction.snapTo(displayFraction)
        } else {
            glideFraction.animateTo(
                targetValue = displayFraction,
                animationSpec = tween(durationMillis = 520, easing = LinearEasing),
            )
        }
    }
    val lyricIndex = remember(lyricLines, position) { lyricLines.indexAt(position) }

    // 封面主色：不开闪光时当背景色，开了闪光当光晕色 —— 所以不管开关都取
    val coverColor = rememberCoverColor(current.coverUrl)
    val hasCoverColor = coverColor != Color.Unspecified
    val glowColor = coverColor
    val glowEnergy by animateFloatAsState(
        targetValue = if (beatGlow) beatEnergy else 0f,
        animationSpec = tween(durationMillis = 90),
        label = "beatGlow",
    )
    // 拍点时把光色稍微推向白色，让「亮起来」更有光感
    val glowHot = lerp(glowColor, Color.White, 0.18f * glowEnergy)
    val glowActive = beatGlow && hasCoverColor
    // 深色主题走「黑舞台」（四周黑、中间光源）；浅色主题保持浅色底，光晕收敛成一层彩色呼吸，
    // 这样浅色模式下文字 / 控件仍然是深色，不会出现「满屏白字」。
    val darkStage = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glowStrength = if (darkStage) 1f else 0.38f
    // 背景色：不开闪光（默认）时用封面主色铺一层「上彩下暗」的渐变底，不再是纯黑 / 纯白；
    // 开了闪光就保持黑舞台，闪光的对比更清楚。换歌时颜色渐变过渡，不会硬切。
    val darkBase = Color(0xFF0D0D11)
    val bgTopTarget = when {
        !hasCoverColor -> MaterialTheme.colorScheme.background
        darkStage -> lerp(darkBase, coverColor, 0.40f)
        else -> lerp(MaterialTheme.colorScheme.background, coverColor, 0.14f)
    }
    val bgBottomTarget = if (darkStage) Color(0xFF08080B) else MaterialTheme.colorScheme.background
    val bgTop by animateColorAsState(
        targetValue = bgTopTarget,
        animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        label = "pageBgTop",
    )
    val bgBottom by animateColorAsState(
        targetValue = bgBottomTarget,
        animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        label = "pageBgBottom",
    )
    // 黑舞台（深色主题 + 闪光）下不铺彩色底，其余情况都铺
    val coloredBase = hasCoverColor && !(glowActive && darkStage)
    val pageScheme = if (glowActive && darkStage) {
        MaterialTheme.colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF1C1C1E),
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFD6D6D6),
            outline = Color(0xFF7A7A7A),
            outlineVariant = Color(0xFF3A3A3A),
        )
    } else {
        MaterialTheme.colorScheme
    }

    // 「跟手」下滑收起：手指往下拖时整页跟着走，松手后回弹或收起
    val dismissOffset = remember { Animatable(0f) }
    var pageHeightPx by remember { mutableFloatStateOf(1f) }
    val dragProgress = { (dismissOffset.value / pageHeightPx).coerceIn(0f, 1f) }

    MaterialTheme(colorScheme = pageScheme) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                // 页面往下走时，背后压一层越来越深的遮罩（遮罩本身不随页面移动）
                .drawBehind {
                    drawRect(Color.Black.copy(alpha = 0.45f * dragProgress()))
                }
                .offset { IntOffset(0, dismissOffset.value.roundToInt()) }
                .graphicsLayer {
                    val progress = dragProgress()
                    val scale = 1f - 0.08f * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - 0.15f * progress
                    shape = RoundedCornerShape(26.dp * progress)
                    clip = true
                }
                .onSizeChanged { pageHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            // 只跟「向下」的拖动；往上不跟随，松手回弹
                            scope.launch {
                                dismissOffset.snapTo((dismissOffset.value + dragAmount).coerceAtLeast(0f))
                            }
                        },
                        onDragEnd = {
                            val flingY = tracker.calculateVelocity().y
                            val wentFar = dismissOffset.value > size.height * 0.26f
                            if (wentFar || flingY > 1_300f) {
                                // 交给导航退出动画把页面带走
                                onBack()
                            } else {
                                scope.launch {
                                    dismissOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.85f,
                                            stiffness = 420f,
                                            visibilityThreshold = 0.5f,
                                        ),
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                dismissOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 520f),
                                )
                            }
                        },
                    )
                },
            containerColor = pageScheme.background,
            // 页面背景 / 渐变要一直画到状态栏与导航栏下面，所以内容不整体避开系统栏，
            // 而是由下面 Column 里的 statusBarsPadding / navigationBarsPadding 精确让位。
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // 1) 底色：封面主色渐变（默认不闪光时看到的就是这层）
                        if (coloredBase) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(bgTop, bgBottom),
                                ),
                                topLeft = Offset.Zero,
                                size = size,
                            )
                        }
                        // 2) 灵魂鼓点闪光：颜色取封面综合主色，四周渐隐到黑（黑色舞台）
                        //    亮度和半径跟着鼓点律动：拍点亮起来、拍间几乎熄下去（过渡柔和，不爆闪）
                        if (glowActive) {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        glowHot.copy(alpha = (0.10f + 0.90f * glowEnergy) * glowStrength),
                                        glowColor.copy(alpha = (0.04f + 0.66f * glowEnergy) * glowStrength),
                                        Color.Transparent,
                                    ),
                                    center = Offset(size.width / 2f, size.height * 0.40f),
                                    radius = size.maxDimension * (0.55f + 0.40f * glowEnergy),
                                ),
                                topLeft = Offset.Zero,
                                size = size,
                            )
                        }
                    }
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
            ) {
                PlayerTopBar(
                    title = current.name,
                    subtitle = current.artistText,
                    onBack = onBack,
                    onSubtitleClick = current.artistList.firstOrNull()?.id?.let { artistId ->
                        { onOpenArtist(artistId) }
                    },
                ) {
                    if (sleepRemaining != null) {
                        Text(
                            text = TimeUtils.formatDuration(sleepRemaining!!),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 2.dp),
                        )
                    }
                    IconButton(onClick = { showPlayerSettings = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = Str.res(R.string.playback_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
    
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = {
                        // 封面 <-> 歌词：小距离滑动 + 淡入淡出，轻一点更细腻
                        val forward = targetState
                        val enter = slideInHorizontally(
                            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                        ) { if (forward) it / 5 else -it / 5 } + fadeIn(animationSpec = tween(durationMillis = 220))
                        val exit = slideOutHorizontally(
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                        ) { if (forward) -it / 5 else it / 5 } + fadeOut(animationSpec = tween(durationMillis = 170))
                        enter togetherWith exit
                    },
                    label = "coverLyrics",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { lyrics ->
                    if (lyrics) {
                        LyricView(
                            lines = lyricLines,
                            currentIndex = lyricIndex,
                            showTranslation = showTranslation,
                            onSeek = { viewModel.seekTo(it) },
                            onBlankTap = { showLyrics = false },
                            onToggleTranslation = viewModel::toggleLyricsTranslation,
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            // 「灵魂鼓点闪光」：四周黑、中间是光源，跟着鼓点呼吸
                            if (beatGlow && glowColor != Color.Unspecified) {
                                Canvas(
                                    modifier = Modifier.size((300f + 200f * glowEnergy).dp),
                                ) {
                                    val glowRadius = size.minDimension / 2f
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                glowHot.copy(alpha = 0.08f + 0.82f * glowEnergy),
                                                glowColor.copy(alpha = 0.04f + 0.46f * glowEnergy),
                                                Color.Transparent,
                                            ),
                                            center = center,
                                            radius = glowRadius,
                                        ),
                                        radius = glowRadius,
                                        center = center,
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .combinedClickable(
                                        onClick = { showLyrics = true },
                                        onLongClick = { showFullCover = true },
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                CoverImage(
                                    url = current.coverUrl,
                                    modifier = Modifier.size(300.dp),
                                    shape = RoundedCornerShape(16.dp),
                                )
                            }
                        }
                    }
                }
    
                // 进度条
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val leftTime = if (dragFraction != null) {
                        ((dragFraction ?: 0f) * duration).toLong()
                    } else {
                        (glideFraction.value * duration).toLong()
                    }
                    Text(
                        text = TimeUtils.formatDuration(leftTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PlayerProgressBar(
                        fraction = dragFraction ?: glideFraction.value,
                        dragging = dragFraction != null,
                        onDrag = { dragFraction = it },
                        onDragFinished = { value ->
                            viewModel.seekToFraction(value)
                            dragFraction = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                    )
                    Text(
                        text = TimeUtils.formatDuration(duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
    
                // 功能按钮（上排：点赞 / 评论 / 音质 / 倍速 / 加队列）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val liked = current.id in likedIds
                    // 点亮爱心：先弹一下再回弹，颜色同步渐变
                    val likeScale = remember { Animatable(1f) }
                    var wasLiked by remember { mutableStateOf(liked) }
                    LaunchedEffect(liked) {
                        if (liked && !wasLiked) {
                            likeScale.animateTo(1.3f, tween(durationMillis = 130))
                            likeScale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 560f))
                        }
                        wasLiked = liked
                    }
                    val likeTint by animateColorAsState(
                        targetValue = if (liked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 240),
                        label = "likeTint",
                    )
                    IconButton(onClick = { viewModel.toggleLike(current.id) }) {
                        Icon(
                            imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = Str.res(R.string.like),
                            tint = likeTint,
                            modifier = Modifier.graphicsLayer {
                                scaleX = likeScale.value
                                scaleY = likeScale.value
                            },
                        )
                    }
                    IconButton(onClick = { onOpenComments(current.id) }) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubbleOutline,
                            contentDescription = Str.res(R.string.comments_2),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 音质/倍速改用图标：俄语、德语等的文字标签过长，会把整行按钮挤出屏幕。
                    // 音质是普通选择项（默认就是极高），不做常驻高亮；倍速非 1x 时才用主题色提示。
                    IconButton(onClick = { showQuality = true }) {
                        Icon(
                            imageVector = Icons.Rounded.HighQuality,
                            contentDescription = Str.res(R.string.audio_quality),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showSpeed = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = Str.res(R.string.playback_speed),
                            tint = if (!kwargsEquals(speed, 1f)) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = { viewModel.addToQueue(current) }) {
                        Icon(
                            imageVector = Icons.Rounded.PlaylistAdd,
                            contentDescription = Str.res(R.string.add_to_queue),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val progress = downloadProgress[current.id]
                    val downloaded = current.id in downloadedIds
                    IconButton(onClick = { viewModel.download(current) }) {
                        when {
                            progress != null -> CircularProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
    
                            downloaded -> Icon(
                                imageVector = Icons.Rounded.DownloadDone,
                                contentDescription = Str.res(R.string.downloaded),
                                tint = MaterialTheme.colorScheme.primary,
                            )
    
                            else -> Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = Str.res(R.string.download),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
    
                // 播放控制（下排：模式 / 上一首 / 播放 / 下一首 / 目录）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 私人FM 是单曲流，没有列表/随机模式：
                    // 用同宽的空白占位，保持上一首/播放/下一首/目录的位置和普通播放完全一致
                    if (!isFmMode) {
                        IconButton(onClick = { viewModel.cyclePlayMode() }) {
                            Icon(
                                imageVector = when (playMode) {
                                    PlayMode.ORDER -> Icons.AutoMirrored.Rounded.TrendingFlat
                                    PlayMode.LOOP_ALL -> Icons.Rounded.Repeat
                                    PlayMode.LOOP_ONE -> Icons.Rounded.RepeatOne
                                    PlayMode.SHUFFLE -> Icons.Rounded.Shuffle
                                },
                                contentDescription = Str.res(R.string.play_mode),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                    IconButton(onClick = { viewModel.previous() }, modifier = Modifier.size(56.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = Str.res(R.string.previous),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    // 按下时按钮会缩一下：反馈更「跟手」
                    val playInteraction = remember { MutableInteractionSource() }
                    val playPressed by playInteraction.collectIsPressedAsState()
                    val playScale by animateFloatAsState(
                        targetValue = if (playPressed) 0.90f else 1f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
                        label = "playScale",
                    )
                    FilledIconButton(
                        onClick = { viewModel.togglePlayPause() },
                        interactionSource = playInteraction,
                        modifier = Modifier
                            .size(68.dp)
                            .graphicsLayer {
                                scaleX = playScale
                                scaleY = playScale
                            },
                    ) {
                        Crossfade(
                            targetState = if (isBuffering) null else isPlaying,
                            animationSpec = tween(durationMillis = 180),
                            label = "playIcon",
                        ) { playing ->
                            if (playing == null) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(
                                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playing) Str.res(R.string.pause) else Str.res(R.string.play),
                                    modifier = Modifier.size(38.dp),
                                )
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.next() }, modifier = Modifier.size(56.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = Str.res(R.string.next),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    IconButton(onClick = { showDirectory = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Toc,
                            contentDescription = Str.res(R.string.queue),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    
        // ---------------- 目录（播放队列 / 歌词翻译 / 播放设置） ----------------
        if (showDirectory) {
            ModalBottomSheet(onDismissRequest = { showDirectory = false }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Text(
                        text = Str.res(R.string.queue),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDirectory = false
                                showQueue = true
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = Str.res(R.string.queue_2),
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .weight(1f),
                        )
                        Text(
                            text = Str.res(R.string.songs_2, queue.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    if (mvId > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDirectory = false
                                    viewModel.pause()
                                    onOpenMv(mvId)
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = Str.res(R.string.watch_mv),
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .weight(1f),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleLyricsTranslation() }
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = null,
                            tint = if (showTranslation) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp),
                        )
                        Column(
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .weight(1f),
                        ) {
                            Text(Str.res(R.string.lyrics_translation))
                            Text(
                                text = if (showTranslation) Str.res(R.string.show_chinese_translation_on_the_lyrics_page) else Str.res(R.string.off),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = showTranslation,
                            onCheckedChange = { viewModel.toggleLyricsTranslation() },
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDirectory = false
                                showPlayerSettings = true
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = Str.res(R.string.playback_settings),
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .weight(1f),
                        )
                        Text(
                            text = Str.res(R.string.sleep_timer_equalizer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDirectory = false
                                if (!shareSong(context, current.id, current.name, current.artistText)) {
                                    viewModel.notify(Str.res(R.string.no_app_available_to_share))
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.IosShare,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = Str.res(R.string.share_song),
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDirectory = false
                                val ok = copySongLink(context, current.id)
                                viewModel.notify(if (ok) Str.res(R.string.link_copied) else Str.res(R.string.copy_failed))
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = Str.res(R.string.copy_link),
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .weight(1f),
                        )
                    }
                }
            }
        }
    
        // ---------------- 播放队列 ----------------
        if (showQueue) {
            ModalBottomSheet(onDismissRequest = { showQueue = false }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = Str.res(R.string.queue_3, queue.size),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.clearQueue(); showQueue = false }) {
                            Text(Str.res(R.string.clear))
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                    ) {
                        itemsIndexed(queue, key = { index, item -> "$index-${item.id}" }) { index, item ->
                            SongRow(
                                song = item,
                                onClick = { viewModel.playAt(index) },
                                index = index,
                                isCurrent = index == currentIndex,
                                trailing = {
                                    IconButton(onClick = { viewModel.removeAt(index) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = Str.res(R.string.remove_from_queue),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    
        // ---------------- 音质选择 ----------------
        if (showQuality) {
            ModalBottomSheet(onDismissRequest = { showQuality = false }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                    Text(
                        text = Str.res(R.string.audio_quality),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    SoundQuality.all.forEach { level ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setQuality(level)
                                    showQuality = false
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
                    Text(
                        text = Str.res(R.string.actual_quality_depends_on_your_account_and_the_r),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
        }
    
        // ---------------- 倍速选择 ----------------
        if (showSpeed) {
            ModalBottomSheet(onDismissRequest = { showSpeed = false }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                    Text(
                        text = Str.res(R.string.playback_speed),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    SPEED_OPTIONS.forEach { option ->
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
                                text = formatSpeed(option),
                                modifier = Modifier.weight(1f),
                            )
                            if (kwargsEquals(option, speed)) {
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
        // ---------------- 定时停止 ----------------
        if (showSleepTimer) {
            ModalBottomSheet(onDismissRequest = { showSleepTimer = false }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                    Text(
                        text = Str.res(R.string.sleep_timer),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    Text(
                        text = if (sleepRemaining != null) {
                            Str.res(R.string.on_left, TimeUtils.formatDuration(sleepRemaining!!))
                        } else {
                            Str.res(R.string.playback_pauses_automatically_when_the_timer_end)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    SLEEP_OPTIONS.forEach { minutes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSleepTimer(minutes)
                                    showSleepTimer = false
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (minutes == null) Str.res(R.string.turn_off_timer) else Str.res(R.string.min, minutes),
                                modifier = Modifier.weight(1f),
                            )
                            if (minutes == null && sleepRemaining == null) {
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

    // ---------------- 播放设置（功能列表） ----------------
    if (showPlayerSettings) {
        ModalBottomSheet(onDismissRequest = { showPlayerSettings = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                Text(
                    text = Str.res(R.string.playback_settings),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showPlayerSettings = false
                            showSleepTimer = true
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = Str.res(R.string.sleep_timer),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = sleepRemaining?.let { TimeUtils.formatDuration(it) } ?: Str.res(R.string.off_2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showPlayerSettings = false
                            onOpenEqualizer()
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = Str.res(R.string.equalizer),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // ---------------- 封面全屏查看（长按封面触发，可保存到相册） ----------------
    if (showFullCover) {
        Dialog(
            onDismissRequest = { showFullCover = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                AsyncImage(
                    model = toHdCover(current.coverUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                )
                IconButton(
                    onClick = { showFullCover = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = Str.res(R.string.close), tint = Color.White)
                }
                Button(
                    onClick = {
                        val url = toHdCover(current.coverUrl)
                        if (url == null || savingCover) return@Button
                        savingCover = true
                        scope.launch {
                            val ok = saveCoverToGallery(context, url)
                            savingCover = false
                            // 用系统 Toast：全屏弹窗是独立窗口，页面里的 Snackbar 会被它盖住看不见
                            Toast.makeText(
                                context,
                                if (ok) Str.res(R.string.saved_to_gallery) else Str.res(R.string.save_failed_check_storage_permission),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp),
                ) {
                    Text(if (savingCover) Str.res(R.string.saving) else Str.res(R.string.save_image))
                }
            }
        }
    }
}

/** 取高清封面（网易云图片 CDN 支持 param 缩放） */
private fun toHdCover(url: String?): String? =
    url?.substringBefore('?')?.takeIf { it.isNotBlank() }?.let { "$it?param=1080y1080" }

private val SLEEP_OPTIONS: List<Int?> = listOf(null, 15, 30, 45, 60, 90)

private val SPEED_OPTIONS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

private fun kwargsEquals(a: Float, b: Float): Boolean = kotlin.math.abs(a - b) < 0.001f

private fun formatSpeed(speed: Float): String {
    val value = when {
        kwargsEquals(speed, speed.toInt().toFloat()) -> String.format(Locale.US, "%.1f", speed)
        else -> String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
    }
    return "${value}x"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onSubtitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = Str.res(R.string.collapse))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .basicMarquee()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(ClipData.newPlainText(title, title))
                                Toast.makeText(
                                    context,
                                    Str.res(R.string.song_name_copied),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    ),
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (onSubtitleClick != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .basicMarquee()
                        .then(
                            if (onSubtitleClick != null) {
                                Modifier.clickable(onClick = onSubtitleClick)
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) { actions() }
    }
}

/** 自绘播放进度条：细圆角轨道 + 小圆点滑块，点击/拖动都能跳转，比默认 Slider 更轻盈 */
@Composable
private fun PlayerProgressBar(
    fraction: Float,
    onDrag: (Float) -> Unit,
    onDragFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
) {
    var widthPx by remember { mutableStateOf(0) }
    var draggingFraction by remember { mutableStateOf<Float?>(null) }
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    // 手指按上去的瞬间，滑块和轨道稍微变粗：反馈更「跟手」
    val thumbRadius by animateFloatAsState(
        targetValue = if (dragging) 7f else 5f,
        animationSpec = tween(durationMillis = 140),
        label = "thumbRadius",
    )
    val trackWidth by animateFloatAsState(
        targetValue = if (dragging) 4f else 3f,
        animationSpec = tween(durationMillis = 140),
        label = "trackWidth",
    )

    Box(
        modifier = modifier
            .height(28.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (widthPx > 0) {
                        val value = (offset.x / widthPx).coerceIn(0f, 1f)
                        onDrag(value)
                        onDragFinished(value)
                    }
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (widthPx > 0) {
                            val value = (offset.x / widthPx).coerceIn(0f, 1f)
                            draggingFraction = value
                            onDrag(value)
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (widthPx > 0) {
                            val value = (change.position.x / widthPx).coerceIn(0f, 1f)
                            draggingFraction = value
                            onDrag(value)
                        }
                    },
                    onDragEnd = {
                        draggingFraction?.let(onDragFinished)
                        draggingFraction = null
                    },
                    onDragCancel = {
                        draggingFraction?.let(onDragFinished)
                        draggingFraction = null
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val strokeWidth = trackWidth.dp.toPx()
            val activeWidth = (size.width * fraction.coerceIn(0f, 1f)).coerceIn(0f, size.width)

            // 未播放部分：细圆角直线轨道
            drawLine(
                color = inactiveColor,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )

            // 已播放部分
            if (activeWidth > 0f) {
                drawLine(
                    color = activeColor,
                    start = Offset(0f, centerY),
                    end = Offset(activeWidth, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            drawCircle(
                color = activeColor,
                radius = thumbRadius.dp.toPx(),
                center = Offset(activeWidth, centerY),
            )
        }
    }
}

/** 歌词视图：当前行高亮居中滚动，点击歌词跳转进度；点空白处 / 点当前行返回封面 */
@Composable
private fun LyricView(
    lines: List<LrcLine>,
    currentIndex: Int,
    showTranslation: Boolean,
    onSeek: (Long) -> Unit,
    onBlankTap: () -> Unit,
    onToggleTranslation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blankTap = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onBlankTap,
    )
    if (lines.isEmpty()) {
        // 没有歌词时同样能点空白回到封面
        Box(
            modifier = modifier.fillMaxSize().then(blankTap),
            contentAlignment = Alignment.Center,
        ) {
            NcEmpty(message = Str.res(R.string.no_lyrics))
        }
        return
    }
    val listState = rememberLazyListState()
    var firstLayout by remember(lines) { mutableStateOf(true) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(blankTap),
    ) {
        // 上下留白 ≈ 半个视口高度：当前行滚到列表可见区首个位置时，正好落在屏幕正中
        val verticalPadding = maxHeight / 2 - 22.dp
        LaunchedEffect(currentIndex, lines, showTranslation) {
            if (currentIndex !in lines.indices) return@LaunchedEffect
            val smooth = !firstLayout
            firstLayout = false
            // 整段定位只做「一次」滚动动画：目标行在屏幕上时直接滚到正中，
            // 不在屏幕上时先滚进来（不再追一次修正动画），避免两段式移动带来的顿挫感
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo.firstOrNull { it.index == currentIndex }
            if (visible == null) {
                if (smooth) {
                    listState.animateScrollToItem(currentIndex)
                } else {
                    listState.scrollToItem(currentIndex)
                }
                return@LaunchedEffect
            }
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            val delta = (visible.offset + visible.size / 2f) - viewportCenter
            if (abs(delta) <= 1f) return@LaunchedEffect
            if (smooth) {
                listState.animateScrollBy(
                    delta,
                    tween(durationMillis = 420, easing = FastOutSlowInEasing),
                )
            } else {
                listState.scrollBy(delta)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = verticalPadding),
        ) {
            itemsIndexed(lines) { index, line ->
                val selected = index == currentIndex
                // 当前行「变大 + 变亮 + 变粗」用一次柔和过渡，切换时不再硬切
                val highlight by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = tween(durationMillis = 240),
                    label = "lyricHighlight",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .graphicsLayer {
                                val scale = 0.97f + 0.03f * highlight
                                scaleX = scale
                                scaleY = scale
                                alpha = 0.70f + 0.30f * highlight
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                // 点当前行（正在播放处）返回封面；点其他行跳转进度
                                if (selected) onBlankTap() else onSeek(line.timeMs)
                            }
                            // 歌词尽量用满屏宽：左右只留一点点呼吸空间，长句不再过早换行
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = line.text,
                            textAlign = TextAlign.Center,
                            style = if (selected) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        val translation = line.translation
                        if (showTranslation && !translation.isNullOrBlank()) {
                            Text(
                                text = translation,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }
        }
        // 歌词页左下角的翻译开关：开启时图标用主题色，关闭时保持低对比度
        IconButton(
            onClick = onToggleTranslation,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 4.dp, bottom = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Translate,
                contentDescription = Str.res(R.string.lyrics_translation),
                tint = if (showTranslation) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
