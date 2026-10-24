package com.ncmusic.core.network.crypto

import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云官方接口加密实现（仅供个人学习研究）。
 *
 * 共三种协议：
 * - weapi：AES-CBC 两轮（预设密钥 + 随机密钥），随机密钥用 RSA 公钥加密（无填充，左补零到 128 字节）
 * - eapi ：AES-ECB（固定密钥），报文带 md5 摘要
 * - 所有密钥均为公开的协议常量，不涉及任何账号数据
 */
object NeteaseCrypto {

    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"
    private const val EAPI_KEY = "e82ckenh8dichen8"

    private const val MODULUS_HEX =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"
    private const val EXPONENT_HEX = "010001"

    private const val EAPI_SEPARATOR = "-36cd479b6b5-"

    /** weapi 加密结果：params + encSecKey（均为 Base64） */
    data class WeapiParams(val params: String, val encSecKey: String)

    /** weapi 加密：dataJson 为明文 JSON 字符串 */
    fun weapi(dataJson: String): WeapiParams = weapiWithKey(dataJson, randomSecretKey())

    /** weapi 加密（指定随机密钥，仅供单元测试交叉验证使用） */
    internal fun weapiWithKey(dataJson: String, secretKey: String): WeapiParams {
        val params = aesCbcEncrypt(aesCbcEncrypt(dataJson, PRESET_KEY), secretKey)
        val encSecKey = rsaEncrypt(secretKey)
        return WeapiParams(params = params, encSecKey = encSecKey)
    }

    /**
     * eapi 加密。
     *
     * @param path  参与摘要计算的接口路径（如 /api/song/enhance/player/url/v1）
     * @param dataJson 明文 JSON 字符串
     * @return 十六进制大写密文
     */
    fun eapi(path: String, dataJson: String): String {
        val message = "nobody${path}use${dataJson}md5forencrypt"
        val digest = md5Hex(message)
        val payload = "$path$EAPI_SEPARATOR$dataJson$EAPI_SEPARATOR$digest"
        return aesEcbEncryptHex(payload, EAPI_KEY)
    }

    // ---------------- 基础算法 ----------------

    /** 生成 16 个 hex 字符（8 字节）的随机密钥 */
    private fun randomSecretKey(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** AES-128-CBC 加密 -> Base64 */
    private fun aesCbcEncrypt(text: String, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(IV.toByteArray(Charsets.UTF_8)),
        )
        return Base64.getEncoder().encodeToString(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }

    /** AES-128-ECB 加密 -> 十六进制大写 */
    private fun aesEcbEncryptHex(text: String, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        return cipher.doFinal(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    }

    /**
     * 原始 RSA 加密（无填充）：
     * 明文反转 -> 左补零到 128 字节（1024 位公钥）-> modPow
     * 等价于 JS 里的 m^e mod n 大数运算
     */
    private fun rsaEncrypt(text: String): String {
        val reversed = text.reversed().toByteArray(Charsets.UTF_8)
        val padded = ByteArray(128)
        System.arraycopy(reversed, 0, padded, 128 - reversed.size, reversed.size)

        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            KeyFactory.getInstance("RSA").generatePublic(
                RSAPublicKeySpec(BigInteger(MODULUS_HEX, 16), BigInteger(EXPONENT_HEX, 16)),
            ),
        )
        return cipher.doFinal(padded).joinToString("") { "%02x".format(it) }
    }

    /** MD5 -> 十六进制小写 */
    fun md5Hex(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
