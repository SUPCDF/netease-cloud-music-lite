package com.ncmusic.core.data.repository

import com.ncmusic.core.common.di.IoDispatcher
import com.ncmusic.core.common.result.AppError
import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.network.api.NeteaseApi
import com.ncmusic.core.network.api.WeapiBody
import com.ncmusic.core.network.model.Album
import com.ncmusic.core.network.model.Artist
import com.ncmusic.core.network.model.Song
import com.ncmusic.core.network.util.apiCall
import com.ncmusic.core.network.util.jsonOf
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import com.ncmusic.core.common.R
import com.ncmusic.core.common.i18n.Str

/** 歌手页数据（详情 + 热门 50 首） */
data class ArtistDetail(
    val artist: Artist,
    val hotSongs: List<Song>,
)

/** 歌手专辑分页 */
data class ArtistAlbumPage(
    val albums: List<Album>,
    val more: Boolean,
)

/** 歌手仓库：详情 / 专辑 */
@Singleton
class ArtistRepository @Inject constructor(
    private val api: NeteaseApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** 歌手详情（一次拿回资料 + 热门 50 首） */
    suspend fun detail(id: Long): AppResult<ArtistDetail> = apiCall(io) {
        val resp = api.artistDetail(id, WeapiBody(jsonOf()))
        val artist = resp.artist ?: throw AppError.Api(-404, Str.res(R.string.artist_not_found_or_unavailable))
        ArtistDetail(
            artist = artist,
            hotSongs = resp.hotSongs ?: emptyList(),
        )
    }

    /** 歌手专辑列表（分页） */
    suspend fun albums(
        id: Long,
        offset: Int = 0,
        limit: Int = 30,
    ): AppResult<ArtistAlbumPage> = apiCall(io) {
        val resp = api.artistAlbums(
            id,
            WeapiBody(jsonOf("limit" to limit, "offset" to offset, "total" to true)),
        )
        ArtistAlbumPage(
            albums = resp.hotAlbums ?: emptyList(),
            more = resp.more ?: false,
        )
    }
}
