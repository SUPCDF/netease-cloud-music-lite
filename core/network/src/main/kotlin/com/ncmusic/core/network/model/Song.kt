package com.ncmusic.core.network.model

import kotlinx.serialization.Serializable

/** 歌手（列表精简 / 歌手页详情共用） */
@Serializable
data class Artist(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null,
    val img1v1Url: String? = null,
    val briefDesc: String? = null,
    val albumSize: Int? = null,
    val musicSize: Int? = null,
    val followed: Boolean? = null,
    val alias: List<String>? = null,
)

/** 专辑（精简 / 歌手专辑列表 / 专辑详情共用） */
@Serializable
data class Album(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null,
    val size: Int? = null,
    val publishTime: Long? = null,
    val artist: Artist? = null,
    val company: String? = null,
    val description: String? = null,
    val briefDesc: String? = null,
)

/** 歌曲权限信息（音质 / 付费状态） */
@Serializable
data class Privilege(
    val id: Long = 0,
    val fee: Int? = null,
    val payed: Int? = null,
    val st: Int? = null,
    val pl: Long? = null,
    val dl: Long? = null,
    val sp: Int? = null,
    val cp: Int? = null,
    val subp: Int? = null,
    val flag: Int? = null,
    val maxbr: Long? = null,
    val fl: Long? = null,
    val toast: Boolean? = null,
    val preSell: Boolean? = null,
)

/**
 * 歌曲模型。
 * 不同接口返回的字段名不一致（ar/artists、al/album、dt/duration），
 * 这里全部兼容，通过扩展属性统一取值。
 */
@Serializable
data class Song(
    val id: Long = 0,
    val name: String = "",
    val ar: List<Artist>? = null,
    val artists: List<Artist>? = null,
    val al: Album? = null,
    val album: Album? = null,
    val dt: Long? = null,
    val duration: Long? = null,
    val fee: Int? = null,
    val mv: Long? = null,
    val mvid: Long? = null,
    val no: Int? = null,
    val alias: List<String>? = null,
    val alia: List<String>? = null,
    val privilege: Privilege? = null,
) {
    /** 歌手列表（兼容 ar / artists 两种字段） */
    val artistList: List<Artist>
        get() = ar ?: artists ?: emptyList()

    /** 歌手名（多选题取斜杠分隔） */
    val artistText: String
        get() = artistList.joinToString("/") { it.name }

    /** 专辑名 */
    val albumName: String
        get() = (al ?: album)?.name ?: ""

    /** 封面图 */
    val coverUrl: String?
        get() = (al ?: album)?.picUrl

    /** 时长（毫秒） */
    val durationMs: Long
        get() = dt ?: duration ?: 0L

    /** 别名文案（副标题） */
    val aliasText: String?
        get() = (alias ?: alia)?.firstOrNull()

    /** MV id（0 表示没有 MV） */
    val musicVideoId: Long
        get() = listOfNotNull(mv, mvid).firstOrNull { it > 0L } ?: 0L
}

@Serializable
data class SongDetailResp(
    val code: Int? = null,
    val songs: List<Song>? = null,
    val privileges: List<Privilege>? = null,
)

@Serializable
data class FreeTrialInfo(
    val start: Long? = null,
    val end: Long? = null,
)

@Serializable
data class FreeTrialPrivilege(
    val resConsumable: Boolean? = null,
    val userConsumable: Boolean? = null,
    val listenType: Long? = null,
    val cannotListenReason: Long? = null,
)

/** 播放地址（/song/enhance/player/url/v1 返回的 data 数组元素） */
@Serializable
data class SongUrlItem(
    val id: Long = 0,
    val url: String? = null,
    val br: Long? = null,
    val size: Long? = null,
    val md5: String? = null,
    val code: Int? = null,
    val expi: Int? = null,
    val type: String? = null,
    val time: Long? = null,
    val fee: Int? = null,
    val payed: Int? = null,
    val flag: Int? = null,
    val level: String? = null,
    val encodeType: String? = null,
    val freeTrialInfo: FreeTrialInfo? = null,
    val freeTrialPrivilege: FreeTrialPrivilege? = null,
)

@Serializable
data class SongUrlResp(
    val code: Int? = null,
    val data: List<SongUrlItem>? = null,
)

@Serializable
data class Lrc(
    val version: Int? = null,
    val lyric: String? = null,
)

@Serializable
data class LyricResp(
    val code: Int? = null,
    val lrc: Lrc? = null,
    val tlyric: Lrc? = null,
    val klyric: Lrc? = null,
)

/** 歌手详情响应（/v1/artist/{id}，含热门 50 首） */
@Serializable
data class ArtistResp(
    val code: Int? = null,
    val artist: Artist? = null,
    val hotSongs: List<Song>? = null,
)

/** 歌手专辑列表响应（/artist/albums/{id}） */
@Serializable
data class ArtistAlbumsResp(
    val code: Int? = null,
    val more: Boolean? = null,
    val hotAlbums: List<Album>? = null,
)

/** 专辑详情响应（/v1/album/{id}） */
@Serializable
data class AlbumDetailResp(
    val code: Int? = null,
    val album: Album? = null,
    val songs: List<Song>? = null,
)
