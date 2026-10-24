package com.ncmusic.feature.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.repository.LoginRepository
import com.ncmusic.core.data.repository.MessageRepository
import com.ncmusic.core.data.settings.SettingsRepository
import com.ncmusic.core.network.model.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 私信会话列表状态 */
data class ConversationListUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val conversations: List<Conversation> = emptyList(),
    val more: Boolean = false,
    val loadingMore: Boolean = false,
    val myUid: Long = 0L,
    /** 本地已读时间：对方 uid -> 已读到的消息时间，用来把服务端未读数清掉 */
    val readTimes: Map<Long, Long> = emptyMap(),
)

/** 私信会话列表（我的消息） */
@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val loginRepository: LoginRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConversationListUiState())
    val state: StateFlow<ConversationListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.messageReadTimes.collect { times ->
                _state.update { it.copy(readTimes = times) }
            }
        }
        load(reset = true)
    }

    fun load(reset: Boolean = false) {
        val snapshot = _state.value
        if (!reset && (!snapshot.more || snapshot.loadingMore)) return
        viewModelScope.launch {
            _state.update {
                if (reset) {
                    it.copy(loading = true, error = null, conversations = emptyList(), more = false)
                } else {
                    it.copy(loadingMore = true)
                }
            }
            if (_state.value.myUid == 0L) {
                val myUid = loginRepository.account().getOrNull()?.account?.id ?: 0L
                _state.update { it.copy(myUid = myUid) }
            }
            val offset = if (reset) 0 else snapshot.conversations.size
            when (val result = messageRepository.conversations(offset)) {
                is AppResult.Success -> {
                    val list = result.data.msgs ?: emptyList()
                    _state.update {
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            conversations = if (reset) list else it.conversations + list,
                            more = result.data.more ?: false,
                        )
                    }
                }

                is AppResult.Failure -> {
                    _state.update {
                        if (reset) {
                            it.copy(loading = false, error = result.error.userMessage)
                        } else {
                            it.copy(loadingMore = false)
                        }
                    }
                }
            }
        }
    }

    /**
     * 静默刷新第一页（供页面轮询使用）：
     * 私信没有推送通道，靠定时拉取让列表接近实时；已加载的多页会话会按 uid 去重合并保留。
     */
    fun refresh() {
        if (_state.value.loading) return
        viewModelScope.launch {
            val snapshot = _state.value
            when (val result = messageRepository.conversations(0)) {
                is AppResult.Success -> {
                    val fresh = result.data.msgs ?: emptyList()
                    val merged = (fresh + snapshot.conversations)
                        .distinctBy { it.opponentId(snapshot.myUid) }
                        .sortedByDescending { it.lastMsgTime ?: 0L }
                    _state.update {
                        it.copy(
                            conversations = merged,
                            more = result.data.more ?: it.more,
                        )
                    }
                }

                is AppResult.Failure -> Unit
            }
        }
    }
}
