package com.ncmusic.feature.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.core.common.model.SoundQuality
import com.ncmusic.core.data.download.DownloadedSong
import com.ncmusic.designsystem.component.CoverImage
import com.ncmusic.designsystem.component.NcEmpty
import com.ncmusic.designsystem.component.NcTopBar
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 我的下载页：本地离线歌曲，点击即播放本地文件 */
@Composable
fun DownloadScreen(
    onBack: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.toast) {
        val toast = state.toast
        if (toast != null) {
            snackbarHostState.showSnackbar(toast)
            viewModel.consumeToast()
        }
    }

    Scaffold(
        topBar = { NcTopBar(title = Str.res(R.string.downloads), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.songs.isEmpty() && !state.loading) {
            NcEmpty(Str.res(R.string.no_downloads))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                itemsIndexed(
                    state.songs,
                    key = { _, item -> "download-${item.songId}" },
                ) { index, item ->
                    DownloadedSongRow(
                        item = item,
                        isCurrent = item.songId == state.currentSongId,
                        onClick = { viewModel.play(index) },
                        onDelete = { viewModel.delete(item.songId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedSongRow(
    item: DownloadedSong,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = item.coverUrl,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(10.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${item.artist} · ${SoundQuality.displayName(item.quality)} · ${formatSize(item.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = Str.res(R.string.delete_download),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> Str.res(R.string.unknown_size)
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}
