package com.ncmusic.lite.navigation

/**
 * 全局路由表。
 * 所有页面路由以常量管理，参数拼接统一通过方法生成，避免散落的字符串拼接。
 */
object Route {

    // ---------- 底部导航（一级页面） ----------
    const val HOME = "home"
    const val MINE = "mine"

    // ---------- 二级页面 ----------
    const val LOGIN = "login"
    const val ACCOUNT = "account"
    const val SETTINGS = "settings"
    const val EQUALIZER = "equalizer"
    const val PLAYER = "player"
    const val SEARCH = "search"
    const val DAILY = "daily"
    const val RECENT_PLAY = "recent_play"
    const val RANKING = "ranking"
    const val DOWNLOADS = "downloads"

    const val PLAYLIST_DETAIL = "playlist/{playlistId}"
    fun playlistDetail(playlistId: Long): String = "playlist/$playlistId"

    const val COMMENT = "comment/{songId}"
    fun comment(songId: Long): String = "comment/$songId"

    const val MV = "mv/{mvId}"
    fun mv(mvId: Long): String = "mv/$mvId"

    const val ARTIST = "artist/{artistId}"
    fun artist(artistId: Long): String = "artist/$artistId"

    const val ALBUM = "album/{albumId}"
    fun album(albumId: Long): String = "album/$albumId"

    const val USER_LIST = "userlist/{uid}/{type}"
    fun userList(uid: Long, type: String): String = "userlist/$uid/$type"

    const val USER_PROFILE = "user/{uid}"
    fun userProfile(uid: Long): String = "user/$uid"

    const val CHAT = "chat/{uid}"
    fun chat(uid: Long): String = "chat/$uid"

    const val MESSAGES = "messages"

    const val COMMENT_ARG_SONG_ID = "songId"
    const val MV_ARG_ID = "mvId"
    const val PLAYLIST_ARG_ID = "playlistId"
    const val ARTIST_ARG_ID = "artistId"
    const val ALBUM_ARG_ID = "albumId"
    const val USER_LIST_ARG_UID = "uid"
    const val USER_LIST_ARG_TYPE = "type"
    const val USER_PROFILE_ARG_UID = "uid"
    const val CHAT_ARG_UID = "uid"
}
