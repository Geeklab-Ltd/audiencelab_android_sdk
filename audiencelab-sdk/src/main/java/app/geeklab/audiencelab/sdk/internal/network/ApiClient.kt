package app.geeklab.audiencelab.sdk.internal.network

internal interface ApiClient {
    fun post(path: String, body: Map<String, Any?>, callback: (Result<Map<String, Any?>>) -> Unit)
}
