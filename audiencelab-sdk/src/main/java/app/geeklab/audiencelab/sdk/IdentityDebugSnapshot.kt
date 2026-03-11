package app.geeklab.audiencelab.sdk

data class IdentityDebugSnapshot(
    val settled: Boolean,
    val advertisingIdSettled: Boolean,
    val appSetIdSettled: Boolean,
    val androidIdSettled: Boolean,
    val advertisingIdAvailable: Boolean,
    val appSetIdAvailable: Boolean,
    val androidIdAvailable: Boolean,
    val advertisingIdPreview: String?,
    val appSetIdPreview: String?,
    val androidIdPreview: String?,
    val limitAdTracking: Boolean?
)
