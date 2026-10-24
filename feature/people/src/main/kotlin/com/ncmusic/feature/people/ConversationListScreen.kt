package com.ncmusic.feature.people

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.core.common.util.TimeUtils
import com.ncmusic.core.network.model.Conversation
import com.ncmusic.designsystem.component.AvatarImage
import com.ncmusic.designsystem.component.NcEmpty
import com.ncmusic.designsystem.component.NcError
import com.ncmusic.designsystem.component.NcLoading
import com.ncmusic.designsystem.component.NcTopBar
import kotlinx.coroutines.delay
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 私信会话列表（我的消息） */
@Composable
fun ConversationListScreen(
    onBack: () -> Unit,
    onConversationClick: (Long) -> Unit,
    viewModel: ConversationListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 私信没有实时推送通道：页面停留期间每 8 秒静默拉取一次，让列表接近实时
    LaunchedEffect(Unit) {
        while (true) {
            delay(8_000)
            viewModel.refresh()
        }
    }

    Scaffold(topBar = { NcTopBar(title = Str.res(R.string.messages), onBack = onBack) }) { padding ->
        when {
            state.loading -> NcLoading(Modifier.padding(padding))
            state.error != null -> NcError(
                message = state.error.orEmpty(),
                modifier = Modifier.padding(padding),
                onRetry = { viewModel.load(reset = true) },
            )

            state.conversations.isEmpty() -> NcEmpty(
                message = Str.res(R.string.no_chats),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                itemsIndexed(
                    items = state.conversations,
                    key = { index, conv -> "conv-$index-${conv.opponentId(state.myUid)}" },
                ) { _, conversation ->
                    ConversationRow(
                        conversation = conversation,
                        myUid = state.myUid,
                        readTime = conversation.opponentId(state.myUid)
                            ?.let { state.readTimes[it] } ?: 0L,
                        onClick = {
                            conversation.opponentId(state.myUid)?.let(onConversationClick)
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
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    myUid: Long,
    readTime: Long,
    onClick: () -> Unit,
) {
    val opponent = conversation.opponent(myUid)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarImage(
            url = opponent?.avatarUrl,
            modifier = Modifier.size(52.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = opponent?.nickname ?: Str.res(R.string.unknown_user),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = conversation.lastMsgText.ifBlank { Str.res(R.string.no_messages) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val time = conversation.lastMsgTime ?: 0L
            if (time > 0) {
                Text(
                    text = TimeUtils.formatRelativeTime(time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val unread = if ((conversation.lastMsgTime ?: 0L) <= readTime) {
                0
            } else {
                conversation.newMsgCount ?: 0
            }
            if (unread > 0) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (unread > 99) "99+" else "$unread",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        }
    }
}
