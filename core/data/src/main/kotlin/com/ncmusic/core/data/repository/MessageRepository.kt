package com.ncmusic.core.data.repository

import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.network.api.EapiBody
import com.ncmusic.core.network.api.NeteaseApi
import com.ncmusic.core.network.api.WeapiBody
import com.ncmusic.core.network.model.MsgHistoryResp
import com.ncmusic.core.network.model.MsgUsersResp
import com.ncmusic.core.network.util.apiCall
import com.ncmusic.core.network.util.jsonOf
import com.ncmusic.core.network.util.requireCode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 私信仓库：会话列表 / 聊天历史 / 发送 */
@Singleton
class MessageRepository @Inject constructor(
    private val api: NeteaseApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** 会话列表（按最近消息时间倒序，more 表示还有下一页） */
    suspend fun conversations(
        offset: Int = 0,
        limit: Int = 30,
    ): AppResult<MsgUsersResp> = apiCall(io) {
        api.msgPrivateUsers(
            WeapiBody(jsonOf("offset" to offset, "limit" to limit, "total" to true)),
        )
    }

    /** 与某人的聊天历史（before 为分页时间戳，0 表示最新一页；返回按时间倒序） */
    suspend fun history(
        uid: Long,
        before: Long = 0,
        limit: Int = 30,
    ): AppResult<MsgHistoryResp> = apiCall(io) {
        api.msgPrivateHistory(
            WeapiBody(
                jsonOf(
                    "userId" to uid,
                    "limit" to limit,
                    "time" to before,
                    "total" to true,
                ),
            ),
        )
    }

    /** 发送文字私信（eapi 移动端通道，weapi 会被风控拦截） */
    suspend fun sendText(uid: Long, text: String): AppResult<Unit> = apiCall(io) {
        val resp = api.sendPrivateMsgEapi(
            SEND_URL,
            EapiBody(
                digestPath = "/api/msg/private/send",
                data = jsonOf(
                    "type" to "text",
                    "msg" to text,
                    "userIds" to "[$uid]",
                ),
                mobile = true,
            ),
        )
        requireCode(resp.code, resp.message ?: Str.res(R.string.failed_to_send))
        Unit
    }

    companion object {
        const val SEND_URL = "https://interface.music.163.com/eapi/msg/private/send"
    }
}
