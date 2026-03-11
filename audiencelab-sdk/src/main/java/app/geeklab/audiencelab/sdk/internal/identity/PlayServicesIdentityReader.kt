package app.geeklab.audiencelab.sdk.internal.identity

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import app.geeklab.audiencelab.sdk.AdvertisingInfo

internal object PlayServicesIdentityReader {
    private const val AD_ID_PERMISSION = "com.google.android.gms.permission.AD_ID"

    @Volatile
    private var cachedAdvertisingInfo: AdvertisingInfo? = null

    @Volatile
    private var advertisingInfoResolved: Boolean = false

    @Volatile
    private var cachedAppSetId: String? = null

    @Volatile
    private var appSetIdResolved: Boolean = false

    internal data class AdvertisingInfoState(
        val info: AdvertisingInfo?,
        val settled: Boolean
    )

    internal data class AppSetIdState(
        val appSetId: String?,
        val settled: Boolean
    )

    fun getAdvertisingInfo(context: Context): AdvertisingInfo? {
        return getAdvertisingInfoState(context = context, allowResolve = true).info
    }

    fun getAppSetId(context: Context): String? {
        return getAppSetIdState(context = context, allowResolve = true).appSetId
    }

    fun getAdvertisingInfoState(context: Context, allowResolve: Boolean): AdvertisingInfoState {
        cachedAdvertisingInfo?.let {
            return AdvertisingInfoState(info = it, settled = true)
        }
        if (!isAdIdPermissionDeclared(context)) {
            advertisingInfoResolved = true
            cachedAdvertisingInfo = null
            return AdvertisingInfoState(info = null, settled = true)
        }
        if (advertisingInfoResolved) {
            return AdvertisingInfoState(info = cachedAdvertisingInfo, settled = true)
        }
        if (!allowResolve || isMainThread()) {
            return AdvertisingInfoState(info = cachedAdvertisingInfo, settled = false)
        }

        return synchronized(this) {
            cachedAdvertisingInfo?.let {
                return@synchronized AdvertisingInfoState(info = it, settled = true)
            }
            if (advertisingInfoResolved) {
                return@synchronized AdvertisingInfoState(info = cachedAdvertisingInfo, settled = true)
            }

            val resolved = readAdvertisingInfoReflectively(context)
            cachedAdvertisingInfo = resolved
            advertisingInfoResolved = true
            AdvertisingInfoState(info = resolved, settled = true)
        }
    }

    fun getAppSetIdState(context: Context, allowResolve: Boolean): AppSetIdState {
        cachedAppSetId?.let {
            return AppSetIdState(appSetId = it, settled = true)
        }
        if (appSetIdResolved) {
            return AppSetIdState(appSetId = cachedAppSetId, settled = true)
        }
        if (!allowResolve || isMainThread()) {
            return AppSetIdState(appSetId = cachedAppSetId, settled = false)
        }

        return synchronized(this) {
            cachedAppSetId?.let {
                return@synchronized AppSetIdState(appSetId = it, settled = true)
            }
            if (appSetIdResolved) {
                return@synchronized AppSetIdState(appSetId = cachedAppSetId, settled = true)
            }

            val resolved = readAppSetIdReflectively(context)
            cachedAppSetId = resolved
            appSetIdResolved = true
            AppSetIdState(appSetId = resolved, settled = true)
        }
    }

    internal fun readAdvertisingInfoReflectively(context: Context?): AdvertisingInfo? {
        return runCatching {
            val clientClass = Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient")
            val info = clientClass
                .getMethod("getAdvertisingIdInfo", Context::class.java)
                .invoke(null, context)
                ?: return@runCatching null

            val advertisingId = info.javaClass
                .getMethod("getId")
                .invoke(info) as? String
            val limitAdTracking = info.javaClass
                .getMethod("isLimitAdTrackingEnabled")
                .invoke(info) as? Boolean

            AdvertisingInfo(
                advertisingId = advertisingId?.trim()?.takeIf { it.isNotEmpty() },
                limitAdTracking = limitAdTracking
            )
        }.getOrNull()
    }

    internal fun readAppSetIdReflectively(context: Context?): String? {
        return runCatching {
            val appSetClass = Class.forName("com.google.android.gms.appset.AppSet")
            val client = appSetClass
                .getMethod("getClient", Context::class.java)
                .invoke(null, context)
                ?: return@runCatching null

            val task = client.javaClass
                .getMethod("getAppSetIdInfo")
                .invoke(client)
                ?: return@runCatching null

            val tasksClass = Class.forName("com.google.android.gms.tasks.Tasks")
            val info = tasksClass
                .methods
                .firstOrNull { method ->
                    method.name == "await" && method.parameterTypes.size == 1
                }
                ?.invoke(null, task)
                ?: return@runCatching null

            (info.javaClass.getMethod("getId").invoke(info) as? String)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    internal fun resetForTesting() {
        cachedAdvertisingInfo = null
        advertisingInfoResolved = false
        cachedAppSetId = null
        appSetIdResolved = false
    }

    private fun isAdIdPermissionDeclared(context: Context): Boolean {
        val requestedPermissions = runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            }
            packageInfo.requestedPermissions?.toList().orEmpty()
        }.getOrElse {
            emptyList()
        }

        return requestedPermissions.contains(AD_ID_PERMISSION)
    }

    private fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }
}
