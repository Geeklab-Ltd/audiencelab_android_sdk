package app.geeklab.audiencelab.sdk.internal.network

import app.geeklab.audiencelab.sdk.RequestDebugEntry
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor
import java.util.concurrent.Executors

internal class HttpApiClient(
    private val baseUrl: String,
    private val apiKeyProvider: () -> String?,
    private val requestObserver: ((RequestDebugEntry) -> Unit)? = null,
    private val executor: Executor = Executors.newSingleThreadExecutor(),
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 10_000
) : ApiClient {
    override fun post(path: String, body: Map<String, Any?>, callback: (Result<Map<String, Any?>>) -> Unit) {
        executor.execute {
            callback(runCatching { doPost(path = path, body = body) })
        }
    }

    private fun doPost(path: String, body: Map<String, Any?>): Map<String, Any?> {
        val apiKey = apiKeyProvider()?.trim().orEmpty()
        if (apiKey.isEmpty()) {
            throw NetworkError.ApiKeyMissing
        }

        val endpoint = buildEndpoint(path)
        val connection = (URL(endpoint).openConnection() as? HttpURLConnection)
            ?: throw NetworkError.InvalidUrl(endpoint)
        val requestBodyString = JsonCodec.toJsonString(body)
        val requestName = deriveRequestName(path = path, body = body)
        val authHeaderPreview = sanitizeApiKeyPreview(apiKey)

        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("geeklab-api-key", apiKey)
            connection.doOutput = true

            connection.outputStream.use { stream ->
                stream.write(requestBodyString.toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val responseBody = if (status in 200..299) {
                connection.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }
            val parsed = JsonCodec.parseObjectOrEmpty(responseBody)
            if (status !in 200..299) {
                throw NetworkError.HttpFailure(statusCode = status, responseBody = parsed)
            }
            notifyObserver(
                RequestDebugEntry(
                    requestName = requestName,
                    endpoint = path,
                    authHeaderPreview = authHeaderPreview,
                    requestBody = requestBodyString,
                    responseStatusCode = status,
                    responseBody = responseBody,
                    success = true,
                    errorMessage = null,
                    timestampMillis = System.currentTimeMillis()
                )
            )
            parsed
        } catch (error: NetworkError) {
            notifyObserver(
                RequestDebugEntry(
                    requestName = requestName,
                    endpoint = path,
                    authHeaderPreview = authHeaderPreview,
                    requestBody = requestBodyString,
                    responseStatusCode = (error as? NetworkError.HttpFailure)?.statusCode,
                    responseBody = (error as? NetworkError.HttpFailure)?.responseBody?.let(JsonCodec::toJsonString),
                    success = false,
                    errorMessage = error.message,
                    timestampMillis = System.currentTimeMillis()
                )
            )
            throw error
        } catch (error: Throwable) {
            notifyObserver(
                RequestDebugEntry(
                    requestName = requestName,
                    endpoint = path,
                    authHeaderPreview = authHeaderPreview,
                    requestBody = requestBodyString,
                    responseStatusCode = null,
                    responseBody = null,
                    success = false,
                    errorMessage = error.message,
                    timestampMillis = System.currentTimeMillis()
                )
            )
            throw NetworkError.TransportFailure(error)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildEndpoint(path: String): String {
        val normalizedBase = baseUrl.trim().removeSuffix("/")
        if (normalizedBase.isEmpty()) {
            throw NetworkError.InvalidUrl(baseUrl)
        }
        val normalizedPath = "/" + path.trim().trimStart('/')
        return normalizedBase + normalizedPath
    }

    private fun notifyObserver(entry: RequestDebugEntry) {
        runCatching {
            requestObserver?.invoke(entry)
        }
    }

    private fun deriveRequestName(path: String, body: Map<String, Any?>): String {
        val normalizedPath = path.trim().trim('/').ifEmpty { "request" }
        if (normalizedPath == "webhook") {
            val type = body["type"] as? String ?: return "webhook"
            if (type == "session") {
                val payload = body["payload"] as? Map<*, *>
                val action = payload?.get("a") as? String
                return if (action.isNullOrBlank()) "webhook:session" else "webhook:session:$action"
            }
            if (type == "custom") {
                val payload = body["payload"] as? Map<*, *>
                val eventName = payload?.get("en") as? String
                return if (eventName.isNullOrBlank()) "webhook:custom" else "webhook:custom:$eventName"
            }
            return "webhook:$type"
        }
        return normalizedPath
    }

    private fun sanitizeApiKeyPreview(apiKey: String): String? {
        val normalized = apiKey.trim()
        if (normalized.isEmpty()) {
            return null
        }
        if (normalized.length <= 10) {
            return normalized.take(2) + "..." + normalized.takeLast(2)
        }
        return normalized.take(6) + "..." + normalized.takeLast(4)
    }
}
