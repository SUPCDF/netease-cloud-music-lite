package com.ncmusic.feature.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.repository.LoginRepository
import com.ncmusic.core.data.repository.MessageRepository
import com.ncmusic.core.data.repository.UserRepository
import com.ncmusic.core.data.settings.SettingsRepository
import com.ncmusic.core.network.model.ChatMessage
import com.ncmusic.core.network.model.UserBrief
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 聊天页状态 */
data class ChatUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val opponent: UserBrief? = null,
    val myUid: Long = 0L,
    /** 按时间升序（旧 -> 新），直接用于列表展示 */
    val messages: List<ChatMessage> = emptyList(),
    val more: Boolean = false,
    val loadingMore: Boolean = false,
    val sending: Boolean = false,
    val inputText: String = "",
    val toast: String? = null,
)

/** 私信聊天页：历史消息 / 发送文字 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val loginRepository: LoginRepository,
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val uid: Long = savedStateHandle[ARG_UID] ?: 0L
    private var loaded = false

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** 打开会话时：拿自己的 uid（区分消息左右）+ 对方资料 + 最新一页历史 */
    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            if (!loaded) {
                loaded = true
                val myUid = loginRepository.account().getOrNull()?.account?.id ?: 0L
                _state.update { it.copy(myUid = myUid) }
                // 对方资料（拿昵称/头像/会员；失败不阻塞聊天）
                val profile = userRepository.profile(uid).getOrNull()
                _state.update {
                    it.copy(
                        opponent = profile?.let { p ->
                            UserBrief(
                                userId = p.userId,
                                nickname = p.nickname,
                                avatarUrl = p.avatarUrl,
                                signature = p.signature,
                                vipType = p.vipType,
                            )
                        },
                    )
                }
            }
            when (val result = messageRepository.history(uid)) {
                is AppResult.Success -> {
                    val msgs = (result.data.msgs ?: emptyList())
                        .sortedBy { it.time ?: 0 }
                        .distinctBy { it.id ?: it.hashCode() }
                    _state.update {
                        it.copy(
                            loading = false,
                            messages = msgs,
                            more = result.data.more ?: false,
                        )
                    }
                    markRead(msgs.maxOfOrNull { it.time ?: 0L } ?: 0L)
                }

                is AppResult.Failure -> {
                    _state.update { it.copy(loading = false, error = result.error.userMessage) }
                }
            }
        }
    }

    /** 加载更早的消息（历史接口按时间倒序返回，取当前最早一条的时间为游标） */
    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loadingMore || !snapshot.more || snapshot.messages.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            val before = snapshot.messages.firstOrNull()?.time ?: 0L
            when (val result = messageRepository.history(uid, before = before)) {
                is AppResult.Success -> {
                    val existingIds = snapshot.messages.mapNotNull { it.id }.toSet()
                    val older = (result.data.msgs ?: emptyList())
                        .filter { it.id == null || it.id !in existingIds }
                        .sortedBy { it.time ?: 0 }
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            // 分页接口会带上边界消息；id 为空的消息只能用内容去重，
                            // 否则重复 item 会让 LazyColumn 的 key 冲突而崩溃。
                            messages = (older + it.messages)
                                .distinctBy { msg -> msg.id ?: msg.hashCode() },
                            more = result.data.more ?: false,
                        )
                    }
                }

                is AppResult.Failure -> {
                    _state.update { it.copy(loadingMore = false) }
                }
            }
        }
    }

    fun updateInput(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    /**
     * 轮询最新消息（页面每 5 秒调用一次）。
     * 网易私信没有可用的推送通道，用静默拉取 + 本地已读时间达到「近似实时」。
     *
     * 自己发的消息会先在本地回显（id 为空），服务端回显后要「一对一」替换掉本地那条：
     * 服务端回显的富文本（JSON 串）和本地明文不一定逐字相同，所以按展示文本 displayText 比对，
     * 否则同一条消息会同时留下本地 + 服务端两条，看起来就是「发出去一会儿变两条」。
     */
    fun pollNew() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.loadingMore) return
        viewModelScope.launch {
            try {
                when (val result = messageRepository.history(uid)) {
                    is AppResult.Success -> {
                        val server = (result.data.msgs ?: emptyList())
                            .sortedBy { it.time ?: 0 }
                            .distinctBy { it.id ?: it.hashCode() }
                        val merged = mergeServer(snapshot.messages, server)
                        if (merged != snapshot.messages) {
                            _state.update { it.copy(messages = merged) }
                        }
                        markRead(merged.maxOfOrNull { it.time ?: 0L } ?: 0L)
                    }

                    is AppResult.Failure -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 轮询失败保持静默，绝不让后台刷新把前台页面带崩
            }
        }
    }

    /**
     * 合并服务端消息与本地回显：
     * 服务端已回显的本地消息（同发送者 + 同展示文本 + 时间接近）一对一移除，
     * 服务端还没回显的本地消息保留（发送成功但服务端还没来得及回显）。
     */
    private fun mergeServer(
        local: List<ChatMessage>,
        server: List<ChatMessage>,
    ): List<ChatMessage> {
        val claimed = mutableSetOf<Int>()
        val pendingEchoes = local.filter { echo ->
            if (echo.id != null) return@filter false
            val hitIndex = server.indices.firstOrNull { index ->
                index !in claimed &&
                    server[index].displayText == echo.displayText &&
                    isFromSameSender(server[index], echo) &&
                    sameMoment(server[index].time, echo.time)
            }
            if (hitIndex == null) return@filter true
            claimed += hitIndex
            false
        }
        return (server + pendingEchoes)
            .sortedBy { it.time ?: 0L }
            .distinctBy { it.id ?: it.hashCode() }
    }

    /** 本地回显的发送者是自己（拿不到 uid 时按 0 当通配，避免多出一条重复消息） */
    private fun isFromSameSender(server: ChatMessage, echo: ChatMessage): Boolean {
        val echoUid = echo.fromUser?.userId ?: 0L
        return echoUid == 0L || server.fromUser?.userId == echoUid
    }

    /** 私信时间戳可能是秒也可能是毫秒，统一成毫秒后比较，2 分钟内视为同一条 */
    private fun sameMoment(a: Long?, b: Long?): Boolean {
        if (a == null || b == null) return false
        return kotlin.math.abs(toMillis(a) - toMillis(b)) < 120_000L
    }

    private fun toMillis(time: Long): Long =
        if (time < 100_000_000_000L) time * 1_000L else time

    /** 记录本地已读位置，配合会话列表把「已读还给 1 未读」的服务端计数清掉 */
    private fun markRead(timeMs: Long) {
        if (timeMs <= 0L) return
        viewModelScope.launch {
            runCatching { settingsRepository.markConversationRead(uid, timeMs) }
        }
    }

    /** 发送文字私信 */
    fun send() {
        val snapshot = _state.value
        val content = snapshot.inputText.trim()
        if (content.isEmpty() || snapshot.sending) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true) }
            try {
                when (val result = messageRepository.sendText(uid, content)) {
                    is AppResult.Success -> {
                        // 本地回显已发出的消息（无需重拉列表）。
                        // 昵称在聊天气泡里不展示，这里不再取资源字符串，避免后台线程访问资源。
                        val local = ChatMessage(
                            id = null,
                            msg = content,
                            time = System.currentTimeMillis(),
                            fromUser = UserBrief(userId = snapshot.myUid),
                            toUser = UserBrief(userId = uid),
                        )
                        _state.update {
                            it.copy(
                                sending = false,
                                inputText = "",
                                messages = (it.messages + local)
                                    .distinctBy { msg -> msg.id ?: msg.hashCode() },
                            )
                        }
                        markRead(local.time ?: 0L)
                    }

                    is AppResult.Failure -> {
                        _state.update {
                            it.copy(sending = false, toast = result.error.userMessage)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 发送链路上的任何意外都不应让 App 闪退（例如 Compose/R8 兼容问题）
                android.util.Log.e("ChatViewModel", "send failed", e)
                _state.update {
                    it.copy(sending = false, toast = e.message ?: "Send failed")
                }
            }
        }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    companion object {
        const val ARG_UID = "uid"
    }
}
