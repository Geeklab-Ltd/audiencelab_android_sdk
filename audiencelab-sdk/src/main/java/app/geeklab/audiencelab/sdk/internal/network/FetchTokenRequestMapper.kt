package app.geeklab.audiencelab.sdk.internal.network

import app.geeklab.audiencelab.sdk.internal.device.DeviceInfo
import app.geeklab.audiencelab.sdk.internal.identity.IdentitySnapshot

internal data class FetchTokenBuildInput(
    val sdkVersion: String,
    val sdkType: String,
    val appVersion: String,
    val developmentMode: Boolean,
    val whitelistedProperties: Map<String, String>,
    val deviceInfo: DeviceInfo,
    val identity: IdentitySnapshot
)

internal object FetchTokenRequestMapper {
    fun map(input: FetchTokenBuildInput): Map<String, Any?> {
        val payload = mutableMapOf<String, Any?>(
            "data" to mapOf(
                "os_system" to input.deviceInfo.osSystem,
                "device_model" to input.deviceInfo.deviceModel,
                "device_name" to input.deviceInfo.deviceName,
                "width" to input.deviceInfo.width,
                "height" to input.deviceInfo.height,
                "timezone" to input.deviceInfo.timezone
            ),
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

        return payload
    }
}
