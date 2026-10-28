package app.geeklab.audiencelab.sdk.internal.network

internal object RequestDebugSanitizer {
    private const val REDACTED_BODY = "{\"redacted\":true}"

    @Suppress("UNUSED_PARAMETER")
    fun requestBody(path: String, body: Map<String, Any?>): String = REDACTED_BODY

    @Suppress("UNUSED_PARAMETER")
    fun responseBody(path: String, body: Map<String, Any?>): String = REDACTED_BODY
}
