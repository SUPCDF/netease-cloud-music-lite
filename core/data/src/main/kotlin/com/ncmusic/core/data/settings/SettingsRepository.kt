package com.ncmusic.core.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ncmusic.core.common.model.PlayMode
import com.ncmusic.core.common.model.SoundQuality
import com.ncmusic.core.common.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 应用设置（DataStore 持久化） */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val quality = stringPreferencesKey("sound_quality")
        val playbackSpeed = floatPreferencesKey("playback_speed")
        val playMode = stringPreferencesKey("play_mode")
        val searchHistory = stringPreferencesKey("search_history")
        val lyricsTranslation = booleanPreferencesKey("lyrics_translation")
        val eqEnabled = booleanPreferencesKey("eq_enabled")
        val eqBands = stringPreferencesKey("eq_bands")
        val msgReadTimes = stringPreferencesKey("msg_read_times")
        val beatGlow = booleanPreferencesKey("beat_glow")
        val lockLyrics = booleanPreferencesKey("lock_lyrics")
    }

    private val json = Json { ignoreUnknownKeys = true }

    // ---------------- 主题 ----------------

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[Keys.themeMode] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    val dynamicColor: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.dynamicColor] ?: true
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setDynamicColor(enable: Boolean) {
        context.settingsDataStore.edit { it[Keys.dynamicColor] = enable }
    }

    // ---------------- 播放 ----------------

    val soundQuality: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.quality] ?: SoundQuality.EXHIGH
    }

    val playbackSpeed: Flow<Float> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.playbackSpeed] ?: 1.0f
    }

    val playMode: Flow<PlayMode> = context.settingsDataStore.data.map { prefs ->
        runCatching { PlayMode.valueOf(prefs[Keys.playMode] ?: PlayMode.LOOP_ALL.name) }
            .getOrDefault(PlayMode.LOOP_ALL)
    }

    suspend fun setSoundQuality(level: String) {
        context.settingsDataStore.edit { it[Keys.quality] = level }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.settingsDataStore.edit { it[Keys.playbackSpeed] = speed }
    }

    suspend fun setPlayMode(mode: PlayMode) {
        context.settingsDataStore.edit { it[Keys.playMode] = mode.name }
    }

    // ---------------- 播放动效 ----------------

    /** 灵魂鼓点闪光（播放页背景随鼓点脉动；默认关闭） */
    val beatGlowEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.beatGlow] ?: false
    }

    suspend fun setBeatGlowEnabled(enable: Boolean) {
        context.settingsDataStore.edit { it[Keys.beatGlow] = enable }
    }

    // ---------------- 歌词 ----------------

    /** 歌词页是否显示翻译（默认显示） */
    val lyricsTranslation: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.lyricsTranslation] ?: true
    }

    suspend fun setLyricsTranslation(enable: Boolean) {
        context.settingsDataStore.edit { it[Keys.lyricsTranslation] = enable }
    }

    /** 锁屏 / 通知栏媒体卡片第二行显示当前歌词（默认开启） */
    val lockLyrics: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.lockLyrics] ?: true
    }

    suspend fun setLockLyrics(enable: Boolean) {
        context.settingsDataStore.edit { it[Keys.lockLyrics] = enable }
    }

    // ---------------- 均衡器 ----------------

    val eqEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.eqEnabled] ?: false
    }

    /** 各频段增益（毫贝，索引与设备频段一一对应） */
    val eqBands: Flow<List<Int>> = context.settingsDataStore.data.map { prefs ->
        val raw = prefs[Keys.eqBands] ?: return@map emptyList()
        runCatching {
            json.decodeFromString<List<Int>>(raw)
        }.getOrDefault(emptyList())
    }

    suspend fun setEqEnabled(enable: Boolean) {
        context.settingsDataStore.edit { it[Keys.eqEnabled] = enable }
    }

    suspend fun setEqBands(levels: List<Int>) {
        context.settingsDataStore.edit { it[Keys.eqBands] = json.encodeToString(levels) }
    }

    // ---------------- 搜索历史 ----------------

    val searchHistory: Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        val raw = prefs[Keys.searchHistory] ?: return@map emptyList()
        runCatching {
            json.decodeFromString<List<String>>(raw)
        }.getOrDefault(emptyList())
    }

    suspend fun addSearchHistory(keyword: String) {
        if (keyword.isBlank()) return
        context.settingsDataStore.edit { prefs ->
            val current = runCatching {
                prefs[Keys.searchHistory]?.let { json.decodeFromString<List<String>>(it) }
            }.getOrNull() ?: emptyList()
            val updated = (listOf(keyword) + current.filter { it != keyword }).take(MAX_HISTORY)
            prefs[Keys.searchHistory] = json.encodeToString<List<String>>(updated)
        }
    }

    suspend fun removeSearchHistory(keyword: String) {
        context.settingsDataStore.edit { prefs ->
            val current = runCatching {
                prefs[Keys.searchHistory]?.let { json.decodeFromString<List<String>>(it) }
            }.getOrNull() ?: emptyList()
            prefs[Keys.searchHistory] = json.encodeToString<List<String>>(current.filter { it != keyword })
        }
    }

    suspend fun clearSearchHistory() {
        context.settingsDataStore.edit { it.remove(Keys.searchHistory) }
    }

    // ---------------- 私信已读 ----------------

    /** 每个会话最后一次「已读」的时间（对方 uid -> 毫秒时间戳），用于本地抵消服务端未读数 */
    val messageReadTimes: Flow<Map<Long, Long>> = context.settingsDataStore.data.map { prefs ->
        val raw = prefs[Keys.msgReadTimes] ?: return@map emptyMap()
        runCatching {
            json.decodeFromString<Map<Long, Long>>(raw)
        }.getOrDefault(emptyMap())
    }

    /** 记录已把对方 uid 的会话读到了 timeMs 这条消息 */
    suspend fun markConversationRead(uid: Long, timeMs: Long) {
        if (uid <= 0L || timeMs <= 0L) return
        context.settingsDataStore.edit { prefs ->
            val current = runCatching {
                prefs[Keys.msgReadTimes]?.let { json.decodeFromString<Map<Long, Long>>(it) }
            }.getOrNull() ?: emptyMap()
            if (timeMs <= (current[uid] ?: 0L)) return@edit
            prefs[Keys.msgReadTimes] = json.encodeToString(current + (uid to timeMs))
        }
    }

    private companion object {
        const val MAX_HISTORY = 20
    }
}
