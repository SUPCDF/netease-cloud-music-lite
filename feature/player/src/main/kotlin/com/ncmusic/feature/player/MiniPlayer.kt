package com.ncmusic.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.designsystem.component.CoverImage
import kotlin.math.abs
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/**
 * 迷你播放条（底部导航上方）。
 * 没有正在播放的歌曲时自动不渲染。
 */
@Composable
fun MiniPlayer(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val song by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by viewModel.isBuffering.collectAsStateWithLifecycle()
    val position by viewModel.positionMs.collectAsStateWithLifecycle()
    val duration by viewModel.durationMs.collectAsStateWithLifecycle()

    val current = song ?: return
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    // 进度 500ms 才推一次：补一层线性滑行，小横条连续走、不会一跳一跳
    val glideFraction = remember { Animatable(fraction) }
    LaunchedEffect(fraction, duration) {
        if (abs(fraction - glideFraction.value) > 0.03f) {
            glideFraction.snapTo(fraction)
        } else {
            glideFraction.animateTo(
                targetValue = fraction,
                animationSpec = tween(durationMillis = 540, easing = LinearEasing),
            )
        }
    }
    // 播放 / 暂停：图标弹一下
    val playPop = remember { Animatable(1f) }
    var firstPlay by remember { mutableStateOf(true) }
    LaunchedEffect(isPlaying) {
        if (firstPlay) {
            firstPlay = false
        } else {
            playPop.animateTo(0.82f, tween(durationMillis = 110))
            playPop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 640f))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(
                url = current.coverUrl,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    text = current.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
                Text(
                    text = current.artistText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { viewModel.previous() }) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = Str.res(R.string.previous),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = { viewModel.togglePlayPause() }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) Str.res(R.string.pause) else Str.res(R.string.play),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.graphicsLayer {
                        scaleX = playPop.value
                        scaleY = playPop.value
                    },
                )
            }
            IconButton(onClick = { viewModel.next() }) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = Str.res(R.string.next),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        LinearProgressIndicator(
            progress = { if (isBuffering && fraction == 0f) 0f else glideFraction.value },
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
