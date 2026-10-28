package app.geeklab.audiencelab.sdk.internal.network

import org.json.JSONArray
import org.json.JSONObject

internal object JsonCodec {
    fun toJsonString(payload: Map<String, Any?>): String {
        return toJsonObject(payload).toString()
    }

    fun parseObjectOrEmpty(raw: String?): Map<String, Any?> {
        if (raw.isNullOrBlank()) {
            return emptyMap()
        }
        return runCatching {
            val objectValue = JSONObject(raw)
            fromJsonObject(objectValue)
        }.getOrElse {
            emptyMap()
        }
    }

    private fun toJsonObject(map: Map<String, Any?>): JSONObject {
        val objectValue = JSONObject()
        map.forEach { (key, value) ->
            objectValue.put(key, toJsonValue(value))
        }
        return objectValue
    }

    private fun toJsonArray(list: List<*>): JSONArray {
        val array = JSONArray()
        list.forEach { item ->
            array.put(toJsonValue(item))
        }
        return array
    }

    private fun toJsonValue(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                val typed = value.entries
                    .filter { it.key is String }
                    .associate { it.key as String to it.value }
                toJsonObject(typed)
            }
            is List<*> -> toJsonArray(value)
            is Array<*> -> toJsonArray(value.toList())
            is Number, is Boolean, is String -> value
            else -> value.toString()
        }
    }

    private fun fromJsonObject(objectValue: JSONObject): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val iterator = objectValue.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            result[key] = fromJsonValue(objectValue.opt(key))
        }
        return result
    }

    private fun fromJsonArray(array: JSONArray): List<Any?> {
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                add(fromJsonValue(array.opt(index)))
            }
        }
    }

    private fun fromJsonValue(value: Any?): Any? {
        return when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> fromJsonObject(value)
            is JSONArray -> fromJsonArray(value)
            else -> value
        }
    }
}
