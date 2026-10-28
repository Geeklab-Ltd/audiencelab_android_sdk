package app.geeklab.audiencelab.sdk.internal.network

internal sealed class NetworkError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    internal object ApiKeyMissing : NetworkError("API key is missing")
    internal data class InvalidUrl(val rawUrl: String) : NetworkError("Invalid URL: $rawUrl")
    internal data class HttpFailure(val statusCode: Int, val responseBody: Map<String, Any?>) :
        NetworkError("HTTP request failed with status $statusCode")
    internal data class SerializationFailure(val detail: String) : NetworkError(detail)
    internal data class TransportFailure(val transportError: Throwable) :
        NetworkError("Transport error: ${transportError.message}", transportError)
}
