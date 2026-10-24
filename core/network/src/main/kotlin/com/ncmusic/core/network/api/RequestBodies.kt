package com.ncmusic.core.network.api

import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink

/**
 * weapi 明文请求体。
 * 仓库层只负责组装明文 JSON 参数，加密由 NeteaseEncryptInterceptor 统一完成。
 */
class WeapiBody(val data: JsonObject) : RequestBody() {

    override fun contentType(): MediaType = JSON_MEDIA_TYPE

    override fun writeTo(sink: BufferedSink) {
        sink.writeUtf8(data.toString())
    }
}

/**
 * eapi 明文请求体。
 *
 * @param digestPath 参与摘要计算的路径（通常是 /api/xxx 形式，与实际 HTTP 路径可以不同）
 * @param mobile 是否模拟官方移动端请求（补充设备 header、移动端 UA / Cookie）。
 *               评论发表等写操作走 weapi 会触发网易风控（code 250），必须使用移动端通道。
 */
class EapiBody(
    val digestPath: String,
    val data: JsonObject,
    val mobile: Boolean = false,
) : RequestBody() {

    override fun contentType(): MediaType = JSON_MEDIA_TYPE

    override fun writeTo(sink: BufferedSink) {
        sink.writeUtf8(data.toString())
    }
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
