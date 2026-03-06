package app.geeklab.audiencelab.sdk.internal

import android.util.Log

internal object Logger {
    private const val TAG = "AudienceLabSDK"
    @Volatile
    private var debugEnabled: Boolean = false

    fun configure(enabled: Boolean) {
        debugEnabled = enabled
    }

    fun d(message: String) {
        if (debugEnabled) {
            runCatching { Log.d(TAG, message) }
        }
    }

    fun w(message: String) {
        runCatching { Log.w(TAG, message) }
    }
}
