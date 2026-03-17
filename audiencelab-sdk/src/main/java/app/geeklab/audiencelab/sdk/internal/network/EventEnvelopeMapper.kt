package app.geeklab.audiencelab.sdk.internal.network

import app.geeklab.audiencelab.sdk.internal.device.DeviceInfo
import app.geeklab.audiencelab.sdk.internal.identity.IdentitySnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

internal data class EnvelopeBuildInput(
    val type: String,
    val payload: Map<String, Any?>,
    val creativeToken: String,
    val retentionDay: Int?,
    val sdkVersion: String,
    val sdkType: String,
    val appVersion: String,
    val developmentMode: Boolean,
    val createdAtMillis: Long,
    val dedupeKey: String?,
    val whitelistedProperties: Map<String, String>,
    val blacklistedProperties: Map<String, String>,
    val deviceInfo: DeviceInfo,
    val identity: IdentitySnapshot,
    val eventId: String = generateEventId()
) {
    companion object {
        fun generateEventId(): String {
            return "evt_" + UUID.randomUUID().toString().replace("-", "")
        }
    }
}

internal object EventEnvelopeMapper {
    fun map(input: EnvelopeBuildInput): Map<String, Any?> {
        val normalizedType = normalizeType(input.type)
        val envelope = mutableMapOf<String, Any?>(
            "creativeToken" to input.creativeToken,
            "type" to normalizedType,
            "payload" to input.payload,
            "os_system" to input.deviceInfo.osSystem,
            "device_name" to input.deviceInfo.deviceName,
            "device_model" to input.deviceInfo.deviceModel,
            "sdk_version" to input.sdkVersion,
            "sdk_type" to input.sdkType,
            "app_version" to input.appVersion,
            "dev" to input.developmentMode,
            "created_at" to formatCreatedAt(input.createdAtMillis),
            "utc_offset" to input.deviceInfo.utcOffsetHours,
            "eid" to input.eventId
        )

        if (input.retentionDay != null) {
            envelope["retention_day"] = input.retentionDay
        }

        if (input.identity.ga?.isNotBlank() == true) {
            envelope["ga"] = input.identity.ga
        }
        if (input.identity.asid?.isNotBlank() == true) {
            envelope["asid"] = input.identity.asid
        }
        if (input.identity.aid?.isNotBlank() == true) {
            envelope["aid"] = input.identity.aid
        }
        if (input.identity.lat != null) {
            envelope["lat"] = input.identity.lat
        }
        if (!input.dedupeKey.isNullOrBlank()) {
            envelope["dk"] = input.dedupeKey
        }
        if (input.whitelistedProperties.isNotEmpty()) {
            envelope["wp"] = input.whitelistedProperties
        }
        if (input.blacklistedProperties.isNotEmpty()) {
            envelope["bp"] = input.blacklistedProperties
        }

        return envelope
    }

    private fun normalizeType(type: String): String {
        return when (type) {
            "custom.ad" -> "ad"
            "custom.purchase" -> "purchase"
            else -> type
        }
    }

    private fun formatCreatedAt(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(timestampMillis.coerceAtLeast(0L)))
    }
}
