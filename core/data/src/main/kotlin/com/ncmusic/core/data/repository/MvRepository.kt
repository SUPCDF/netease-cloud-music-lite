package com.ncmusic.core.data.repository

import com.ncmusic.core.common.R
import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.common.i18n.Str
import com.ncmusic.core.common.result.AppError
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.network.api.NeteaseApi
import com.ncmusic.core.network.model.MvDetail
import com.ncmusic.core.network.model.MvUrlItem
import com.ncmusic.core.network.util.apiCall
import com.ncmusic.core.network.util.requireCode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

/** MV 详情与播放地址 */
@Singleton
class MvRepository @Inject constructor(
    private val api: NeteaseApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** 获取 MV 详情；data 缺失时视为不可用 */
    suspend fun detail(mvId: Long): AppResult<MvDetail> = apiCall(io) {
        val resp = api.mvDetail(mvId)
        requireCode(resp.code, Str.res(R.string.mv_load_failed))
        resp.data ?: throw AppError.Api(resp.code ?: -1, Str.res(R.string.mv_unavailable))
    }

    /**
     * 获取 MV 播放地址。
     * 接口支持 1080/720/480/240，账号或版权限制时高清晰度可能返回空地址，依次降级。
     */
    suspend fun videoUrl(mvId: Long): AppResult<MvUrlItem> = apiCall(io) {
        for (resolution in RESOLUTIONS) {
            val resp = api.mvUrl(mvId, resolution)
            val item = resp.data
            if (resp.code == 200 && !item?.url.isNullOrBlank()) return@apiCall item
        }
        throw AppError.Api(-1, Str.res(R.string.mv_unavailable))
    }

    private companion object {
        // 默认 720p，兼顾清晰度与手机流量；高清晰度为空时再降级/兜底
        val RESOLUTIONS = intArrayOf(720, 480, 1080, 240)
    }
}
