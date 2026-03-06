package app.geeklab.audiencelab.sdk

import app.geeklab.audiencelab.sdk.internal.storage.AudienceLabStateStore
import app.geeklab.audiencelab.sdk.internal.storage.InMemoryKeyValueStore
import app.geeklab.audiencelab.sdk.internal.storage.PersistedQueuedEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudienceLabSDKTest {
    @Before
    fun setUp() {
        AudienceLabSDK.resetForTesting(clearPersistence = true)
    }

    @Test
    fun initialize_returnsFalse_whenApiKeyIsBlank() {
        val initialized = AudienceLabSDK.initialize("   ")

        assertFalse(initialized)
        assertFalse(AudienceLabSDK.isInitialized())
    }

    @Test
    fun initialize_setsSdkState_whenApiKeyIsValid() {
        val initialized = AudienceLabSDK.initialize(
            apiKey = "test-api-key-123",
            options = AudienceLabOptions(isDebugEnabled = true)
        )

        assertTrue(initialized)
        assertTrue(AudienceLabSDK.isInitialized())
        assertNotNull(AudienceLabSDK.getConfiguredApiKeyPreview())
    }

    @Test
    fun toggles_areUpdated_afterInitialization() {
        AudienceLabSDK.initialize(
            apiKey = "test-api-key-123",
            options = AudienceLabOptions(
                isSdkEnabled = true,
                isMetricsEnabled = true,
                isDebugEnabled = false
            )
        )

        AudienceLabSDK.setSdkEnabled(false)
        AudienceLabSDK.toggleMetricsCollection(false)
        AudienceLabSDK.setDebugEnabled(true)

        assertFalse(AudienceLabSDK.getIsSDKEnabled())
        assertFalse(AudienceLabSDK.getIsMetricsCollection())
        assertTrue(AudienceLabSDK.getIsDebugEnabled())
    }

    @Test
    fun handleDeepLink_extractsToken_whenPresent() {
        AudienceLabSDK.initialize("test-api-key-123")

        AudienceLabSDK.handleDeepLink("myapp://open?audiencelab_token=token-abc-123")

        assertEquals("token-abc-123", AudienceLabSDK.getCreativeToken())
        assertEquals("myapp://open?audiencelab_token=token-abc-123", AudienceLabSDK.getDeepLink())
    }

    @Test
    fun sendAdEvent_updatesTotalsAndQueue() {
        AudienceLabSDK.initialize("test-api-key-123")

        AudienceLabSDK.sendAdEvent(
            adId = "ad-1",
            name = "rewarded",
            source = "admob",
            watchTime = 30.0,
            reward = true,
            mediaSource = "network-a",
            channel = "gameplay",
            value = 1.25,
            currency = "USD"
        )

        assertEquals(2, AudienceLabSDK.getQueueSize())
        assertEquals(1.25, AudienceLabSDK.getTotalAdValue(), 0.0001)
    }

    @Test
    fun sendPurchaseEvent_incrementsTotalOnlyForSuccessLikeStatuses() {
        AudienceLabSDK.initialize("test-api-key-123")

        AudienceLabSDK.sendPurchaseEvent(
            itemId = "starter.pack",
            itemName = "Starter Pack",
            value = 4.99,
            currency = "USD",
            status = "success"
        )
        AudienceLabSDK.sendPurchaseEvent(
            itemId = "vip.monthly",
            itemName = "VIP Monthly",
            value = 9.99,
            currency = "USD",
            status = "failed"
        )

        assertEquals(3, AudienceLabSDK.getQueueSize())
        assertEquals(4.99, AudienceLabSDK.getTotalPurchaseValue(), 0.0001)
    }

    @Test
    fun sendCustomEvent_rejectsBlankNames() {
        AudienceLabSDK.initialize("test-api-key-123")

        AudienceLabSDK.sendCustomEvent(name = "   ", params = mapOf("foo" to "bar"))

        assertEquals(1, AudienceLabSDK.getQueueSize())
    }

    @Test
    fun sendUserMetrics_onlySendsOncePerDay() {
        AudienceLabSDK.initialize("test-api-key-123")

        AudienceLabSDK.sendUserMetrics()
        AudienceLabSDK.sendUserMetrics()

        assertEquals(2, AudienceLabSDK.getQueueSize())
    }

    @Test
    fun userProperties_supportSetUnsetAndClear() {
        AudienceLabSDK.initialize("test-api-key-123")

        AudienceLabSDK.setUserProperty("country", "FI", isWhitelisted = true)
        assertEquals("FI", AudienceLabSDK.getWhitelistedUserProperties()["country"])

        AudienceLabSDK.unsetUserProperty("country", isWhitelisted = true)
        assertNull(AudienceLabSDK.getWhitelistedUserProperties()["country"])

        AudienceLabSDK.setUserProperty("region", "eu", isWhitelisted = false)
        AudienceLabSDK.clearUserProperties(isWhitelisted = false)
        assertTrue(AudienceLabSDK.getBlacklistedUserProperties().isEmpty())
    }

    @Test
    fun requestResultCallback_isInvoked_whenEventIsAccepted() {
        AudienceLabSDK.initialize("test-api-key-123")

        var callbackSuccess: Boolean? = null
        var callbackError: String? = "placeholder"
        AudienceLabSDK.setRequestResultCallback { success, errorMessage ->
            callbackSuccess = success
            callbackError = errorMessage
        }

        AudienceLabSDK.sendCustomEvent("test_event")

        assertEquals(true, callbackSuccess)
        assertNull(callbackError)
    }

    @Test
    fun persistedState_survivesReinitialize_withSharedStore() {
        val backing = mutableMapOf<String, Any?>()
        val store = AudienceLabStateStore(InMemoryKeyValueStore(backing))

        AudienceLabSDK.setStateStoreForTesting(store)
        AudienceLabSDK.initialize(
            apiKey = "test-api-key-123",
            options = AudienceLabOptions(
                appVersion = "1.2.3",
                isDebugEnabled = true
            )
        )
        AudienceLabSDK.sendAdEvent(
            adId = "ad-1",
            name = "rewarded",
            source = "admob",
            watchTime = 30.0,
            reward = true,
            mediaSource = "network-a",
            channel = "gameplay",
            value = 2.5,
            currency = "USD"
        )
        AudienceLabSDK.sendPurchaseEvent(
            itemId = "starter.pack",
            itemName = "Starter Pack",
            value = 4.99,
            currency = "USD",
            status = "success"
        )
        AudienceLabSDK.setSdkEnabled(false)
        AudienceLabSDK.toggleMetricsCollection(false)
        AudienceLabSDK.handleDeepLink("myapp://open?creative_token=persisted-token")
        AudienceLabSDK.sendUserMetrics()

        AudienceLabSDK.resetForTesting(clearPersistence = false)
        AudienceLabSDK.setStateStoreForTesting(store)
        AudienceLabSDK.initialize("test-api-key-123")

        assertFalse(AudienceLabSDK.getIsSDKEnabled())
        assertFalse(AudienceLabSDK.getIsMetricsCollection())
        assertTrue(AudienceLabSDK.getIsDebugEnabled())
        assertEquals("1.2.3", AudienceLabSDK.getAppVersion())
        assertEquals("persisted-token", AudienceLabSDK.getCreativeToken())
        assertEquals(2.5, AudienceLabSDK.getTotalAdValue(), 0.0001)
        assertEquals(4.99, AudienceLabSDK.getTotalPurchaseValue(), 0.0001)

        AudienceLabSDK.sendUserMetrics()
        assertEquals(3, AudienceLabSDK.getQueueSize())
    }

    @Test
    fun sessionLifecycle_emitsTimeoutEndAndNewStart_afterInactivity() {
        val backing = mutableMapOf<String, Any?>()
        val store = AudienceLabStateStore(InMemoryKeyValueStore(backing))
        var now = 1_700_000_000_000L

        AudienceLabSDK.setStateStoreForTesting(store)
        AudienceLabSDK.setNowProviderForTesting { now }
        AudienceLabSDK.initialize("test-api-key-123")

        AudienceLabSDK.sendCustomEvent("first_event")
        now += (30L * 60L * 1000L) + 1L
        AudienceLabSDK.sendCustomEvent("second_event")

        val snapshot = store.loadSnapshot()
        val sessionEvents = snapshot.queuedEvents
            .filter { it.type == "session" }
            .mapNotNull { queued ->
                val action = queued.payload["a"] as? String ?: return@mapNotNull null
                action to queued.payload
            }

        assertEquals(3, sessionEvents.size)
        assertEquals("start", sessionEvents[0].first)
        assertEquals("end", sessionEvents[1].first)
        assertEquals("timeout", sessionEvents[1].second["r"])
        assertEquals("start", sessionEvents[2].first)
        assertEquals(2, snapshot.sessionIndex)
        assertEquals(5, snapshot.queuedEvents.size)
    }

    @Test
    fun queuePolicy_prunesStaleEventsDuringRestore() {
        val backing = mutableMapOf<String, Any?>()
        val store = AudienceLabStateStore(InMemoryKeyValueStore(backing))
        var now = 1_700_000_000_000L
        val staleQueuedAt = now - (24L * 60L * 60L * 1000L) - 1L
        val freshQueuedAt = now - 10_000L

        store.setSessionMarkers(
            sessionId = "session-existing",
            sessionIndex = 1,
            sessionLastActiveAtMillis = now,
            sessionStartAtMillis = now - 60_000L,
            sessionAccumulatedDurationMillis = 5_000L,
            sessionSegmentStartAtMillis = null
        )
        store.setQueuedEvents(
            listOf(
                PersistedQueuedEvent(
                    eventId = "evt_stale",
                    type = "custom",
                    dedupeKey = null,
                    payload = mapOf("en" to "stale"),
                    attemptCount = 0,
                    nextAttemptAtMillis = 0L,
                    queuedAtMillis = staleQueuedAt
                ),
                PersistedQueuedEvent(
                    eventId = "evt_fresh",
                    type = "custom",
                    dedupeKey = null,
                    payload = mapOf("en" to "fresh"),
                    attemptCount = 0,
                    nextAttemptAtMillis = 0L,
                    queuedAtMillis = freshQueuedAt
                )
            )
        )

        AudienceLabSDK.setStateStoreForTesting(store)
        AudienceLabSDK.setNowProviderForTesting { now }
        AudienceLabSDK.initialize("test-api-key-123")

        val snapshot = store.loadSnapshot()
        assertEquals(1, snapshot.queuedEvents.size)
        assertEquals("evt_fresh", snapshot.queuedEvents.first().eventId)
    }

    @Test
    fun sessionLifecycle_emitsBackgroundTimeoutEnd_afterLongBackgroundGap() {
        val backing = mutableMapOf<String, Any?>()
        val store = AudienceLabStateStore(InMemoryKeyValueStore(backing))
        var now = 1_700_000_000_000L
        val onBackground = AudienceLabSDK::class.java.getDeclaredMethod("onBackground").apply {
            isAccessible = true
        }
        val onForeground = AudienceLabSDK::class.java.getDeclaredMethod("onForeground").apply {
            isAccessible = true
        }

        AudienceLabSDK.setStateStoreForTesting(store)
        AudienceLabSDK.setNowProviderForTesting { now }
        AudienceLabSDK.initialize("test-api-key-123")
        AudienceLabSDK.sendCustomEvent("active_event")

        onBackground.invoke(AudienceLabSDK)
        now += (30L * 60L * 1000L) + 1L
        onForeground.invoke(AudienceLabSDK)

        val snapshot = store.loadSnapshot()
        val sessionEvents = snapshot.queuedEvents
            .filter { it.type == "session" }
            .mapNotNull { queued ->
                val action = queued.payload["a"] as? String ?: return@mapNotNull null
                action to queued.payload
            }

        assertEquals(3, sessionEvents.size)
        assertEquals("start", sessionEvents[0].first)
        assertEquals("end", sessionEvents[1].first)
        assertEquals("background_timeout", sessionEvents[1].second["r"])
        assertEquals("start", sessionEvents[2].first)
        assertEquals(2, snapshot.sessionIndex)
    }

    @Test
    fun queuePolicy_capsQueueSizeAt200() {
        AudienceLabSDK.initialize("test-api-key-123")

        repeat(250) { index ->
            AudienceLabSDK.sendCustomEvent(name = "event_$index")
        }

        assertEquals(200, AudienceLabSDK.getQueueSize())
    }
}
