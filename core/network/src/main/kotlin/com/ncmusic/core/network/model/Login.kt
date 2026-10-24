package com.ncmusic.core.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** 通用状态响应（多数写操作只返回 code） */
@Serializable
data class ApiStatusResp(
    val code: Int? = null,
    val message: String? = null,
    val msg: String? = null,
    /**
     * 各接口返回类型不统一：验证码接口返回布尔值，部分接口返回数字或对象。
     * 用 JsonElement 接收，避免类型不一致导致整包解析失败。
     */
    val data: JsonElement? = null,
)

/** 二维码 key 响应 */
@Serializable
data class QrUnikeyResp(
    val code: Int? = null,
    val unikey: String? = null,
)

/**
 * 二维码扫描状态响应
 * 800 二维码过期 / 801 等待扫码 / 802 待确认 / 803 授权成功
 */
@Serializable
data class QrCheckResp(
    val code: Int? = null,
    val message: String? = null,
    val cookie: String? = null,
    val avatarUrl: String? = null,
    val nickname: String? = null,
)

/** 搜索响应（/search/get 老接口的 result 结构） */
@Serializable
data class SearchResp(
    val code: Int? = null,
    val result: SearchResult? = null,
)

@Serializable
data class SearchResult(
    val songs: List<Song>? = null,
    val songCount: Int? = null,
    val artists: List<SearchArtist>? = null,
    val artistCount: Int? = null,
    val albums: List<SearchAlbum>? = null,
    val albumCount: Int? = null,
    val playlists: List<PlaylistBrief>? = null,
    val playlistCount: Int? = null,
    val userprofiles: List<UserBrief>? = null,
    val userprofileCount: Int? = null,
)

@Serializable
data class SearchArtist(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null,
    val albumSize: Int? = null,
    val musicSize: Int? = null,
)

@Serializable
data class SearchAlbum(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null,
    val artist: Artist? = null,
)

/** 搜索建议 */
@Serializable
data class SuggestResp(
    val code: Int? = null,
    val result: SuggestResult? = null,
)

@Serializable
data class SuggestResult(
    val songs: List<Song>? = null,
    val artists: List<SearchArtist>? = null,
    val albums: List<SearchAlbum>? = null,
    val playlists: List<PlaylistBrief>? = null,
    val order: List<String>? = null,
)
