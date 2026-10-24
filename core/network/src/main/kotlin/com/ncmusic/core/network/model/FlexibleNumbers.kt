package com.ncmusic.core.network.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 宽松 Long 解析器。
 *
 * 网易部分接口会把大数字（如歌单播放量 150925568）序列化成浮点科学计数法（1.50925568E8），
 * kotlinx.serialization 的严格模式会直接抛异常，导致整个响应解析失败。
 * 这里统一兼容以下形式：
 * - 整数字面量：123
 * - 科学计数法：1.50925568E8
 * - 字符串数字："123"
 * - null 值
 */
object FlexibleLongSerializer : KSerializer<Long?> {

    override val descriptor =
        PrimitiveSerialDescriptor("com.ncmusic.FlexibleLong", PrimitiveKind.LONG).nullable

    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeLong(value)
        }
    }

    override fun deserialize(decoder: Decoder): Long? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeLong()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return null
        val content = element.jsonPrimitive.content
        return content.toLongOrNull() ?: content.toDoubleOrNull()?.toLong()
    }
}
