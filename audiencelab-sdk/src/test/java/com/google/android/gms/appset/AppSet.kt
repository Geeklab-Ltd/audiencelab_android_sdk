package com.google.android.gms.appset

import android.content.Context

object AppSet {
    @Volatile
    var appSetId: String? = "appset-test-456"

    @JvmStatic
    fun getClient(context: Context?): AppSetClient {
        return AppSetClient(appSetId)
    }
}

class AppSetClient(
    private val id: String?
) {
    fun getAppSetIdInfo(): AppSetIdInfo {
        return AppSetIdInfo(id)
    }
}

class AppSetIdInfo(
    private val id: String?
) {
    fun getId(): String? = id
}
