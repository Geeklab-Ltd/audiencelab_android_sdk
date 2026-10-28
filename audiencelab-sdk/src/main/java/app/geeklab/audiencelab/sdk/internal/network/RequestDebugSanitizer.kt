package app.geeklab.audiencelab.sdk.internal.network

internal object RequestDebugSanitizer {
    private const val REDACTED_BODY = "{\"redacted\":true}"
    private const val REDACTED_VALUE = "[REDACTED]"
    private val sensitiveKeys = setOf("creativeToken", "x1", "x7")

    fun requestBody(path: String, body: Map<String, Any?>): String {
        if (isLifecycleEndpoint(path)) {
            return REDACTED_BODY
        }
        return JsonCodec.toJsonString(redactMap(body))
    }

    fun responseBody(path: String, body: Map<String, Any?>): String {
        if (isLifecycleEndpoint(path)) {
            return REDACTED_BODY
        }
        return JsonCodec.toJsonString(redactMap(body))
    }

    private fun isLifecycleEndpoint(path: String): Boolean {
        return path.trim().trim('/').equals("fetch-token", ignoreCase = true)
    }

    private fun redactMap(map: Map<String, Any?>): Map<String, Any?> {
        return map.mapValues { (key, value) ->
            if (key in sensitiveKeys) REDACTED_VALUE else redactValue(value)
        }
    }

    private fun redactValue(value: Any?): Any? {
        return when (value) {
            is Map<*, *> -> value.entries.associate { entry ->
                val key = entry.key?.toString().orEmpty()
                key to if (key in sensitiveKeys) REDACTED_VALUE else redactValue(entry.value)
            }
            is List<*> -> value.map(::redactValue)
            else -> value
        }
    }
}
