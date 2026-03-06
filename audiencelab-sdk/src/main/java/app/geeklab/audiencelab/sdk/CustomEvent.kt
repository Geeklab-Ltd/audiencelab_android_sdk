package app.geeklab.audiencelab.sdk

data class CustomEvent @JvmOverloads constructor(
    val name: String,
    val params: Map<String, Any?> = emptyMap(),
    val dedupeKey: String? = null
)
