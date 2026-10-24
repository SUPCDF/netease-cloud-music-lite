package com.ncmusic.core.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 私信会话列表响应（/msg/private/users） */
@Serializable
data class MsgUsersResp(
    val code: Int? = null,
    val more: Boolean? = null,
    val newMsgCount: Int? = null,
    val msgs: List<Conversation>? = null,
)

/** 会话（含对方用户资料、最近一条消息） */
@Serializable
data class Conversation(
    val user: ConvUser? = null,
    val fromUser: UserBrief? = null,
    val toUser: UserBrief? = null,
    val lastMsg: String? = null,
    val lastMsgTime: Long? = null,
    val newMsgCount: Int? = null,
) {
    /** 对方用户 id（不是我自己的那一方） */
    fun opponentId(myUid: Long): Long? {
        val from = user?.fromUserId
        val to = user?.toUserId
        return when {
            to == myUid -> from
            from == myUid -> to
            else -> from ?: to
        }
    }

    /** 对方资料 */
    fun opponent(myUid: Long): UserBrief? {
        val oid = opponentId(myUid) ?: return fromUser ?: toUser
        return when (oid) {
            fromUser?.userId -> fromUser
            toUser?.userId -> toUser
            else -> fromUser?.takeIf { it.userId != myUid } ?: toUser
        }
    }

    /** 最近一条消息的展示文本（富文本 JSON 提取内层 msg） */
    val lastMsgText: String
        get() = ChatMessage.extractDisplayText(lastMsg)
}

/** 会话对方信息（会话列表返回的简版） */
@Serializable
data class ConvUser(
    val id: Long? = null,
    val fromUserId: Long? = null,
    val toUserId: Long? = null,
)

/** 私信历史响应（/msg/private/history） */
@Serializable
data class MsgHistoryResp(
    val code: Int? = null,
    val more: Boolean? = null,
    val msgs: List<ChatMessage>? = null,
)

/** 单条私信消息 */
@Serializable
data class ChatMessage(
    val id: Long? = null,
    val msg: String? = null,
    val time: Long? = null,
    val fromUser: UserBrief? = null,
    val toUser: UserBrief? = null,
) {
    /** 展示文本：普通文本直接返回；富文本消息是 JSON 串，提取内层 msg 字段 */
    val displayText: String
        get() = extractDisplayText(msg)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun extractDisplayText(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            if (!raw.trimStart().startsWith("{")) return raw
            return runCatching {
                val obj = json.parseToJsonElement(raw).jsonObject
                obj["msg"]?.jsonPrimitive?.content
                    ?: obj["pushMsg"]?.jsonPrimitive?.content
                    ?: raw
            }.getOrDefault(raw)
        }
    }
}
