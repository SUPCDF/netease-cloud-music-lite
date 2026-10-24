package com.ncmusic.core.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/** 本地播放历史 */
@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val songId: Long,
    val name: String,
    val artist: String,
    val coverUrl: String?,
    val playedAt: Long,
)

@Dao
interface PlayHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PlayHistoryEntity)

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<PlayHistoryEntity>

    @Query("DELETE FROM play_history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM play_history")
    suspend fun count(): Int
}

@Database(entities = [PlayHistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playHistoryDao(): PlayHistoryDao
}
