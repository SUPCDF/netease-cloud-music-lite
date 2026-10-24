package com.ncmusic.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ncmusic.core.common.util.TimeUtils
import com.ncmusic.core.network.model.PlaylistBrief
import com.ncmusic.core.network.model.Song
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 封面图（带占位底色，失败/空链接显示音符图标；换图时淡入淡出，不会硬切） */
@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val request = remember(url, context) {
        if (url.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(220)
                .build()
        }
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** 区域标题（可带右侧操作） */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionText != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(actionText, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** 出现时淡入（约 240ms）：状态切换不会「啪」地跳出来 */
@Composable
private fun rememberAppearAlpha(): Float {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(durationMillis = 240)) }
    return alpha.value
}

/** 加载中占位 */
@Composable
fun NcLoading(modifier: Modifier = Modifier) {
    val alpha = rememberAppearAlpha()
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** 错误占位（可重试） */
@Composable
fun NcError(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val alpha = rememberAppearAlpha()
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onRetry) { Text(Str.res(R.string.retry)) }
            }
        }
    }
}

/** 空状态占位 */
@Composable
fun NcEmpty(message: String = Str.res(R.string.no_data), modifier: Modifier = Modifier) {
    val alpha = rememberAppearAlpha()
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 通用标题栏 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NcTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = Str.res(R.string.back))
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/** 歌单网格卡片（推荐歌单 / 歌单广场） */
@Composable
fun PlaylistGridCard(
    playlist: PlaylistBrief,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 按下时整张卡片缩一下：手指还没抬起就有反馈，更「跟手」
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "cardPress",
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
    ) {
        Box {
            CoverImage(
                url = playlist.cover,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = TimeUtils.formatCount(playlist.playCount ?: 0),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
    }
}

/** 歌单横排条目（排行榜 / 歌单列表） */
@Composable
fun PlaylistRowItem(
    playlist: PlaylistBrief,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = playlist.cover,
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(8.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle ?: playlist.creator?.nickname.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if ((playlist.trackCount ?: 0) > 0) {
            Text(
                text = Str.res(R.string.tracks, playlist.trackCount ?: 0),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 歌曲行（列表统一使用）。
 *
 * @param index 显示序号（歌单/排行榜场景）；为 null 时不显示序号列
 * @param isCurrent 是否正在播放（显示频谱图标 + 主色标题）
 * @param trailing 右侧自定义内容（默认显示时长）
 */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
    isCurrent: Boolean = false,
    subtitle: String? = null,
    onClickMore: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = if (onClickMore != null) 4.dp else 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isCurrent) {
                    // 「正在播放」图标轻轻呼吸
                    val pulse = rememberInfiniteTransition(label = "playingPulse")
                    val eqAlpha by pulse.animateFloat(
                        initialValue = 0.55f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 900),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "eqAlpha",
                    )
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = Str.res(R.string.now_playing),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = eqAlpha),
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle ?: song.artistText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Text(
                text = TimeUtils.formatDuration(song.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

/** 横向滚动歌单卡片列表的通用间距 */
val GridSpacing = PaddingValues(horizontal = 16.dp)

/** 空占位：3 列歌单网格（推荐歌单） */
val PlaylistGridArrangement = Arrangement.spacedBy(10.dp)

/**
 * 会员徽章（黑胶VIP / 黑胶SVIP）。
 *
 * 网易云 vipType 编码：10/11 = 黑胶VIP；100/110 = 黑胶VIP（+音乐包）；200/210 = 黑胶SVIP。
 */
@Composable
fun VipBadge(vipType: Int?, modifier: Modifier = Modifier) {
    val label = when {
        vipType == null -> return
        vipType / 100 == 2 -> "SVIP"
        vipType / 100 == 1 || vipType >= 10 -> "VIP"
        else -> return
    }
    val gradient = if (label == "SVIP") {
        listOf(Color(0xFFF7D794), Color(0xFFE8B44C), Color(0xFFD1922C))
    } else {
        listOf(Color(0xFFD8C08A), Color(0xFFC5A55E), Color(0xFFA8843C))
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            brush = Brush.linearGradient(gradient),
        ),
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF232326))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
