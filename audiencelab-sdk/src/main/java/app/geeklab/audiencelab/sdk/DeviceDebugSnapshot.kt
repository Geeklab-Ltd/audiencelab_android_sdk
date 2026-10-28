package app.geeklab.audiencelab.sdk

data class DeviceDebugSnapshot(
    val deviceName: String,
    val deviceModel: String,
    val osSystem: String,
    val timezone: String,
    val width: Int,
    val height: Int,
    val glEsVersion: String?,
    val hardware: String?,
    val board: String?,
    val device: String?
)
