package com.ncmusic.feature.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ncmusic.core.network.model.ChatMessage
import com.ncmusic.designsystem.component.AvatarImage
import com.ncmusic.designsystem.component.NcError
import com.ncmusic.designsystem.component.NcLoading
import com.ncmusic.designsystem.component.NcTopBar
import com.ncmusic.designsystem.component.VipBadge
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 私信聊天页（不是自己的消息显示在左侧，自己的消息在右侧） */
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(state.toast) {
        val toast = state.toast
        if (toast != null) {
            snackbarHostState.showSnackbar(toast)
            viewModel.consumeToast()
        }
    }

    // 私信无推送通道：停留聊天页时每 5 秒静默拉取一次最新消息
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            viewModel.pollNew()
        }
    }

    // 最新一条变化（首次加载 / 新发送）时滚到底部（reverseLayout 下 index 0 = 底部）
    LaunchedEffect(state.messages.lastOrNull()?.time) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // 滚动到顶部附近时自动加载更早的消息（reverseLayout 下最后一项 = 视觉顶部）。
    // 注意：LaunchedEffect 只以 listState 为 key，闭包里的 state 会停留在首次组合时的空列表，
    // 必须用 rememberUpdatedState 跟随最新消息数量，否则会无限触发 loadMore。
    val messageCount by rememberUpdatedState(state.messages.size)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && messageCount > 0 && lastIndex >= messageCount - 1) {
                    viewModel.loadMore()
                }
            }
    }

    Scaffold(
        topBar = {
            NcTopBar(
                title = state.opponent?.nickname ?: Str.res(R.string.chat),
                onBack = onBack,
                actions = {
                    VipBadge(vipType = state.opponent?.vipType)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            when {
                state.loading -> NcLoading(Modifier.weight(1f))
                state.error != null -> NcError(
                    message = state.error.orEmpty(),
                    modifier = Modifier.weight(1f),
                    onRetry = viewModel::load,
                )

                else -> {
                    val messages = state.messages
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        itemsIndexed(
                            items = messages.asReversed(),
                            key = { _, msg -> "msg-${msg.id ?: msg.hashCode()}" },
                        ) { index, msg ->
                            val mine = msg.fromUser?.userId != null &&
                                msg.fromUser?.userId == state.myUid
                            // 相邻消息间隔超过 10 分钟时显示时间（reversed 列表里下一条 index+1 是更早的）
                            val older = messages.asReversed().getOrNull(index + 1)
                            val showTime = older == null ||
                                ((msg.time ?: 0L) - (older.time ?: 0L)) > 10 * 60_000L
                            MessageRow(
                                msg = msg,
                                mine = mine,
                                opponentAvatar = state.opponent?.avatarUrl,
                                showTime = showTime,
                            )
                        }

                        // 加载更早的消息（reverseLayout 下最后一个 item 位于视觉顶部）
                        item(key = "load-more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    state.loadingMore -> CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )

                                    !state.more && messages.isNotEmpty() -> Text(
                                        text = Str.res(R.string.no_earlier_messages),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = state.inputText,
                            onValueChange = { viewModel.updateInput(it) },
                            placeholder = { Text(Str.res(R.string.send_a_message)) },
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
        }
    }
}

@Composable
private fun MessageRow(
    msg: ChatMessage,
    mine: Boolean,
    opponentAvatar: String?,
    showTime: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showTime) {
            Text(
                text = formatMsgTime(msg.time ?: 0L),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            if (!mine) {
                AvatarImage(
                    url = opponentAvatar,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .clip(
                        if (mine) {
                            RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
                        } else {
                            RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
                        },
                    )
                    .background(
                        if (mine) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    text = msg.displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mine) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}

private fun formatMsgTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestampMs }
    val sameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm" else "MM-dd HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestampMs))
}
