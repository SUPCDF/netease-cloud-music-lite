package com.ncmusic.core.data.model

import com.ncmusic.core.network.model.SearchAlbum
import com.ncmusic.core.network.model.SearchArtist
import com.ncmusic.core.network.model.Song
import com.ncmusic.core.network.model.PlaylistBrief
import com.ncmusic.core.network.model.UserBrief

/** 分页数据 */
data class Paged<T>(
    val items: List<T>,
    val more: Boolean,
)

/** 搜索结果（各类型可同时存在） */
data class SearchResultData(
    val songs: List<Song> = emptyList(),
    val artists: List<SearchArtist> = emptyList(),
    val albums: List<SearchAlbum> = emptyList(),
    val playlists: List<PlaylistBrief> = emptyList(),
    val users: List<UserBrief> = emptyList(),
    val more: Boolean = false,
)
