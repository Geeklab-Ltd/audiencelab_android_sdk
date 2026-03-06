package app.geeklab.audiencelab.sdk

data class PurchaseEvent @JvmOverloads constructor(
    val itemId: String,
    val itemName: String,
    val value: Double,
    val currency: String,
    val status: String,
    val transactionId: String? = null,
    val dedupeKey: String? = null
)
