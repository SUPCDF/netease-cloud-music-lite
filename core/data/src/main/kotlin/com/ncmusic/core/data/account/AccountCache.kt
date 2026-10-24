package com.ncmusic.core.data.account

import android.content.Context
import com.ncmusic.core.network.model.Account
import com.ncmusic.core.network.model.Playlist
import com.ncmusic.core.network.model.Profile
import com.ncmusic.core.network.model.UserBrief
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 登录账号 + 个人资料的本地缓存。
 *
 * 目的：
 * 1. 冷启动 / 弱网时「我的」页立即显示已登录和头像昵称，不再先闪一下未登录；
 * 2. 完全离线时仍能进入下载、最近播放等本地功能，而不是被当成未登录而全部隐藏。
 *
 * 用 SharedPreferences 而不是 DataStore：需要同步读取，才能保证首帧就是已登录状态。
 */
@Singleton
class AccountCache @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.getSharedPreferences("ncmusic_account", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedAccount: Account? = null

    @Volatile
    private var cachedProfile: Profile? = null

    @Volatile
    private var cachedPlaylists: List<Playlist>? = null

    @Volatile
    private var cachedFollowing: List<UserBrief>? = null

    @Volatile
    private var cachedFollowers: List<UserBrief>? = null

    init {
        reload()
    }

    fun account(): Account? = cachedAccount

    fun profile(): Profile? = cachedProfile

    /** 我的歌单（自己创建 + 收藏，第一次抓取后整份缓存） */
    fun playlists(): List<Playlist>? = cachedPlaylists

    /** 我的关注列表（第一页，离线可看） */
    fun following(): List<UserBrief>? = cachedFollowing

    /** 我的粉丝列表（第一页，离线可看） */
    fun followers(): List<UserBrief>? = cachedFollowers

    /** 缓存账号 + 资料；profile 为 null 时保留原有资料 */
    fun save(account: Account?, profile: Profile?) {
        val uid = account?.id?.takeIf { it > 0L }
            ?: profile?.userId?.takeIf { it > 0L }
            ?: return
        // 切换账号时不要沿用上一个账号的资料
        val previous = cachedProfile?.takeIf { it.userId == uid }
        val mergedProfile = (profile ?: previous)?.let { mergeProfile(previous, it) }
        val previousUid = cachedAccount?.id

        cachedAccount = Account(id = uid)
        if (mergedProfile != null) cachedProfile = mergedProfile

        val editor = prefs.edit().putLong(KEY_UID, uid)
        if (mergedProfile != null) {
            editor.putString(KEY_PROFILE, json.encodeToString(mergedProfile))
        }
        if (previousUid != null && previousUid != uid) {
            // 换号：歌单、关注、粉丝都属于上一个账号，必须清掉
            cachedPlaylists = null
            cachedFollowing = null
            cachedFollowers = null
            editor
                .remove(KEY_PLAYLISTS)
                .remove(KEY_FOLLOWING)
                .remove(KEY_FOLLOWERS)
        }
        editor.apply()
    }

    /** 只有资料变化（改昵称 / 头像 / 简介）时使用 */
    fun saveProfile(profile: Profile) {
        save(account = null, profile = profile)
    }

    fun savePlaylists(playlists: List<Playlist>) {
        if (cachedAccount == null) return
        cachedPlaylists = playlists
        prefs.edit().putString(KEY_PLAYLISTS, json.encodeToString(playlists)).apply()
    }

    fun saveFollowing(users: List<UserBrief>) {
        if (cachedAccount == null) return
        cachedFollowing = users
        prefs.edit().putString(KEY_FOLLOWING, json.encodeToString(users)).apply()
    }

    fun saveFollowers(users: List<UserBrief>) {
        if (cachedAccount == null) return
        cachedFollowers = users
        prefs.edit().putString(KEY_FOLLOWERS, json.encodeToString(users)).apply()
    }

    fun clear() {
        cachedAccount = null
        cachedProfile = null
        cachedPlaylists = null
        cachedFollowing = null
        cachedFollowers = null
        prefs.edit().clear().apply()
    }

    /**
     * 合并资料。
     *
     * `/api/v1/account` 返回的 profile 字段比 `user/detail` 少（等级 / 关注数 / 粉丝数等可能没有），
     * 直接用它会把手头更完整的缓存覆盖成空值。这里只在新值为空时保留旧值。
     */
    private fun mergeProfile(old: Profile?, incoming: Profile): Profile {
        if (old == null || old.userId != incoming.userId) return incoming
        return incoming.copy(
            avatarUrl = incoming.avatarUrl ?: old.avatarUrl,
            backgroundUrl = incoming.backgroundUrl ?: old.backgroundUrl,
            signature = incoming.signature ?: old.signature,
            follows = incoming.follows ?: old.follows,
            followeds = incoming.followeds ?: old.followeds,
            playlistCount = incoming.playlistCount ?: old.playlistCount,
            playlistsCount = incoming.playlistsCount ?: old.playlistsCount,
            level = incoming.level?.takeIf { it > 0 } ?: old.level,
            listenSongs = incoming.listenSongs?.takeIf { it > 0 } ?: old.listenSongs,
            createTime = incoming.createTime ?: old.createTime,
            gender = incoming.gender ?: old.gender,
            birthday = incoming.birthday ?: old.birthday,
            province = incoming.province ?: old.province,
            city = incoming.city ?: old.city,
            vipType = incoming.vipType ?: old.vipType,
        )
    }

    private fun reload() {
        val uid = prefs.getLong(KEY_UID, 0L)
        if (uid <= 0L) return
        cachedAccount = Account(id = uid)
        cachedProfile = prefs.getString(KEY_PROFILE, null)?.let { raw ->
            runCatching { json.decodeFromString<Profile>(raw) }.getOrNull()
        }
        cachedPlaylists = prefs.getString(KEY_PLAYLISTS, null)?.let { raw ->
            runCatching { json.decodeFromString<List<Playlist>>(raw) }.getOrNull()
        }
        cachedFollowing = prefs.getString(KEY_FOLLOWING, null)?.let { raw ->
            runCatching { json.decodeFromString<List<UserBrief>>(raw) }.getOrNull()
        }
        cachedFollowers = prefs.getString(KEY_FOLLOWERS, null)?.let { raw ->
            runCatching { json.decodeFromString<List<UserBrief>>(raw) }.getOrNull()
        }
    }

    private companion object {
        const val KEY_UID = "uid"
        const val KEY_PROFILE = "profile"
        const val KEY_PLAYLISTS = "playlists"
        const val KEY_FOLLOWING = "following"
        const val KEY_FOLLOWERS = "followers"
    }
}
