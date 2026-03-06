package app.geeklab.audiencelab.sdk

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import app.geeklab.audiencelab.sdk.internal.Logger
import app.geeklab.audiencelab.sdk.internal.device.DeviceInfoCollector
import app.geeklab.audiencelab.sdk.internal.identity.IdentityCollector
import app.geeklab.audiencelab.sdk.internal.identity.IdentityOverrides
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
import java.math.BigDecimal
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object AudienceLabSDK {
    private const val DEFAULT_STATE_PREFERENCES = "audiencelab.sdk.state"
    private const val ORGANIC_TOKEN = "organic#gp"
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

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

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

    @Synchronized
    @JvmStatic
    fun setAdvertisingIdProvider(provider: AdvertisingIdProvider?) {
        advertisingIdProvider = provider
    }

    @Synchronized
    @JvmStatic
    fun setAppSetIdProvider(provider: AppSetIdProvider?) {
        appSetIdProvider = provider
    }

    @Synchronized
    @JvmStatic
    fun setAdvertisingId(gaid: String?) {
        manualAdvertisingId = gaid?.trim()?.takeIf { it.isNotEmpty() }
    }

    @Synchronized
    @JvmStatic
    fun clearAdvertisingId() {
        manualAdvertisingId = null
    }

    @Synchronized
    @JvmStatic
    fun setAppSetId(appSetId: String?) {
        manualAppSetId = appSetId?.trim()?.takeIf { it.isNotEmpty() }
    }

    @Synchronized
    @JvmStatic
    fun clearAppSetId() {
        manualAppSetId = null
    }

    @Synchronized
    @JvmStatic
    fun setLimitAdTracking(value: Boolean?) {
        manualLimitAdTracking = value
    }

    @Synchronized
    @JvmStatic
    fun handleDeepLink(url: String?) {
        val normalized = url?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return
        }

        val token = extractTokenFromUrl(normalized)
        if (!token.isNullOrBlank()) {
            stateStore.setCreativeToken(token)
        }

        state = state.copy(
            deepLink = normalized,
            creativeToken = token ?: state.creativeToken
        )
    }

    @JvmStatic
    fun getCreativeToken(): String? = state.creativeToken

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
            (currentDay - previousLastDay - 1).coerceAtLeast(0)
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
        val totalAdValue = sanitizeCounter(state.totalAdValue + sanitizedValue)
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
                "value" to decimalString(sanitizedValue),
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
            sanitizeCounter(state.totalPurchaseValue + sanitizedValue)
        } else {
            state.totalPurchaseValue
        }
        state = state.copy(totalPurchaseValue = totalPurchaseValue)
        stateStore.setTotalPurchaseValue(totalPurchaseValue)

        val payload = mutableMapOf<String, Any?>(
            "item_id" to event.itemId,
            "item_name" to event.itemName,
            "value" to decimalString(sanitizedValue),
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
        val normalizedKey = key.trim()
        val normalizedValue = value.trim()
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
        val normalizedKey = key.trim()
        if (normalizedKey.isEmpty()) {
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
        updateUserProperties(scope, emptyMap())
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
    fun setRequestResultCallback(callback: RequestResultCallback?) {
        requestResultCallback = callback
    }

    @JvmStatic
    fun clearRequestResultCallback() {
        requestResultCallback = null
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
        apiClient = null
        applicationContext = null
        tokenFetchInProgress = false
        tokenFetchAttempt = 0
        tokenFetchNextAttemptAtMillis = 0L
        advertisingIdProvider = null
        appSetIdProvider = null
        manualAdvertisingId = null
        manualAppSetId = null
        manualLimitAdTracking = null
        queueDispatchInProgress = false
        cancelScheduledTasks()
        unregisterRuntimeSignals()
        nowProvider = { System.currentTimeMillis() }
        Logger.configure(false)
    }

    @Synchronized
    internal fun setStateStoreForTesting(store: AudienceLabStateStore) {
        stateStore = store
        state = State()
        requestResultCallback = null
        tokenFetchInProgress = false
        tokenFetchAttempt = 0
        tokenFetchNextAttemptAtMillis = 0L
        queueDispatchInProgress = false
        cancelScheduledTasks()
        unregisterRuntimeSignals()
        nowProvider = { System.currentTimeMillis() }
        Logger.configure(false)
    }

    @Synchronized
    internal fun setNowProviderForTesting(provider: () -> Long) {
        nowProvider = provider
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
        val (sdkEnabled, metricsEnabled, debugEnabled) = if (snapshot.hasStoredConfig) {
            Triple(snapshot.sdkEnabled, snapshot.metricsEnabled, snapshot.debugEnabled)
        } else {
            Triple(options.isSdkEnabled, options.isMetricsEnabled, options.isDebugEnabled)
        }
        val baseUrl = resolveBaseUrl(options)

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
            appVersion = appVersion,
            baseUrl = baseUrl,
            creativeToken = snapshot.creativeToken,
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
            queue = restoredQueue
        )
        persistQueue()

        stateStore.persistConfiguration(
            sdkEnabled = sdkEnabled,
            metricsEnabled = metricsEnabled,
            debugEnabled = debugEnabled,
            appVersion = appVersion
        )
        restoreSessionLifecycleAtInitialization(snapshot = snapshot, nowMillis = nowMillis)

        configureNetworkClient(baseUrl = baseUrl)
        registerRuntimeSignals(applicationContext)

        Logger.configure(debugEnabled)
        Logger.d("AudienceLab Android SDK initialized with persisted state")

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
            apiKeyProvider = { state.apiKey }
        )
    }

    private fun fetchCreativeTokenIfNeeded(trigger: String) {
        if (state.creativeToken?.isNotBlank() == true) {
            synchronized(this) {
                clearTokenRetryStateLocked()
            }
            return
        }

        val client = apiClient ?: return

        synchronized(this) {
            if (tokenFetchInProgress || state.creativeToken?.isNotBlank() == true) {
                return
            }

            val nowMillis = nowMillis()
            if (tokenFetchNextAttemptAtMillis > nowMillis) {
                val delayMillis = (tokenFetchNextAttemptAtMillis - nowMillis).coerceAtLeast(0L)
                scheduleTokenRetryLocked(delayMillis = delayMillis, reason = "deferred_$trigger")
                return
            }

            tokenFetchInProgress = true
        }

        val deviceInfo = DeviceInfoCollector.collect(applicationContext)
        val identity = collectIdentity()
        val payload = FetchTokenRequestMapper.map(
            FetchTokenBuildInput(
                sdkVersion = getSdkVersion(),
                sdkType = SDK_TYPE,
                appVersion = getAppVersion(),
                developmentMode = BuildConfig.DEBUG,
                whitelistedProperties = state.whitelistedUserProperties,
                deviceInfo = deviceInfo,
                identity = identity
            )
        )

        client.post(Endpoints.FETCH_TOKEN, payload) { result ->
            synchronized(this) {
                tokenFetchInProgress = false
            }

            result.onSuccess { response ->
                val token = response["token"] as? String
                if (!token.isNullOrBlank()) {
                    synchronized(this) {
                        state = state.copy(creativeToken = token)
                        stateStore.setCreativeToken(token)
                        clearTokenRetryStateLocked()
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
                    if (retryable) {
                        tokenFetchAttempt += 1
                        val delayMillis = RetryPolicy.backoffMillis(tokenFetchAttempt)
                        tokenFetchNextAttemptAtMillis = nowMillis() + delayMillis
                        scheduleTokenRetryLocked(delayMillis = delayMillis, reason = "fetch_token_retry")
                    } else {
                        clearTokenRetryStateLocked()
                    }
                }
                Logger.w("Fetch token failed ($trigger): ${error.message ?: "unknown"}")
            }
        }
    }

    private fun scheduleTokenRetryLocked(delayMillis: Long, reason: String) {
        val normalizedDelayMillis = delayMillis.coerceAtLeast(0L)
        tokenRetryFuture?.cancel(false)
        tokenRetryFuture = scheduler.schedule(
            { fetchCreativeTokenIfNeeded(trigger = reason) },
            normalizedDelayMillis,
            TimeUnit.MILLISECONDS
        )
    }

    private fun clearTokenRetryStateLocked() {
        tokenFetchAttempt = 0
        tokenFetchNextAttemptAtMillis = 0L
        tokenRetryFuture?.cancel(false)
        tokenRetryFuture = null
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
            queueFlushFuture = scheduler.schedule(
                { flushQueue(reason) },
                normalizedDelayMillis,
                TimeUnit.MILLISECONDS
            )
        }
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
            EventEnvelopeMapper.map(
                EnvelopeBuildInput(
                    type = event.type,
                    payload = event.payload,
                    creativeToken = state.creativeToken ?: ORGANIC_TOKEN,
                    sdkVersion = getSdkVersion(),
                    sdkType = SDK_TYPE,
                    appVersion = getAppVersion(),
                    developmentMode = BuildConfig.DEBUG,
                    dedupeKey = event.dedupeKey,
                    whitelistedProperties = state.whitelistedUserProperties,
                    blacklistedProperties = state.blacklistedUserProperties,
                    deviceInfo = DeviceInfoCollector.collect(applicationContext),
                    identity = collectIdentity(),
                    eventId = event.eventId
                )
            )
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
                attemptCount = event.attemptCount,
                nextAttemptAtMillis = event.nextAttemptAtMillis,
                queuedAtMillis = event.queuedAtMillis
            )
        }
        stateStore.setQueuedEvents(persisted)
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

        return QueuedEvent(
            eventId = persisted.eventId,
            type = persisted.type,
            dedupeKey = persisted.dedupeKey,
            payload = persisted.payload,
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

        fetchCreativeTokenIfNeeded(trigger = "foreground")
        scheduleQueueFlush(delayMillis = 0L, reason = "foreground")
    }

    private fun onConnectivityRestored() {
        fetchCreativeTokenIfNeeded(trigger = "connectivity")
        scheduleQueueFlush(delayMillis = 0L, reason = "connectivity")
    }

    private fun collectIdentity() = IdentityCollector.collect(
        context = applicationContext,
        advertisingIdProvider = advertisingIdProvider,
        appSetIdProvider = appSetIdProvider,
        overrides = IdentityOverrides(
            ga = manualAdvertisingId,
            asid = manualAppSetId,
            lat = manualLimitAdTracking
        )
    )

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
        return value
    }

    private fun sanitizeMonetaryValue(value: Double): Double {
        if (!value.isFinite()) {
            return 0.0
        }
        return value
    }

    private fun decimalString(value: Double): String {
        val normalized = sanitizeMonetaryValue(value)
        return BigDecimal.valueOf(normalized).stripTrailingZeros().toPlainString()
    }

    private fun utcEpochDay(nowMillis: Long = nowMillis()): Int {
        return (nowMillis / MILLIS_PER_DAY).toInt()
    }

    private data class State(
        val initialized: Boolean = false,
        val apiKey: String? = null,
        val sdkEnabled: Boolean = true,
        val metricsEnabled: Boolean = true,
        val debugEnabled: Boolean = false,
        val appVersion: String? = null,
        val baseUrl: String? = null,
        val creativeToken: String? = null,
        val deepLink: String? = null,
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
        val attemptCount: Int = 0,
        val nextAttemptAtMillis: Long = 0L,
        val queuedAtMillis: Long
    )
}
