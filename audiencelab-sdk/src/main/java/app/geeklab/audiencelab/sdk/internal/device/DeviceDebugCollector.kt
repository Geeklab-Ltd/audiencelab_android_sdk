package app.geeklab.audiencelab.sdk.internal.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import app.geeklab.audiencelab.sdk.DeviceDebugSnapshot

internal object DeviceDebugCollector {
    fun collect(context: Context?): DeviceDebugSnapshot {
        val info = DeviceInfoCollector.collect(context)
        val activityManager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val glEsVersion = runCatching {
            activityManager?.deviceConfigurationInfo?.glEsVersion
        }.getOrNull()?.takeIf { it.isNotBlank() }

        return DeviceDebugSnapshot(
            deviceName = info.deviceName,
            deviceModel = info.deviceModel,
            osSystem = info.osSystem,
            timezone = info.timezone,
            width = info.width,
            height = info.height,
            glEsVersion = glEsVersion,
            hardware = Build.HARDWARE?.trim()?.takeIf { it.isNotEmpty() },
            board = Build.BOARD?.trim()?.takeIf { it.isNotEmpty() },
            device = Build.DEVICE?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}
