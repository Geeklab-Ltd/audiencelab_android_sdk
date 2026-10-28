package app.geeklab.audiencelab.sdk.internal.identity

import android.content.Context
import android.provider.Settings
import app.geeklab.audiencelab.sdk.AdvertisingInfo
import app.geeklab.audiencelab.sdk.AdvertisingIdProvider
import app.geeklab.audiencelab.sdk.AppSetIdProvider

internal object IdentityCollector {
    @Volatile
    private var cachedProviderAdvertisingInfo: AdvertisingInfo? = null

    @Volatile
    private var providerAdvertisingInfoResolved: Boolean = false

    @Volatile
    private var cachedProviderAppSetId: String? = null

    @Volatile
    private var providerAppSetIdResolved: Boolean = false

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

    internal data class ProviderAdvertisingInfoState(
        val info: AdvertisingInfo?,
        val settled: Boolean
    )

    internal data class ProviderAppSetIdState(
        val appSetId: String?,
        val settled: Boolean
    )

    fun clearProviderCache() {
        cachedProviderAdvertisingInfo = null
        providerAdvertisingInfoResolved = false
        cachedProviderAppSetId = null
        providerAppSetIdResolved = false
    }

    fun collect(
        context: Context?,
        advertisingIdProvider: AdvertisingIdProvider?,
        appSetIdProvider: AppSetIdProvider?,
        overrides: IdentityOverrides,
        googlePlayAutoResolveEnabled: Boolean = true
    ): IdentitySnapshot {
        return collectDetailed(
            context = context,
            advertisingIdProvider = advertisingIdProvider,
            appSetIdProvider = appSetIdProvider,
            overrides = overrides,
            allowAutoResolve = true,
            googlePlayAutoResolveEnabled = googlePlayAutoResolveEnabled
        ).snapshot
    }

    fun collectDetailed(
        context: Context?,
        advertisingIdProvider: AdvertisingIdProvider?,
        appSetIdProvider: AppSetIdProvider?,
        overrides: IdentityOverrides,
        allowAutoResolve: Boolean,
        googlePlayAutoResolveEnabled: Boolean = true
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

        val providerInfoState = if (advertisingIdProvider != null) {
            getProviderAdvertisingInfoState(
                context = context,
                provider = advertisingIdProvider,
                allowResolve = allowAutoResolve
            )
        } else {
            ProviderAdvertisingInfoState(info = null, settled = false)
        }
        val advertisingInfoState = if (!googlePlayAutoResolveEnabled) {
            PlayServicesIdentityReader.AdvertisingInfoState(info = null, settled = true)
        } else if (advertisingIdProvider == null && overrides.ga == null && overrides.lat == null) {
            PlayServicesIdentityReader.getAdvertisingInfoState(
                context = context,
                allowResolve = allowAutoResolve
            )
        } else {
            PlayServicesIdentityReader.AdvertisingInfoState(info = null, settled = true)
        }
        val providerAppSetIdState = if (appSetIdProvider != null) {
            getProviderAppSetIdState(
                context = context,
                provider = appSetIdProvider,
                allowResolve = allowAutoResolve
            )
        } else {
            ProviderAppSetIdState(appSetId = null, settled = false)
        }
        val appSetIdState = if (!googlePlayAutoResolveEnabled) {
            PlayServicesIdentityReader.AppSetIdState(appSetId = null, settled = true)
        } else if (appSetIdProvider == null && overrides.asid == null) {
            PlayServicesIdentityReader.getAppSetIdState(
                context = context,
                allowResolve = allowAutoResolve
            )
        } else {
            PlayServicesIdentityReader.AppSetIdState(appSetId = null, settled = true)
        }
        val ga = overrides.ga ?: providerInfoState.info?.advertisingId
            ?: advertisingInfoState.info?.advertisingId
        val asid = overrides.asid ?: providerAppSetIdState.appSetId ?: appSetIdState.appSetId
        val lat = overrides.lat ?: providerInfoState.info?.limitAdTracking ?: advertisingInfoState.info?.limitAdTracking
        val diagnostics = IdentityDiagnostics(
            advertisingIdSettled = when {
                overrides.ga != null || overrides.lat != null -> true
                advertisingIdProvider != null -> providerInfoState.settled
                else -> advertisingInfoState.settled
            },
            appSetIdSettled = when {
                overrides.asid != null -> true
                appSetIdProvider != null -> providerAppSetIdState.settled
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

    private fun getProviderAdvertisingInfoState(
        context: Context,
        provider: AdvertisingIdProvider,
        allowResolve: Boolean
    ): ProviderAdvertisingInfoState {
        cachedProviderAdvertisingInfo?.let {
            return ProviderAdvertisingInfoState(info = it, settled = true)
        }
        if (providerAdvertisingInfoResolved) {
            return ProviderAdvertisingInfoState(info = cachedProviderAdvertisingInfo, settled = true)
        }
        if (!allowResolve) {
            return ProviderAdvertisingInfoState(info = cachedProviderAdvertisingInfo, settled = false)
        }

        return synchronized(this) {
            cachedProviderAdvertisingInfo?.let {
                return@synchronized ProviderAdvertisingInfoState(info = it, settled = true)
            }
            if (providerAdvertisingInfoResolved) {
                return@synchronized ProviderAdvertisingInfoState(info = cachedProviderAdvertisingInfo, settled = true)
            }

            val resolved = runCatching {
                normalizeAdvertisingInfo(provider.getAdvertisingInfo(context))
            }.getOrNull()
            cachedProviderAdvertisingInfo = resolved
            providerAdvertisingInfoResolved = true
            ProviderAdvertisingInfoState(info = resolved, settled = true)
        }
    }

    private fun getProviderAppSetIdState(
        context: Context,
        provider: AppSetIdProvider,
        allowResolve: Boolean
    ): ProviderAppSetIdState {
        cachedProviderAppSetId?.let {
            return ProviderAppSetIdState(appSetId = it, settled = true)
        }
        if (providerAppSetIdResolved) {
            return ProviderAppSetIdState(appSetId = cachedProviderAppSetId, settled = true)
        }
        if (!allowResolve) {
            return ProviderAppSetIdState(appSetId = cachedProviderAppSetId, settled = false)
        }

        return synchronized(this) {
            cachedProviderAppSetId?.let {
                return@synchronized ProviderAppSetIdState(appSetId = it, settled = true)
            }
            if (providerAppSetIdResolved) {
                return@synchronized ProviderAppSetIdState(appSetId = cachedProviderAppSetId, settled = true)
            }

            val resolved = runCatching {
                provider.getAppSetId(context)?.trim()?.takeIf { it.isNotEmpty() }
            }.getOrNull()
            cachedProviderAppSetId = resolved
            providerAppSetIdResolved = true
            ProviderAppSetIdState(appSetId = resolved, settled = true)
        }
    }

    private fun normalizeAdvertisingInfo(info: AdvertisingInfo?): AdvertisingInfo? {
        if (info == null) {
            return null
        }
        val advertisingId = info.advertisingId?.trim()?.takeIf { it.isNotEmpty() }
        val limitAdTracking = info.limitAdTracking
        return if (advertisingId != null || limitAdTracking != null) {
            AdvertisingInfo(
                advertisingId = advertisingId,
                limitAdTracking = limitAdTracking
            )
        } else {
            null
        }
    }
}
