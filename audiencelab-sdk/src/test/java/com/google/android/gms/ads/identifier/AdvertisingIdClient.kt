package com.google.android.gms.ads.identifier

import android.content.Context

object AdvertisingIdClient {
    @Volatile
    var advertisingId: String? = "gaid-test-123"

    @Volatile
    var limitAdTrackingEnabled: Boolean = false

    @JvmStatic
    fun getAdvertisingIdInfo(context: Context?): Info {
        return Info(advertisingId, limitAdTrackingEnabled)
    }

    class Info(
        private val id: String?,
        private val limitAdTracking: Boolean
    ) {
        fun getId(): String? = id

        fun isLimitAdTrackingEnabled(): Boolean = limitAdTracking
    }
}
