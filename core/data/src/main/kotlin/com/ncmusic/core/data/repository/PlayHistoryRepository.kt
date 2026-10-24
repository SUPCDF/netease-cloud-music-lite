package com.ncmusic.core.data.repository

import com.ncmusic.core.common.result.AppResult
import com.ncmusic.core.data.db.PlayHistoryDao
import com.ncmusic.core.data.db.PlayHistoryEntity
import com.ncmusic.core.network.model.Album
import com.ncmusic.core.network.model.Artist
import com.ncmusic.core.network.model.Song
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 本地播放历史仓库（Room） */
@Singleton
class PlayHistoryRepository @Inject constructor(
    private val dao: PlayHistoryDao,
) {

    /** 记录一次播放 */
    suspend fun record(song: Song) = withContext(Dispatchers.IO) {
        dao.insert(
            PlayHistoryEntity(
                songId = song.id,
                name = song.name,
                artist = song.artistText,
                coverUrl = song.coverUrl,
                playedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** 最近播放（映射回 Song 以便直接播放） */
    suspend fun recentSongs(limit: Int = 100): AppResult<List<Song>> = withContext(Dispatchers.IO) {
        val entities = dao.recent(limit)
        AppResult.Success(
            entities.map { entity ->
                Song(
                    id = entity.songId,
                    name = entity.name,
                    artists = listOf(Artist(name = entity.artist)),
                    al = Album(name = "", picUrl = entity.coverUrl),
                )
            },
        )
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dao.clear()
    }
}
