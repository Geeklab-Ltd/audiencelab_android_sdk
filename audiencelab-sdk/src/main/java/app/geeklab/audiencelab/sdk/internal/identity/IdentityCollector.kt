package app.geeklab.audiencelab.sdk.internal.identity

import android.content.Context
import android.provider.Settings
import app.geeklab.audiencelab.sdk.AdvertisingIdProvider
import app.geeklab.audiencelab.sdk.AppSetIdProvider

internal object IdentityCollector {
    fun collect(
        context: Context?,
        advertisingIdProvider: AdvertisingIdProvider?,
        appSetIdProvider: AppSetIdProvider?,
        overrides: IdentityOverrides
    ): IdentitySnapshot {
        val aid = readAndroidId(context)
        if (context == null) {
            return IdentitySnapshot(
                ga = overrides.ga,
                asid = overrides.asid,
                aid = aid,
                lat = overrides.lat
            )
        }

        val providerInfo = runCatching {
            advertisingIdProvider?.getAdvertisingInfo(context)
        }.getOrNull()
        val ga = overrides.ga ?: providerInfo?.advertisingId?.trim()?.takeIf { it.isNotEmpty() }
        val asid = overrides.asid ?: runCatching {
            appSetIdProvider?.getAppSetId(context)?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()
        val lat = overrides.lat ?: providerInfo?.limitAdTracking

        return IdentitySnapshot(
            ga = ga,
            asid = asid,
            aid = aid,
            lat = lat
        )
    }

    private fun readAndroidId(context: Context?): String? {
        if (context == null) {
            return null
        }
        return runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
}
