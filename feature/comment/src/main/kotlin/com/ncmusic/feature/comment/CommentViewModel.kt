package com.ncmusic.feature.comment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.result.getOrNull
import com.ncmusic.core.data.repository.CommentRepository
import com.ncmusic.core.data.repository.LoginRepository
import com.ncmusic.core.network.model.Comment
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 评论页状态 */
data class CommentUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val hot: List<Comment> = emptyList(),
    val list: List<Comment> = emptyList(),
    val total: Int = 0,
    val more: Boolean = false,
    val loadingMore: Boolean = false,
    val loggedIn: Boolean = false,
    val myUid: Long? = null,
    val sending: Boolean = false,
    val inputText: String = "",
    val replyTo: Comment? = null,
    val toast: String? = null,
)

/** 评论 ViewModel：精彩评论 / 最新评论 / 点赞 / 发布 / 回复 / 删除（歌曲与 MV 共用） */
@HiltViewModel
class CommentViewModel @Inject constructor(
    private val commentRepository: CommentRepository,
    private val loginRepository: LoginRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CommentUiState())
    val state = _state.asStateFlow()

    private var threadId = ""
    private var loaded = false

    fun load(threadId: String, refresh: Boolean = false) {
        if (!refresh && loaded && this.threadId == threadId) return
        this.threadId = threadId
        loaded = true
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            checkLogin()
            when (val result = commentRepository.comments(threadId, offset = 0, limit = PAGE_SIZE)) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        hot = result.data.hotComments ?: emptyList(),
                        list = result.data.comments ?: emptyList(),
                        total = result.data.total ?: 0,
                        more = result.data.more ?: false,
                    )
                }

                is AppResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.error.userMessage)
                }
            }
        }
    }

    private suspend fun checkLogin() {
        val account = loginRepository.account().getOrNull()?.account ?: run {
            _state.update { it.copy(loggedIn = false, myUid = null) }
            return
        }
        _state.update { it.copy(loggedIn = true, myUid = account.id) }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loadingMore || !snapshot.more) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val result = commentRepository.comments(threadId, offset = snapshot.list.size, limit = PAGE_SIZE)) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        loadingMore = false,
                        list = it.list + (result.data.comments ?: emptyList()),
                        more = result.data.more ?: false,
                    )
                }

                is AppResult.Failure -> _state.update {
                    it.copy(loadingMore = false, toast = result.error.userMessage)
                }
            }
        }
    }

    // ---------------- 点赞 ----------------

    fun toggleLike(comment: Comment) {
        viewModelScope.launch {
            if (!loginRepository.isLoggedInLocally()) {
                _state.update { it.copy(toast = Str.res(R.string.log_in_to_like)) }
                return@launch
            }
            val liked = comment.liked == true
            when (val result = commentRepository.likeComment(threadId, comment.commentId, !liked)) {
                is AppResult.Success -> {
                    _state.update { current ->
                        current.copy(
                            hot = current.hot.map { it.applyLikeChange(comment.commentId, !liked) },
                            list = current.list.map { it.applyLikeChange(comment.commentId, !liked) },
                        )
                    }
                }

                is AppResult.Failure -> _state.update { it.copy(toast = result.error.userMessage) }
            }
        }
    }

    private fun Comment.applyLikeChange(targetId: Long, liked: Boolean): Comment = if (commentId == targetId) {
        copy(
            liked = liked,
            likedCount = ((likedCount ?: 0) + if (liked) 1 else -1).coerceAtLeast(0),
        )
    } else {
        this
    }

    // ---------------- 发布 / 回复 ----------------

    fun updateInput(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    fun setReplyTo(comment: Comment?) {
        _state.update { it.copy(replyTo = comment) }
    }

    fun send() {
        val content = _state.value.inputText.trim()
        if (content.isEmpty()) return
        val reply = _state.value.replyTo
        viewModelScope.launch {
            if (!loginRepository.isLoggedInLocally()) {
                _state.update { it.copy(toast = Str.res(R.string.log_in_to_comment)) }
                return@launch
            }
            _state.update { it.copy(sending = true) }
            val result = if (reply != null) {
                commentRepository.replyComment(threadId, reply.commentId, content)
            } else {
                commentRepository.addComment(threadId, content)
            }
            when (result) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            sending = false,
                            inputText = "",
                            replyTo = null,
                            toast = if (reply != null) Str.res(R.string.reply_posted) else Str.res(R.string.comment_posted),
                        )
                    }
                    load(threadId, refresh = true)
                }

                is AppResult.Failure -> _state.update {
                    it.copy(sending = false, toast = result.error.userMessage)
                }
            }
        }
    }

    /** 删除自己的评论 */
    fun deleteComment(comment: Comment) {
        viewModelScope.launch {
            when (val result = commentRepository.deleteComment(threadId, comment.commentId)) {
                is AppResult.Success -> {
                    _state.update { current ->
                        current.copy(
                            hot = current.hot.filterNot { it.commentId == comment.commentId },
                            list = current.list.filterNot { it.commentId == comment.commentId },
                            toast = Str.res(R.string.comment_deleted),
                        )
                    }
                }

                is AppResult.Failure -> _state.update { it.copy(toast = result.error.userMessage) }
            }
        }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
