package com.ncmusic.core.data.repository

import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.network.api.EapiBody
import com.ncmusic.core.network.api.NeteaseApi
import com.ncmusic.core.network.api.WeapiBody
import com.ncmusic.core.network.model.CommentResp
import com.ncmusic.core.network.util.apiCall
import com.ncmusic.core.network.util.jsonOf
import com.ncmusic.core.network.util.requireCode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 评论仓库，threadId 同时支持歌曲（R_SO_4_*）与 MV（R_MV_5_*） */
@Singleton
class CommentRepository @Inject constructor(
    private val api: NeteaseApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** 评论列表（精彩评论在首页返回，翻页用 offset） */
    suspend fun comments(
        threadId: String,
        offset: Int = 0,
        limit: Int = 20,
    ): AppResult<CommentResp> = apiCall(io) {
        api.comments(
            threadId = threadId,
            body = WeapiBody(
                jsonOf(
                    "offset" to offset,
                    "limit" to limit,
                    "beforeTime" to 0,
                    "total" to true,
                ),
            ),
        )
    }

    /** 点赞 / 取消点赞评论 */
    suspend fun likeComment(
        threadId: String,
        commentId: Long,
        like: Boolean,
    ): AppResult<Unit> = apiCall(io) {
        val body = WeapiBody(
            jsonOf(
                "threadId" to threadId,
                "commentId" to commentId.toString(),
            ),
        )
        val resp = if (like) api.likeComment(body) else api.unlikeComment(body)
        requireCode(resp.code, Str.res(R.string.action_failed))
        Unit
    }

    /** 发表评论（eapi 移动端通道，weapi 会被风控拦截） */
    suspend fun addComment(threadId: String, content: String): AppResult<Unit> = apiCall(io) {
        val resp = api.addCommentEapi(
            COMMENT_ADD_URL,
            EapiBody(
                digestPath = "/api/v1/resource/comments/add",
                data = jsonOf(
                    "threadId" to threadId,
                    "content" to content,
                ),
                mobile = true,
            ),
        )
        requireCode(resp.code, resp.message ?: Str.res(R.string.failed_to_comment))
        Unit
    }

    /** 回复评论（eapi 移动端通道） */
    suspend fun replyComment(
        threadId: String,
        commentId: Long,
        content: String,
    ): AppResult<Unit> = apiCall(io) {
        val resp = api.replyCommentEapi(
            COMMENT_REPLY_URL,
            EapiBody(
                digestPath = "/api/resource/comments/reply",
                data = jsonOf(
                    "threadId" to threadId,
                    "commentId" to commentId.toString(),
                    "content" to content,
                ),
                mobile = true,
            ),
        )
        requireCode(resp.code, resp.message ?: Str.res(R.string.failed_to_reply))
        Unit
    }

    /** 删除自己的评论（eapi 移动端通道） */
    suspend fun deleteComment(threadId: String, commentId: Long): AppResult<Unit> = apiCall(io) {
        val resp = api.deleteCommentEapi(
            COMMENT_DELETE_URL,
            EapiBody(
                digestPath = "/api/resource/comments/delete",
                data = jsonOf(
                    "threadId" to threadId,
                    "commentId" to commentId.toString(),
                ),
                mobile = true,
            ),
        )
        requireCode(resp.code, Str.res(R.string.delete_failed))
        Unit
    }

    private companion object {
        const val COMMENT_ADD_URL = "https://interface.music.163.com/eapi/v1/resource/comments/add"
        const val COMMENT_REPLY_URL = "https://interface.music.163.com/eapi/resource/comments/reply"
        const val COMMENT_DELETE_URL = "https://interface.music.163.com/eapi/resource/comments/delete"
    }
}
