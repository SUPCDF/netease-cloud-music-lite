package com.ncmusic.feature.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.core.common.util.TimeUtils
import com.ncmusic.core.network.model.Song
import com.ncmusic.designsystem.component.CoverImage
import com.ncmusic.designsystem.component.NcError
import com.ncmusic.designsystem.component.NcLoading
import com.ncmusic.designsystem.component.NcTopBar
import com.ncmusic.designsystem.component.SongRow
import com.ncmusic.designsystem.util.copySongLink
import com.ncmusic.designsystem.util.shareSong
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 歌单详情页：播放全部 / 收藏 / 喜欢 / 加歌删歌 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var menuSongId by remember { mutableStateOf<Long?>(null) }
    var sheetSong by remember { mutableStateOf<Song?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    LaunchedEffect(playlistId) { viewModel.load(playlistId) }
    LaunchedEffect(state.toast) {
        val toast = state.toast
        if (toast != null) {
            snackbarHostState.showSnackbar(toast)
            viewModel.consumeToast()
        }
    }

    Scaffold(
        topBar = { NcTopBar(title = state.playlist?.name ?: Str.res(R.string.playlist), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> NcLoading()

                state.error != null -> NcError(
                    message = state.error ?: Str.res(R.string.failed_to_load_playlist),
                    onRetry = { viewModel.load(playlistId, force = true) },
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (state.offline) {
                        item {
                            Text(
                                text = Str.res(R.string.offline_cache_banner),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    item {
                        PlaylistHeader(
                            state = state,
                            onPlayAll = { viewModel.playAll() },
                            onToggleSubscribe = { viewModel.toggleSubscribe() },
                        )
                    }
                    itemsIndexed(state.songs, key = { index, song -> "$index-${song.id}" }) { index, song ->
                        SongRow(
                            song = song,
                            onClick = { viewModel.playSong(song) },
                            index = index,
                            isCurrent = song.id == state.currentSongId,
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val liked = song.id in state.likedIds
                                    IconButton(onClick = { viewModel.toggleLike(song.id) }) {
                                        Icon(
                                            imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
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
                                                leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, contentDescription = null) },
                                                onClick = {
                                                    menuSongId = null
                                                    viewModel.playNext(song)
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(Str.res(R.string.add_to_queue)) },
                                                leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                                                onClick = {
                                                    menuSongId = null
                                                    viewModel.addToQueue(song)
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(Str.res(R.string.add_to_playlist)) },
                                                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                                onClick = {
                                                    menuSongId = null
                                                    sheetSong = song
                                                    viewModel.loadMyPlaylists()
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(Str.res(R.string.share_song)) },
                                                leadingIcon = { Icon(Icons.Rounded.IosShare, contentDescription = null) },
                                                onClick = {
                                                    menuSongId = null
                                                    if (!shareSong(context, song.id, song.name, song.artistText)) {
                                                        viewModel.notify(Str.res(R.string.no_app_available_to_share))
                                                    }
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(Str.res(R.string.copy_link)) },
                                                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                                                onClick = {
                                                    menuSongId = null
                                                    val ok = copySongLink(context, song.id)
                                                    viewModel.notify(if (ok) Str.res(R.string.link_copied) else Str.res(R.string.copy_failed))
                                                },
                                            )
                                            if (state.myUid != null && state.playlist?.userId == state.myUid) {
                                                DropdownMenuItem(
                                                    text = { Text(Str.res(R.string.remove_from_playlist)) },
                                                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                                    onClick = {
                                                        menuSongId = null
                                                        viewModel.removeTrack(song.id)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    // ---------------- 添加到歌单 ----------------
    val target = sheetSong
    if (target != null) {
        ModalBottomSheet(onDismissRequest = { sheetSong = null }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(
                    text = Str.res(R.string.add_to_playlist_2, target.name),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            newPlaylistName = ""
                            showCreateDialog = true
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(Str.res(R.string.new_playlist), modifier = Modifier.padding(start = 12.dp))
                }
                if (state.loadingMyPlaylists) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                state.myPlaylists.forEach { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.addToPlaylist(playlist.id, target.id)
                                sheetSong = null
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverImage(
                            url = playlist.coverImgUrl,
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(6.dp),
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = Str.res(R.string.tracks, playlist.trackCount ?: 0),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    // ---------------- 新建歌单 ----------------
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(Str.res(R.string.new_playlist)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(Str.res(R.string.playlist_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val song = sheetSong
                        if (song != null) {
                            viewModel.createPlaylistAndAdd(newPlaylistName, song.id)
                        }
                        showCreateDialog = false
                        sheetSong = null
                    },
                ) {
                    Text(Str.res(R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(Str.res(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaylistHeader(
    state: PlaylistDetailUiState,
    onPlayAll: () -> Unit,
    onToggleSubscribe: () -> Unit,
) {
    val playlist = state.playlist ?: return
    var descExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            CoverImage(
                url = playlist.coverImgUrl,
                modifier = Modifier.size(110.dp),
                shape = RoundedCornerShape(12.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = playlist.creator?.nickname.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = Str.res(
                        R.string.playlist_stats,
                        TimeUtils.formatCount(playlist.playCount ?: 0),
                        playlist.trackCount ?: 0,
                        TimeUtils.formatCount(playlist.subscribedCount ?: 0),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilledTonalButton(onClick = onPlayAll) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(Str.res(R.string.play_all), modifier = Modifier.padding(start = 4.dp))
                    }
                    TextButton(onClick = onToggleSubscribe) {
                        val subscribed = playlist.subscribed == true
                        Icon(
                            imageVector = if (subscribed) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (subscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (subscribed) Str.res(R.string.favorited) else Str.res(R.string.favorite),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }

        val description = playlist.description
        if (!description.isNullOrBlank()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (descExpanded) 100 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description.length > 60) {
                    TextButton(onClick = { descExpanded = !descExpanded }) {
                        Text(if (descExpanded) Str.res(R.string.collapse) else Str.res(R.string.expand))
                    }
                }
            }
        }

        val tags = playlist.tags
        if (!tags.isNullOrEmpty()) {
            Text(
                text = tags.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        Text(
            text = Str.res(R.string.songs_3, state.songs.size),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
