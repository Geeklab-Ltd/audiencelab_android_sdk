package app.geeklab.audiencelab.sdk.internal.network

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor
import java.util.concurrent.Executors

internal class HttpApiClient(
    private val baseUrl: String,
    private val apiKeyProvider: () -> String?,
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

        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("geeklab-api-key", apiKey)
            connection.doOutput = true

            val bodyString = JsonCodec.toJsonString(body)
            connection.outputStream.use { stream ->
                stream.write(bodyString.toByteArray(Charsets.UTF_8))
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
            parsed
        } catch (error: NetworkError) {
            throw error
        } catch (error: Throwable) {
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
}
