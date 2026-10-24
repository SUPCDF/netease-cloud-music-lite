package com.ncmusic.feature.people

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.core.network.model.Playlist
import com.ncmusic.core.network.model.PlaylistBrief
import com.ncmusic.core.network.model.Profile
import com.ncmusic.designsystem.component.AvatarImage
import com.ncmusic.designsystem.component.NcError
import com.ncmusic.designsystem.component.NcLoading
import com.ncmusic.designsystem.component.NcTopBar
import com.ncmusic.designsystem.component.PlaylistRowItem
import com.ncmusic.designsystem.component.SectionHeader
import com.ncmusic.designsystem.component.VipBadge
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 用户主页：资料卡片 + 私信入口 + TA 的歌单 */
@Composable
fun UserProfileScreen(
    onBack: () -> Unit,
    onChatClick: (Long) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onOpenUserList: (String) -> Unit,
    viewModel: UserProfileViewModel = hiltViewModel(),
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

    val context = LocalContext.current
    // 关注 / 取关被网易风控拦截：弹窗说明真实原因，并提供「去验证」入口
    state.verifyTip?.let { tip ->
        AlertDialog(
            onDismissRequest = viewModel::dismissVerify,
            title = { Text(Str.res(R.string.verification_required)) },
            text = { Text(tip) },
            confirmButton = {
                val url = state.verifyUrl
                if (url.isNullOrBlank()) {
                    TextButton(onClick = viewModel::dismissVerify) { Text(Str.res(R.string.got_it)) }
                } else {
                    TextButton(
                        onClick = {
                            openVerifyUrl(context, url)
                            viewModel.dismissVerify()
                        },
                    ) { Text(Str.res(R.string.verify)) }
                }
            },
            dismissButton = {
                if (!state.verifyUrl.isNullOrBlank()) {
                    TextButton(onClick = viewModel::dismissVerify) { Text(Str.res(R.string.cancel)) }
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            NcTopBar(
                title = state.profile?.nickname ?: Str.res(R.string.user),
                onBack = onBack,
            )
        },
    ) { padding ->
        when {
            state.loading -> NcLoading(Modifier.padding(padding))
            state.error != null -> NcError(
                message = state.error.orEmpty(),
                modifier = Modifier.padding(padding),
                onRetry = viewModel::load,
            )

            else -> {
                val profile = state.profile
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    if (profile != null) {
                        item {
                            ProfileHeader(
                                profile = profile,
                                showChat = !state.isSelf,
                                following = state.following,
                                followBusy = state.followBusy,
                                onToggleFollow = viewModel::toggleFollow,
                                onChatClick = { onChatClick(profile.userId) },
                                onOpenUserList = onOpenUserList,
                            )
                        }
                    }

                    item { SectionHeader(Str.res(R.string.their_playlists)) }

                    if (state.playlists.isEmpty()) {
                        item {
                            Text(
                                text = Str.res(R.string.no_public_playlists),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        items(state.playlists, key = { "pl-${it.id}" }) { playlist ->
                            PlaylistRowItem(
                                playlist = playlist.toBrief(),
                                onClick = { onPlaylistClick(playlist.id) },
                                subtitle = Str.res(R.string.tracks, playlist.trackCount ?: 0),
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: Profile,
    showChat: Boolean,
    following: Boolean?,
    followBusy: Boolean,
    onToggleFollow: () -> Unit,
    onChatClick: () -> Unit,
    onOpenUserList: (String) -> Unit,
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarImage(
                url = profile.avatarUrl,
                modifier = Modifier.size(80.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.nickname,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    VipBadge(vipType = profile.vipType, modifier = Modifier.padding(start = 6.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        text = Str.res(R.string.following_4, profile.follows ?: 0),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onOpenUserList("follows") }
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Text(
                        text = Str.res(R.string.followers_3, profile.followeds ?: 0),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onOpenUserList("followeds") }
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                    )
                    val level = profile.level ?: 0
                    if (level > 0) {
                        Text(
                            text = Str.res(R.string.lv, level),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = profile.signature.orEmpty().trim().ifBlank { Str.res(R.string.nothing_here_yet) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (showChat) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (following == true) {
                    OutlinedButton(
                        onClick = onToggleFollow,
                        enabled = !followBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(Str.res(R.string.following_3))
                    }
                } else {
                    Button(
                        onClick = onToggleFollow,
                        enabled = !followBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (followBusy) Str.res(R.string.processing_2) else Str.res(R.string.follow))
                    }
                }
                FilledTonalButton(
                    onClick = onChatClick,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(Str.res(R.string.message), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/** Playlist -> PlaylistBrief（复用设计系统条目） */
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

/**
 * 打开网易安全验证页。
 *
 * 关注 / 取关被风控拦截时服务端会返回验证地址（st.music.163.com/encrypt-pages…），
 * 在浏览器或官方 App 里完成手机号 / 短信验证后即可继续关注。
 */
private fun openVerifyUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
