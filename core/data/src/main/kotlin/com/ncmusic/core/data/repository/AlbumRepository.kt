package com.ncmusic.core.data.repository

import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.common.result.AppError
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.network.api.NeteaseApi
import com.ncmusic.core.network.api.WeapiBody
import com.ncmusic.core.network.model.Album
import com.ncmusic.core.network.model.Song
import com.ncmusic.core.network.util.apiCall
import com.ncmusic.core.network.util.jsonOf
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 专辑详情（专辑信息 + 曲目） */
data class AlbumDetail(
    val album: Album,
    val songs: List<Song>,
)

/** 专辑仓库：详情（一次拿回专辑信息与曲目列表） */
@Singleton
class AlbumRepository @Inject constructor(
    private val api: NeteaseApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun detail(id: Long): AppResult<AlbumDetail> = apiCall(io) {
        val resp = api.albumDetail(id, WeapiBody(jsonOf()))
        val album = resp.album ?: throw AppError.Api(-404, Str.res(R.string.album_not_found_or_unavailable))
        AlbumDetail(
            album = album,
            songs = resp.songs ?: emptyList(),
        )
    }
}
