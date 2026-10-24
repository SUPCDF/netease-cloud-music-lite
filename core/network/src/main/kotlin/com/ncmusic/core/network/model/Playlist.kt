package com.ncmusic.core.network.model

import kotlinx.serialization.Serializable

/** 歌单精简信息（列表 / 推荐 / 排行榜场景） */
@Serializable
data class PlaylistBrief(
    val id: Long = 0,
    val name: String = "",
    val coverImgUrl: String? = null,
    val picUrl: String? = null,
    @Serializable(with = FlexibleLongSerializer::class)
    val playCount: Long? = null,
    val trackCount: Int? = null,
    val description: String? = null,
    val userId: Long? = null,
    val creator: UserBrief? = null,
    val updateFrequency: String? = null,
    val subscribed: Boolean? = null,
    val specialType: Int? = null,
) {
    /** 封面（兼容两套字段名） */
    val cover: String? get() = coverImgUrl ?: picUrl
}

/** 歌单内歌曲 id 项 */
@Serializable
data class TrackId(
    val id: Long = 0,
)

/** 歌单完整信息（详情页） */
@Serializable
data class Playlist(
    val id: Long = 0,
    val name: String = "",
    val coverImgUrl: String? = null,
    val creator: UserBrief? = null,
    @Serializable(with = FlexibleLongSerializer::class)
    val playCount: Long? = null,
    val trackCount: Int? = null,
    val subscribed: Boolean? = null,
    @Serializable(with = FlexibleLongSerializer::class)
    val subscribedCount: Long? = null,
    @Serializable(with = FlexibleLongSerializer::class)
    val shareCount: Long? = null,
    @Serializable(with = FlexibleLongSerializer::class)
    val commentCount: Long? = null,
    val tags: List<String>? = null,
    val description: String? = null,
    val updateTime: Long? = null,
    val createTime: Long? = null,
    val userId: Long? = null,
    val tracks: List<Song>? = null,
    val trackIds: List<TrackId>? = null,
    val specialType: Int? = null,
    val privacy: Int? = null,
)

@Serializable
data class PlaylistDetailResp(
    val code: Int? = null,
    val playlist: Playlist? = null,
    val privileges: List<Privilege>? = null,
)

/** 推荐歌单 */
@Serializable
data class PersonalizedResp(
    val code: Int? = null,
    val result: List<PlaylistBrief>? = null,
)

/** 分类歌单项 */
@Serializable
data class CatItem(
    val name: String = "",
    val category: Int? = null,
    val hot: Boolean? = null,
    val activity: Boolean? = null,
    val resourceCount: Int? = null,
    val resourceType: Int? = null,
)

@Serializable
data class CatalogueResp(
    val code: Int? = null,
    val all: CatItem? = null,
    val sub: List<CatItem>? = null,
)

/** 分类歌单列表 */
@Serializable
data class TopPlaylistResp(
    val code: Int? = null,
    val playlists: List<PlaylistBrief>? = null,
    val total: Int? = null,
    val more: Boolean? = null,
)

/** 排行榜 */
@Serializable
data class ToplistResp(
    val code: Int? = null,
    val list: List<PlaylistBrief>? = null,
)

/** 推荐新音乐项 */
@Serializable
data class NewsongItem(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null,
    val song: Song? = null,
    val artists: List<Artist>? = null,
)

@Serializable
data class NewsongResp(
    val code: Int? = null,
    val result: List<NewsongItem>? = null,
)

/** 每日推荐歌曲 */
@Serializable
data class RecommendReason(
    val songId: Long? = null,
    val reason: String? = null,
    val reasonId: String? = null,
)

@Serializable
data class RecommendData(
    val dailySongs: List<Song>? = null,
    val orderSongs: List<Long>? = null,
    val recommendReasons: List<RecommendReason>? = null,
)

@Serializable
data class RecommendSongsResp(
    val code: Int? = null,
    val data: RecommendData? = null,
)

/** 私人FM */
@Serializable
data class FmResp(
    val code: Int? = null,
    val data: List<Song>? = null,
)

/** 我喜欢的音乐 id 列表 */
@Serializable
data class LikelistResp(
    val code: Int? = null,
    val ids: List<Long>? = null,
)

/** 用户歌单列表 */
@Serializable
data class UserPlaylistResp(
    val code: Int? = null,
    val more: Boolean? = null,
    val playlist: List<Playlist>? = null,
)

/** 创建歌单响应 */
@Serializable
data class PlaylistCreateResp(
    val code: Int? = null,
    val playlist: Playlist? = null,
    val id: Long? = null,
)
