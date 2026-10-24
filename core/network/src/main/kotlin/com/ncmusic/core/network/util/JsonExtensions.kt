package com.ncmusic.core.network.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** 快速构造接口请求参数 JSON（自动映射 Kotlin 类型到 JSON 类型） */
fun jsonOf(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
    pairs.forEach { (key, value) ->
        if (value != null) putAny(key, value)
    }
}

private fun JsonObjectBuilder.putAny(key: String, value: Any) {
    when (value) {
        is String -> put(key, JsonPrimitive(value))
        is Boolean -> put(key, JsonPrimitive(value))
        is Int -> put(key, JsonPrimitive(value))
        is Long -> put(key, JsonPrimitive(value))
        is Double -> put(key, JsonPrimitive(value))
        is Float -> put(key, JsonPrimitive(value.toDouble()))
        is List<*> -> put(key, JsonArray(value.filterNotNull().map { it.toJsonPrimitive() }))
        else -> put(key, JsonPrimitive(value.toString()))
    }
}

private fun Any.toJsonPrimitive(): JsonPrimitive = when (this) {
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Int -> JsonPrimitive(this)
    is Long -> JsonPrimitive(this)
    is Double -> JsonPrimitive(this)
    is Float -> JsonPrimitive(this.toDouble())
    else -> JsonPrimitive(toString())
}
