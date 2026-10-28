package app.geeklab.audiencelab.sdk

data class QueueDebugEntry(
    val requestName: String,
    val eventId: String,
    val type: String,
    val payload: String,
    val createdAtMillis: Long,
    val attemptCount: Int,
    val nextAttemptAtMillis: Long,
    val queuedAtMillis: Long
)
