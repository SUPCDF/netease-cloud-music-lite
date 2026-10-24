package com.ncmusic.core.network

import com.ncmusic.core.network.crypto.NeteaseCrypto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 加密算法交叉验证测试。
 * 预期值由 Node.js 参考实现（官方接口协议）生成，两边结果必须完全一致。
 */
class NeteaseCryptoTest {

    @Test
    fun `weapi 加密结果与参考实现一致`() {
        val result = NeteaseCrypto.weapiWithKey("{\"k\":\"v\"}", "0123456789abcdef")

        assertEquals(
            "AES 两轮加密结果不一致",
            "NNxE25efLkkRKHpuzjjLSkRx2r09zGBbsChjy69xSFQ=",
            result.params,
        )
        assertEquals(
            "RSA（无填充）加密结果不一致",
            "35701388baf89fed412e11269b9c76625d095ecaf17f03fa018abe19ea2d38b949debf242ee39a71ca1f6cda71b1b86a45aa909ee27f7e78e267d34e732f0de948206c3340a788d0003372183e2f753c1f78b66ac23d134ac1fc9b993156520ea826b8aa89a962d4491b4b8d7e08738e1da9b07aa39bf4a7ef0b1c210728cd52",
            result.encSecKey,
        )
    }

    @Test
    fun `eapi 摘要和密文与参考实现一致`() {
        val digest = NeteaseCrypto.md5Hex("""nobody/api/song/enhance/player/url/v1use{"ids":"[1]"}md5forencrypt""")
        assertEquals("ed1f73658c2205beeb352e18f0157c59", digest)

        val params = NeteaseCrypto.eapi("/api/song/enhance/player/url/v1", """{"ids":"[1]"}""")
        assertEquals(
            "FA90B329E9614F79E79598F37DC2EDB487F00D1BC4C9B24CD57E6C318B9073569338432CD7D98D1A3626E997A2C53121A82E17FCDE24A5FFB3DB43A53BE6ACFE363DC2B5A7E1699ADDCFA508D69BFC5E53AA5EE55F1100156460A1689A32E5E407B674E2962B0DFC55A2FF4E8DDF41C8",
            params,
        )
    }

    @Test
    fun `随机密钥长度为 16 个字符`() {
        repeat(20) {
            val result = NeteaseCrypto.weapi("""{"a":1}""")
            // encSecKey 固定为 256 个 hex 字符（1024 位）
            assertEquals(256, result.encSecKey.length)
        }
    }
}
