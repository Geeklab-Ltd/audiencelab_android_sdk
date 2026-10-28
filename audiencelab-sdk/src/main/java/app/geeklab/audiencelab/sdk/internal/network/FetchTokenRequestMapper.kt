package app.geeklab.audiencelab.sdk.internal.network

import app.geeklab.audiencelab.sdk.internal.device.DeviceInfo
import app.geeklab.audiencelab.sdk.internal.identity.IdentitySnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class FetchTokenBuildInput(
    val sdkVersion: String,
    val sdkType: String,
    val appVersion: String,
    val createdAtMillis: Long,
    val developmentMode: Boolean,
    val whitelistedProperties: Map<String, String>,
    val blacklistedProperties: Map<String, String>,
    val deviceInfo: DeviceInfo,
    val identity: IdentitySnapshot
)

internal object FetchTokenRequestMapper {
    fun map(input: FetchTokenBuildInput): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>(
            "os_system" to input.deviceInfo.osSystem,
            "device_model" to input.deviceInfo.deviceModel,
            "device_name" to input.deviceInfo.deviceName,
            "dpi" to input.deviceInfo.dpi,
            "window_width" to input.deviceInfo.width,
            "window_height" to input.deviceInfo.height,
            "timezone" to input.deviceInfo.timezone
        )
        if (input.deviceInfo.lowBatteryLevel != null) {
            data["low_battery_level"] = input.deviceInfo.lowBatteryLevel
        }
        if (!input.deviceInfo.glEsVersion.isNullOrBlank()) {
            data["gpu_version"] = input.deviceInfo.glEsVersion
        }

        val payload = mutableMapOf<String, Any?>(
            "type" to "device-metrics",
            "data" to data,
            "created_at" to formatCreatedAt(input.createdAtMillis),
            "sdk_version" to input.sdkVersion,
            "sdk_type" to input.sdkType,
            "app_version" to input.appVersion,
            "dev" to input.developmentMode
        )

        if (input.identity.ga?.isNotBlank() == true) {
            payload["ga"] = input.identity.ga
        }
        if (input.identity.asid?.isNotBlank() == true) {
            payload["asid"] = input.identity.asid
        }
        if (input.identity.aid?.isNotBlank() == true) {
            payload["aid"] = input.identity.aid
        }
        if (input.identity.lat != null) {
            payload["lat"] = input.identity.lat
        }
        if (input.whitelistedProperties.isNotEmpty()) {
            payload["wp"] = input.whitelistedProperties
        }
        if (input.blacklistedProperties.isNotEmpty()) {
            payload["bp"] = input.blacklistedProperties
        }

        return payload
    }

    private fun formatCreatedAt(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(timestampMillis.coerceAtLeast(0L)))
    }
}
