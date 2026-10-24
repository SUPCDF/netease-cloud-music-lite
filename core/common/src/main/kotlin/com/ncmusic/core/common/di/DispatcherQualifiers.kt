package com.ncmusic.core.common.di

import javax.inject.Qualifier

/** IO 调度器（网络、磁盘） */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Default 调度器（CPU 密集型计算，如歌词解析） */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Main 调度器 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher
