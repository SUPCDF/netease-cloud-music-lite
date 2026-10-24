package com.ncmusic.core.network

import com.ncmusic.core.network.interceptor.withReplacedFormBody
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.BufferedSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 回归测试：移动端 eapi 请求在网络拦截器阶段替换请求体后，
 * Content-Type / Content-Length / Transfer-Encoding 必须与新请求体一致。
 *
 * 背景：评论发表等写操作通过 MobileEapiInterceptor（网络拦截器）把明文 JSON 体
 * 替换为加密后的 form 体。BridgeInterceptor 在进入网络拦截器之前，已按旧请求体
 * 写好了 `Content-Type: application/json` 和 `Transfer-Encoding: chunked`；
 * OkHttp 4.12 在替换 body 后**不会自动更新这些头**。若不同步修正，网易 eapi
 * 服务器会收到「JSON 声明 + chunked 编码的 form 报文」并返回空响应
 * （表现为评论发送失败，报 JSON "EOF" 解析错误）。
 */
class MobileEapiBodySwapTest {

    private val jsonBody = object : RequestBody() {
        override fun contentType() = "application/json".toMediaType()

        override fun writeTo(sink: BufferedSink) {
            sink.writeUtf8("""{"threadId":"R_SO_4_1","content":"hi"}""")
        }
    }

    private val params = "X".repeat(600)
    private val expectedBody = "params=$params"

    private fun clientWithNetworkSwap(swap: (Request) -> Request) = OkHttpClient.Builder()
        .addNetworkInterceptor { chain -> chain.proceed(swap(chain.request())) }
        .build()

    private fun request(server: MockWebServer) = Request.Builder()
        .url(server.url("/eapi/v1/resource/comments/add"))
        .post(jsonBody)
        .build()

    private fun runExchange(swap: (Request) -> Request): okhttp3.mockwebserver.RecordedRequest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.start()
        try {
            clientWithNetworkSwap(swap).newCall(request(server)).execute().close()
            return server.takeRequest()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `replaced body is sent with corrected headers`() {
        // 与 MobileEapiInterceptor 相同的处理方式（生产代码路径）
        val recorded = runExchange { request ->
            request.withReplacedFormBody(FormBody.Builder().add("params", params).build())
        }

        assertEquals("application/x-www-form-urlencoded", recorded.getHeader("Content-Type"))
        assertNull(recorded.getHeader("Transfer-Encoding"))
        assertEquals(expectedBody.length.toLong(), recorded.bodySize)
        assertEquals(expectedBody, recorded.body.readUtf8())
    }

    @Test
    fun `stale headers remain when body is replaced without header fix`() {
        // 仅替换 body、不修正头部 —— 即修复前的行为。
        // 该用例记录 OkHttp 的行为：旧头（json + chunked）会残留，
        // 因此网络拦截器替换 body 时必须手动修正，否则请求形态非法。
        val recorded = runExchange { request ->
            request.newBuilder()
                .post(FormBody.Builder().add("params", params).build())
                .build()
        }

        assertEquals("application/json", recorded.getHeader("Content-Type"))
        assertEquals("chunked", recorded.getHeader("Transfer-Encoding"))
    }
}
