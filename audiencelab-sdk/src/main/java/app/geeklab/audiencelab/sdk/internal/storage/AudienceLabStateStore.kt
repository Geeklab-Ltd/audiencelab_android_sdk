package app.geeklab.audiencelab.sdk.internal.storage

import app.geeklab.audiencelab.sdk.internal.util.CurrencyValue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

internal data class PersistedQueuedEvent(
    val eventId: String,
    val type: String,
    val dedupeKey: String?,
    val payload: Map<String, Any?>,
    val createdAtMillis: Long,
    val attemptCount: Int,
    val nextAttemptAtMillis: Long,
    val queuedAtMillis: Long
)

internal data class AudienceLabStateSnapshot(
    val hasStoredConfig: Boolean,
    val sdkEnabled: Boolean,
    val metricsEnabled: Boolean,
    val debugEnabled: Boolean,
    val appVersion: String?,
    val creativeToken: String?,
    val localInstallInstanceId: String?,
    val registeredInstallInstanceId: String?,
    val installId: String?,
    val lifecycleDecisionCode: String?,
    val lifecycleRefreshAfter: String?,
    val totalAdValue: Double,
    val totalPurchaseValue: Double,
    val firstRetentionDay: Int?,
    val lastRetentionDay: Int?,
    val sessionId: String?,
    val sessionIndex: Int,
    val sessionLastActiveAtMillis: Long?,
    val sessionStartAtMillis: Long?,
    val sessionAccumulatedDurationMillis: Long,
    val sessionSegmentStartAtMillis: Long?,
    val queuedEvents: List<PersistedQueuedEvent>
)

internal class AudienceLabStateStore(
    private val keyValueStore: KeyValueStore
) {
    fun loadSnapshot(): AudienceLabStateSnapshot {
        val totalAdValue = sanitizeCounter(
            readDouble(KEY_TOTAL_AD_VALUE, defaultValue = 0.0)
        )
        val totalPurchaseValue = sanitizeCounter(
            readDouble(KEY_TOTAL_PURCHASE_VALUE, defaultValue = 0.0)
        )
        val firstRetentionDay = sanitizeDay(
            readNullableInt(KEY_FIRST_RETENTION_DAY)
        )
        val lastRetentionDay = sanitizeDay(
            readNullableInt(KEY_LAST_RETENTION_DAY)
        )
        val sessionIndex = sanitizeSessionIndex(
            keyValueStore.getInt(KEY_SESSION_INDEX, 0)
        )
        val sessionLastActiveAtMillis = sanitizeTimestamp(
            readNullableLong(KEY_SESSION_LAST_ACTIVE_AT)
        )
        val sessionStartAtMillis = sanitizeTimestamp(
            readNullableLong(KEY_SESSION_START_AT)
        )
        val sessionAccumulatedDurationMillis = sanitizeDuration(
            keyValueStore.getLong(KEY_SESSION_ACCUMULATED_DURATION_MS, 0L)
        )
        val sessionSegmentStartAtMillis = sanitizeTimestamp(
            readNullableLong(KEY_SESSION_SEGMENT_START_AT)
        )
        val queuedEvents = readQueuedEvents(
            keyValueStore.getString(KEY_EVENT_QUEUE, null)
        )

        return AudienceLabStateSnapshot(
            hasStoredConfig = keyValueStore.contains(KEY_CONFIG_STORED),
            sdkEnabled = keyValueStore.getBoolean(KEY_SDK_ENABLED, true),
            metricsEnabled = keyValueStore.getBoolean(KEY_METRICS_ENABLED, true),
            debugEnabled = keyValueStore.getBoolean(KEY_DEBUG_ENABLED, false),
            appVersion = keyValueStore.getString(KEY_APP_VERSION, null),
            creativeToken = keyValueStore.getString(KEY_CREATIVE_TOKEN, null),
            localInstallInstanceId = keyValueStore.getString(KEY_LOCAL_INSTALL_INSTANCE_ID, null),
            registeredInstallInstanceId = keyValueStore.getString(KEY_REGISTERED_INSTALL_INSTANCE_ID, null),
            installId = keyValueStore.getString(KEY_INSTALL_ID, null),
            lifecycleDecisionCode = keyValueStore.getString(KEY_LIFECYCLE_DECISION_CODE, null),
            lifecycleRefreshAfter = keyValueStore.getString(KEY_LIFECYCLE_REFRESH_AFTER, null),
            totalAdValue = totalAdValue,
            totalPurchaseValue = totalPurchaseValue,
            firstRetentionDay = firstRetentionDay,
            lastRetentionDay = lastRetentionDay,
            sessionId = keyValueStore.getString(KEY_SESSION_ID, null),
            sessionIndex = sessionIndex,
            sessionLastActiveAtMillis = sessionLastActiveAtMillis,
            sessionStartAtMillis = sessionStartAtMillis,
            sessionAccumulatedDurationMillis = sessionAccumulatedDurationMillis,
            sessionSegmentStartAtMillis = sessionSegmentStartAtMillis,
            queuedEvents = queuedEvents
        )
    }

    fun persistConfiguration(
        sdkEnabled: Boolean,
        metricsEnabled: Boolean,
        debugEnabled: Boolean,
        appVersion: String?
    ) {
        keyValueStore.putBoolean(KEY_SDK_ENABLED, sdkEnabled)
        keyValueStore.putBoolean(KEY_METRICS_ENABLED, metricsEnabled)
        keyValueStore.putBoolean(KEY_DEBUG_ENABLED, debugEnabled)
        keyValueStore.putString(KEY_APP_VERSION, appVersion?.trim()?.takeIf { it.isNotEmpty() })
        keyValueStore.putBoolean(KEY_CONFIG_STORED, true)
    }

    fun setCreativeToken(token: String?) {
        keyValueStore.putString(KEY_CREATIVE_TOKEN, token?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun reconcileInstallInstance(installInstanceId: String): Boolean {
        val normalized = installInstanceId.trim()
        require(normalized.isNotEmpty()) { "Install instance ID must not be empty" }
        if (keyValueStore.getString(KEY_LOCAL_INSTALL_INSTANCE_ID, null) == normalized) {
            return false
        }

        keyValueStore.putString(KEY_LOCAL_INSTALL_INSTANCE_ID, normalized)
        keyValueStore.remove(KEY_REGISTERED_INSTALL_INSTANCE_ID)
        keyValueStore.remove(KEY_INSTALL_ID)
        keyValueStore.remove(KEY_LIFECYCLE_DECISION_CODE)
        keyValueStore.remove(KEY_LIFECYCLE_REFRESH_AFTER)
        return true
    }

    fun markInstallInstanceRegistered(installInstanceId: String) {
        putNonBlankString(KEY_REGISTERED_INSTALL_INSTANCE_ID, installInstanceId)
    }

    fun updateLifecycleContext(
        installId: String?,
        decisionCode: String?,
        refreshAfter: String?
    ) {
        putNonBlankString(KEY_INSTALL_ID, installId)
        putNonBlankString(KEY_LIFECYCLE_DECISION_CODE, decisionCode)
        putNonBlankString(KEY_LIFECYCLE_REFRESH_AFTER, refreshAfter)
    }

    fun setTotalAdValue(value: Double) {
        writeDouble(KEY_TOTAL_AD_VALUE, sanitizeCounter(value))
    }

    fun setTotalPurchaseValue(value: Double) {
        writeDouble(KEY_TOTAL_PURCHASE_VALUE, sanitizeCounter(value))
    }

    fun setRetentionMarkers(firstRetentionDay: Int?, lastRetentionDay: Int?) {
        writeNullableInt(KEY_FIRST_RETENTION_DAY, sanitizeDay(firstRetentionDay))
        writeNullableInt(KEY_LAST_RETENTION_DAY, sanitizeDay(lastRetentionDay))
    }

    fun setSessionMarkers(
        sessionId: String?,
        sessionIndex: Int,
        sessionLastActiveAtMillis: Long?,
        sessionStartAtMillis: Long?,
        sessionAccumulatedDurationMillis: Long,
        sessionSegmentStartAtMillis: Long?
    ) {
        keyValueStore.putString(KEY_SESSION_ID, sessionId?.trim()?.takeIf { it.isNotEmpty() })
        keyValueStore.putInt(KEY_SESSION_INDEX, sanitizeSessionIndex(sessionIndex))
        writeNullableLong(KEY_SESSION_LAST_ACTIVE_AT, sanitizeTimestamp(sessionLastActiveAtMillis))
        writeNullableLong(KEY_SESSION_START_AT, sanitizeTimestamp(sessionStartAtMillis))
        keyValueStore.putLong(
            KEY_SESSION_ACCUMULATED_DURATION_MS,
            sanitizeDuration(sessionAccumulatedDurationMillis)
        )
        writeNullableLong(KEY_SESSION_SEGMENT_START_AT, sanitizeTimestamp(sessionSegmentStartAtMillis))
    }

    fun setQueuedEvents(events: List<PersistedQueuedEvent>) {
        if (events.isEmpty()) {
            keyValueStore.remove(KEY_EVENT_QUEUE)
            return
        }
        keyValueStore.putString(KEY_EVENT_QUEUE, encodeQueuedEvents(events))
    }

    fun clear() {
        keyValueStore.clear()
    }

    private fun readDouble(key: String, defaultValue: Double): Double {
        val bits = keyValueStore.getLong(key, defaultValue.toBits())
        return Double.fromBits(bits)
    }

    private fun writeDouble(key: String, value: Double) {
        keyValueStore.putLong(key, value.toBits())
    }

    private fun putNonBlankString(key: String, value: String?) {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return
        keyValueStore.putString(key, normalized)
    }

    private fun readNullableInt(key: String): Int? {
        if (!keyValueStore.contains(key)) {
            return null
        }
        return keyValueStore.getInt(key, 0)
    }

    private fun writeNullableInt(key: String, value: Int?) {
        if (value == null) {
            keyValueStore.remove(key)
        } else {
            keyValueStore.putInt(key, value)
        }
    }

    private fun readNullableLong(key: String): Long? {
        if (!keyValueStore.contains(key)) {
            return null
        }
        return keyValueStore.getLong(key, 0L)
    }

    private fun writeNullableLong(key: String, value: Long?) {
        if (value == null) {
            keyValueStore.remove(key)
        } else {
            keyValueStore.putLong(key, value)
        }
    }

    private fun sanitizeCounter(value: Double): Double {
        if (!value.isFinite() || value < 0.0) {
            return 0.0
        }
        return CurrencyValue.normalize(value)
    }

    private fun sanitizeDay(day: Int?): Int? {
        if (day == null || day < 0) {
            return null
        }
        return day
    }

    private fun sanitizeSessionIndex(index: Int): Int = index.coerceAtLeast(0)

    private fun sanitizeTimestamp(timestamp: Long?): Long? {
        if (timestamp == null || timestamp < 0L) {
            return null
        }
        return timestamp
    }

    private fun sanitizeDuration(durationMillis: Long): Long {
        return durationMillis.coerceAtLeast(0L)
    }

    private fun readQueuedEvents(raw: String?): List<PersistedQueuedEvent> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }

        return runCatching {
            val decoded = decodeObject(raw) as? List<*> ?: return@runCatching emptyList()
            decoded.mapNotNull { decodeQueuedEvent(it as? Map<*, *>) }
        }.getOrElse {
            emptyList()
        }
    }

    private fun encodeQueuedEvents(events: List<PersistedQueuedEvent>): String {
        val serializable = events.map { event ->
            mapOf(
                "eid" to event.eventId,
                "type" to event.type,
                "dk" to event.dedupeKey,
                "payload" to normalizeSerializableValue(event.payload),
                "created_at" to event.createdAtMillis.coerceAtLeast(0L),
                "attempt" to event.attemptCount.coerceAtLeast(0),
                "next_at" to event.nextAttemptAtMillis.coerceAtLeast(0L),
                "queued_at" to event.queuedAtMillis.coerceAtLeast(0L)
            )
        }
        return encodeObject(serializable)
    }

    private fun decodeQueuedEvent(raw: Map<*, *>?): PersistedQueuedEvent? {
        if (raw == null) {
            return null
        }

        val eventId = (raw["eid"] as? String)?.trim().orEmpty()
        val type = (raw["type"] as? String)?.trim().orEmpty()
        if (eventId.isEmpty() || type.isEmpty()) {
            return null
        }

        val dedupeKey = (raw["dk"] as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val payload = normalizePayloadMap(raw["payload"])
        val createdAtMillis = (raw["created_at"] as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
        val attemptCount = (raw["attempt"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        val nextAttemptAtMillis = (raw["next_at"] as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
        val queuedAtMillis = (raw["queued_at"] as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L

        return PersistedQueuedEvent(
            eventId = eventId,
            type = type,
            dedupeKey = dedupeKey,
            payload = payload,
            createdAtMillis = createdAtMillis,
            attemptCount = attemptCount,
            nextAttemptAtMillis = nextAttemptAtMillis,
            queuedAtMillis = queuedAtMillis
        )
    }

    private fun normalizePayloadMap(raw: Any?): Map<String, Any?> {
        val map = raw as? Map<*, *> ?: return emptyMap()
        return map.entries
            .mapNotNull { (key, value) ->
                val normalizedKey = key as? String ?: return@mapNotNull null
                normalizedKey to normalizeSerializableValue(value)
            }
            .toMap()
    }

    private fun normalizeSerializableValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is String, is Boolean, is Int, is Long, is Double, is Float, is Short, is Byte -> value
            is Number -> value.toDouble()
            is Map<*, *> -> {
                value.entries
                    .mapNotNull { (rawKey, rawValue) ->
                        val key = rawKey as? String ?: return@mapNotNull null
                        key to normalizeSerializableValue(rawValue)
                    }
                    .toMap()
            }
            is List<*> -> value.map { normalizeSerializableValue(it) }
            is Array<*> -> value.map { normalizeSerializableValue(it) }
            else -> value.toString()
        }
    }

    private fun encodeObject(value: Any): String {
        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { stream ->
                stream.writeObject(value)
            }
            output.toByteArray()
        }
        return bytesToHex(bytes)
    }

    private fun decodeObject(raw: String): Any? {
        val bytes = hexToBytes(raw)
        return ByteArrayInputStream(bytes).use { input ->
            ObjectInputStream(input).use { stream ->
                stream.readObject()
            }
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            val normalized = byte.toInt() and 0xff
            if (normalized < 16) {
                builder.append('0')
            }
            builder.append(normalized.toString(16))
        }
        return builder.toString()
    }

    private fun hexToBytes(raw: String): ByteArray {
        if (raw.length % 2 != 0) {
            throw IllegalArgumentException("invalid hex payload length")
        }
        val output = ByteArray(raw.length / 2)
        var index = 0
        while (index < raw.length) {
            val byteValue = raw.substring(index, index + 2).toInt(16)
            output[index / 2] = byteValue.toByte()
            index += 2
        }
        return output
    }

    private companion object {
        const val KEY_CONFIG_STORED = "cfg.stored"
        const val KEY_SDK_ENABLED = "cfg.sdk_enabled"
        const val KEY_METRICS_ENABLED = "cfg.metrics_enabled"
        const val KEY_DEBUG_ENABLED = "cfg.debug_enabled"
        const val KEY_APP_VERSION = "cfg.app_version"

        const val KEY_CREATIVE_TOKEN = "token.creative"
        const val KEY_LOCAL_INSTALL_INSTANCE_ID = "lifecycle.local_install_instance_id"
        const val KEY_REGISTERED_INSTALL_INSTANCE_ID = "lifecycle.registered_install_instance_id"
        const val KEY_INSTALL_ID = "lifecycle.install_id"
        const val KEY_LIFECYCLE_DECISION_CODE = "lifecycle.decision_code"
        const val KEY_LIFECYCLE_REFRESH_AFTER = "lifecycle.refresh_after"
        const val KEY_TOTAL_AD_VALUE = "counter.total_ad_value"
        const val KEY_TOTAL_PURCHASE_VALUE = "counter.total_purchase_value"

        const val KEY_FIRST_RETENTION_DAY = "retention.first_day"
        const val KEY_LAST_RETENTION_DAY = "retention.last_day"

        const val KEY_SESSION_ID = "session.id"
        const val KEY_SESSION_INDEX = "session.index"
        const val KEY_SESSION_LAST_ACTIVE_AT = "session.last_active_ms"
        const val KEY_SESSION_START_AT = "session.start_ms"
        const val KEY_SESSION_ACCUMULATED_DURATION_MS = "session.duration_ms"
        const val KEY_SESSION_SEGMENT_START_AT = "session.segment_start_ms"

        const val KEY_EVENT_QUEUE = "queue.events"
    }
}
