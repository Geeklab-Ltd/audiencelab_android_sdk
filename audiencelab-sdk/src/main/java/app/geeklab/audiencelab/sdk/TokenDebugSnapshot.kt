package app.geeklab.audiencelab.sdk

data class TokenDebugSnapshot(
    val hasValidToken: Boolean,
    val creativeTokenPreview: String?,
    val lastFetchStatus: String,
    val lastAttemptAtMillis: Long?,
    val attemptCount: Int,
    val nextRetryAtMillis: Long?
)
