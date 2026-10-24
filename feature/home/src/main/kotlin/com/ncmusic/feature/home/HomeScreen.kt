package com.ncmusic.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.core.network.model.PlaylistBrief
import com.ncmusic.designsystem.component.CoverImage
import com.ncmusic.designsystem.component.NcError
import com.ncmusic.designsystem.component.NcLoading
import com.ncmusic.designsystem.component.PlaylistGridCard
import com.ncmusic.designsystem.component.PlaylistRowItem
import com.ncmusic.designsystem.component.SectionHeader
import com.ncmusic.designsystem.component.SongRow
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 首页（发现页）：每日推荐 / 私人FM 入口 + 推荐歌单 + 排行榜 + 新歌 + 歌单广场（支持下拉刷新） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlaylistClick: (Long) -> Unit,
    onSearchClick: () -> Unit,
    onDailyClick: () -> Unit,
    onNeedLogin: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
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

    // 状态栏透明后页面会绘制到状态栏下方，这里主动避开状态栏（背景仍由根 Scaffold 铺满）
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        when {
            state.loading -> NcLoading()

            state.error != null -> NcError(
                message = state.error ?: Str.res(R.string.failed_to_load),
                onRetry = { viewModel.load() },
            )

            else -> PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    item { HomeHeader(onSearchClick = onSearchClick) }
                    item {
                        QuickEntries(
                            onDaily = onDailyClick,
                            onFm = { viewModel.playPersonalFm(onNeedLogin) },
                        )
                    }

                    if (state.personalized.isNotEmpty()) {
                        item { SectionHeader(Str.res(R.string.recommended_playlists)) }
                        items(state.personalized.chunked(3)) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                row.forEach { playlist ->
                                    PlaylistGridCard(
                                        playlist = playlist,
                                        onClick = { onPlaylistClick(playlist.id) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(3 - row.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    if (state.toplists.isNotEmpty()) {
                        item { SectionHeader(Str.res(R.string.charts)) }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.toplists.take(8)) { toplist ->
                                    ToplistCard(
                                        toplist = toplist,
                                        onClick = { onPlaylistClick(toplist.id) },
                                    )
                                }
                            }
                        }
                    }

                    if (state.newSongs.isNotEmpty()) {
                        item { SectionHeader(Str.res(R.string.new_songs)) }
                        items(state.newSongs.take(8)) { song ->
                            SongRow(
                                song = song,
                                onClick = { viewModel.playNewSong(song) },
                            )
                        }
                    }

                    item { SectionHeader(Str.res(R.string.playlist_square)) }
                    item {
                        CategoryChips(
                            categories = state.categories.take(16),
                            selected = state.selectedCategory,
                            onSelect = { viewModel.selectCategory(it) },
                        )
                    }
                    if (state.categoryLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    items(state.categoryPlaylists) { playlist ->
                        PlaylistRowItem(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist.id) },
                        )
                    }
                    if (state.categoryMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                TextButton(onClick = { viewModel.loadCategory(loadMore = true) }) {
                                    Text(if (state.loadingMore) Str.res(R.string.loading) else Str.res(R.string.load_more))
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun HomeHeader(onSearchClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = Str.res(R.string.discover_music),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSearchClick) {
            Icon(Icons.Rounded.Search, contentDescription = Str.res(R.string.search))
        }
    }
}

@Composable
private fun QuickEntries(onDaily: () -> Unit, onFm: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickEntryCard(
            title = Str.res(R.string.daily_recommendations),
            subtitle = Str.res(R.string.updated_daily_for_your_taste),
            icon = Icons.Rounded.DateRange,
            onClick = onDaily,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        QuickEntryCard(
            title = Str.res(R.string.personal_fm),
            subtitle = Str.res(R.string.endless_shuffle),
            icon = Icons.Rounded.Radio,
            onClick = onFm,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun QuickEntryCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ToplistCard(toplist: PlaylistBrief, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        CoverImage(
            url = toplist.cover,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
        )
        Text(
            text = toplist.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = toplist.updateFrequency.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun CategoryChips(
    categories: List<com.ncmusic.core.network.model.CatItem>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories) { category ->
            FilterChip(
                selected = category.name == selected,
                onClick = { onSelect(category.name) },
                label = { Text(category.name) },
            )
        }
    }
}
