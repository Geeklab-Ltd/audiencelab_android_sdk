package app.geeklab.audiencelab.sdk

import app.geeklab.audiencelab.sdk.internal.network.ApiClient
import app.geeklab.audiencelab.sdk.internal.network.Endpoints
import app.geeklab.audiencelab.sdk.internal.network.NetworkError
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AudienceLabSDKTest {
    @Before
    fun setUp() {
        AudienceLabSDK.resetForTesting(clearPersistence = true)
        AudienceLabSDK.setRunTasksInlineForTesting(true)
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
    fun initialize_usesExplicitDevelopmentModeOverride() {
        AudienceLabSDK.initialize(
            apiKey = "test-api-key-123",
            options = AudienceLabOptions(isDevelopmentMode = true)
        )

        assertTrue(AudienceLabSDK.isDevelopmentMode())
    }

    @Test
    fun initialize_defaultsDevelopmentModeToFalse_withoutContextOrOverride() {
        AudienceLabSDK.initialize("test-api-key-123")

        assertFalse(AudienceLabSDK.isDevelopmentMode())
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
    fun handleDeepLink_rejectsInvalidPlaceholderToken() {
        AudienceLabSDK.initialize("test-api-key-123")

        AudienceLabSDK.handleDeepLink("myapp://open?audiencelab_token=bin")

        assertNull(AudienceLabSDK.getCreativeToken())
        assertEquals("myapp://open?audiencelab_token=bin", AudienceLabSDK.getDeepLink())
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

        assertEquals(3, AudienceLabSDK.getQueueSize())
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

        assertEquals(4, AudienceLabSDK.getQueueSize())
        assertEquals(4.99, AudienceLabSDK.getTotalPurchaseValue(), 0.0001)
    }

    @Test
    fun adAndPurchaseValues_areStoredAsNumbersInQueuedPayloads() {
        val backing = mutableMapOf<String, Any?>()
        val store = AudienceLabStateStore(InMemoryKeyValueStore(backing))

        AudienceLabSDK.setStateStoreForTesting(store)
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
        AudienceLabSDK.sendPurchaseEvent(
            itemId = "starter.pack",
            itemName = "Starter Pack",
            value = 4.99,
            currency = "USD",
            status = "success"
        )

        val queuedEvents = store.loadSnapshot().queuedEvents
        val adPayload = queuedEvents.first { it.type == "custom.ad" }.payload
        val purchasePayload = queuedEvents.first { it.type == "custom.purchase" }.payload

        assertTrue(adPayload["value"] is Number)
        assertTrue(purchasePayload["value"] is Number)
        assertEquals(1.25, (adPayload["value"] as Number).toDouble(), 0.0001)
        assertEquals(4.99, (purchasePayload["value"] as Number).toDouble(), 0.0001)
    }

    @Test
    fun sendCustomEvent_rejectsBlankNames() {
        AudienceLabSDK.initialize("test-api-key-123")

        AudienceLabSDK.sendCustomEvent(name = "   ", params = mapOf("foo" to "bar"))

        assertEquals(2, AudienceLabSDK.getQueueSize())
    }

    @Test
    fun sendUserMetrics_onlySendsOncePerDay() {
        AudienceLabSDK.initialize("test-api-key-123")

        AudienceLabSDK.sendUserMetrics()
        AudienceLabSDK.sendUserMetrics()

        assertEquals(2, AudienceLabSDK.getQueueSize())
    }

    @Test
    fun identityState_isSettled_withoutContextInitialization() {
        AudienceLabSDK.initialize("test-api-key-123")

        assertTrue(AudienceLabSDK.isIdentitySettled())
        val snapshot = AudienceLabSDK.getIdentityDebugSnapshot()
        assertTrue(snapshot.settled)
        assertTrue(snapshot.androidIdSettled)
        assertTrue(snapshot.advertisingIdSettled)
        assertTrue(snapshot.appSetIdSettled)
    }

    @Test
    fun initialize_enqueuesSessionStartAndRetentionDayZero_onFirstLaunch() {
        val backing = mutableMapOf<String, Any?>()
        val store = AudienceLabStateStore(InMemoryKeyValueStore(backing))
        val now = 10L * 24L * 60L * 60L * 1000L

        AudienceLabSDK.setStateStoreForTesting(store)
        AudienceLabSDK.setNowProviderForTesting { now }
        AudienceLabSDK.initialize("test-api-key-123")

        val snapshot = store.loadSnapshot()
        val sessionEvents = snapshot.queuedEvents.filter { it.type == "session" }
        val retentionEvents = snapshot.queuedEvents.filter { it.type == "retention" }

        assertEquals(1, sessionEvents.size)
        assertEquals("start", sessionEvents.first().payload["a"])
        assertEquals(1, retentionEvents.size)
        assertEquals(0, retentionEvents.first().payload["retention_day"])
        assertEquals(0, retentionEvents.first().payload["backfill_day"])
        assertEquals(10, snapshot.firstRetentionDay)
        assertEquals(10, snapshot.lastRetentionDay)
    }

    @Test
    fun initialize_usesLastOpenedRetentionDayAsBackfillValue() {
        val backing = mutableMapOf<String, Any?>()
        val store = AudienceLabStateStore(InMemoryKeyValueStore(backing))
        val currentDay = 105
        val now = currentDay.toLong() * 24L * 60L * 60L * 1000L

        store.setRetentionMarkers(firstRetentionDay = 100, lastRetentionDay = 102)
        AudienceLabSDK.setStateStoreForTesting(store)
        AudienceLabSDK.setNowProviderForTesting { now }
        AudienceLabSDK.initialize("test-api-key-123")

        val retentionEvent = store.loadSnapshot().queuedEvents.last { it.type == "retention" }

        assertEquals(5, retentionEvent.payload["retention_day"])
        assertEquals(2, retentionEvent.payload["backfill_day"])
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
    fun blacklistedEmailAndPhone_areHashedBeforePersisting() {
        AudienceLabSDK.initialize("test-api-key-123")

        AudienceLabSDK.setUserProperty("Email", " User@Example.com ", scope = UserPropertyScope.BLACKLISTED)
        AudienceLabSDK.setUserProperty("phone", "+358 40 123 4567 890", scope = UserPropertyScope.BLACKLISTED)

        val blacklisted = AudienceLabSDK.getBlacklistedUserProperties()
        assertEquals(
            "b4c9a289323b21a01c3e940f150eb9b8c542587f1abfd8f0e1cc1ffc5e475514",
            blacklisted["email"]
        )
        assertEquals(
            "5ae9302068031b93b67fdf05fa3e3410e31dc227c12bfbed09ff764e17c7690e",
            blacklisted["phone"]
        )
    }

    @Test
    fun blacklistedEmailAndPhone_keepPreHashedValuesNormalized() {
        AudienceLabSDK.initialize("test-api-key-123")

        AudienceLabSDK.setUserProperty(
            "email",
            "B4C9A289323B21A01C3E940F150EB9B8C542587F1ABFD8F0E1CC1FFC5E475514",
            scope = UserPropertyScope.BLACKLISTED
        )

        assertEquals(
            "b4c9a289323b21a01c3e940f150eb9b8c542587f1abfd8f0e1cc1ffc5e475514",
            AudienceLabSDK.getBlacklistedUserProperties()["email"]
        )
    }

    @Test
    fun reservedWhitelistedKeys_areRejectedFromDeveloperSetters() {
        AudienceLabSDK.initialize("test-api-key-123")

        AudienceLabSDK.setUserProperty("_network", "meta", scope = UserPropertyScope.WHITELISTED)

        assertNull(AudienceLabSDK.getWhitelistedUserProperties()["_network"])
    }

    @Test
    fun fetchCreativeToken_mergesServerWhitelistedProperties() {
        val latch = CountDownLatch(1)
        AudienceLabSDK.initialize("test-api-key-123")
        AudienceLabSDK.setUserProperty("country", "FI", scope = UserPropertyScope.WHITELISTED)
        AudienceLabSDK.setApiClientForTesting(
            object : ApiClient {
                override fun post(path: String, body: Map<String, Any?>, callback: (Result<Map<String, Any?>>) -> Unit) {
                    callback(
                        Result.success(
                            mapOf(
                                "token" to "server-token-123",
                                "wp" to mapOf(
                                    "_network" to "meta",
                                    "campaign" to "spring_launch"
                                )
                            )
                        )
                    )
                    latch.countDown()
                }
            }
        )

        invokePrivateSdkMethod("fetchCreativeTokenIfNeeded", "wp_merge_test")

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        val properties = AudienceLabSDK.getWhitelistedUserProperties()
        assertEquals("FI", properties["country"])
        assertEquals("meta", properties["_network"])
        assertEquals("spring_launch", properties["campaign"])
    }

    @Test
    fun reservedWhitelistedProperties_surviveUnsetAndClear() {
        val backing = mutableMapOf<String, Any?>()
        val store = AudienceLabStateStore(InMemoryKeyValueStore(backing))
        AudienceLabSDK.setStateStoreForTesting(store)
        AudienceLabSDK.initialize("test-api-key-123")

        val mergeMethod = AudienceLabSDK::class.java.getDeclaredMethod(
            "mergeServerWhitelistedProperties",
            Map::class.java
        ).apply {
            isAccessible = true
        }
        mergeMethod.invoke(AudienceLabSDK, mapOf("_network" to "meta", "country" to "FI"))

        AudienceLabSDK.unsetUserProperty("_network", isWhitelisted = true)
        AudienceLabSDK.clearUserProperties(isWhitelisted = true)

        assertEquals("meta", AudienceLabSDK.getWhitelistedUserProperties()["_network"])
        assertNull(AudienceLabSDK.getWhitelistedUserProperties()["country"])
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
        assertEquals(4, AudienceLabSDK.getQueueSize())
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
        assertEquals(6, snapshot.queuedEvents.size)
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
                    createdAtMillis = staleQueuedAt,
                    attemptCount = 0,
                    nextAttemptAtMillis = 0L,
                    queuedAtMillis = staleQueuedAt
                ),
                PersistedQueuedEvent(
                    eventId = "evt_fresh",
                    type = "custom",
                    dedupeKey = null,
                    payload = mapOf("en" to "fresh"),
                    createdAtMillis = freshQueuedAt,
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
        assertEquals(2, snapshot.queuedEvents.size)
        assertEquals("evt_fresh", snapshot.queuedEvents.first { it.type == "custom" }.eventId)
        val retentionEvent = snapshot.queuedEvents.first { it.type == "retention" }
        assertEquals(0, retentionEvent.payload["retention_day"])
        assertEquals(0, retentionEvent.payload["backfill_day"])
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

    @Test
    fun flushQueue_keepsWebhookEventsQueued_whenFetchTokenReturnsUnauthorized() {
        AudienceLabSDK.initialize("test-api-key-123")
        AudienceLabSDK.clearQueueForTesting()
        AudienceLabSDK.sendCustomEvent("queued_until_token")

        val calledPaths = mutableListOf<String>()
        AudienceLabSDK.setApiClientForTesting(
            object : ApiClient {
                override fun post(path: String, body: Map<String, Any?>, callback: (Result<Map<String, Any?>>) -> Unit) {
                    calledPaths.add(path)
                    if (path == Endpoints.FETCH_TOKEN) {
                        callback(
                            Result.failure(
                                NetworkError.HttpFailure(
                                    statusCode = 401,
                                    responseBody = mapOf("error" to "unauthorized")
                                )
                            )
                        )
                    } else {
                        callback(Result.success(emptyMap()))
                    }
                }
            }
        )

        invokePrivateSdkMethod("flushQueue", "test_unauthorized")

        assertEquals(listOf(Endpoints.FETCH_TOKEN), calledPaths)
        assertEquals(1, AudienceLabSDK.getQueueSize())
    }

    @Test
    fun flushQueue_dispatchesWebhook_afterCreativeTokenBecomesAvailable() {
        AudienceLabSDK.initialize("test-api-key-123")
        AudienceLabSDK.clearQueueForTesting()
        AudienceLabSDK.sendCustomEvent("queued_then_delivered")
        AudienceLabSDK.handleDeepLink("myapp://open?creative_token=real-token-123")

        val webhookBodies = mutableListOf<Map<String, Any?>>()
        AudienceLabSDK.setApiClientForTesting(
            object : ApiClient {
                override fun post(path: String, body: Map<String, Any?>, callback: (Result<Map<String, Any?>>) -> Unit) {
                    if (path == Endpoints.WEBHOOK) {
                        webhookBodies.add(body)
                    }
                    callback(Result.success(emptyMap()))
                }
            }
        )

        invokePrivateSdkMethod("flushQueue", "test_ready")

        assertEquals(0, AudienceLabSDK.getQueueSize())
        assertEquals(1, webhookBodies.size)
        assertEquals("real-token-123", webhookBodies.single()["creativeToken"])
        assertEquals("custom", webhookBodies.single()["type"])
        assertEquals("queued_then_delivered", (webhookBodies.single()["payload"] as? Map<*, *>)?.get("en"))
    }

    @Test
    fun fetchCreativeToken_rejectsBlankServerToken() {
        val latch = CountDownLatch(1)
        AudienceLabSDK.initialize("test-api-key-123")
        AudienceLabSDK.setApiClientForTesting(
            object : ApiClient {
                override fun post(path: String, body: Map<String, Any?>, callback: (Result<Map<String, Any?>>) -> Unit) {
                    callback(Result.success(mapOf("token" to "   ")))
                    latch.countDown()
                }
            }
        )

        invokePrivateSdkMethod("fetchCreativeTokenIfNeeded", "blank_token_test")

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertNull(AudienceLabSDK.getCreativeToken())
        assertEquals("invalid_token", AudienceLabSDK.getTokenDebugSnapshot().lastFetchStatus)
    }

    @Test
    fun fetchCreativeToken_rejectsPlaceholderServerToken() {
        val latch = CountDownLatch(1)
        AudienceLabSDK.initialize("test-api-key-123")
        AudienceLabSDK.setApiClientForTesting(
            object : ApiClient {
                override fun post(path: String, body: Map<String, Any?>, callback: (Result<Map<String, Any?>>) -> Unit) {
                    callback(Result.success(mapOf("token" to "bin")))
                    latch.countDown()
                }
            }
        )

        invokePrivateSdkMethod("fetchCreativeTokenIfNeeded", "placeholder_token_test")

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertNull(AudienceLabSDK.getCreativeToken())
        assertEquals("invalid_token", AudienceLabSDK.getTokenDebugSnapshot().lastFetchStatus)
    }

    @Test
    fun fetchCreativeToken_acceptsValidServerToken_andResetsRetryBudget() {
        val latch = CountDownLatch(1)
        AudienceLabSDK.initialize("test-api-key-123")
        setPrivateSdkField("tokenFetchAttempt", 4)
        setPrivateSdkField("tokenFetchNextAttemptAtMillis", 123_456L)
        AudienceLabSDK.setApiClientForTesting(
            object : ApiClient {
                override fun post(path: String, body: Map<String, Any?>, callback: (Result<Map<String, Any?>>) -> Unit) {
                    callback(Result.success(mapOf("token" to "server-token-123")))
                    latch.countDown()
                }
            }
        )

        invokePrivateSdkMethod("fetchCreativeTokenIfNeeded", "success_token_test")

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        val snapshot = AudienceLabSDK.getTokenDebugSnapshot()
        assertEquals("server-token-123", AudienceLabSDK.getCreativeToken())
        assertTrue(snapshot.hasValidToken)
        assertEquals("ok", snapshot.lastFetchStatus)
        assertEquals(0, snapshot.attemptCount)
        assertNull(snapshot.nextRetryAtMillis)
    }

    @Test
    fun connectivityRestore_clearsTokenRetryBudget() {
        AudienceLabSDK.initialize("test-api-key-123")
        setPrivateSdkField("tokenFetchAttempt", 3)
        setPrivateSdkField("tokenFetchNextAttemptAtMillis", 999_999L)

        invokePrivateNoArgSdkMethod("onConnectivityRestored")

        val snapshot = AudienceLabSDK.getTokenDebugSnapshot()
        assertEquals(0, snapshot.attemptCount)
        assertNull(snapshot.nextRetryAtMillis)
    }

    @Test
    fun webhookEnvelope_preservesCreatedAtAcrossReplay() {
        val backing = mutableMapOf<String, Any?>()
        val store = AudienceLabStateStore(InMemoryKeyValueStore(backing))
        val now = 1_700_000_000_000L
        AudienceLabSDK.setStateStoreForTesting(store)
        AudienceLabSDK.setNowProviderForTesting { now }
        AudienceLabSDK.initialize("test-api-key-123")
        AudienceLabSDK.sendCustomEvent("created_at_test")
        AudienceLabSDK.handleDeepLink("myapp://open?creative_token=real-token-123")

        val queueEntry = AudienceLabSDK.getQueueDebugSnapshot().first { it.type == "custom" }
        val webhookBodies = mutableListOf<Map<String, Any?>>()
        AudienceLabSDK.setApiClientForTesting(
            object : ApiClient {
                override fun post(path: String, body: Map<String, Any?>, callback: (Result<Map<String, Any?>>) -> Unit) {
                    if (path == Endpoints.WEBHOOK) {
                        webhookBodies.add(body)
                    }
                    callback(Result.success(emptyMap()))
                }
            }
        )

        invokePrivateSdkMethod("flushQueue", "created_at_test")

        val createdAt = webhookBodies.first()["created_at"] as? String
        assertEquals("2023-11-14 22:13:20", createdAt)
        assertEquals(now, queueEntry.createdAtMillis)
    }

    private fun invokePrivateSdkMethod(methodName: String, arg: String) {
        val method = AudienceLabSDK::class.java.getDeclaredMethod(methodName, String::class.java)
        method.isAccessible = true
        method.invoke(AudienceLabSDK, arg)
    }

    private fun invokePrivateNoArgSdkMethod(methodName: String) {
        val method = AudienceLabSDK::class.java.getDeclaredMethod(methodName)
        method.isAccessible = true
        method.invoke(AudienceLabSDK)
    }

    private fun setPrivateSdkField(fieldName: String, value: Any) {
        val field = AudienceLabSDK::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(AudienceLabSDK, value)
    }
}
