package app.geeklab.audiencelab.sdk

fun interface RequestResultCallback {
    fun onResult(success: Boolean, errorMessage: String?)
}
