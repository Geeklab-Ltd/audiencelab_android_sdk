package app.geeklab.audiencelab.sdk

import android.content.Context

fun interface AppSetIdProvider {
    fun getAppSetId(context: Context): String?
}
