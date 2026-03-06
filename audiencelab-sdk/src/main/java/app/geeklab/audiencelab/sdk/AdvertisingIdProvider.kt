package app.geeklab.audiencelab.sdk

import android.content.Context

data class AdvertisingInfo @JvmOverloads constructor(
    val advertisingId: String? = null,
    val limitAdTracking: Boolean? = null
)

fun interface AdvertisingIdProvider {
    fun getAdvertisingInfo(context: Context): AdvertisingInfo?
}
