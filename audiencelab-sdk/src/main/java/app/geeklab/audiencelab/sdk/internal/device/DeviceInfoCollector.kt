package app.geeklab.audiencelab.sdk.internal.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.content.res.Resources
import android.os.Build
import java.util.Locale
import java.util.TimeZone

internal object DeviceInfoCollector {
    fun collect(context: Context?): DeviceInfo {
        val displayMetrics = runCatching {
            (context?.resources ?: Resources.getSystem()).displayMetrics
        }.getOrNull()
        val activityManager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val timezone = TimeZone.getDefault()
        val now = System.currentTimeMillis()
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim().ifEmpty { "unknown" }
        val deviceName = if (manufacturer.isBlank()) {
            model
        } else {
            val manufacturerTitle = manufacturer.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
            }
            "$manufacturerTitle $model".trim()
        }
        val osVersion = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() }
            ?: Build.VERSION.SDK_INT.toString()
        val batteryIntent = runCatching {
            context?.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val lowBatteryLevel = batteryIntent?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level <= 0 || scale <= 0) {
                null
            } else {
                (level.toDouble() / scale.toDouble()) < 0.2
            }
        }
        val glEsVersion = runCatching {
            activityManager?.deviceConfigurationInfo?.glEsVersion
        }.getOrNull()?.takeIf { it.isNotBlank() }

        return DeviceInfo(
            deviceName = deviceName,
            deviceModel = model,
            osSystem = "Android $osVersion",
            dpi = displayMetrics?.densityDpi ?: 0,
            width = displayMetrics?.widthPixels ?: 0,
            height = displayMetrics?.heightPixels ?: 0,
            timezone = timezone.id,
            utcOffsetHours = timezone.getOffset(now).toDouble() / 3_600_000.0,
            lowBatteryLevel = lowBatteryLevel,
            glEsVersion = glEsVersion
        )
    }
}
