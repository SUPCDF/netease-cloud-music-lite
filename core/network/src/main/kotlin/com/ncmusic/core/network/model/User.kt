package com.ncmusic.core.network.model

import kotlinx.serialization.Serializable

/** 用户精简信息（评论 / 歌单创建者等场景） */
@Serializable
data class UserBrief(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String? = null,
    val signature: String? = null,
    val vipType: Int? = null,
    val followed: Boolean? = null,
    val backgroundUrl: String? = null,
)

/** 完整用户资料 */
@Serializable
data class Profile(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String? = null,
    val backgroundUrl: String? = null,
    val signature: String? = null,
    val follows: Int? = null,
    val followeds: Int? = null,
    val playlistCount: Int? = null,
    val playlistsCount: Int? = null,
    val level: Int? = null,
    val listenSongs: Int? = null,
    val createTime: Long? = null,
    val gender: Int? = null,
    val birthday: Long? = null,
    val province: Int? = null,
    val city: Int? = null,
    val vipType: Int? = null,
    val followed: Boolean? = null,
)

/** 账号信息（登录状态） */
@Serializable
data class Account(
    val id: Long = 0,
    val anonimousUser: Boolean? = null,
    val createTime: Long? = null,
    val username: String? = null,
    val status: Int? = null,
    val vipType: Int? = null,
)

/** 登录状态 / 账号信息响应 */
@Serializable
data class AccountResp(
    val code: Int? = null,
    val account: Account? = null,
    val profile: Profile? = null,
)

/** 手机号登录响应 */
@Serializable
data class LoginResp(
    val code: Int? = null,
    val message: String? = null,
    val msg: String? = null,
    val account: Account? = null,
    val profile: Profile? = null,
    val bindings: List<Binding>? = null,
)

@Serializable
data class Binding(
    val bindingType: Int? = null,
    val url: String? = null,
    val userId: Long? = null,
    val expired: Boolean? = null,
)

/** 用户详情（含等级） */
@Serializable
data class UserDetailResp(
    val code: Int? = null,
    val level: Int? = null,
    val listenSongs: Int? = null,
    val createTime: Long? = null,
    val profile: Profile? = null,
)

/** 用户等级响应（/user/level；user/detail 已不再返回等级） */
@Serializable
data class UserLevelResp(
    val code: Int? = null,
    val data: UserLevelData? = null,
)

@Serializable
data class UserLevelData(
    val userId: Long? = null,
    val level: Int? = null,
    val nowPlayCount: Int? = null,
    val nextPlayCount: Int? = null,
    val nowLoginCount: Int? = null,
    val nextLoginCount: Int? = null,
)

/**
 * 关注 / 取关响应。
 *
 * 网易对这类写操作有风控：被拦截时返回 code -462，
 * data 里带 blockText（说明）与 url（验证地址）。
 */
@Serializable
data class FollowResp(
    val code: Int? = null,
    val message: String? = null,
    val data: FollowVerifyData? = null,
)

/** 风控验证信息（code -462 时返回） */
@Serializable
data class FollowVerifyData(
    val verifyType: Int? = null,
    val verifyId: Long? = null,
    val verifyToken: String? = null,
    val blockText: String? = null,
    val url: String? = null,
)

/** 关注 / 粉丝列表 */
@Serializable
data class FollowsResp(
    val code: Int? = null,
    val more: Boolean? = null,
    val follow: List<UserBrief>? = null,
    val followeds: List<UserBrief>? = null,
)

/** 播放记录项（听歌排行 / 最近播放） */
@Serializable
data class RecordItem(
    val song: Song? = null,
    val playCount: Int? = null,
    val score: Int? = null,
    val listenTime: Int? = null,
    val playCountAll: Int? = null,
)

@Serializable
data class RecordResp(
    val code: Int? = null,
    val allData: List<RecordItem>? = null,
    val weekData: List<RecordItem>? = null,
)
