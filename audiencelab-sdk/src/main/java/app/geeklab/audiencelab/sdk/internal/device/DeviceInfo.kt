package app.geeklab.audiencelab.sdk.internal.device

internal data class DeviceInfo(
    val deviceName: String,
    val deviceModel: String,
    val osSystem: String,
    val width: Int,
    val height: Int,
    val timezone: String,
    val utcOffsetHours: Double
)
