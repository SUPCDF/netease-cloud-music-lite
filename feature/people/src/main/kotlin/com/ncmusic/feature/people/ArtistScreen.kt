package com.ncmusic.feature.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.core.network.model.Album
import com.ncmusic.core.network.model.Artist
import com.ncmusic.designsystem.component.CoverImage
import com.ncmusic.designsystem.component.NcError
import com.ncmusic.designsystem.component.NcLoading
import com.ncmusic.designsystem.component.NcTopBar
import com.ncmusic.designsystem.component.SectionHeader
import com.ncmusic.designsystem.component.SongRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 歌手页：资料 / 热门歌曲 / 专辑 */
@Composable
fun ArtistScreen(
    onBack: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()

    Scaffold(topBar = { NcTopBar(title = state.artist?.name ?: Str.res(R.string.artist), onBack = onBack) }) { padding ->
        when {
            state.loading -> NcLoading(Modifier.padding(padding))
            state.error != null -> NcError(
                message = state.error.orEmpty(),
                modifier = Modifier.padding(padding),
                onRetry = viewModel::load,
            )

            else -> {
                val artist = state.artist ?: return@Scaffold
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    item { ArtistHeader(artist) }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = Str.res(R.string.popular_songs),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.hotSongs.isNotEmpty()) {
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
                        items = state.hotSongs,
                        key = { _, song -> "hot-${song.id}" },
                    ) { index, song ->
                        SongRow(
                            song = song,
                            index = index,
                            isCurrent = song.id == currentSong?.id,
                            subtitle = song.albumName.takeIf { it.isNotBlank() } ?: song.artistText,
                            onClick = { viewModel.playSong(song) },
                        )
                    }

                    if (state.hotSongs.isEmpty()) {
                        item {
                            Text(
                                text = Str.res(R.string.no_popular_songs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }

                    item { SectionHeader(Str.res(R.string.albums)) }

                    items(state.albums, key = { "album-${it.id}" }) { album ->
                        AlbumRow(album, onClick = { onAlbumClick(album.id) })
                    }

                    if (state.albumsLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp))
                            }
                        }
                    } else if (state.albumsMore && state.albums.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.loadAlbums() }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = Str.res(R.string.load_more_albums),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    if (!state.albumsLoading && state.albums.isEmpty()) {
                        item {
                            Text(
                                text = Str.res(R.string.no_albums),
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
private fun ArtistHeader(artist: Artist) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = artist.picUrl ?: artist.img1v1Url,
            modifier = Modifier.size(88.dp),
            shape = CircleShape,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val alias = artist.alias.orEmpty().filter { it.isNotBlank() }
            if (alias.isNotEmpty()) {
                Text(
                    text = alias.joinToString(" / "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = Str.res(R.string.albums_tracks, artist.albumSize ?: 0, artist.musicSize ?: 0),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            val desc = artist.briefDesc.orEmpty().trim()
            if (desc.isNotEmpty()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun AlbumRow(album: Album, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = album.picUrl,
            modifier = Modifier.size(56.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(Str.res(R.string.tracks, album.size ?: 0))
                    album.publishTime?.let { time ->
                        append(" · ")
                        append(formatYear(time))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private fun formatYear(timestampMs: Long): String =
    SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(timestampMs))
