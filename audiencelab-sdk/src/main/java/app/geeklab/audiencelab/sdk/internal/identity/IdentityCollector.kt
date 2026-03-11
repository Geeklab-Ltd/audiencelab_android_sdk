package app.geeklab.audiencelab.sdk.internal.identity

import android.content.Context
import android.provider.Settings
import app.geeklab.audiencelab.sdk.AdvertisingIdProvider
import app.geeklab.audiencelab.sdk.AppSetIdProvider

internal object IdentityCollector {
    data class IdentityDiagnostics(
        val advertisingIdSettled: Boolean,
        val appSetIdSettled: Boolean,
        val androidIdSettled: Boolean,
        val advertisingId: String?,
        val appSetId: String?,
        val androidId: String?,
        val limitAdTracking: Boolean?
    ) {
        val settled: Boolean
            get() = advertisingIdSettled && appSetIdSettled && androidIdSettled
    }

    data class IdentityCollectionResult(
        val snapshot: IdentitySnapshot,
        val diagnostics: IdentityDiagnostics
    )

    fun collect(
        context: Context?,
        advertisingIdProvider: AdvertisingIdProvider?,
        appSetIdProvider: AppSetIdProvider?,
        overrides: IdentityOverrides
    ): IdentitySnapshot {
        return collectDetailed(
            context = context,
            advertisingIdProvider = advertisingIdProvider,
            appSetIdProvider = appSetIdProvider,
            overrides = overrides,
            allowAutoResolve = true
        ).snapshot
    }

    fun collectDetailed(
        context: Context?,
        advertisingIdProvider: AdvertisingIdProvider?,
        appSetIdProvider: AppSetIdProvider?,
        overrides: IdentityOverrides,
        allowAutoResolve: Boolean
    ): IdentityCollectionResult {
        val aid = readAndroidId(context)
        if (context == null) {
            return IdentityCollectionResult(
                snapshot = IdentitySnapshot(
                    ga = overrides.ga,
                    asid = overrides.asid,
                    aid = aid,
                    lat = overrides.lat
                ),
                diagnostics = IdentityDiagnostics(
                    advertisingIdSettled = true,
                    appSetIdSettled = true,
                    androidIdSettled = true,
                    advertisingId = overrides.ga,
                    appSetId = overrides.asid,
                    androidId = aid,
                    limitAdTracking = overrides.lat
                )
            )
        }

        val providerInfo = runCatching {
            advertisingIdProvider?.getAdvertisingInfo(context)
        }.getOrNull()
        val advertisingInfoState = if (advertisingIdProvider == null && overrides.ga == null && overrides.lat == null) {
            PlayServicesIdentityReader.getAdvertisingInfoState(
                context = context,
                allowResolve = allowAutoResolve
            )
        } else {
            PlayServicesIdentityReader.AdvertisingInfoState(info = null, settled = true)
        }
        val appSetIdState = if (appSetIdProvider == null && overrides.asid == null) {
            PlayServicesIdentityReader.getAppSetIdState(
                context = context,
                allowResolve = allowAutoResolve
            )
        } else {
            PlayServicesIdentityReader.AppSetIdState(appSetId = null, settled = true)
        }
        val ga = overrides.ga ?: providerInfo?.advertisingId?.trim()?.takeIf { it.isNotEmpty() }
            ?: advertisingInfoState.info?.advertisingId
        val asid = overrides.asid ?: runCatching {
            appSetIdProvider?.getAppSetId(context)?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull() ?: appSetIdState.appSetId
        val lat = overrides.lat ?: providerInfo?.limitAdTracking ?: advertisingInfoState.info?.limitAdTracking
        val diagnostics = IdentityDiagnostics(
            advertisingIdSettled = when {
                overrides.ga != null || overrides.lat != null -> true
                advertisingIdProvider != null -> true
                else -> advertisingInfoState.settled
            },
            appSetIdSettled = when {
                overrides.asid != null -> true
                appSetIdProvider != null -> true
                else -> appSetIdState.settled
            },
            androidIdSettled = true,
            advertisingId = ga,
            appSetId = asid,
            androidId = aid,
            limitAdTracking = lat
        )

        return IdentityCollectionResult(
            snapshot = IdentitySnapshot(
                ga = ga,
                asid = asid,
                aid = aid,
                lat = lat
            ),
            diagnostics = diagnostics
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
