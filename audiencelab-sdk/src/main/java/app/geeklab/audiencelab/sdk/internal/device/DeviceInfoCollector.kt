package app.geeklab.audiencelab.sdk.internal.device

import android.content.Context
import android.content.res.Resources
import android.os.Build
import java.util.Locale
import java.util.TimeZone

internal object DeviceInfoCollector {
    fun collect(context: Context?): DeviceInfo {
        val displayMetrics = (context?.resources ?: Resources.getSystem()).displayMetrics
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

        return DeviceInfo(
            deviceName = deviceName,
            deviceModel = model,
            osSystem = "Android $osVersion",
            width = displayMetrics.widthPixels,
            height = displayMetrics.heightPixels,
            timezone = timezone.id,
            utcOffsetHours = timezone.getOffset(now).toDouble() / 3_600_000.0
        )
    }
}
