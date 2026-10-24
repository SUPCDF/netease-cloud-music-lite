package com.ncmusic.core.network.model

import kotlinx.serialization.Serializable

/** MV 清晰度信息（仅用于展示 / 判断可用清晰度） */
@Serializable
data class MvBr(
    val size: Double? = null,
    val br: Int? = null,
    val point: Int? = null,
)

/** MV 详情（/api/v1/mv/detail） */
@Serializable
data class MvDetail(
    val id: Long = 0,
    val name: String = "",
    val artistId: Long = 0,
    val artistName: String = "",
    val cover: String? = null,
    val duration: Long = 0,
    val playCount: Long? = null,
    val commentCount: Long? = null,
    val commentThreadId: String? = null,
    val publishTime: String? = null,
    val desc: String? = null,
    val artists: List<Artist>? = null,
    val brs: List<MvBr>? = null,
)

@Serializable
data class MvDetailResp(
    val code: Int? = null,
    val data: MvDetail? = null,
)

/** MV 播放地址 */
@Serializable
data class MvUrlItem(
    val id: Long = 0,
    val url: String? = null,
    val r: Int? = null,
    val size: Double? = null,
    val md5: String? = null,
    val code: Int? = null,
    val expi: Long? = null,
    val fee: Int? = null,
    val mvFee: Int? = null,
    val msg: String? = null,
)

@Serializable
data class MvUrlResp(
    val code: Int? = null,
    val data: MvUrlItem? = null,
)
