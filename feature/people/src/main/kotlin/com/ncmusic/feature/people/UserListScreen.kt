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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.core.network.model.UserBrief
import com.ncmusic.designsystem.component.AvatarImage
import com.ncmusic.designsystem.component.NcEmpty
import com.ncmusic.designsystem.component.NcError
import com.ncmusic.designsystem.component.NcLoading
import com.ncmusic.designsystem.component.NcTopBar
import com.ncmusic.designsystem.component.VipBadge
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 关注 / 粉丝列表页 */
@Composable
fun UserListScreen(
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit,
    viewModel: UserListViewModel = hiltViewModel(),
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            NcTopBar(
                title = if (viewModel.isFollowers) Str.res(R.string.followers_2) else Str.res(R.string.following_2),
                onBack = onBack,
            )
        },
    ) { padding ->
        when {
            state.loading -> NcLoading(Modifier.padding(padding))
            state.error != null -> NcError(
                message = state.error.orEmpty(),
                modifier = Modifier.padding(padding),
                onRetry = { viewModel.load(reset = true) },
            )

            state.users.isEmpty() -> NcEmpty(
                message = if (viewModel.isFollowers) Str.res(R.string.no_followers) else Str.res(R.string.not_following_anyone),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(state.users, key = { "user-${it.userId}" }) { user ->
                    UserRow(
                        user = user,
                        onClick = { onUserClick(user.userId) },
                        trailing = if (viewModel.isFollowers) {
                            null
                        } else {
                            {
                                val busy = user.userId in state.busyIds
                                TextButton(
                                    onClick = { viewModel.unfollow(user.userId) },
                                    enabled = !busy,
                                ) {
                                    Text(if (busy) Str.res(R.string.processing) else Str.res(R.string.following_3))
                                }
                            }
                        },
                    )
                }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (state.more && !state.loadingMore) {
                                    Modifier.clickable { viewModel.load() }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.loadingMore) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        } else if (state.more) {
                            Text(
                                text = Str.res(R.string.load_more),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                text = Str.res(R.string.all_loaded),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 用户行（头像 + 昵称 + 会员徽章 + 签名） */
@Composable
fun UserRow(
    user: UserBrief,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
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
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                VipBadge(vipType = user.vipType, modifier = Modifier.padding(start = 6.dp))
            }
            val signature = user.signature.orEmpty().trim()
            if (signature.isNotEmpty()) {
                Text(
                    text = signature,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
