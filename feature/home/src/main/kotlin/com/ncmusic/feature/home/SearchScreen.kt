package com.ncmusic.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.core.network.model.SearchAlbum
import com.ncmusic.core.network.model.SearchArtist
import com.ncmusic.core.network.model.UserBrief
import com.ncmusic.designsystem.component.AvatarImage
import com.ncmusic.designsystem.component.CoverImage
import com.ncmusic.designsystem.component.NcEmpty
import com.ncmusic.designsystem.component.NcError
import com.ncmusic.designsystem.component.NcLoading
import com.ncmusic.designsystem.component.PlaylistRowItem
import com.ncmusic.designsystem.component.SectionHeader
import com.ncmusic.designsystem.component.SongRow
import com.ncmusic.designsystem.component.VipBadge
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 搜索页：关键词建议 + 分类结果（单曲/歌手/专辑/歌单/用户）+ 历史记录 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onUserClick: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // 顶部搜索栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = Str.res(R.string.back))
            }
            OutlinedTextField(
                value = state.keyword,
                onValueChange = { viewModel.updateKeyword(it) },
                placeholder = { Text(Str.res(R.string.search_songs_artists_playlists), style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.keyword.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearKeyword() }) {
                            Icon(Icons.Rounded.Close, contentDescription = Str.res(R.string.clear))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.submit() }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { viewModel.submit() }) { Text(Str.res(R.string.search)) }
        }

        if (!state.submitted) {
            // 搜索历史 + 输入建议
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (state.keyword.isBlank() && state.history.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader(title = Str.res(R.string.search_history), modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearHistory() }) { Text(Str.res(R.string.clear)) }
                        Spacer(Modifier.size(8.dp))
                    }
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        state.history.forEach { keyword ->
                            InputChip(
                                selected = false,
                                onClick = { viewModel.submit(keyword) },
                                label = { Text(keyword, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = Str.res(R.string.delete),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { viewModel.removeHistory(keyword) },
                                    )
                                },
                            )
                        }
                    }
                }
                if (state.suggestions.isNotEmpty()) {
                    SectionHeader(title = Str.res(R.string.suggestions))
                    state.suggestions.forEach { song ->
                        SongRow(
                            song = song,
                            onClick = {
                                viewModel.updateKeyword(song.name)
                                viewModel.submit(song.name)
                            },
                        )
                    }
                }
            }
            return
        }

        // 结果分类 tabs：俄语/法语等标签较长，固定等分会被裁切，改为可横向滚动
        ScrollableTabRow(
            selectedTabIndex = state.tabIndex,
            edgePadding = 0.dp,
        ) {
            SEARCH_TABS.forEachIndexed { index, tab ->
                Tab(
                    selected = state.tabIndex == index,
                    onClick = { viewModel.selectTab(index) },
                    text = { Text(Str.res(tab.titleRes), maxLines = 1) },
                )
            }
        }

        when {
            state.loading -> NcLoading()

            state.error != null -> NcError(
                message = state.error ?: Str.res(R.string.search_failed),
                onRetry = { viewModel.submit() },
            )

            else -> when (state.tabIndex) {
                0 -> SongResults(state, viewModel)
                1 -> ArtistResults(state, onArtistClick)
                2 -> AlbumResults(state, onAlbumClick)
                3 -> PlaylistResults(state, onPlaylistClick)
                else -> UserResults(state, onUserClick)
            }
        }
    }
}

@Composable
private fun SongResults(state: SearchUiState, viewModel: SearchViewModel) {
    if (state.songs.isEmpty()) {
        NcEmpty(Str.res(R.string.no_matching_songs))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(state.songs) { index, song ->
            SongRow(
                song = song,
                onClick = { viewModel.playSong(song) },
                index = index,
                isCurrent = song.id == state.playingSongId,
                trailing = {
                    CoverImage(
                        url = song.coverUrl,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(40.dp),
                        shape = RoundedCornerShape(6.dp),
                    )
                },
            )
        }
        if (state.more) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = { viewModel.loadMore() }) {
                        Text(if (state.loadingMore) Str.res(R.string.loading) else Str.res(R.string.load_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistResults(state: SearchUiState, onArtistClick: (Long) -> Unit) {
    if (state.artists.isEmpty()) {
        NcEmpty(Str.res(R.string.no_matching_artists))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.artists, key = { it.id }) { artist ->
            ArtistRow(artist, onClick = { onArtistClick(artist.id) })
        }
    }
}

@Composable
private fun ArtistRow(artist: SearchArtist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = artist.picUrl,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = Str.res(R.string.albums_tracks, artist.albumSize ?: 0, artist.musicSize ?: 0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlbumResults(state: SearchUiState, onAlbumClick: (Long) -> Unit) {
    if (state.albums.isEmpty()) {
        NcEmpty(Str.res(R.string.no_matching_albums))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.albums, key = { it.id }) { album ->
            AlbumRow(album, onClick = { onAlbumClick(album.id) })
        }
    }
}

@Composable
private fun AlbumRow(album: SearchAlbum, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(url = album.picUrl, modifier = Modifier.size(48.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = album.artist?.name.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaylistResults(state: SearchUiState, onPlaylistClick: (Long) -> Unit) {
    if (state.playlists.isEmpty()) {
        NcEmpty(Str.res(R.string.no_matching_playlists))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.playlists, key = { it.id }) { playlist ->
            PlaylistRowItem(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist.id) },
            )
        }
    }
}

@Composable
private fun UserResults(state: SearchUiState, onUserClick: (Long) -> Unit) {
    if (state.users.isEmpty()) {
        NcEmpty(Str.res(R.string.no_matching_users))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.users, key = { it.userId }) { user ->
            UserRow(user, onClick = { onUserClick(user.userId) })
        }
    }
}

@Composable
private fun UserRow(user: UserBrief, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarImage(
            url = user.avatarUrl,
            modifier = Modifier.size(48.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.nickname,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                VipBadge(vipType = user.vipType, modifier = Modifier.padding(start = 6.dp))
            }
            val signature = user.signature
            if (!signature.isNullOrBlank()) {
                Text(
                    text = signature,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
