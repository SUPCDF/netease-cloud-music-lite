package com.ncmusic.core.data.repository

import com.ncmusic.core.common.result.AppError
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.data.account.AccountCache
import com.ncmusic.core.network.api.NeteaseApi
import com.ncmusic.core.network.api.WeapiBody
import com.ncmusic.core.network.cookie.CookieStore
import com.ncmusic.core.network.crypto.NeteaseCrypto
import com.ncmusic.core.network.model.Account
import com.ncmusic.core.network.model.AccountResp
import com.ncmusic.core.network.model.Profile
import com.ncmusic.core.network.model.QrCheckResp
import com.ncmusic.core.network.util.apiCall
import com.ncmusic.core.network.util.jsonOf
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 登录相关仓库：二维码 / 手机号密码 / 验证码 / 登录态 */
@Singleton
class LoginRepository @Inject constructor(
    private val api: NeteaseApi,
    private val cookieStore: CookieStore,
    private val accountCache: AccountCache,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** 获取二维码 key（type=1 网页版） */
    suspend fun qrUnikey(): AppResult<String> = apiCall(io) {
        val resp = api.qrUnikey(WeapiBody(jsonOf("type" to 1)))
        resp.unikey ?: throw AppError.Api(resp.code ?: -1, Str.res(R.string.failed_to_get_qr_code))
    }

    /** 轮询二维码状态 */
    suspend fun qrCheck(key: String): AppResult<QrCheckResp> = apiCall(io) {
        api.qrCheck(WeapiBody(jsonOf("key" to key, "type" to 1)))
    }

    /** 手机号 + 密码登录（密码本地 md5 后上传） */
    suspend fun loginByPassword(phone: String, password: String): AppResult<Profile> = apiCall(io) {
        val resp = api.loginCellphone(
            WeapiBody(
                jsonOf(
                    "phone" to phone,
                    "countrycode" to "86",
                    "password" to NeteaseCrypto.md5Hex(password),
                    "rememberLogin" to "true",
                ),
            ),
        )
        if (resp.code != 200) {
            throw AppError.Api(resp.code ?: -1, resp.message ?: resp.msg ?: Str.res(R.string.login_failed))
        }
        val profile = resp.profile ?: throw AppError.Api(resp.code ?: -1, Str.res(R.string.login_failed))
        accountCache.saveProfile(profile)
        profile
    }

    /** 手机号 + 验证码登录 */
    suspend fun loginByCaptcha(phone: String, captcha: String): AppResult<Profile> = apiCall(io) {
        val resp = api.loginCellphone(
            WeapiBody(
                jsonOf(
                    "phone" to phone,
                    "countrycode" to "86",
                    "captcha" to captcha,
                    "rememberLogin" to "true",
                ),
            ),
        )
        if (resp.code != 200) {
            throw AppError.Api(resp.code ?: -1, resp.message ?: resp.msg ?: Str.res(R.string.code_login_failed))
        }
        val profile = resp.profile ?: Profile()
        accountCache.saveProfile(profile)
        profile
    }

    /** 发送短信验证码 */
    suspend fun sendCaptcha(phone: String): AppResult<Unit> = apiCall(io) {
        val resp = api.captchaSent(WeapiBody(jsonOf("cellphone" to phone, "ctcode" to "86")))
        if (resp.code != 200) {
            throw AppError.Api(resp.code ?: -1, resp.message ?: resp.msg ?: Str.res(R.string.failed_to_send_code))
        }
    }

    /** 校验短信验证码 */
    suspend fun verifyCaptcha(phone: String, captcha: String): AppResult<Unit> = apiCall(io) {
        val resp = api.captchaVerify(WeapiBody(jsonOf("cellphone" to phone, "ctcode" to "86", "captcha" to captcha)))
        if (resp.code != 200) {
            throw AppError.Api(resp.code ?: -1, resp.message ?: resp.msg ?: Str.res(R.string.code_verification_failed))
        }
    }

    /** 退出登录（清空本地 Cookie 与账号缓存） */
    suspend fun logout(): AppResult<Unit> = apiCall(io) {
        runCatching { api.logout(WeapiBody(jsonOf())) }
        cookieStore.clear()
        accountCache.clear()
    }

    /**
     * 登录状态（服务端）。
     *
     * 网络失败时回退到本地缓存：离线也要认得出「已登录」，
     * 否则「我的」页会瞬间变成未登录，下载 / 最近播放等入口全部消失。
     */
    suspend fun account(): AppResult<AccountResp> {
        val result = apiCall(io) { api.account(WeapiBody(jsonOf())) }
        return when (result) {
            is AppResult.Success -> {
                val resp = result.data
                val account = resp.account
                if (account != null) {
                    accountCache.save(account, resp.profile ?: accountCache.profile())
                } else if (!cookieStore.hasMusicU() || resp.code == 301 || resp.code == 302) {
                    // 服务端明确返回未登录 / 登录过期，清掉本地缓存
                    accountCache.clear()
                }
                result
            }

            is AppResult.Failure -> {
                val cachedAccount = accountCache.account()
                if (cachedAccount != null) {
                    AppResult.Success(
                        AccountResp(
                            code = 200,
                            account = cachedAccount,
                            profile = accountCache.profile(),
                        ),
                    )
                } else {
                    result
                }
            }
        }
    }

    /** 本地缓存的账号（同步读取，用于首帧判断登录态） */
    fun cachedAccount(): Account? = accountCache.account()

    /** 本地缓存的个人资料（同步读取，用于首帧展示头像 / 昵称） */
    fun cachedProfile(): Profile? = accountCache.profile()

    /** 资料 / 头像变更后同步缓存，离线打开「我的」页也能看到最新内容 */
    fun cacheProfile(profile: Profile) = accountCache.saveProfile(profile)

    /** 本地是否有登录态（Cookie 或账号缓存任一存在即可） */
    fun isLoggedInLocally(): Boolean =
        cookieStore.hasMusicU() || accountCache.account() != null
}
