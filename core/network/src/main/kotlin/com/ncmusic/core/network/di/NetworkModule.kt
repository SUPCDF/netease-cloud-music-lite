package com.ncmusic.core.network.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.ncmusic.core.network.BuildConfig
import com.ncmusic.core.network.api.NeteaseApi
import com.ncmusic.core.network.cookie.CookieStore
import com.ncmusic.core.network.interceptor.FollowCookieInterceptor
import com.ncmusic.core.network.interceptor.MobileEapiInterceptor
import com.ncmusic.core.network.interceptor.NeteaseEncryptInterceptor
import com.ncmusic.core.network.interceptor.NeteaseHeaderInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

/** 网络层依赖注入 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    const val BASE_URL = "https://music.163.com/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideCookieStore(@ApplicationContext context: Context): CookieStore = CookieStore(context)

    @Provides
    @Singleton
    fun provideOkHttpClient(cookieStore: CookieStore): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .cookieJar(cookieStore.cookieJar())
            .addInterceptor(NeteaseHeaderInterceptor())
            .addInterceptor(NeteaseEncryptInterceptor(cookieStore))
            .addNetworkInterceptor(MobileEapiInterceptor(cookieStore))
            .addNetworkInterceptor(FollowCookieInterceptor())
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideNeteaseApi(retrofit: Retrofit): NeteaseApi = retrofit.create(NeteaseApi::class.java)
}
