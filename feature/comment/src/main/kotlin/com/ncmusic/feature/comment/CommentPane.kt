package com.ncmusic.feature.comment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str
import com.ncmusic.core.common.util.TimeUtils
import com.ncmusic.core.network.model.Comment
import com.ncmusic.designsystem.component.AvatarImage
import com.ncmusic.designsystem.component.NcEmpty
import com.ncmusic.designsystem.component.NcError
import com.ncmusic.designsystem.component.NcLoading
import com.ncmusic.designsystem.component.SectionHeader
import com.ncmusic.designsystem.component.VipBadge
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 可嵌入的评论区（歌曲页 / MV 页共用）：
 * 列表 + 自动翻页 + 点赞 / 回复 / 删除 + 底部输入栏。
 */
@Composable
fun CommentPane(
    threadId: String,
    viewModel: CommentViewModel,
    snackbarHostState: SnackbarHostState,
    onUserClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(threadId) {
        if (threadId.isNotBlank()) viewModel.load(threadId)
    }
    LaunchedEffect(state.toast) {
        val toast = state.toast
        if (toast != null) {
            snackbarHostState.showSnackbar(toast)
            viewModel.consumeToast()
        }
    }

    // 长按评论复制内容
    val onCopyComment: (String) -> Unit = { text ->
        if (text.isNotBlank()) {
            clipboard.setText(AnnotatedString(text))
            scope.launch { snackbarHostState.showSnackbar(Str.res(R.string.comment_copied)) }
        }
    }

    // 接近底部时自动加载下一页
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .collect { nearBottom ->
                if (nearBottom && latestState.more && !latestState.loadingMore) {
                    viewModel.loadMore()
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                state.loading -> NcLoading()

                state.error != null -> NcError(
                    message = state.error ?: Str.res(R.string.failed_to_load_comments),
                    onRetry = { viewModel.load(threadId, refresh = true) },
                )

                state.hot.isEmpty() && state.list.isEmpty() -> NcEmpty(
                    Str.res(R.string.no_comments_yet_be_the_first),
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.hot.isNotEmpty()) {
                        item { SectionHeader(Str.res(R.string.top_comments)) }
                        items(state.hot, key = { "hot-${it.commentId}" }) { comment ->
                            CommentItem(
                                comment = comment,
                                myUid = state.myUid,
                                onLike = { viewModel.toggleLike(comment) },
                                onReply = { viewModel.setReplyTo(comment) },
                                onDelete = { viewModel.deleteComment(comment) },
                                onUserClick = onUserClick,
                                onCopy = onCopyComment,
                            )
                        }
                        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
                    }
                    if (state.list.isNotEmpty()) {
                        item { SectionHeader(Str.res(R.string.latest_comments)) }
                        items(state.list, key = { "list-${it.commentId}" }) { comment ->
                            CommentItem(
                                comment = comment,
                                myUid = state.myUid,
                                onLike = { viewModel.toggleLike(comment) },
                                onReply = { viewModel.setReplyTo(comment) },
                                onDelete = { viewModel.deleteComment(comment) },
                                onUserClick = onUserClick,
                                onCopy = onCopyComment,
                            )
                        }
                    }
                    if (state.more) {
                        item(key = "load-more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.loadingMore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val replyTo = state.replyTo
        if (replyTo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = Str.res(R.string.replying_to, replyTo.user?.nickname.orEmpty()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { viewModel.setReplyTo(null) }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = Str.res(R.string.cancel_reply),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.inputText,
                onValueChange = { viewModel.updateInput(it) },
                placeholder = { Text(Str.res(R.string.say_something)) },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            if (state.sending) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(
                    onClick = { viewModel.send() },
                    enabled = state.inputText.isNotBlank(),
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = Str.res(R.string.send))
                }
            }
        }
    }
}

@Composable
private fun CommentItem(
    comment: Comment,
    myUid: Long?,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onUserClick: (Long) -> Unit,
    onCopy: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AvatarImage(
            url = comment.user?.avatarUrl,
            modifier = Modifier.size(36.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.user?.nickname.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable {
                            val uid = comment.user?.userId ?: 0L
                            if (uid > 0) onUserClick(uid)
                        },
                )
                VipBadge(
                    vipType = comment.user?.vipType,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = TimeUtils.formatRelativeTime(comment.time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .pointerInput(comment.content) {
                        detectTapGestures(onLongPress = { onCopy(comment.content) })
                    },
            )
            val reply = comment.beReplied?.firstOrNull()
            if (reply != null) {
                Text(
                    text = "@${reply.user?.nickname.orEmpty()}：${reply.content.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = Str.res(R.string.reply),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onReply)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                if (myUid != null && comment.user?.userId == myUid) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDelete),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = Str.res(R.string.delete),
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.size(6.dp))
                }
                val likedCount = comment.likedCount ?: 0
                if (likedCount > 0) {
                    Text(
                        text = likedCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                val liked = comment.liked == true
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onLike),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (liked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt,
                        contentDescription = Str.res(R.string.like_2),
                        modifier = Modifier.size(17.dp),
                        tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
