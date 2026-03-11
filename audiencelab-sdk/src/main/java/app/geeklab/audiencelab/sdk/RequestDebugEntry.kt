package app.geeklab.audiencelab.sdk

data class RequestDebugEntry(
    val requestName: String,
    val endpoint: String,
    val authHeaderPreview: String?,
    val requestBody: String,
    val responseStatusCode: Int?,
    val responseBody: String?,
    val success: Boolean,
    val errorMessage: String?,
    val timestampMillis: Long
)
