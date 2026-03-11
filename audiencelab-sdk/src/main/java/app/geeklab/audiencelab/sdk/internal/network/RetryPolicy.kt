package app.geeklab.audiencelab.sdk.internal.network

internal object RetryPolicy {
    private const val BASE_BACKOFF_MILLIS = 2_000L
    private const val MAX_BACKOFF_MILLIS = 5 * 60_000L
    private const val MAX_EXPONENT = 8
    private const val TOKEN_BASE_BACKOFF_MILLIS = 5_000L
    private const val TOKEN_MAX_BACKOFF_MILLIS = 5 * 60_000L
    private const val TOKEN_MAX_ATTEMPTS = 10

    fun backoffMillis(attempt: Int): Long {
        val normalizedAttempt = attempt.coerceAtLeast(1)
        val exponent = (normalizedAttempt - 1).coerceAtMost(MAX_EXPONENT)
        val candidate = BASE_BACKOFF_MILLIS * (1L shl exponent)
        return candidate.coerceAtMost(MAX_BACKOFF_MILLIS)
    }

    fun tokenBackoffMillis(attempt: Int, jitterRatio: Double): Long {
        val normalizedAttempt = attempt.coerceAtLeast(1)
        val exponent = (normalizedAttempt - 1).coerceAtMost(MAX_EXPONENT)
        val candidate = TOKEN_BASE_BACKOFF_MILLIS * (1L shl exponent)
        val capped = candidate.coerceAtMost(TOKEN_MAX_BACKOFF_MILLIS)
        val normalizedJitter = jitterRatio.coerceIn(0.0, 0.3)
        val jitterMillis = (capped * normalizedJitter).toLong()
        return (capped + jitterMillis).coerceAtMost(TOKEN_MAX_BACKOFF_MILLIS)
    }

    fun canRetryToken(failedAttemptCount: Int): Boolean {
        return failedAttemptCount < TOKEN_MAX_ATTEMPTS
    }

    fun isRetryable(error: Throwable): Boolean {
        val networkError = error as? NetworkError ?: return false
        return when (networkError) {
            NetworkError.ApiKeyMissing -> false
            is NetworkError.InvalidUrl -> false
            is NetworkError.SerializationFailure -> false
            is NetworkError.TransportFailure -> true
            is NetworkError.HttpFailure -> isRetryableStatus(networkError.statusCode)
        }
    }

    private fun isRetryableStatus(statusCode: Int): Boolean {
        return statusCode == 408 || statusCode == 429 || statusCode in 500..599
    }
}
