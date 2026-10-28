package app.geeklab.audiencelab.sdk

data class AdEvent @JvmOverloads constructor(
    val adId: String,
    val name: String,
    val source: String,
    val watchTime: Double,
    val reward: Boolean,
    val mediaSource: String,
    val channel: String,
    val value: Double,
    val currency: String,
    val dedupeKey: String? = null
)
