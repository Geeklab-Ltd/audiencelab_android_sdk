package app.geeklab.audiencelab.sdk

data class AudienceLabOptions @JvmOverloads constructor(
    val isSdkEnabled: Boolean = true,
    val isMetricsEnabled: Boolean = true,
    val isDebugEnabled: Boolean = false,
    val isDevelopmentMode: Boolean? = null,
    val appVersion: String? = null,
    val baseUrl: String? = null,
    val autoResolveGooglePlayIds: Boolean = true
)
