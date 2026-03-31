package app.geeklab.audiencelab.sdk

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import app.geeklab.audiencelab.sdk.internal.Logger
import app.geeklab.audiencelab.sdk.internal.device.DeviceDebugCollector
import app.geeklab.audiencelab.sdk.internal.device.DeviceInfoCollector
import app.geeklab.audiencelab.sdk.internal.identity.IdentityCollector
import app.geeklab.audiencelab.sdk.internal.identity.IdentityOverrides
import app.geeklab.audiencelab.sdk.internal.identity.PlayServicesIdentityReader
import app.geeklab.audiencelab.sdk.internal.network.ApiClient
import app.geeklab.audiencelab.sdk.internal.network.Endpoints
import app.geeklab.audiencelab.sdk.internal.network.EnvelopeBuildInput
import app.geeklab.audiencelab.sdk.internal.network.EventEnvelopeMapper
import app.geeklab.audiencelab.sdk.internal.network.FetchTokenBuildInput
import app.geeklab.audiencelab.sdk.internal.network.FetchTokenRequestMapper
import app.geeklab.audiencelab.sdk.internal.network.HttpApiClient
import app.geeklab.audiencelab.sdk.internal.network.NetworkError
import app.geeklab.audiencelab.sdk.internal.network.RetryPolicy
import app.geeklab.audiencelab.sdk.internal.storage.AudienceLabStateSnapshot
import app.geeklab.audiencelab.sdk.internal.storage.AudienceLabStateStore
import app.geeklab.audiencelab.sdk.internal.storage.InMemoryKeyValueStore
import app.geeklab.audiencelab.sdk.internal.storage.PersistedQueuedEvent
import app.geeklab.audiencelab.sdk.internal.storage.SharedPreferencesKeyValueStore
import app.geeklab.audiencelab.sdk.internal.util.CurrencyValue
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object AudienceLabSDK {
    private const val DEFAULT_STATE_PREFERENCES = "audiencelab.sdk.state"
    private const val SDK_TYPE = "native_android"
    private const val MAX_QUEUE_SIZE = 200
    private const val MAX_USER_PROPERTY_COUNT = 50
    private const val MAX_USER_PROPERTY_KEY_LENGTH = 64
    private const val MAX_USER_PROPERTY_VALUE_LENGTH = 256
    private const val MAX_USER_PROPERTY_BYTES = 2048
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    private const val SESSION_TIMEOUT_MILLIS = 30L * 60L * 1000L
    private const val MAX_QUEUE_RETRY_DELAY_MILLIS = 5L * 60L * 1000L
    private const val MAX_QUEUE_AGE_MILLIS = 24L * 60L * 60L * 1000L
    private val hashRequiredBlacklistedKeys = setOf("email", "phone")

    private val deepLinkTokenKeys = listOf(
        "audiencelab_token",
        "creative_token",
        "token",
        "al_token",
        "geeklab_ct"
    )

    @Volatile
    private var state: State = State()

    @Volatile
    private var requestResultCallback: RequestResultCallback? = null

    @Volatile
    private var requestDebugListener: RequestDebugListener? = null

    @Volatile
    private var stateStore: AudienceLabStateStore = AudienceLabStateStore(InMemoryKeyValueStore())

    @Volatile
    private var apiClient: ApiClient? = null

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var tokenFetchInProgress: Boolean = false

    @Volatile
    private var advertisingIdProvider: AdvertisingIdProvider? = null

    @Volatile
    private var appSetIdProvider: AppSetIdProvider? = null

    @Volatile
    private var manualAdvertisingId: String? = null

    @Volatile
    private var manualAppSetId: String? = null

    @Volatile
    private var manualLimitAdTracking: Boolean? = null

    @Volatile
    private var queueDispatchInProgress: Boolean = false

    @Volatile
    private var tokenFetchAttempt: Int = 0

    @Volatile
    private var tokenFetchNextAttemptAtMillis: Long = 0L

    @Volatile
    private var tokenFetchLastStatus: String = "idle"

    @Volatile
    private var tokenFetchLastAttemptAtMillis: Long? = null

    @Volatile
    private var tokenRetryFuture: ScheduledFuture<*>? = null

    @Volatile
    private var queueFlushFuture: ScheduledFuture<*>? = null

    @Volatile
    private var lifecycleApplication: Application? = null

    @Volatile
    private var activityLifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null

    @Volatile
    private var startedActivityCount: Int = 0

    @Volatile
    private var connectivityManager: ConnectivityManager? = null

    @Volatile
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var nowProvider: () -> Long = { System.currentTimeMillis() }

    @Volatile
    private var runTasksInlineForTesting: Boolean = false

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val persistenceExecutor: Executor = Executors.newSingleThreadExecutor()

    @Synchronized
    @JvmStatic
    @JvmOverloads
    fun initialize(apiKey: String, options: AudienceLabOptions = AudienceLabOptions()): Boolean {
        return initializeInternal(context = null, apiKey = apiKey, options = options)
    }

    @Synchronized
    @JvmStatic
    @JvmOverloads
    fun initialize(context: Context, apiKey: String, options: AudienceLabOptions = AudienceLabOptions()): Boolean {
        return initializeInternal(context = context.applicationContext, apiKey = apiKey, options = options)
    }

    @JvmStatic
    @JvmOverloads
    fun init(apiKey: String, options: AudienceLabOptions = AudienceLabOptions()): Boolean {
        return initialize(apiKey, options)
    }

    @JvmStatic
    @JvmOverloads
    fun init(context: Context, apiKey: String, options: AudienceLabOptions = AudienceLabOptions()): Boolean {
        return initialize(context, apiKey, options)
    }

    @JvmStatic
    fun isInitialized(): Boolean = state.initialized

    @Synchronized
    @JvmStatic
    fun setSdkEnabled(enabled: Boolean) {
        state = state.copy(sdkEnabled = enabled)
        persistConfigurationFromState()
    }

    @JvmStatic
    fun setSDKEnabled(enabled: Boolean) = setSdkEnabled(enabled)

    @JvmStatic
    fun isSdkEnabled(): Boolean = state.sdkEnabled

    @JvmStatic
    fun getIsSDKEnabled(): Boolean = isSdkEnabled()

    @Synchronized
    @JvmStatic
    fun toggleMetricsCollection(enabled: Boolean) {
        state = state.copy(metricsEnabled = enabled)
        persistConfigurationFromState()
    }

    @JvmStatic
    fun isMetricsCollectionEnabled(): Boolean = state.metricsEnabled

    @JvmStatic
    fun getIsMetricsCollection(): Boolean = isMetricsCollectionEnabled()

    @Synchronized
    @JvmStatic
    fun setDebugEnabled(enabled: Boolean) {
        Logger.configure(enabled)
        state = state.copy(debugEnabled = enabled)
        persistConfigurationFromState()
    }

    @JvmStatic
    fun getIsDebugEnabled(): Boolean = state.debugEnabled

    @JvmStatic
    fun isDebugEnabled(): Boolean = getIsDebugEnabled()

    @JvmStatic
    fun isDevelopmentMode(): Boolean = state.developmentMode

    @Synchronized
    @JvmStatic
    fun isIdentitySettled(): Boolean {
        refreshIdentityDebugState(allowAutoResolve = false)
        return state.identityDebugSnapshot.settled
    }

    @Synchronized
    @JvmStatic
    fun getIdentityDebugSnapshot(): IdentityDebugSnapshot {
        refreshIdentityDebugState(allowAutoResolve = false)
        return state.identityDebugSnapshot
    }

    @JvmStatic
    fun getDeviceDebugSnapshot(): DeviceDebugSnapshot = DeviceDebugCollector.collect(applicationContext)

    @JvmStatic
    fun getTokenDebugSnapshot(): TokenDebugSnapshot {
        val token = normalizeValidToken(state.creativeToken)
        return TokenDebugSnapshot(
            hasValidToken = token != null,
            creativeTokenPreview = maskDebugValue(token),
            lastFetchStatus = tokenFetchLastStatus,
            lastAttemptAtMillis = tokenFetchLastAttemptAtMillis,
            attemptCount = tokenFetchAttempt,
            nextRetryAtMillis = tokenFetchNextAttemptAtMillis.takeIf { it > 0L }
        )
    }

    @Synchronized
    @JvmStatic
    fun setAdvertisingIdProvider(provider: AdvertisingIdProvider?) {
        advertisingIdProvider = provider
        refreshIdentityDebugState(allowAutoResolve = false)
    }

    @Synchronized
    @JvmStatic
    fun setAppSetIdProvider(provider: AppSetIdProvider?) {
        appSetIdProvider = provider
        refreshIdentityDebugState(allowAutoResolve = false)
    }

    /**
     * Sets the Google Advertising ID (GAID) manually.
     *
     * Can be called before or after [initialize]. When called before, the ID is
     * included in the very first token fetch and event dispatch.
     */
    @Synchronized
    @JvmStatic
    fun setAdvertisingId(gaid: String?) {
        manualAdvertisingId = normalizeValidToken(gaid)
        refreshIdentityDebugState(allowAutoResolve = false)
    }

    @Synchronized
    @JvmStatic
    fun clearAdvertisingId() {
        manualAdvertisingId = null
        refreshIdentityDebugState(allowAutoResolve = false)
    }

    /**
     * Sets the App Set ID manually.
     *
     * Can be called before or after [initialize]. When called before, the ID is
     * included in the very first token fetch and event dispatch.
     */
    @Synchronized
    @JvmStatic
    fun setAppSetId(appSetId: String?) {
        manualAppSetId = normalizeValidToken(appSetId)
        refreshIdentityDebugState(allowAutoResolve = false)
    }

    @Synchronized
    @JvmStatic
    fun clearAppSetId() {
        manualAppSetId = null
        refreshIdentityDebugState(allowAutoResolve = false)
    }

    @Synchronized
    @JvmStatic
    fun setLimitAdTracking(value: Boolean?) {
        manualLimitAdTracking = value
        refreshIdentityDebugState(allowAutoResolve = false)
    }

    @Synchronized
    @JvmStatic
    fun handleDeepLink(url: String?) {
        val normalized = url?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return
        }

        val token = normalizeValidToken(extractTokenFromUrl(normalized))
        if (token != null) {
            stateStore.setCreativeToken(token)
            tokenFetchLastStatus = "deep_link_token"
        }

        state = state.copy(
            deepLink = normalized,
            creativeToken = token ?: state.creativeToken
        )

        if (token != null && apiClient != null && state.queue.isNotEmpty()) {
            scheduleQueueFlush(delayMillis = 0L, reason = "deep_link_token")
        }
    }

    @JvmStatic
    fun getCreativeToken(): String? = normalizeValidToken(state.creativeToken)

    @JvmStatic
    fun getDeepLink(): String? = state.deepLink

    @Synchronized
    @JvmStatic
    fun sendUserMetrics() {
        if (!validateTrackingState("retention")) {
            return
        }

        touchSessionMarker()

        val currentDay = utcEpochDay()
        if (state.lastRetentionDay == currentDay) {
            return
        }

        val firstDay = state.firstRetentionDay ?: currentDay
        val retentionDay = (currentDay - firstDay).coerceAtLeast(0)
        val previousLastDay = state.lastRetentionDay
        val backfillDay = if (previousLastDay == null) {
            0
        } else {
            (previousLastDay - firstDay).coerceAtLeast(0)
        }

        state = state.copy(
            firstRetentionDay = firstDay,
            lastRetentionDay = currentDay
        )

        stateStore.setRetentionMarkers(
            firstRetentionDay = state.firstRetentionDay,
            lastRetentionDay = state.lastRetentionDay
        )

        enqueueEvent(
            type = "retention",
            payload = mapOf(
                "retention_day" to retentionDay,
                "backfill_day" to backfillDay
            )
        )
    }

    @Synchronized
    @JvmStatic
    @JvmOverloads
    fun sendAdEvent(
        adId: String,
        name: String,
        source: String,
        watchTime: Double,
        reward: Boolean,
        mediaSource: String,
        channel: String,
        value: Double,
        currency: String,
        dedupeKey: String? = null
    ) {
        sendAdEvent(
            AdEvent(
                adId = adId,
                name = name,
                source = source,
                watchTime = watchTime,
                reward = reward,
                mediaSource = mediaSource,
                channel = channel,
                value = value,
                currency = currency,
                dedupeKey = dedupeKey
            )
        )
    }

    @Synchronized
    @JvmStatic
    fun sendAdEvent(event: AdEvent) {
        if (!validateTrackingState("custom.ad")) {
            return
        }

        touchSessionMarker()

        val sanitizedValue = sanitizeMonetaryValue(event.value)
        val totalAdValue = sanitizeCounter(CurrencyValue.add(state.totalAdValue, sanitizedValue))
        state = state.copy(totalAdValue = totalAdValue)
        stateStore.setTotalAdValue(totalAdValue)

        enqueueEvent(
            type = "custom.ad",
            payload = mapOf(
                "ad_id" to event.adId,
                "name" to event.name,
                "source" to event.source,
                "watch_time" to event.watchTime,
                "reward" to event.reward,
                "media_source" to event.mediaSource,
                "channel" to event.channel,
                "value" to sanitizedValue,
                "currency" to event.currency,
                "total_ad_value" to totalAdValue
            ),
            dedupeKey = event.dedupeKey
        )
    }

    @Synchronized
    @JvmStatic
    @JvmOverloads
    fun sendPurchaseEvent(
        itemId: String,
        itemName: String,
        value: Double,
        currency: String,
        status: String,
        transactionId: String? = null,
        dedupeKey: String? = null
    ) {
        sendPurchaseEvent(
            PurchaseEvent(
                itemId = itemId,
                itemName = itemName,
                value = value,
                currency = currency,
                status = status,
                transactionId = transactionId,
                dedupeKey = dedupeKey
            )
        )
    }

    @Synchronized
    @JvmStatic
    fun sendPurchaseEvent(event: PurchaseEvent) {
        if (!validateTrackingState("custom.purchase")) {
            return
        }

        touchSessionMarker()

        val sanitizedValue = sanitizeMonetaryValue(event.value)
        val shouldIncrementTotalValue = event.status.equals("completed", ignoreCase = true) ||
            event.status.equals("success", ignoreCase = true)
        val totalPurchaseValue = if (shouldIncrementTotalValue) {
            sanitizeCounter(CurrencyValue.add(state.totalPurchaseValue, sanitizedValue))
        } else {
            state.totalPurchaseValue
        }
        state = state.copy(totalPurchaseValue = totalPurchaseValue)
        stateStore.setTotalPurchaseValue(totalPurchaseValue)

        val payload = mutableMapOf<String, Any?>(
            "item_id" to event.itemId,
            "item_name" to event.itemName,
            "value" to sanitizedValue,
            "currency" to event.currency,
            "status" to event.status,
            "total_purchase_value" to totalPurchaseValue
        )
        if (!event.transactionId.isNullOrBlank()) {
            payload["tr_id"] = event.transactionId
        }

        enqueueEvent(
            type = "custom.purchase",
            payload = payload,
            dedupeKey = event.dedupeKey
        )
    }

    @Synchronized
    @JvmStatic
    @JvmOverloads
    fun sendCustomEvent(
        name: String,
        params: Map<String, Any?> = emptyMap(),
        dedupeKey: String? = null
    ) {
        sendCustomEvent(
            CustomEvent(
                name = name,
                params = params,
                dedupeKey = dedupeKey
            )
        )
    }

    @Synchronized
    @JvmStatic
    fun sendCustomEvent(event: CustomEvent) {
        if (!validateTrackingState("custom")) {
            return
        }

        val normalizedName = event.name.trim()
        if (normalizedName.isEmpty()) {
            Logger.w("Custom event rejected: empty name")
            return
        }

        touchSessionMarker()

        enqueueEvent(
            type = "custom",
            payload = mapOf(
                "en" to normalizedName,
                "pr" to event.params
            ),
            dedupeKey = event.dedupeKey
        )
    }

    @JvmStatic
    @JvmOverloads
    fun sendCustomAdMetrics(
        adId: String,
        name: String,
        source: String,
        watchTime: Double,
        reward: Boolean,
        mediaSource: String,
        channel: String,
        value: Double,
        currency: String,
        dedupeKey: String? = null
    ) {
        sendAdEvent(
            adId = adId,
            name = name,
            source = source,
            watchTime = watchTime,
            reward = reward,
            mediaSource = mediaSource,
            channel = channel,
            value = value,
            currency = currency,
            dedupeKey = dedupeKey
        )
    }

    @JvmStatic
    @JvmOverloads
    fun sendCustomPurchaseMetrics(
        itemId: String,
        itemName: String,
        value: Double,
        currency: String,
        status: String,
        transactionId: String? = null,
        dedupeKey: String? = null
    ) {
        sendPurchaseEvent(
            itemId = itemId,
            itemName = itemName,
            value = value,
            currency = currency,
            status = status,
            transactionId = transactionId,
            dedupeKey = dedupeKey
        )
    }

    @Synchronized
    @JvmStatic
    fun setUserProperty(key: String, value: String, isWhitelisted: Boolean) {
        val scope = if (isWhitelisted) UserPropertyScope.WHITELISTED else UserPropertyScope.BLACKLISTED
        setUserProperty(key = key, value = value, scope = scope)
    }

    @Synchronized
    @JvmStatic
    fun setUserProperty(key: String, value: String, scope: UserPropertyScope) {
        val normalizedKey = normalizeUserPropertyKey(key = key, scope = scope)
        val normalizedValue = normalizeUserPropertyValue(
            key = normalizedKey,
            value = value,
            scope = scope
        )
        if (scope == UserPropertyScope.WHITELISTED && isReservedWhitelistedPropertyKey(normalizedKey)) {
            Logger.w("User property rejected: reserved whitelisted key $normalizedKey")
            return
        }
        if (!isValidUserProperty(key = normalizedKey, value = normalizedValue)) {
            return
        }

        val updated = getUserProperties(scope).toMutableMap()
        if (!updated.containsKey(normalizedKey) && updated.size >= MAX_USER_PROPERTY_COUNT) {
            Logger.w("User property rejected: max count reached for $scope")
            return
        }

        updated[normalizedKey] = normalizedValue
        if (estimatePropertySize(updated) > MAX_USER_PROPERTY_BYTES) {
            Logger.w("User property rejected: max serialized size reached for $scope")
            return
        }

        updateUserProperties(scope, updated)
    }

    @Synchronized
    @JvmStatic
    fun setUserProperty(key: String, value: Any?, isWhitelisted: Boolean) {
        val scope = if (isWhitelisted) UserPropertyScope.WHITELISTED else UserPropertyScope.BLACKLISTED
        setUserProperty(key = key, value = value, scope = scope)
    }

    @Synchronized
    @JvmStatic
    fun setUserProperty(key: String, value: Any?, scope: UserPropertyScope) {
        val normalizedValue = when (value) {
            null -> ""
            is String -> value
            is Number -> value.toString()
            is Boolean -> value.toString()
            else -> value.toString()
        }
        setUserProperty(key = key, value = normalizedValue, scope = scope)
    }

    @Synchronized
    @JvmStatic
    fun unsetUserProperty(key: String, isWhitelisted: Boolean) {
        val scope = if (isWhitelisted) UserPropertyScope.WHITELISTED else UserPropertyScope.BLACKLISTED
        unsetUserProperty(key, scope)
    }

    @Synchronized
    @JvmStatic
    fun unsetUserProperty(key: String, scope: UserPropertyScope) {
        val normalizedKey = normalizeUserPropertyKey(key = key, scope = scope)
        if (normalizedKey.isEmpty()) {
            return
        }
        if (scope == UserPropertyScope.WHITELISTED && isReservedWhitelistedPropertyKey(normalizedKey)) {
            Logger.w("Reserved whitelisted property preserved: $normalizedKey")
            return
        }

        val updated = getUserProperties(scope).toMutableMap()
        updated.remove(normalizedKey)
        updateUserProperties(scope, updated)
    }

    @Synchronized
    @JvmStatic
    fun clearUserProperties(isWhitelisted: Boolean) {
        val scope = if (isWhitelisted) UserPropertyScope.WHITELISTED else UserPropertyScope.BLACKLISTED
        clearUserProperties(scope)
    }

    @Synchronized
    @JvmStatic
    fun clearUserProperties(scope: UserPropertyScope) {
        val next = if (scope == UserPropertyScope.WHITELISTED) {
            getUserProperties(scope).filterKeys(::isReservedWhitelistedPropertyKey)
        } else {
            emptyMap()
        }
        updateUserProperties(scope, next)
    }

    @JvmStatic
    fun getWhitelistedUserProperties(): Map<String, String> = state.whitelistedUserProperties

    @JvmStatic
    fun getBlacklistedUserProperties(): Map<String, String> = state.blacklistedUserProperties

    @JvmStatic
    fun getTotalAdValue(): Double = state.totalAdValue

    @JvmStatic
    fun getTotalPurchaseValue(): Double = state.totalPurchaseValue

    @JvmStatic
    fun getQueueSize(): Int = state.queue.size

    @JvmStatic
    fun getQueueDebugSnapshot(): List<QueueDebugEntry> {
        return state.queue.map { event ->
            QueueDebugEntry(
                requestName = buildRequestName(type = event.type, payload = event.payload),
                eventId = event.eventId,
                type = event.type,
                payload = stringifyPayload(event.payload),
                createdAtMillis = event.createdAtMillis,
                attemptCount = event.attemptCount,
                nextAttemptAtMillis = event.nextAttemptAtMillis,
                queuedAtMillis = event.queuedAtMillis
            )
        }
    }

    @JvmStatic
    fun setRequestResultCallback(callback: RequestResultCallback?) {
        requestResultCallback = callback
    }

    @JvmStatic
    fun clearRequestResultCallback() {
        requestResultCallback = null
    }

    @JvmStatic
    fun setRequestDebugListener(listener: RequestDebugListener?) {
        requestDebugListener = listener
    }

    @JvmStatic
    fun clearRequestDebugListener() {
        requestDebugListener = null
    }

    @JvmStatic
    fun getSdkVersion(): String = BuildConfig.SDK_VERSION

    @JvmStatic
    fun getSDKVersion(): String = getSdkVersion()

    @JvmStatic
    fun getAppVersion(): String = state.appVersion ?: "unknown"

    @JvmStatic
    fun getRuntimeVersion(): String {
        val version = runCatching { android.os.Build.VERSION.RELEASE }.getOrNull()
        return if (version.isNullOrBlank()) "Android unknown" else "Android $version"
    }

    @JvmStatic
    fun getConfiguredApiKeyPreview(): String? {
        val key = state.apiKey ?: return null
        if (key.length <= 6) return "***"
        return key.take(3) + "***" + key.takeLast(3)
    }

    @Synchronized
    @JvmStatic
    @JvmOverloads
    fun resetForTesting(clearPersistence: Boolean = true) {
        if (clearPersistence) {
            stateStore.clear()
        }
        state = State()
        requestResultCallback = null
        requestDebugListener = null
        apiClient = null
        applicationContext = null
        tokenFetchInProgress = false
        tokenFetchAttempt = 0
        tokenFetchNextAttemptAtMillis = 0L
        tokenFetchLastStatus = "idle"
        tokenFetchLastAttemptAtMillis = null
        advertisingIdProvider = null
        appSetIdProvider = null
        manualAdvertisingId = null
        manualAppSetId = null
        manualLimitAdTracking = null
        queueDispatchInProgress = false
        cancelScheduledTasks()
        unregisterRuntimeSignals()
        nowProvider = { System.currentTimeMillis() }
        runTasksInlineForTesting = false
        PlayServicesIdentityReader.resetForTesting()
        Logger.configure(false)
    }

    @Synchronized
    internal fun setStateStoreForTesting(store: AudienceLabStateStore) {
        stateStore = store
        state = State()
        requestResultCallback = null
        requestDebugListener = null
        tokenFetchInProgress = false
        tokenFetchAttempt = 0
        tokenFetchNextAttemptAtMillis = 0L
        tokenFetchLastStatus = "idle"
        tokenFetchLastAttemptAtMillis = null
        queueDispatchInProgress = false
        cancelScheduledTasks()
        unregisterRuntimeSignals()
        nowProvider = { System.currentTimeMillis() }
        runTasksInlineForTesting = false
        PlayServicesIdentityReader.resetForTesting()
        Logger.configure(false)
    }

    @Synchronized
    internal fun setNowProviderForTesting(provider: () -> Long) {
        nowProvider = provider
    }

    @Synchronized
    internal fun setRunTasksInlineForTesting(enabled: Boolean) {
        runTasksInlineForTesting = enabled
    }

    @Synchronized
    internal fun setApiClientForTesting(client: ApiClient?) {
        apiClient = client
        queueDispatchInProgress = false
    }

    @Synchronized
    internal fun clearQueueForTesting() {
        queueFlushFuture?.cancel(false)
        queueFlushFuture = null
        queueDispatchInProgress = false
        state = state.copy(queue = emptyList())
        persistQueue()
    }

    private fun initializeInternal(context: Context?, apiKey: String, options: AudienceLabOptions): Boolean {
        val normalizedApiKey = apiKey.trim()
        if (normalizedApiKey.isEmpty()) {
            Logger.w("Initialization failed: API key is empty")
            return false
        }

        if (context != null) {
            applicationContext = context
        }

        configureStateStore(applicationContext)

        val snapshot = stateStore.loadSnapshot()
        val appVersion = resolveAppVersion(
            explicitAppVersion = options.appVersion,
            context = applicationContext,
            fallback = snapshot.appVersion
        )
        val sdkEnabled = options.isSdkEnabled
        val metricsEnabled = options.isMetricsEnabled
        val debugEnabled = options.isDebugEnabled
        val baseUrl = resolveBaseUrl(options)
        val creativeToken = normalizeValidToken(snapshot.creativeToken)
        val developmentMode = resolveDevelopmentMode(
            explicitValue = options.isDevelopmentMode,
            context = applicationContext
        )

        val nowMillis = nowMillis()
        val restoredQueue = applyQueuePolicies(
            snapshot.queuedEvents.mapNotNull { persisted ->
                mapPersistedQueuedEvent(
                    persisted = persisted,
                    nowMillis = nowMillis
                )
            },
            nowMillis = nowMillis
        )

        state = State(
            initialized = true,
            apiKey = normalizedApiKey,
            sdkEnabled = sdkEnabled,
            metricsEnabled = metricsEnabled,
            debugEnabled = debugEnabled,
            developmentMode = developmentMode,
            appVersion = appVersion,
            baseUrl = baseUrl,
            creativeToken = creativeToken,
            totalAdValue = snapshot.totalAdValue,
            totalPurchaseValue = snapshot.totalPurchaseValue,
            firstRetentionDay = snapshot.firstRetentionDay,
            lastRetentionDay = snapshot.lastRetentionDay,
            sessionId = snapshot.sessionId,
            sessionIndex = snapshot.sessionIndex,
            sessionLastActiveAtMillis = snapshot.sessionLastActiveAtMillis,
            sessionStartAtMillis = snapshot.sessionStartAtMillis,
            sessionAccumulatedDurationMillis = snapshot.sessionAccumulatedDurationMillis,
            sessionSegmentStartAtMillis = snapshot.sessionSegmentStartAtMillis,
            queue = restoredQueue,
            identityDebugSnapshot = emptyIdentityDebugSnapshot()
        )
        persistQueue()
        stateStore.setCreativeToken(creativeToken)
        tokenFetchLastStatus = if (creativeToken != null) "token_available" else "missing"
        tokenFetchLastAttemptAtMillis = null
        tokenFetchAttempt = 0
        tokenFetchNextAttemptAtMillis = 0L

        stateStore.persistConfiguration(
            sdkEnabled = sdkEnabled,
            metricsEnabled = metricsEnabled,
            debugEnabled = debugEnabled,
            appVersion = appVersion
        )
        refreshIdentityDebugState(allowAutoResolve = false)
        restoreSessionLifecycleAtInitialization(snapshot = snapshot, nowMillis = nowMillis)

        configureNetworkClient(baseUrl = baseUrl)
        registerRuntimeSignals(applicationContext)

        Logger.configure(debugEnabled)
        Logger.d("AudienceLab Android SDK initialized with persisted state")

        sendUserMetrics()
        fetchCreativeTokenIfNeeded(trigger = "initialize")
        scheduleQueueFlush(delayMillis = 0L, reason = "initialize")

        return true
    }

    private fun configureStateStore(context: Context?) {
        if (context == null) {
            return
        }

        val sharedPreferences = context.getSharedPreferences(DEFAULT_STATE_PREFERENCES, Context.MODE_PRIVATE)
        stateStore = AudienceLabStateStore(SharedPreferencesKeyValueStore(sharedPreferences))
    }

    private fun configureNetworkClient(baseUrl: String) {
        if (applicationContext == null) {
            apiClient = null
            return
        }

        apiClient = HttpApiClient(
            baseUrl = baseUrl,
            apiKeyProvider = { state.apiKey },
            requestObserver = { entry ->
                requestDebugListener?.onEntry(entry)
            }
        )
    }

    private fun fetchCreativeTokenIfNeeded(trigger: String) {
        if (hasValidCreativeToken()) {
            synchronized(this) {
                tokenFetchLastStatus = "token_available"
                clearTokenRetryStateLocked()
            }
            return
        }

        val client = apiClient ?: run {
            synchronized(this) {
                tokenFetchLastStatus = "client_unavailable"
            }
            return
        }

        synchronized(this) {
            if (tokenFetchInProgress || hasValidCreativeToken()) {
                return
            }

            val nowMillis = nowMillis()
            if (tokenFetchNextAttemptAtMillis > nowMillis) {
                val delayMillis = (tokenFetchNextAttemptAtMillis - nowMillis).coerceAtLeast(0L)
                scheduleTokenRetryLocked(delayMillis = delayMillis, reason = "deferred_$trigger")
                return
            }

            tokenFetchInProgress = true
            tokenFetchLastStatus = "fetching"
            tokenFetchLastAttemptAtMillis = nowMillis
        }

        executeAsync {
            val deviceInfo = DeviceInfoCollector.collect(applicationContext)
            val identity = collectIdentity()
            val payload = FetchTokenRequestMapper.map(
                FetchTokenBuildInput(
                    sdkVersion = getSdkVersion(),
                    sdkType = SDK_TYPE,
                    appVersion = getAppVersion(),
                    createdAtMillis = nowMillis(),
                    developmentMode = state.developmentMode,
                    whitelistedProperties = state.whitelistedUserProperties,
                    blacklistedProperties = state.blacklistedUserProperties,
                    deviceInfo = deviceInfo,
                    identity = identity
                )
            )

            client.post(Endpoints.FETCH_TOKEN, payload) { result ->
                synchronized(this) {
                    tokenFetchInProgress = false
                }

                result.onSuccess { response ->
                    val token = normalizeValidToken(response["token"] as? String)
                    if (token != null) {
                        synchronized(this) {
                            state = state.copy(creativeToken = token)
                            stateStore.setCreativeToken(token)
                            tokenFetchLastStatus = "ok"
                            clearTokenRetryStateLocked()
                            if (state.queue.isNotEmpty()) {
                                scheduleQueueFlush(delayMillis = 0L, reason = "token_available")
                            }
                        }
                    } else {
                        synchronized(this) {
                            tokenFetchLastStatus = "invalid_token"
                        }
                    }

                    val serverWp = normalizeStringMap(response["wp"])
                    if (serverWp.isNotEmpty()) {
                        synchronized(this) {
                            mergeServerWhitelistedProperties(serverWp)
                        }
                    }
                }.onFailure { error ->
                    val retryable = RetryPolicy.isRetryable(error)
                    synchronized(this) {
                        val nextFailedAttemptCount = tokenFetchAttempt + 1
                        tokenFetchAttempt = nextFailedAttemptCount
                        tokenFetchLastStatus = readableTokenStatus(error)
                        if (retryable && RetryPolicy.canRetryToken(nextFailedAttemptCount)) {
                            val delayMillis = RetryPolicy.tokenBackoffMillis(
                                attempt = nextFailedAttemptCount,
                                jitterRatio = randomJitterRatio()
                            )
                            tokenFetchNextAttemptAtMillis = nowMillis() + delayMillis
                            scheduleTokenRetryLocked(delayMillis = delayMillis, reason = "fetch_token_retry")
                        } else if (retryable) {
                            tokenFetchLastStatus = "max_attempts"
                            tokenFetchNextAttemptAtMillis = 0L
                            tokenRetryFuture?.cancel(false)
                            tokenRetryFuture = null
                        } else {
                            tokenFetchNextAttemptAtMillis = 0L
                            tokenRetryFuture?.cancel(false)
                            tokenRetryFuture = null
                        }
                    }
                    Logger.w("Fetch token failed ($trigger): ${error.message ?: "unknown"}")
                }
            }
        }
    }

    private fun scheduleTokenRetryLocked(delayMillis: Long, reason: String) {
        val normalizedDelayMillis = delayMillis.coerceAtLeast(0L)
        tokenRetryFuture?.cancel(false)
        tokenRetryFuture = scheduleAsync(
            { fetchCreativeTokenIfNeeded(trigger = reason) },
            normalizedDelayMillis
        )
    }

    private fun clearTokenRetryStateLocked() {
        tokenFetchAttempt = 0
        tokenFetchNextAttemptAtMillis = 0L
        tokenRetryFuture?.cancel(false)
        tokenRetryFuture = null
    }

    private fun hasValidCreativeToken(): Boolean = normalizeValidToken(state.creativeToken) != null

    private fun normalizeValidToken(token: String?): String? {
        val normalized = token
            ?.trim()
            ?.trimStart('?')
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        if (normalized.equals("bin", ignoreCase = true)) {
            return null
        }
        return normalized
    }

    private fun randomJitterRatio(): Double = Math.random() * 0.3

    private fun readableTokenStatus(error: Throwable): String {
        val networkError = error as? NetworkError
        return when (networkError) {
            is NetworkError.HttpFailure -> "http_${networkError.statusCode}"
            NetworkError.ApiKeyMissing -> "api_key_missing"
            is NetworkError.InvalidUrl -> "invalid_url"
            is NetworkError.SerializationFailure -> "serialization_error"
            is NetworkError.TransportFailure -> "transport_error"
            else -> "request_failed"
        }
    }

    private fun nowMillis(): Long = nowProvider.invoke()

    private fun restoreSessionLifecycleAtInitialization(snapshot: AudienceLabStateSnapshot, nowMillis: Long) {
        val hasPersistedSession = !snapshot.sessionId.isNullOrBlank() && snapshot.sessionIndex > 0
        val isExpired = isSessionExpired(lastActiveAtMillis = snapshot.sessionLastActiveAtMillis, nowMillis = nowMillis)

        when {
            !hasPersistedSession -> {
                startNewSession(nowMillis = nowMillis, emitStartEvent = true)
            }
            isExpired -> {
                val persistedSessionId = snapshot.sessionId.orEmpty()
                val persistedSessionIndex = snapshot.sessionIndex
                val durationSeconds = snapshot.sessionAccumulatedDurationMillis
                    .toDouble()
                    .div(1_000.0)
                    .roundToInt()
                    .coerceAtLeast(0)

                enqueueSessionLifecycleEvent(
                    action = "end",
                    sessionId = persistedSessionId,
                    sessionIndex = persistedSessionIndex,
                    reason = "timeout",
                    durationSeconds = durationSeconds
                )
                startNewSession(nowMillis = nowMillis, emitStartEvent = true)
            }
            else -> {
                state = state.copy(
                    sessionLastActiveAtMillis = nowMillis,
                    sessionSegmentStartAtMillis = nowMillis,
                    lastBackgroundAtMillis = null
                )
                persistSessionState()
            }
        }
    }

    private fun resolveBaseUrl(options: AudienceLabOptions): String {
        return options.baseUrl
            ?.trim()
            ?.removeSuffix("/")
            ?.takeIf { it.isNotEmpty() }
            ?: state.baseUrl
            ?: Endpoints.DEFAULT_BASE_URL
    }

    private fun resolveDevelopmentMode(explicitValue: Boolean?, context: Context?): Boolean {
        explicitValue?.let { return it }
        if (context == null) {
            return false
        }
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun refreshIdentityDebugState(allowAutoResolve: Boolean) {
        val result = IdentityCollector.collectDetailed(
            context = applicationContext,
            advertisingIdProvider = advertisingIdProvider,
            appSetIdProvider = appSetIdProvider,
            overrides = IdentityOverrides(
                ga = manualAdvertisingId,
                asid = manualAppSetId,
                lat = manualLimitAdTracking
            ),
            allowAutoResolve = allowAutoResolve
        )
        updateIdentityDebugSnapshot(result.diagnostics)
    }

    private fun updateIdentityDebugSnapshot(diagnostics: IdentityCollector.IdentityDiagnostics) {
        state = state.copy(
            identityDebugSnapshot = IdentityDebugSnapshot(
                settled = diagnostics.settled,
                advertisingIdSettled = diagnostics.advertisingIdSettled,
                appSetIdSettled = diagnostics.appSetIdSettled,
                androidIdSettled = diagnostics.androidIdSettled,
                advertisingIdAvailable = diagnostics.advertisingId != null,
                appSetIdAvailable = diagnostics.appSetId != null,
                androidIdAvailable = diagnostics.androidId != null,
                advertisingIdPreview = maskDebugValue(diagnostics.advertisingId),
                appSetIdPreview = maskDebugValue(diagnostics.appSetId),
                androidIdPreview = maskDebugValue(diagnostics.androidId),
                limitAdTracking = diagnostics.limitAdTracking
            )
        )
    }

    private fun resolveAppVersion(explicitAppVersion: String?, context: Context?, fallback: String?): String {
        explicitAppVersion?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        fallback?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        val packageVersion = readPackageVersion(context)
        if (!packageVersion.isNullOrBlank()) {
            return packageVersion
        }

        return "unknown"
    }

    @Suppress("DEPRECATION")
    private fun readPackageVersion(context: Context?): String? {
        if (context == null) {
            return null
        }
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun persistConfigurationFromState() {
        stateStore.persistConfiguration(
            sdkEnabled = state.sdkEnabled,
            metricsEnabled = state.metricsEnabled,
            debugEnabled = state.debugEnabled,
            appVersion = state.appVersion
        )
    }

    private fun validateTrackingState(eventType: String): Boolean {
        if (!state.initialized) {
            notifyRequestResult(false, "SDK is not initialized")
            return false
        }
        if (!state.sdkEnabled) {
            Logger.d("Event dropped because SDK is disabled: $eventType")
            return false
        }
        if (!state.metricsEnabled) {
            Logger.d("Event dropped because metrics are disabled: $eventType")
            return false
        }
        return true
    }

    private fun touchSessionMarker(nowMillis: Long = nowMillis()) {
        if (!hasValidSession()) {
            startNewSession(nowMillis = nowMillis, emitStartEvent = true)
            return
        }

        if (isSessionExpired(lastActiveAtMillis = state.sessionLastActiveAtMillis, nowMillis = nowMillis)) {
            endCurrentSession(
                reason = "timeout",
                nowMillis = nowMillis,
                includeCurrentSegment = true
            )
            startNewSession(nowMillis = nowMillis, emitStartEvent = true)
            return
        }

        state = state.copy(
            sessionLastActiveAtMillis = nowMillis,
            sessionSegmentStartAtMillis = state.sessionSegmentStartAtMillis ?: nowMillis,
            lastBackgroundAtMillis = null
        )
        persistSessionState()
    }

    private fun hasValidSession(): Boolean {
        return !state.sessionId.isNullOrBlank() &&
            state.sessionIndex > 0 &&
            (state.sessionStartAtMillis ?: 0L) > 0L
    }

    private fun isSessionExpired(lastActiveAtMillis: Long?, nowMillis: Long): Boolean {
        if (lastActiveAtMillis == null) {
            return false
        }
        return (nowMillis - lastActiveAtMillis) > SESSION_TIMEOUT_MILLIS
    }

    private fun startNewSession(nowMillis: Long, emitStartEvent: Boolean) {
        val sessionId = UUID.randomUUID().toString().lowercase(Locale.US)
        val sessionIndex = (state.sessionIndex + 1).coerceAtLeast(1)
        state = state.copy(
            sessionId = sessionId,
            sessionIndex = sessionIndex,
            sessionLastActiveAtMillis = nowMillis,
            sessionStartAtMillis = nowMillis,
            sessionAccumulatedDurationMillis = 0L,
            sessionSegmentStartAtMillis = nowMillis,
            lastBackgroundAtMillis = null
        )
        persistSessionState()
        if (emitStartEvent) {
            enqueueSessionLifecycleEvent(
                action = "start",
                sessionId = sessionId,
                sessionIndex = sessionIndex,
                reason = null,
                durationSeconds = null
            )
        }
    }

    private fun endCurrentSession(
        reason: String,
        nowMillis: Long,
        includeCurrentSegment: Boolean
    ) {
        val sessionId = state.sessionId ?: return
        val sessionIndex = state.sessionIndex
        if (sessionIndex <= 0) {
            return
        }

        val durationMillis = calculateSessionDurationMillis(
            nowMillis = nowMillis,
            includeCurrentSegment = includeCurrentSegment
        )
        val durationSeconds = durationMillis.toDouble()
            .div(1_000.0)
            .roundToInt()
            .coerceAtLeast(0)

        state = state.copy(
            sessionAccumulatedDurationMillis = durationMillis,
            sessionSegmentStartAtMillis = null,
            sessionLastActiveAtMillis = nowMillis,
            lastBackgroundAtMillis = null
        )
        persistSessionState()

        enqueueSessionLifecycleEvent(
            action = "end",
            sessionId = sessionId,
            sessionIndex = sessionIndex,
            reason = reason,
            durationSeconds = durationSeconds
        )
    }

    private fun calculateSessionDurationMillis(nowMillis: Long, includeCurrentSegment: Boolean): Long {
        val baseMillis = state.sessionAccumulatedDurationMillis.coerceAtLeast(0L)
        val segmentMillis = if (includeCurrentSegment) {
            val segmentStartAt = state.sessionSegmentStartAtMillis
            if (segmentStartAt == null) {
                0L
            } else {
                (nowMillis - segmentStartAt).coerceAtLeast(0L)
            }
        } else {
            0L
        }
        return (baseMillis + segmentMillis).coerceAtLeast(0L)
    }

    private fun persistSessionState() {
        stateStore.setSessionMarkers(
            sessionId = state.sessionId,
            sessionIndex = state.sessionIndex,
            sessionLastActiveAtMillis = state.sessionLastActiveAtMillis,
            sessionStartAtMillis = state.sessionStartAtMillis,
            sessionAccumulatedDurationMillis = state.sessionAccumulatedDurationMillis,
            sessionSegmentStartAtMillis = state.sessionSegmentStartAtMillis
        )
    }

    private fun enqueueSessionLifecycleEvent(
        action: String,
        sessionId: String,
        sessionIndex: Int,
        reason: String?,
        durationSeconds: Int?
    ) {
        if (!state.sdkEnabled || !state.metricsEnabled) {
            return
        }

        val payload = mutableMapOf<String, Any?>(
            "a" to action,
            "sid" to sessionId,
            "si" to sessionIndex
        )
        if (!reason.isNullOrBlank()) {
            payload["r"] = reason
        }
        if (durationSeconds != null) {
            payload["sd"] = durationSeconds.coerceAtLeast(0)
        }

        enqueueEvent(type = "session", payload = payload, dedupeKey = null)
    }

    private fun enqueueEvent(type: String, payload: Map<String, Any?>, dedupeKey: String? = null) {
        val nowMillis = nowMillis()
        val event = QueuedEvent(
            eventId = EnvelopeBuildInput.generateEventId(),
            type = type,
            dedupeKey = dedupeKey,
            payload = payload,
            createdAtMillis = nowMillis,
            attemptCount = 0,
            nextAttemptAtMillis = 0L,
            queuedAtMillis = nowMillis
        )

        val queue = applyQueuePolicies(
            state.queue,
            nowMillis = nowMillis
        ).toMutableList()
        queue.add(event)
        state = state.copy(
            queue = applyQueuePolicies(queue, nowMillis = nowMillis)
        )
        persistQueue()

        if (apiClient == null) {
            notifyRequestResult(true, null)
            return
        }
        scheduleQueueFlush(delayMillis = 0L, reason = "event_enqueued")
    }

    private fun scheduleQueueFlush(delayMillis: Long, reason: String) {
        val normalizedDelayMillis = delayMillis.coerceAtLeast(0L)
        synchronized(this) {
            queueFlushFuture?.cancel(false)
            queueFlushFuture = scheduleAsync(
                { flushQueue(reason) },
                normalizedDelayMillis
            )
        }
    }

    private fun executeAsync(task: () -> Unit) {
        if (runTasksInlineForTesting) {
            task()
        } else {
            scheduler.execute(task)
        }
    }

    private fun executePersistAsync(task: () -> Unit) {
        if (runTasksInlineForTesting) {
            task()
        } else {
            persistenceExecutor.execute(task)
        }
    }

    private fun scheduleAsync(task: () -> Unit, delayMillis: Long): ScheduledFuture<*>? {
        if (runTasksInlineForTesting && delayMillis <= 0L) {
            task()
            return null
        }
        return scheduler.schedule(task, delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
    }

    private fun flushQueue(reason: String) {
        val client: ApiClient
        val eventToDispatch: QueuedEvent
        synchronized(this) {
            queueFlushFuture = null
            client = apiClient ?: return
            if (queueDispatchInProgress) {
                return
            }

            val nowMillis = nowMillis()
            trimQueueByPolicyLocked(nowMillis)
            if (state.queue.isEmpty()) {
                return
            }

            if (!hasValidCreativeToken()) {
                fetchCreativeTokenIfNeeded(trigger = "queue_flush_$reason")
                return
            }

            val nextEvent = state.queue.firstOrNull { it.nextAttemptAtMillis <= nowMillis }
            if (nextEvent == null) {
                scheduleNextQueueFlush(nowMillis = nowMillis, reason = reason)
                return
            }

            queueDispatchInProgress = true
            eventToDispatch = nextEvent
        }

        dispatchEvent(eventToDispatch, client)
    }

    private fun dispatchEvent(event: QueuedEvent, client: ApiClient) {
        val envelope = synchronized(this) {
            val creativeToken = normalizeValidToken(state.creativeToken)
            if (creativeToken == null) {
                return@synchronized null
            }
            EventEnvelopeMapper.map(
                EnvelopeBuildInput(
                    type = event.type,
                    payload = event.payload,
                    creativeToken = creativeToken,
                    retentionDay = currentRetentionDay(),
                    sdkVersion = getSdkVersion(),
                    sdkType = SDK_TYPE,
                    appVersion = getAppVersion(),
                    developmentMode = state.developmentMode,
                    createdAtMillis = event.createdAtMillis,
                    dedupeKey = event.dedupeKey,
                    whitelistedProperties = state.whitelistedUserProperties,
                    blacklistedProperties = state.blacklistedUserProperties,
                    deviceInfo = DeviceInfoCollector.collect(applicationContext),
                    identity = collectIdentity(),
                    eventId = event.eventId
                )
            )
        }
        if (envelope == null) {
            synchronized(this) {
                queueDispatchInProgress = false
            }
            return
        }

        client.post(Endpoints.WEBHOOK, envelope) { result ->
            result.onSuccess {
                markEventDispatched(event.eventId)
                notifyRequestResult(true, null)
            }.onFailure { error ->
                val retryable = RetryPolicy.isRetryable(error)
                if (retryable) {
                    markEventForRetry(event.eventId)
                } else {
                    markEventDispatched(event.eventId)
                }
                notifyRequestResult(false, readableErrorMessage(error))
            }
        }
    }

    private fun scheduleNextQueueFlush(nowMillis: Long, reason: String) {
        val nextAttemptAtMillis = state.queue
            .map { it.nextAttemptAtMillis }
            .minOrNull()
            ?: return
        val delayMillis = (nextAttemptAtMillis - nowMillis).coerceAtLeast(0L)
        scheduleQueueFlush(delayMillis = delayMillis, reason = reason)
    }

    private fun markEventForRetry(eventId: String) {
        synchronized(this) {
            val nowMillis = nowMillis()
            trimQueueByPolicyLocked(nowMillis)
            val queue = state.queue.toMutableList()
            val index = queue.indexOfFirst { it.eventId == eventId }
            if (index < 0) {
                queueDispatchInProgress = false
                return
            }

            val original = queue[index]
            val nextAttemptCount = original.attemptCount + 1
            val backoffMillis = RetryPolicy.backoffMillis(nextAttemptCount).coerceAtMost(MAX_QUEUE_RETRY_DELAY_MILLIS)
            queue[index] = original.copy(
                attemptCount = nextAttemptCount,
                nextAttemptAtMillis = nowMillis + backoffMillis
            )

            state = state.copy(queue = queue)
            persistQueue()
            queueDispatchInProgress = false

            scheduleNextQueueFlush(nowMillis = nowMillis, reason = "retry_backoff")
        }
    }

    private fun markEventDispatched(eventId: String) {
        synchronized(this) {
            val nowMillis = nowMillis()
            trimQueueByPolicyLocked(nowMillis)
            if (state.queue.isEmpty()) {
                queueDispatchInProgress = false
                return
            }

            val queue = state.queue.filterNot { it.eventId == eventId }
            state = state.copy(queue = queue)
            persistQueue()
            queueDispatchInProgress = false

            if (queue.isNotEmpty()) {
                scheduleQueueFlush(delayMillis = 0L, reason = "next_event")
            }
        }
    }

    private fun persistQueue() {
        val persisted = state.queue.map { event ->
            PersistedQueuedEvent(
                eventId = event.eventId,
                type = event.type,
                dedupeKey = event.dedupeKey,
                payload = event.payload,
                createdAtMillis = event.createdAtMillis,
                attemptCount = event.attemptCount,
                nextAttemptAtMillis = event.nextAttemptAtMillis,
                queuedAtMillis = event.queuedAtMillis
            )
        }
        executePersistAsync { stateStore.setQueuedEvents(persisted) }
    }

    private fun mapPersistedQueuedEvent(persisted: PersistedQueuedEvent, nowMillis: Long): QueuedEvent? {
        if (persisted.eventId.isBlank() || persisted.type.isBlank()) {
            return null
        }

        val queuedAtMillis = if (persisted.queuedAtMillis > 0L) {
            persisted.queuedAtMillis
        } else {
            nowMillis
        }
        val createdAtMillis = if (persisted.createdAtMillis > 0L) {
            persisted.createdAtMillis
        } else {
            queuedAtMillis
        }

        return QueuedEvent(
            eventId = persisted.eventId,
            type = persisted.type,
            dedupeKey = persisted.dedupeKey,
            payload = persisted.payload,
            createdAtMillis = createdAtMillis,
            attemptCount = persisted.attemptCount.coerceAtLeast(0),
            nextAttemptAtMillis = persisted.nextAttemptAtMillis.coerceAtLeast(0L),
            queuedAtMillis = queuedAtMillis
        )
    }

    private fun applyQueuePolicies(events: List<QueuedEvent>, nowMillis: Long): List<QueuedEvent> {
        val filtered = events.filter { event ->
            !isQueueEventStale(event = event, nowMillis = nowMillis)
        }
        return filtered.takeLast(MAX_QUEUE_SIZE)
    }

    private fun isQueueEventStale(event: QueuedEvent, nowMillis: Long): Boolean {
        return (nowMillis - event.queuedAtMillis) >= MAX_QUEUE_AGE_MILLIS
    }

    private fun trimQueueByPolicyLocked(nowMillis: Long) {
        val trimmed = applyQueuePolicies(events = state.queue, nowMillis = nowMillis)
        if (trimmed != state.queue) {
            state = state.copy(queue = trimmed)
            persistQueue()
        }
    }

    private fun registerRuntimeSignals(context: Context?) {
        val app = context as? Application
        if (app != null && activityLifecycleCallbacks == null) {
            val callbacks = object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: android.app.Activity,
                    savedInstanceState: android.os.Bundle?
                ) = Unit

                override fun onActivityStarted(activity: android.app.Activity) {
                    val shouldSignal = synchronized(this@AudienceLabSDK) {
                        startedActivityCount += 1
                        startedActivityCount == 1
                    }
                    if (shouldSignal) {
                        onForeground()
                    }
                }

                override fun onActivityResumed(activity: android.app.Activity) = Unit

                override fun onActivityPaused(activity: android.app.Activity) = Unit

                override fun onActivityStopped(activity: android.app.Activity) {
                    val shouldSignal = synchronized(this@AudienceLabSDK) {
                        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                        startedActivityCount == 0
                    }
                    if (shouldSignal) {
                        onBackground()
                    }
                }

                override fun onActivitySaveInstanceState(
                    activity: android.app.Activity,
                    outState: android.os.Bundle
                ) = Unit

                override fun onActivityDestroyed(activity: android.app.Activity) = Unit
            }
            app.registerActivityLifecycleCallbacks(callbacks)
            lifecycleApplication = app
            activityLifecycleCallbacks = callbacks
        }

        if (connectivityCallback == null) {
            val manager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (manager != null) {
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        onConnectivityRestored()
                    }
                }
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                runCatching {
                    manager.registerNetworkCallback(request, callback)
                    connectivityManager = manager
                    connectivityCallback = callback
                }.onFailure {
                    Logger.w("Connectivity monitor registration failed: ${it.message ?: "unknown"}")
                }
            }
        }
    }

    private fun unregisterRuntimeSignals() {
        val app = lifecycleApplication
        val callbacks = activityLifecycleCallbacks
        if (app != null && callbacks != null) {
            runCatching {
                app.unregisterActivityLifecycleCallbacks(callbacks)
            }
        }
        lifecycleApplication = null
        activityLifecycleCallbacks = null
        startedActivityCount = 0

        val manager = connectivityManager
        val callback = connectivityCallback
        if (manager != null && callback != null) {
            runCatching {
                manager.unregisterNetworkCallback(callback)
            }
        }
        connectivityManager = null
        connectivityCallback = null
    }

    private fun cancelScheduledTasks() {
        tokenRetryFuture?.cancel(false)
        queueFlushFuture?.cancel(false)
        tokenRetryFuture = null
        queueFlushFuture = null
    }

    private fun onBackground() {
        synchronized(this) {
            if (!state.initialized || !hasValidSession()) {
                return
            }

            val nowMillis = nowMillis()
            val segmentStartAt = state.sessionSegmentStartAtMillis
            val segmentDurationMillis = if (segmentStartAt == null) {
                0L
            } else {
                (nowMillis - segmentStartAt).coerceAtLeast(0L)
            }

            state = state.copy(
                sessionAccumulatedDurationMillis = (state.sessionAccumulatedDurationMillis + segmentDurationMillis)
                    .coerceAtLeast(0L),
                sessionSegmentStartAtMillis = null,
                sessionLastActiveAtMillis = nowMillis,
                lastBackgroundAtMillis = nowMillis
            )
            persistSessionState()
        }
    }

    private fun onForeground() {
        synchronized(this) {
            if (state.initialized) {
                val nowMillis = nowMillis()
                val backgroundAt = state.lastBackgroundAtMillis

                when {
                    !hasValidSession() -> {
                        startNewSession(nowMillis = nowMillis, emitStartEvent = true)
                    }
                    backgroundAt != null && (nowMillis - backgroundAt) > SESSION_TIMEOUT_MILLIS -> {
                        endCurrentSession(
                            reason = "background_timeout",
                            nowMillis = nowMillis,
                            includeCurrentSegment = false
                        )
                        startNewSession(nowMillis = nowMillis, emitStartEvent = true)
                    }
                    else -> {
                        state = state.copy(
                            sessionLastActiveAtMillis = nowMillis,
                            sessionSegmentStartAtMillis = nowMillis,
                            lastBackgroundAtMillis = null
                        )
                        persistSessionState()
                    }
                }
            }
        }

        sendUserMetrics()
        fetchCreativeTokenIfNeeded(trigger = "foreground")
        scheduleQueueFlush(delayMillis = 0L, reason = "foreground")
    }

    private fun onConnectivityRestored() {
        synchronized(this) {
            clearTokenRetryStateLocked()
            tokenFetchLastStatus = "connectivity_restored"
        }
        fetchCreativeTokenIfNeeded(trigger = "connectivity")
        scheduleQueueFlush(delayMillis = 0L, reason = "connectivity")
    }

    private fun collectIdentity(): app.geeklab.audiencelab.sdk.internal.identity.IdentitySnapshot {
        val result = IdentityCollector.collectDetailed(
            context = applicationContext,
            advertisingIdProvider = advertisingIdProvider,
            appSetIdProvider = appSetIdProvider,
            overrides = IdentityOverrides(
                ga = manualAdvertisingId,
                asid = manualAppSetId,
                lat = manualLimitAdTracking
            ),
            allowAutoResolve = true
        )
        updateIdentityDebugSnapshot(result.diagnostics)
        return result.snapshot
    }

    private fun readableErrorMessage(error: Throwable): String {
        val networkError = error as? NetworkError
        return when (networkError) {
            is NetworkError.HttpFailure -> {
                val backendMessage = networkError.responseBody["error"] as? String
                    ?: networkError.responseBody["message"] as? String
                backendMessage ?: "HTTP ${networkError.statusCode}"
            }
            else -> error.message ?: "request failed"
        }
    }

    private fun notifyRequestResult(success: Boolean, errorMessage: String?) {
        runCatching {
            requestResultCallback?.onResult(success, errorMessage)
        }.onFailure {
            Logger.w("Request callback failed: ${it.message ?: "unknown error"}")
        }
    }

    private fun extractTokenFromUrl(url: String): String? {
        val params = parseQueryParams(url)
        val key = deepLinkTokenKeys.firstOrNull { params[it]?.isNotBlank() == true } ?: return null
        return params[key]?.trim()
    }

    private fun parseQueryParams(url: String): Map<String, String> {
        return runCatching {
            val uri = URI(url)
            val query = uri.rawQuery ?: ""
            if (query.isBlank()) {
                emptyMap()
            } else {
                query.split("&")
                    .mapNotNull { part ->
                        if (part.isBlank()) {
                            null
                        } else {
                            val index = part.indexOf("=")
                            if (index < 0) {
                                decodeQueryComponent(part) to ""
                            } else {
                                val key = decodeQueryComponent(part.substring(0, index))
                                val value = decodeQueryComponent(part.substring(index + 1))
                                key to value
                            }
                        }
                    }
                    .toMap()
            }
        }.getOrElse {
            emptyMap()
        }
    }

    private fun decodeQueryComponent(value: String): String {
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrElse {
            value
        }
    }

    private fun normalizeStringMap(raw: Any?): Map<String, String> {
        val map = raw as? Map<*, *> ?: return emptyMap()
        return map.entries
            .mapNotNull { entry ->
                val key = entry.key as? String ?: return@mapNotNull null
                val value = entry.value ?: return@mapNotNull null
                key to value.toString()
            }
            .toMap()
    }

    private fun mergeServerWhitelistedProperties(serverProperties: Map<String, String>) {
        if (serverProperties.isEmpty()) {
            return
        }

        val updated = state.whitelistedUserProperties.toMutableMap()

        serverProperties.forEach { (rawKey, rawValue) ->
            val key = rawKey.trim()
            val value = rawValue.trim()
            if (!isValidUserProperty(key, value)) {
                return@forEach
            }

            if (!updated.containsKey(key) && updated.size >= MAX_USER_PROPERTY_COUNT) {
                return@forEach
            }

            updated[key] = value
            if (estimatePropertySize(updated) > MAX_USER_PROPERTY_BYTES) {
                updated.remove(key)
            }
        }

        state = state.copy(whitelistedUserProperties = updated)
    }

    private fun isReservedWhitelistedPropertyKey(key: String): Boolean {
        return key.startsWith("_")
    }

    private fun normalizeUserPropertyKey(key: String, scope: UserPropertyScope): String {
        val normalized = key.trim()
        return if (scope == UserPropertyScope.BLACKLISTED && requiresHashedBlacklistedValue(normalized)) {
            normalized.lowercase(Locale.US)
        } else {
            normalized
        }
    }

    private fun normalizeUserPropertyValue(key: String, value: String, scope: UserPropertyScope): String {
        val normalized = value.trim()
        if (scope != UserPropertyScope.BLACKLISTED || !requiresHashedBlacklistedValue(key)) {
            return normalized
        }

        val prepared = when (key.lowercase(Locale.US)) {
            "email" -> normalizeEmailForHash(normalized)
            "phone" -> normalizePhoneForHash(normalized)
            else -> normalized
        }
        if (prepared.isEmpty()) {
            return prepared
        }
        if (looksLikeSha256(prepared)) {
            return prepared.lowercase(Locale.US)
        }
        return sha256(prepared)
    }

    private fun requiresHashedBlacklistedValue(key: String): Boolean {
        return key.trim().lowercase(Locale.US) in hashRequiredBlacklistedKeys
    }

    private fun normalizeEmailForHash(value: String): String {
        return value.trim().lowercase(Locale.US)
    }

    private fun normalizePhoneForHash(value: String): String {
        return value.filter(Char::isDigit)
    }

    private fun looksLikeSha256(value: String): Boolean {
        return value.length == 64 && value.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(Locale.US, byte.toInt() and 0xff) }
    }

    private fun isValidUserProperty(key: String, value: String): Boolean {
        if (key.isEmpty()) {
            Logger.w("User property rejected: key is empty")
            return false
        }
        if (key.length > MAX_USER_PROPERTY_KEY_LENGTH) {
            Logger.w("User property rejected: key exceeds $MAX_USER_PROPERTY_KEY_LENGTH chars")
            return false
        }
        if (value.length > MAX_USER_PROPERTY_VALUE_LENGTH) {
            Logger.w("User property rejected: value exceeds $MAX_USER_PROPERTY_VALUE_LENGTH chars")
            return false
        }
        return true
    }

    private fun estimatePropertySize(properties: Map<String, String>): Int {
        return properties.entries.sumOf { (key, value) ->
            key.toByteArray(StandardCharsets.UTF_8).size +
                value.toByteArray(StandardCharsets.UTF_8).size + 2
        }
    }

    private fun getUserProperties(scope: UserPropertyScope): Map<String, String> {
        return when (scope) {
            UserPropertyScope.WHITELISTED -> state.whitelistedUserProperties
            UserPropertyScope.BLACKLISTED -> state.blacklistedUserProperties
        }
    }

    private fun updateUserProperties(scope: UserPropertyScope, next: Map<String, String>) {
        state = when (scope) {
            UserPropertyScope.WHITELISTED -> state.copy(whitelistedUserProperties = next.toMap())
            UserPropertyScope.BLACKLISTED -> state.copy(blacklistedUserProperties = next.toMap())
        }
    }

    private fun sanitizeCounter(value: Double): Double {
        if (!value.isFinite() || value < 0.0) {
            return 0.0
        }
        return CurrencyValue.normalize(value)
    }

    private fun sanitizeMonetaryValue(value: Double): Double {
        if (!value.isFinite()) {
            return 0.0
        }
        return CurrencyValue.normalize(value)
    }

    private fun emptyIdentityDebugSnapshot(): IdentityDebugSnapshot {
        return IdentityDebugSnapshot(
            settled = false,
            advertisingIdSettled = false,
            appSetIdSettled = false,
            androidIdSettled = false,
            advertisingIdAvailable = false,
            appSetIdAvailable = false,
            androidIdAvailable = false,
            advertisingIdPreview = null,
            appSetIdPreview = null,
            androidIdPreview = null,
            limitAdTracking = null
        )
    }

    private fun maskDebugValue(value: String?): String? {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            normalized.length <= 4 -> "*".repeat(normalized.length)
            normalized.length <= 10 -> normalized.take(2) + "..." + normalized.takeLast(2)
            else -> normalized.take(4) + "..." + normalized.takeLast(4)
        }
    }

    private fun stringifyPayload(payload: Map<String, Any?>): String {
        return runCatching {
            app.geeklab.audiencelab.sdk.internal.network.JsonCodec.toJsonString(payload)
        }.getOrElse {
            payload.toString()
        }
    }

    private fun buildRequestName(type: String, payload: Map<String, Any?>): String {
        return when (type) {
            "custom" -> {
                val eventName = payload["en"] as? String
                if (eventName.isNullOrBlank()) "custom" else "custom:$eventName"
            }
            "custom.purchase" -> "purchase"
            "custom.ad" -> "ad"
            "session" -> {
                val action = payload["a"] as? String
                if (action.isNullOrBlank()) "session" else "session:$action"
            }
            else -> type
        }
    }

    private fun utcEpochDay(nowMillis: Long = nowMillis()): Int {
        return (nowMillis / MILLIS_PER_DAY).toInt()
    }

    private fun currentRetentionDay(nowMillis: Long = nowMillis()): Int? {
        val firstDay = state.firstRetentionDay ?: return null
        return (utcEpochDay(nowMillis) - firstDay).coerceAtLeast(0)
    }

    private data class State(
        val initialized: Boolean = false,
        val apiKey: String? = null,
        val sdkEnabled: Boolean = true,
        val metricsEnabled: Boolean = true,
        val debugEnabled: Boolean = false,
        val developmentMode: Boolean = false,
        val appVersion: String? = null,
        val baseUrl: String? = null,
        val creativeToken: String? = null,
        val deepLink: String? = null,
        val identityDebugSnapshot: IdentityDebugSnapshot = IdentityDebugSnapshot(
            settled = false,
            advertisingIdSettled = false,
            appSetIdSettled = false,
            androidIdSettled = false,
            advertisingIdAvailable = false,
            appSetIdAvailable = false,
            androidIdAvailable = false,
            advertisingIdPreview = null,
            appSetIdPreview = null,
            androidIdPreview = null,
            limitAdTracking = null
        ),
        val totalAdValue: Double = 0.0,
        val totalPurchaseValue: Double = 0.0,
        val queue: List<QueuedEvent> = emptyList(),
        val whitelistedUserProperties: Map<String, String> = emptyMap(),
        val blacklistedUserProperties: Map<String, String> = emptyMap(),
        val firstRetentionDay: Int? = null,
        val lastRetentionDay: Int? = null,
        val sessionId: String? = null,
        val sessionIndex: Int = 0,
        val sessionLastActiveAtMillis: Long? = null,
        val sessionStartAtMillis: Long? = null,
        val sessionAccumulatedDurationMillis: Long = 0L,
        val sessionSegmentStartAtMillis: Long? = null,
        val lastBackgroundAtMillis: Long? = null
    )

    private data class QueuedEvent(
        val eventId: String,
        val type: String,
        val dedupeKey: String?,
        val payload: Map<String, Any?>,
        val createdAtMillis: Long,
        val attemptCount: Int = 0,
        val nextAttemptAtMillis: Long = 0L,
        val queuedAtMillis: Long
    )
}
