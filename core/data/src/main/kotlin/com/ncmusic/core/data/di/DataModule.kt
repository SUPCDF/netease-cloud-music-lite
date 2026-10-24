package com.ncmusic.core.data.di

import android.content.Context
import androidx.room.Room
import com.ncmusic.core.data.db.AppDatabase
import com.ncmusic.core.data.db.PlayHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 数据层依赖注入（Room） */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ncmusic.db").build()

    @Provides
    fun providePlayHistoryDao(database: AppDatabase): PlayHistoryDao = database.playHistoryDao()
}
