package app.geeklab.audiencelab.sdk.internal.identity

internal data class IdentitySnapshot(
    val ga: String?,
    val asid: String?,
    val aid: String?,
    val lat: Boolean?
)

internal data class IdentityOverrides(
    val ga: String? = null,
    val asid: String? = null,
    val lat: Boolean? = null
)
