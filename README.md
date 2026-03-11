# AudienceLab Android Native SDK

Kotlin-based native Android SDK for AudienceLab.

Developer integration guide:

- [DEVELOPER_README.md](/Users/nathan/Documents/GitHub/Audiencelab-SDKs/audiencelab_android_sdk/DEVELOPER_README.md)
- [DISTRIBUTION.md](/Users/nathan/Documents/GitHub/Audiencelab-SDKs/audiencelab_android_sdk/DISTRIBUTION.md)

## SDK Targets

- Language: Kotlin
- Min SDK: 23
- Target SDK: 36 (latest stable as of 2026-03-05)
- Compile SDK: 36

## Prerequisites

- JDK: 17 or newer
- Android SDK packages:
  - `platforms;android-36`
  - `build-tools;36.0.0`
  - `platform-tools`

## Dependency Policy

The SDK intentionally keeps dependencies minimal.

- No networking library yet (OkHttp can be added later)
- No JSON library yet (kotlinx.serialization or Moshi can be added later)

## Project Structure

- `audiencelab-sdk/`: Android library module (AAR)
- `sample-app/`: Minimal Android app consuming `audiencelab-sdk`

## Distribution Rules

- Only the `release` AAR is intended for distribution.
- GitHub Actions uploads `audiencelab-sdk-release-<SDK_VERSION>.aar`.
- Maven publishing is configured for:
  - `mavenLocal()`
  - GitHub Packages
- Maven coordinates:
  - `ai.audiencelab:audiencelab-android-sdk:<SDK_VERSION>`
- `dev` is not derived from the SDK AAR build type.
- `dev` is controlled by the host app through `AudienceLabOptions.isDevelopmentMode`, or auto-detected from the host app's debuggable flag when `Context` initialization is used.

## Local Build Commands

From `audiencelab_android_sdk/`:

```bash
./gradlew :audiencelab-sdk:testDebugUnitTest
./gradlew :audiencelab-sdk:assembleRelease
./gradlew :sample-app:assembleDebug
```

## Docker-Based Build/Test (No Local Java 17 Needed)

From `audiencelab_android_sdk/`:

```bash
./docker/run-tests.sh
```

This builds a Docker image with JDK 17 + Android SDK and runs:

- `:audiencelab-sdk:testDebugUnitTest`
- `:audiencelab-sdk:assembleRelease`
- `:sample-app:assembleDebug`

Ensure Docker Desktop / Docker daemon is running before executing the script.

## Public API Baseline (Parity-Oriented)

Primary entry point:

- `AudienceLabSDK` (`object`) with `@JvmStatic` methods for Java-friendly static access

Initialization:

- `initialize(context: Context, apiKey: String, options: AudienceLabOptions = AudienceLabOptions())` (preferred for persisted state)
- `initialize(apiKey: String, options: AudienceLabOptions = AudienceLabOptions())`
- `init(context: Context, apiKey: String, options: AudienceLabOptions = AudienceLabOptions())` (alias)
- `init(apiKey: String, options: AudienceLabOptions = AudienceLabOptions())` (alias)
- `isInitialized()`

Toggles:

- `setSdkEnabled(enabled: Boolean)` / `setSDKEnabled(enabled: Boolean)` (alias)
- `isSdkEnabled()` / `getIsSDKEnabled()` (alias)
- `toggleMetricsCollection(enabled: Boolean)`
- `isMetricsCollectionEnabled()` / `getIsMetricsCollection()` (alias)
- `setDebugEnabled(enabled: Boolean)`
- `isDebugEnabled()` / `getIsDebugEnabled()` (alias)
- `isDevelopmentMode()`

Build-variant behavior:

- `dev` in `/fetch-token` and `/webhook` payloads is driven by the host app.
- Prefer `initialize(context, apiKey, options)` so the SDK can auto-detect the host app debuggable flag.
- If you need explicit control, set `AudienceLabOptions(isDevelopmentMode = BuildConfig.DEBUG)` in the integrating app.

Token and deep link access:

- `handleDeepLink(url: String?)`
- `getCreativeToken()`
- `getDeepLink()`
- `getTokenDebugSnapshot()`

Ad, purchase, custom, and retention events:

- `sendAdEvent(...)` and `sendAdEvent(event: AdEvent)`
- `sendPurchaseEvent(...)` and `sendPurchaseEvent(event: PurchaseEvent)`
- `sendCustomEvent(name, params, dedupeKey)` and `sendCustomEvent(event: CustomEvent)`
- `sendUserMetrics()`
- Legacy aliases:
  - `sendCustomAdMetrics(...)`
  - `sendCustomPurchaseMetrics(...)`

User properties:

- `setUserProperty(key, value, isWhitelisted)`
- `setUserProperty(key, value, scope: UserPropertyScope)`
- `unsetUserProperty(key, isWhitelisted)` / `unsetUserProperty(key, scope)`
- `clearUserProperties(isWhitelisted)` / `clearUserProperties(scope)`

Queue, callbacks, and versions:

- `getQueueSize()`
- `setRequestResultCallback(callback: RequestResultCallback?)`
- `clearRequestResultCallback()`
- `getTotalAdValue()`
- `getTotalPurchaseValue()`
- `getSdkVersion()` / `getSDKVersion()` (alias)
- `getAppVersion()`
- `getRuntimeVersion()`

Identity provider hooks (optional dependency path):

- `setAdvertisingIdProvider(provider: AdvertisingIdProvider?)`
- `setAppSetIdProvider(provider: AppSetIdProvider?)`
- If Google Play Services ID support is already present in the host app, the SDK also attempts automatic reflective lookup for GAID and App Set ID without adding a hard dependency.
- Manual overrides:
  - `setAdvertisingId(...)` / `clearAdvertisingId()`
  - `setAppSetId(...)` / `clearAppSetId()`
  - `setLimitAdTracking(...)`
- Identity/debug observability:
  - `isIdentitySettled()`
  - `getIdentityDebugSnapshot()`
  - `getDeviceDebugSnapshot()`
  - `getQueueDebugSnapshot()`

## Naming and Interop Conventions

- Kotlin API naming: camelCase (`setSdkEnabled`, `sendCustomEvent`)
- Unity/iOS parity aliases retained where useful (`setSDKEnabled`, `getIsSDKEnabled`, `sendCustomAdMetrics`)
- Java interop:
  - `@JvmStatic` for static-style calls: `AudienceLabSDK.initialize(...)`
  - `@JvmOverloads` for optional arguments
  - callback exposed as `RequestResultCallback` functional interface

## Persistence Behavior

- When initialized with `Context`, SDK state is persisted in `SharedPreferences`.
- Persisted fields include SDK toggles, app version, creative token cache, cumulative ad/purchase totals, retention/session markers, and the offline event queue.
- Creative token cache is treated as permanent in this version (no TTL-based refresh logic).
- `initialize(apiKey, options)` (without context) uses in-memory state only.

## Networking Layer

- Lightweight HTTP client based on `HttpURLConnection`.
- Implements:
  - `POST /fetch-token`
  - `POST /webhook`
- Canonical webhook envelope fields include:
  - `type`, `eid`, `dk`
  - identity (`ga`, `asid`, `aid`, `lat`)
  - properties (`wp`, `bp`)
  - event payload under `payload`
- Delivery reliability:
  - queue persisted to disk
  - retry/backoff for `fetch-token` and `webhook` with retryable/non-retryable classification
  - automatic flush on SDK init, app foreground, and connectivity restore
  - webhook delivery blocked until a valid creative token exists
  - token validation rejects blank and placeholder values such as `bin`

## Diagnostics

- Request-level diagnostics are available through `setRequestDebugListener(...)`.
- Queue inspection is available through `getQueueDebugSnapshot()`.
- Token, identity, and device snapshots are available through the public debug snapshot getters.
- The sample app includes request and queue log screens that surface these diagnostics without shipping UI from the SDK itself.
