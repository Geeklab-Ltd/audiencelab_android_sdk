package app.geeklab.audiencelab.sdk.internal.network

internal data class FetchTokenResponse(
    val token: String?,
    val message: String?,
    val whitelistedProperties: Map<String, String>,
    val installId: String?,
    val decisionCode: String?,
    val refreshAfter: String?
)

internal object FetchTokenResponseParser {
    fun parse(response: Map<String, Any?>): FetchTokenResponse {
        return FetchTokenResponse(
            token = response["token"] as? String,
            message = response["message"] as? String,
            whitelistedProperties = normalizeStringMap(response["wp"]),
            installId = optionalString(response["x7"]),
            decisionCode = optionalString(response["x9"]),
            refreshAfter = optionalString(response["xm"])
        )
    }

    private fun optionalString(value: Any?): String? {
        return (value as? String)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun normalizeStringMap(raw: Any?): Map<String, String> {
        val map = raw as? Map<*, *> ?: return emptyMap()
        return map.entries
            .mapNotNull { entry ->
                val key = entry.key as? String ?: return@mapNotNull null
                val value = entry.value ?: return@mapNotNull null
                key to value.toString()
            }
            .toMap()
    }
}
