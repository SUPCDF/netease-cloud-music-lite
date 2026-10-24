package com.ncmusic.feature.mine

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.core.network.model.Playlist
import com.ncmusic.core.network.model.PlaylistBrief
import com.ncmusic.designsystem.component.CoverImage
import com.ncmusic.designsystem.component.PlaylistGridCard
import com.ncmusic.designsystem.component.SectionHeader
import com.ncmusic.designsystem.component.VipBadge
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 我的页：账号资料 / 我喜欢的音乐 / 最近播放入口 / 听歌排行入口 / 我的歌单 / 收藏的歌单 */
@Composable
fun MineScreen(
    onLoginClick: () -> Unit,
    onAccountClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRecentPlayClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onMessagesClick: () -> Unit,
    onFollowsClick: (Long) -> Unit,
    onFollowedsClick: (Long) -> Unit,
    onRankingClick: () -> Unit,
    viewModel: MineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val avatarPicker = rememberLauncherForActivityResult(GridImagePickerContract()) { uri ->
        uri?.let { viewModel.changeAvatar(it) }
    }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.toast) {
        val toast = state.toast
        if (toast != null) {
            snackbarHostState.showSnackbar(toast)
            viewModel.consumeToast()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                MineHeader(
                    state = state,
                    onLoginClick = onLoginClick,
                    onSettingsClick = onSettingsClick,
                    onAvatarClick = {
                        if (state.loggedIn && !state.uploadingAvatar) avatarPicker.launch(Unit)
                    },
                )
            }

            if (state.loggedIn) {
                item {
                    MineEntryRow(
                        title = Str.res(R.string.account_info),
                        onClick = onAccountClick,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ManageAccounts,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                val likedId = state.likedPlaylistId
                if (likedId != null) {
                    item {
                        MineEntryRow(
                            title = Str.res(R.string.liked_songs),
                            onClick = { onPlaylistClick(likedId) },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                item {
                    MineEntryRow(
                        title = Str.res(R.string.recently_played),
                        onClick = onRecentPlayClick,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                item {
                    MineEntryRow(
                        title = Str.res(R.string.downloads),
                        onClick = onDownloadsClick,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                item {
                    MineEntryRow(
                        title = Str.res(R.string.messages),
                        onClick = onMessagesClick,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Email,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                item {
                    MineEntryRow(
                        title = Str.res(R.string.following),
                        onClick = { state.profile?.userId?.let(onFollowsClick) },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                item {
                    MineEntryRow(
                        title = Str.res(R.string.followers),
                        onClick = { state.profile?.userId?.let(onFollowedsClick) },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                item {
                    MineEntryRow(
                        title = Str.res(R.string.listening_stats),
                        onClick = onRankingClick,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Equalizer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            if (state.createdPlaylists.isNotEmpty()) {
                item { SectionHeader(Str.res(R.string.my_playlists)) }
                items(state.createdPlaylists.chunked(3)) { row ->
                    PlaylistGridRow(row = row, onPlaylistClick = onPlaylistClick)
                }
            }

            if (state.subscribedPlaylists.isNotEmpty()) {
                item { SectionHeader(Str.res(R.string.saved_playlists)) }
                items(state.subscribedPlaylists.chunked(3)) { row ->
                    PlaylistGridRow(row = row, onPlaylistClick = onPlaylistClick)
                }
            }

            if (state.loggedIn) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextButton(onClick = { viewModel.logout() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(Str.res(R.string.log_out), modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** 功能入口行（图标 + 标题 + 右箭头） */
@Composable
private fun MineEntryRow(
    title: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaylistGridRow(row: List<Playlist>, onPlaylistClick: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        row.forEach { playlist ->
            PlaylistGridCard(
                playlist = playlist.toBrief(),
                onClick = { onPlaylistClick(playlist.id) },
                modifier = Modifier.weight(1f),
            )
        }
        repeat(3 - row.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MineHeader(
    state: MineUiState,
    onLoginClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAvatarClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clickable(
                        enabled = state.loggedIn && !state.uploadingAvatar,
                        onClickLabel = if (state.loggedIn) {
                            Str.res(R.string.change_avatar)
                        } else {
                            null
                        },
                        onClick = onAvatarClick,
                    ),
            ) {
                CoverImage(
                    url = state.profile?.avatarUrl,
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                )
                if (state.uploadingAvatar) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .semantics {
                                contentDescription = Str.res(R.string.uploading_avatar)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.profile?.nickname
                            ?: if (state.loggedIn) {
                                Str.res(R.string.logged_in)
                            } else {
                                Str.res(R.string.not_logged_in)
                            },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    VipBadge(
                        vipType = state.profile?.vipType,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Text(
                    text = state.profile?.signature.orEmpty().ifBlank {
                        if (state.loggedIn) Str.res(R.string.nothing_here_yet) else Str.res(R.string.log_in_to_sync_playlists_and_history)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val profile = state.profile
                if (state.loggedIn && profile != null) {
                    Text(
                        text = Str.res(R.string.following_followers_lv, profile.follows ?: 0, profile.followeds ?: 0, profile.level ?: 0),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Rounded.Settings, contentDescription = Str.res(R.string.settings))
            }
        }

        if (!state.loggedIn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                FilledTonalButton(
                    onClick = onLoginClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(Str.res(R.string.log_in_sign_up))
                }
            }
            Text(
                text = Str.res(R.string.log_in_for_daily_recommendations_personal_fm_you),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Playlist -> PlaylistBrief（网格卡片复用） */
private fun Playlist.toBrief(): PlaylistBrief = PlaylistBrief(
    id = id,
    name = name,
    coverImgUrl = coverImgUrl,
    playCount = playCount,
    trackCount = trackCount,
    description = description,
    userId = userId,
    creator = creator,
    subscribed = subscribed,
    specialType = specialType,
)
