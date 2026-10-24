package com.ncmusic.core.network.model

import kotlinx.serialization.Serializable

/** 被回复的评论 */
@Serializable
data class BeReplied(
    val user: UserBrief? = null,
    val content: String? = null,
    val beRepliedCommentId: Long? = null,
    val status: Int? = null,
)

/** 评论项 */
@Serializable
data class Comment(
    val commentId: Long = 0,
    val content: String = "",
    val time: Long = 0,
    val likedCount: Int? = null,
    val liked: Boolean? = null,
    val user: UserBrief? = null,
    val beReplied: List<BeReplied>? = null,
    val replyCount: Int? = null,
    val parentCommentId: Long? = null,
    val status: Int? = null,
    val ipLocation: IpLocation? = null,
    val showFloorComment: ShowFloorComment? = null,
)

@Serializable
data class IpLocation(
    val ip: String? = null,
    val location: String? = null,
    val userId: Long? = null,
)

@Serializable
data class ShowFloorComment(
    val replyCount: Int? = null,
    val comments: List<Comment>? = null,
)

/** 歌曲评论列表 */
@Serializable
data class CommentResp(
    val code: Int? = null,
    val total: Int? = null,
    val more: Boolean? = null,
    val moreHot: Boolean? = null,
    val hotComments: List<Comment>? = null,
    val comments: List<Comment>? = null,
    val topComments: List<Comment>? = null,
    val userId: Long? = null,
)

/** 发表评论 / 回复评论响应 */
@Serializable
data class CommentAddResp(
    val code: Int? = null,
    val message: String? = null,
    val comment: Comment? = null,
    val data: CommentData? = null,
)

@Serializable
data class CommentData(
    val comment: Comment? = null,
    val ownerCommentId: Long? = null,
)
