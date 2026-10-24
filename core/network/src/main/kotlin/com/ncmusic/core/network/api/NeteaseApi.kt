package com.ncmusic.core.network.api

import com.ncmusic.core.network.model.AccountResp
import com.ncmusic.core.network.model.ApiStatusResp
import com.ncmusic.core.network.model.AlbumDetailResp
import com.ncmusic.core.network.model.AvatarUploadResp
import com.ncmusic.core.network.model.ArtistAlbumsResp
import com.ncmusic.core.network.model.ArtistResp
import com.ncmusic.core.network.model.CatalogueResp
import com.ncmusic.core.network.model.CommentAddResp
import com.ncmusic.core.network.model.CommentResp
import com.ncmusic.core.network.model.FmResp
import com.ncmusic.core.network.model.FollowResp
import com.ncmusic.core.network.model.FollowsResp
import com.ncmusic.core.network.model.LikelistResp
import com.ncmusic.core.network.model.LoginResp
import com.ncmusic.core.network.model.LyricResp
import com.ncmusic.core.network.model.MsgHistoryResp
import com.ncmusic.core.network.model.MsgUsersResp
import com.ncmusic.core.network.model.MvDetailResp
import com.ncmusic.core.network.model.MvUrlResp
import com.ncmusic.core.network.model.NewsongResp
import com.ncmusic.core.network.model.NosTokenAllocResp
import com.ncmusic.core.network.model.PersonalizedResp
import com.ncmusic.core.network.model.PlaylistCreateResp
import com.ncmusic.core.network.model.PlaylistDetailResp
import com.ncmusic.core.network.model.QrCheckResp
import com.ncmusic.core.network.model.QrUnikeyResp
import com.ncmusic.core.network.model.RecommendSongsResp
import com.ncmusic.core.network.model.RecordResp
import com.ncmusic.core.network.model.SearchResp
import com.ncmusic.core.network.model.SongDetailResp
import com.ncmusic.core.network.model.SongUrlResp
import com.ncmusic.core.network.model.SuggestResp
import com.ncmusic.core.network.model.TopPlaylistResp
import com.ncmusic.core.network.model.ToplistResp
import com.ncmusic.core.network.model.UserDetailResp
import com.ncmusic.core.network.model.UserLevelResp
import com.ncmusic.core.network.model.UserPlaylistResp
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * 网易云官方接口（weapi 协议）。
 *
 * 说明：
 * - 所有请求体都是明文 [WeapiBody]，加密由拦截器统一完成
 * - 接口返回统一是 JSON，code==200 表示业务成功
 * - 这里的路径全部经过实测验证（2026-10 验证通过）
 */
interface NeteaseApi {

    // ---------------- 登录 / 账号 ----------------

    /** 二维码 key（type=1 表示网页版） */
    @POST("weapi/login/qrcode/unikey")
    suspend fun qrUnikey(@Body body: WeapiBody): QrUnikeyResp

    /** 轮询二维码扫描状态（800 过期 / 801 待扫 / 802 待确认 / 803 成功） */
    @POST("weapi/login/qrcode/client/login")
    suspend fun qrCheck(@Body body: WeapiBody): QrCheckResp

    /** 手机号登录（password 字段需 md5；验证码登录则传 captcha） */
    @POST("weapi/w/login/cellphone")
    suspend fun loginCellphone(@Body body: WeapiBody): LoginResp

    /** 发送短信验证码 */
    @POST("weapi/sms/captcha/sent")
    suspend fun captchaSent(@Body body: WeapiBody): ApiStatusResp

    /** 校验短信验证码 */
    @POST("weapi/sms/captcha/verify")
    suspend fun captchaVerify(@Body body: WeapiBody): ApiStatusResp

    /** 退出登录 */
    @POST("weapi/logout")
    suspend fun logout(@Body body: WeapiBody): ApiStatusResp

    /** 登录状态 / 账号信息 */
    @POST("weapi/w/nuser/account/get")
    suspend fun account(@Body body: WeapiBody): AccountResp

    // ---------------- 发现 / 首页 ----------------

    /** 推荐歌单 */
    @POST("weapi/personalized/playlist")
    suspend fun personalizedPlaylist(@Body body: WeapiBody): PersonalizedResp

    /** 推荐新音乐 */
    @POST("weapi/personalized/newsong")
    suspend fun personalizedNewsong(@Body body: WeapiBody): NewsongResp

    /** 每日推荐歌曲（需登录） */
    @POST("weapi/v3/discovery/recommend/songs")
    suspend fun recommendSongs(@Body body: WeapiBody): RecommendSongsResp

    /** 私人FM（需登录） */
    @POST("weapi/v1/radio/get")
    suspend fun personalFm(@Body body: WeapiBody): FmResp

    /** 排行榜列表 */
    @POST("weapi/toplist")
    suspend fun toplist(@Body body: WeapiBody): ToplistResp

    /** 歌单分类 */
    @POST("weapi/playlist/catalogue")
    suspend fun playlistCatalogue(@Body body: WeapiBody): CatalogueResp

    /** 分类歌单（排行榜下方的歌单广场） */
    @POST("weapi/playlist/list")
    suspend fun topPlaylist(@Body body: WeapiBody): TopPlaylistResp

    // ---------------- 搜索 ----------------

    /** 综合搜索（cloudsearch：歌曲结构完整、带封面；type: 1 单曲 / 10 专辑 / 100 歌手 / 1000 歌单 / 1002 用户） */
    @POST("weapi/cloudsearch/get/web")
    suspend fun search(@Body body: WeapiBody): SearchResp

    /** 搜索建议 */
    @POST("weapi/search/suggest/web")
    suspend fun suggest(@Body body: WeapiBody): SuggestResp

    // ---------------- 播放 ----------------

    /** 歌曲详情 */
    @POST("weapi/v3/song/detail")
    suspend fun songDetail(@Body body: WeapiBody): SongDetailResp

    /** 播放地址（v1，level 控制音质） */
    @POST("weapi/song/enhance/player/url/v1")
    suspend fun songUrl(@Body body: WeapiBody): SongUrlResp

    /** eapi 备用通道（weapi 失败时使用） */
    @POST
    suspend fun songUrlEapi(
        @Url url: String,
        @Body body: EapiBody,
    ): SongUrlResp

    /** 歌词 */
    @POST("weapi/song/lyric")
    suspend fun lyric(@Body body: WeapiBody): LyricResp

    /** 播放记录上报（打卡） */
    @POST("weapi/feedback/weblog")
    suspend fun weblog(@Body body: WeapiBody): ApiStatusResp

    // ---------------- MV ----------------

    /** MV 详情（GET /api/v1/mv/detail?id=），包含标题、封面、时长与评论 threadId */
    @GET("api/v1/mv/detail")
    suspend fun mvDetail(@Query("id") id: Long): MvDetailResp

    /** MV 播放地址（GET /api/song/enhance/play/mv/url?id=&r=），r 为 1080/720/480/240 */
    @GET("api/song/enhance/play/mv/url")
    suspend fun mvUrl(
        @Query("id") id: Long,
        @Query("r") resolution: Int,
    ): MvUrlResp

    // ---------------- 歌单 ----------------

    /** 歌单详情（v6，包含歌曲列表，n 最大 1000） */
    @POST("weapi/v6/playlist/detail")
    suspend fun playlistDetail(@Body body: WeapiBody): PlaylistDetailResp

    /** 收藏歌单 */
    @POST("weapi/playlist/subscribe")
    suspend fun subscribePlaylist(@Body body: WeapiBody): ApiStatusResp

    /** 取消收藏歌单（独立接口，只传 id） */
    @POST("weapi/playlist/unsubscribe")
    suspend fun unsubscribePlaylist(@Body body: WeapiBody): ApiStatusResp

    /** 创建歌单 */
    @POST("weapi/playlist/create")
    suspend fun createPlaylist(@Body body: WeapiBody): PlaylistCreateResp

    /** 编辑歌单名称 */
    @POST("weapi/playlist/update/name")
    suspend fun renamePlaylist(@Body body: WeapiBody): ApiStatusResp

    /** 歌单增删歌曲（op=add/del） */
    @POST("weapi/playlist/manipulate/tracks")
    suspend fun manipulateTracks(@Body body: WeapiBody): ApiStatusResp

    /** 删除歌单 */
    @POST("weapi/playlist/delete")
    suspend fun deletePlaylist(@Body body: WeapiBody): ApiStatusResp

    /** 我喜欢的音乐 id 列表（需登录） */
    @POST("weapi/song/like/get")
    suspend fun likelist(@Body body: WeapiBody): LikelistResp

    /** 喜欢 / 取消喜欢单曲（radio/like 已被风控 -460，使用 song/like） */
    @POST("weapi/song/like")
    suspend fun likeSong(@Body body: WeapiBody): ApiStatusResp

    /** 用户歌单（自己或他人） */
    @POST("weapi/user/playlist")
    suspend fun userPlaylists(@Body body: WeapiBody): UserPlaylistResp

    // ---------------- 评论 ----------------

    /** 评论列表（分页），threadId 例如 R_SO_4_歌曲ID / R_MV_5_MVID */
    @POST("weapi/v1/resource/comments/{threadId}")
    suspend fun comments(
        @Path("threadId") threadId: String,
        @Body body: WeapiBody,
    ): CommentResp

    /** 评论点赞（data: threadId + commentId） */
    @POST("weapi/v1/comment/like")
    suspend fun likeComment(@Body body: WeapiBody): ApiStatusResp

    /** 评论取消点赞（data: threadId + commentId） */
    @POST("weapi/v1/comment/unlike")
    suspend fun unlikeComment(@Body body: WeapiBody): ApiStatusResp

    /** 发表评论（eapi 移动端通道：weapi 会被风控返回 code 250） */
    @POST
    suspend fun addCommentEapi(
        @Url url: String,
        @Body body: EapiBody,
    ): CommentAddResp

    /** 回复评论（eapi 移动端通道） */
    @POST
    suspend fun replyCommentEapi(
        @Url url: String,
        @Body body: EapiBody,
    ): CommentAddResp

    /** 删除自己的评论（eapi 移动端通道） */
    @POST
    suspend fun deleteCommentEapi(
        @Url url: String,
        @Body body: EapiBody,
    ): ApiStatusResp

    // ---------------- 用户 ----------------

    /** 用户详情 */
    @POST("weapi/v1/user/detail/{uid}")
    suspend fun userDetail(
        @Path("uid") uid: Long,
        @Body body: WeapiBody,
    ): UserDetailResp

    /** 用户等级（等级已不随 user/detail 返回，需单独请求） */
    @POST("weapi/user/level")
    suspend fun userLevel(@Body body: WeapiBody): UserLevelResp

    /** 听歌排行 / 最近播放（type: 0 所有时间 / 1 近一周） */
    @POST("weapi/v1/play/record")
    suspend fun playRecord(@Body body: WeapiBody): RecordResp

    /** 关注列表（uid 为路径参数；data: offset/limit/order） */
    @POST("weapi/user/getfollows/{uid}")
    suspend fun follows(
        @Path("uid") uid: Long,
        @Body body: WeapiBody,
    ): FollowsResp

    /** 粉丝列表（data: userId/time/limit/offset/getcounts） */
    @POST("weapi/user/getfolloweds/{uid}")
    suspend fun followeds(
        @Path("uid") uid: Long,
        @Body body: WeapiBody,
    ): FollowsResp

    /** 关注用户（weapi/user/follow/{uid}；风控时返回 code -462） */
    @POST("weapi/user/follow/{uid}")
    suspend fun followUser(
        @Path("uid") uid: Long,
        @Body body: WeapiBody,
    ): FollowResp

    /** 取消关注（weapi/user/delfollow/{uid}） */
    @POST("weapi/user/delfollow/{uid}")
    suspend fun unfollowUser(
        @Path("uid") uid: Long,
        @Body body: WeapiBody,
    ): FollowResp

    /** 申请 NOS 上传凭证（头像上传第一步） */
    @POST("weapi/nos/token/alloc")
    suspend fun nosTokenAlloc(@Body body: WeapiBody): NosTokenAllocResp

    /** 更新头像（头像上传第二步，imgid 来自 nos/token/alloc 的 docId） */
    @POST("weapi/user/avatar/upload/v1")
    suspend fun avatarUpload(@Body body: WeapiBody): AvatarUploadResp

    /**
     * 更新用户资料（昵称 / 简介 / 性别 / 生日 / 省市）。
     * 对应网页端「编辑个人资料」，参数需完整携带，否则可能被服务端拒绝。
     */
    @POST("weapi/user/profile/update")
    suspend fun updateProfile(@Body body: WeapiBody): ApiStatusResp

    // ---------------- 歌手 ----------------

    /** 歌手详情（含热门 50 首） */
    @POST("weapi/v1/artist/{id}")
    suspend fun artistDetail(
        @Path("id") id: Long,
        @Body body: WeapiBody,
    ): ArtistResp

    /** 歌手专辑列表 */
    @POST("weapi/artist/albums/{id}")
    suspend fun artistAlbums(
        @Path("id") id: Long,
        @Body body: WeapiBody,
    ): ArtistAlbumsResp

    // ---------------- 专辑 ----------------

    /** 专辑详情（专辑信息 + 曲目列表） */
    @POST("weapi/v1/album/{id}")
    suspend fun albumDetail(
        @Path("id") id: Long,
        @Body body: WeapiBody,
    ): AlbumDetailResp

    // ---------------- 私信 ----------------

    /** 私信会话列表（data: offset/limit/total） */
    @POST("weapi/msg/private/users")
    suspend fun msgPrivateUsers(@Body body: WeapiBody): MsgUsersResp

    /** 私信历史（data: userId/limit/time/total；time 为上一页最早一条的时间戳） */
    @POST("weapi/msg/private/history")
    suspend fun msgPrivateHistory(@Body body: WeapiBody): MsgHistoryResp

    /** 发送私信（eapi 移动端通道，weapi 会被风控拦截） */
    @POST
    suspend fun sendPrivateMsgEapi(
        @Url url: String,
        @Body body: EapiBody,
    ): ApiStatusResp
}
