package app.geeklab.audiencelab.sdk.internal.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudienceLabStateStoreTest {
    @Test
    fun loadSnapshot_returnsDefaults_whenStoreIsEmpty() {
        val store = AudienceLabStateStore(InMemoryKeyValueStore())

        val snapshot = store.loadSnapshot()

        assertFalse(snapshot.hasStoredConfig)
        assertTrue(snapshot.sdkEnabled)
        assertTrue(snapshot.metricsEnabled)
        assertFalse(snapshot.debugEnabled)
        assertNull(snapshot.appVersion)
        assertNull(snapshot.creativeToken)
        assertEquals(0.0, snapshot.totalAdValue, 0.0001)
        assertEquals(0.0, snapshot.totalPurchaseValue, 0.0001)
        assertNull(snapshot.firstRetentionDay)
        assertNull(snapshot.lastRetentionDay)
        assertNull(snapshot.sessionId)
        assertEquals(0, snapshot.sessionIndex)
        assertNull(snapshot.sessionLastActiveAtMillis)
        assertNull(snapshot.sessionStartAtMillis)
        assertEquals(0L, snapshot.sessionAccumulatedDurationMillis)
        assertNull(snapshot.sessionSegmentStartAtMillis)
        assertTrue(snapshot.queuedEvents.isEmpty())
    }

    @Test
    fun loadSnapshot_returnsPersistedValues_afterRoundtrip() {
        val backing = mutableMapOf<String, Any?>()
        val writer = AudienceLabStateStore(InMemoryKeyValueStore(backing))
        writer.persistConfiguration(
            sdkEnabled = false,
            metricsEnabled = false,
            debugEnabled = true,
            appVersion = "1.2.3"
        )
        writer.setCreativeToken("creative-token-123")
        writer.setTotalAdValue(11.5)
        writer.setTotalPurchaseValue(3.25)
        writer.setRetentionMarkers(firstRetentionDay = 100, lastRetentionDay = 101)
        writer.setSessionMarkers(
            sessionId = "session-abc",
            sessionIndex = 3,
            sessionLastActiveAtMillis = 1_700_000_000_000,
            sessionStartAtMillis = 1_699_999_000_000,
            sessionAccumulatedDurationMillis = 125_000,
            sessionSegmentStartAtMillis = 1_700_000_100_000
        )
        writer.setQueuedEvents(
            listOf(
                PersistedQueuedEvent(
                    eventId = "evt_1",
                    type = "custom",
                    dedupeKey = "dk_1",
                    payload = mapOf(
                        "en" to "level_up",
                        "pr" to mapOf("level" to 9)
                    ),
                    attemptCount = 2,
                    nextAttemptAtMillis = 1_700_000_000_500,
                    queuedAtMillis = 1_700_000_000_100
                )
            )
        )

        val reader = AudienceLabStateStore(InMemoryKeyValueStore(backing))
        val snapshot = reader.loadSnapshot()

        assertTrue(snapshot.hasStoredConfig)
        assertFalse(snapshot.sdkEnabled)
        assertFalse(snapshot.metricsEnabled)
        assertTrue(snapshot.debugEnabled)
        assertEquals("1.2.3", snapshot.appVersion)
        assertEquals("creative-token-123", snapshot.creativeToken)
        assertEquals(11.5, snapshot.totalAdValue, 0.0001)
        assertEquals(3.25, snapshot.totalPurchaseValue, 0.0001)
        assertEquals(100, snapshot.firstRetentionDay)
        assertEquals(101, snapshot.lastRetentionDay)
        assertEquals("session-abc", snapshot.sessionId)
        assertEquals(3, snapshot.sessionIndex)
        assertEquals(1_700_000_000_000, snapshot.sessionLastActiveAtMillis)
        assertEquals(1_699_999_000_000, snapshot.sessionStartAtMillis)
        assertEquals(125_000, snapshot.sessionAccumulatedDurationMillis)
        assertEquals(1_700_000_100_000, snapshot.sessionSegmentStartAtMillis)
        assertEquals(1, snapshot.queuedEvents.size)
        val queuedEvent = snapshot.queuedEvents.first()
        assertEquals("evt_1", queuedEvent.eventId)
        assertEquals("custom", queuedEvent.type)
        assertEquals("dk_1", queuedEvent.dedupeKey)
        assertEquals(2, queuedEvent.attemptCount)
        assertEquals(1_700_000_000_500, queuedEvent.nextAttemptAtMillis)
        assertEquals(1_700_000_000_100, queuedEvent.queuedAtMillis)
    }

    @Test
    fun loadSnapshot_normalizesInvalidValues() {
        val store = AudienceLabStateStore(InMemoryKeyValueStore())
        store.persistConfiguration(
            sdkEnabled = true,
            metricsEnabled = true,
            debugEnabled = false,
            appVersion = " "
        )
        store.setCreativeToken("   ")
        store.setTotalAdValue(Double.NaN)
        store.setTotalPurchaseValue(-42.0)
        store.setRetentionMarkers(firstRetentionDay = -1, lastRetentionDay = -4)
        store.setSessionMarkers(
            sessionId = " ",
            sessionIndex = -3,
            sessionLastActiveAtMillis = -1,
            sessionStartAtMillis = -1,
            sessionAccumulatedDurationMillis = -42,
            sessionSegmentStartAtMillis = -100
        )

        val snapshot = store.loadSnapshot()

        assertNull(snapshot.appVersion)
        assertNull(snapshot.creativeToken)
        assertEquals(0.0, snapshot.totalAdValue, 0.0001)
        assertEquals(0.0, snapshot.totalPurchaseValue, 0.0001)
        assertNull(snapshot.firstRetentionDay)
        assertNull(snapshot.lastRetentionDay)
        assertNull(snapshot.sessionId)
        assertEquals(0, snapshot.sessionIndex)
        assertNull(snapshot.sessionLastActiveAtMillis)
        assertNull(snapshot.sessionStartAtMillis)
        assertEquals(0L, snapshot.sessionAccumulatedDurationMillis)
        assertNull(snapshot.sessionSegmentStartAtMillis)
        assertTrue(snapshot.queuedEvents.isEmpty())
    }
}
