package com.ncmusic.feature.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.core.network.model.Album
import com.ncmusic.core.network.model.Song
import com.ncmusic.designsystem.component.CoverImage
import com.ncmusic.designsystem.component.NcError
import com.ncmusic.designsystem.component.NcLoading
import com.ncmusic.designsystem.component.NcTopBar
import com.ncmusic.designsystem.component.SongRow
import com.ncmusic.designsystem.util.copySongLink
import com.ncmusic.designsystem.util.shareSong
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 专辑详情页：专辑资料 + 曲目列表（播放全部 / 喜欢 / 分享） */
@Composable
fun AlbumScreen(
    onBack: () -> Unit,
    onOpenArtist: (Long) -> Unit,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var menuSongId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(state.toast) {
        val toast = state.toast
        if (toast != null) {
            snackbarHostState.showSnackbar(toast)
            viewModel.consumeToast()
        }
    }

    Scaffold(
        topBar = { NcTopBar(title = state.album?.name ?: Str.res(R.string.album), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> NcLoading(Modifier.padding(padding))

            state.error != null -> NcError(
                message = state.error.orEmpty(),
                modifier = Modifier.padding(padding),
                onRetry = viewModel::load,
            )

            else -> {
                val album = state.album ?: return@Scaffold
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    item { AlbumHeader(album = album, onOpenArtist = onOpenArtist) }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = Str.res(R.string.tracks_2),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.songs.isNotEmpty()) {
                                TextButton(onClick = viewModel::playAll) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(Str.res(R.string.play_all), modifier = Modifier.padding(start = 2.dp))
                                }
                            }
                        }
                    }

                    itemsIndexed(
                        items = state.songs,
                        key = { _, song -> "album-${song.id}" },
                    ) { index, song ->
                        SongRow(
                            song = song,
                            index = index,
                            isCurrent = song.id == currentSong?.id,
                            onClick = { viewModel.playSong(song) },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val liked = song.id in state.likedIds
                                    IconButton(onClick = { viewModel.toggleLike(song.id) }) {
                                        Icon(
                                            imageVector = if (liked) {
                                                Icons.Rounded.Favorite
                                            } else {
                                                Icons.Rounded.FavoriteBorder
                                            },
                                            contentDescription = Str.res(R.string.like),
                                            tint = if (liked) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { menuSongId = song.id }) {
                                            Icon(
                                                imageVector = Icons.Rounded.MoreVert,
                                                contentDescription = Str.res(R.string.more),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = menuSongId == song.id,
                                            onDismissRequest = { menuSongId = null },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(Str.res(R.string.play_next)) },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.PlaylistPlay, contentDescription = null)
                                                },
                                                onClick = {
                                                    menuSongId = null
                                                    viewModel.playNext(song)
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(Str.res(R.string.add_to_queue)) },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                                                },
                                                onClick = {
                                                    menuSongId = null
                                                    viewModel.addToQueue(song)
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(Str.res(R.string.share_song)) },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.IosShare, contentDescription = null)
                                                },
                                                onClick = {
                                                    menuSongId = null
                                                    shareSong(context, song.id, song.name, song.artistText)
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(Str.res(R.string.copy_link)) },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.Link, contentDescription = null)
                                                },
                                                onClick = {
                                                    menuSongId = null
                                                    val ok = copySongLink(context, song.id)
                                                    viewModel.notify(if (ok) Str.res(R.string.link_copied) else Str.res(R.string.copy_failed))
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                        )
                    }

                    if (state.songs.isEmpty()) {
                        item {
                            Text(
                                text = Str.res(R.string.no_tracks),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(album: Album, onOpenArtist: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        CoverImage(
            url = album.picUrl,
            modifier = Modifier.size(110.dp),
            shape = RoundedCornerShape(12.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val artist = album.artist
            if (artist != null && artist.name.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenArtist(artist.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 3.dp),
                    )
                }
            }
            Text(
                text = buildString {
                    append(Str.res(R.string.tracks, album.size ?: 0))
                    album.publishTime?.takeIf { it > 0 }?.let { time ->
                        append(" · ")
                        append(formatYear(time))
                    }
                    val company = album.company.orEmpty().trim()
                    if (company.isNotEmpty()) {
                        append(" · ")
                        append(company)
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun formatYear(timestampMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestampMs))
