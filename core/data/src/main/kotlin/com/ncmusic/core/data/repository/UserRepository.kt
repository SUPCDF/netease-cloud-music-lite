package com.ncmusic.core.data.repository

import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.common.result.AppError
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.network.api.NeteaseApi
import com.ncmusic.core.network.api.WeapiBody
import com.ncmusic.core.network.model.FollowResp
import com.ncmusic.core.network.model.FollowsResp
import com.ncmusic.core.network.model.Profile
import com.ncmusic.core.network.model.RecordItem
import com.ncmusic.core.network.util.apiCall
import com.ncmusic.core.network.util.jsonOf
import com.ncmusic.core.network.util.requireCode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 用户仓库：资料 / 听歌排行 / 关注 */
@Singleton
class UserRepository @Inject constructor(
    private val api: NeteaseApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /**
     * 用户资料。
     *
     * 等级必须用 user/detail 响应顶层的 level（目标用户自己的等级）。
     * 之前这里调用 user/level 兜底，但该接口不接受 uid，永远返回「当前登录用户」
     * 的等级，导致打开任何人的主页都显示自己的 Lv.9（等级显示 bug）。
     */
    suspend fun profile(uid: Long): AppResult<Profile> = apiCall(io) {
        val resp = api.userDetail(uid, WeapiBody(jsonOf()))
        val base = resp.profile ?: Profile(userId = uid, nickname = Str.res(R.string.unknown_user))
        val detailLevel = resp.level?.takeIf { it > 0 }
        if (detailLevel != null) base.copy(level = detailLevel) else base
    }

    /** 当前登录用户等级（user/level，仅用于「自己」的场景） */
    suspend fun myLevel(): Int? = runCatching {
        api.userLevel(WeapiBody(jsonOf())).data?.level
    }.getOrNull()

    /**
     * 更新个人资料。
     *
     * 网易该接口要求把昵称 / 签名 / 性别 / 生日 / 省市一并携带，
     * 否则服务端可能按默认值覆盖；这里由调用方传入当前资料补齐。
     */
    suspend fun updateProfile(
        nickname: String,
        signature: String,
        gender: Int?,
        birthday: Long?,
        province: Int?,
        city: Int?,
    ): AppResult<Unit> = apiCall(io) {
        val resp = api.updateProfile(
            WeapiBody(
                jsonOf(
                    "nickname" to nickname,
                    "signature" to signature,
                    "gender" to (gender ?: 0),
                    "birthday" to (birthday ?: 0L),
                    "province" to (province ?: 0),
                    "city" to (city ?: 0),
                ),
            ),
        )
        requireCode(
            resp.code,
            resp.message ?: resp.msg ?: Str.res(R.string.profile_update_failed),
        )
    }

    /** 听歌排行（type: 0 所有时间 / 1 最近一周） */
    suspend fun playRecord(uid: Long, type: Int = 0): AppResult<List<RecordItem>> = apiCall(io) {
        val resp = api.playRecord(WeapiBody(jsonOf("uid" to uid, "type" to type)))
        if (type == 1) resp.weekData ?: emptyList() else resp.allData ?: emptyList()
    }

    /** 关注列表（分页，more 表示还有下一页） */
    suspend fun follows(
        uid: Long,
        offset: Int = 0,
        limit: Int = 30,
    ): AppResult<FollowsResp> = apiCall(io) {
        api.follows(
            uid,
            WeapiBody(jsonOf("offset" to offset, "limit" to limit, "order" to true)),
        )
    }

    /** 粉丝列表（分页，more 表示还有下一页） */
    suspend fun followeds(
        uid: Long,
        offset: Int = 0,
        limit: Int = 30,
    ): AppResult<FollowsResp> = apiCall(io) {
        api.followeds(
            uid,
            WeapiBody(
                jsonOf(
                    "userId" to uid,
                    "time" to 0,
                    "offset" to offset,
                    "limit" to limit,
                    "getcounts" to true,
                ),
            ),
        )
    }

    /**
     * 关注用户。
     *
     * 网易对关注 / 取关有风控：被拦截时返回
     * - `-462`：携带 blockText + 验证地址（需要先在浏览器 / 官方 App 完成安全验证）
     * - `250 / 301`：同样属于风控拦截（此前被误报成「需要登录」，其实是账号被要求验证）
     */
    suspend fun follow(uid: Long): AppResult<Unit> = apiCall(io) {
        checkFollowResponse(api.followUser(uid, WeapiBody(jsonOf())), Str.res(R.string.failed_to_follow))
    }

    /** 取消关注 */
    suspend fun unfollow(uid: Long): AppResult<Unit> = apiCall(io) {
        checkFollowResponse(api.unfollowUser(uid, WeapiBody(jsonOf())), Str.res(R.string.failed_to_unfollow))
    }

    /** 关注类接口统一处理：把风控响应翻译成「需要安全验证」而不是「未登录」 */
    private fun checkFollowResponse(resp: FollowResp, fallback: String) {
        when (resp.code) {
            200 -> Unit
            -462 -> throw AppError.VerificationRequired(
                tip = resp.data?.blockText,
                verifyUrl = resp.data?.url,
            )

            250, 301 -> throw AppError.VerificationRequired(
                tip = Str.res(R.string.verify_tip, resp.code ?: 0),
                verifyUrl = null,
            )

            else -> requireCode(resp.code, resp.message ?: fallback)
        }
    }
}
