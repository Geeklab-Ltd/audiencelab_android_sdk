# AudienceLab Android SDK Developer README

This guide is for a game or app developer integrating the AudienceLab Android Native SDK.

## Current Distribution Model

This repository currently supports Android SDK distribution in two forms:

- release AAR
- Maven artifact publishing

Current output:

- Library module: `audiencelab-sdk/`
- Release AAR: `audiencelab-sdk/build/outputs/versioned-aar/audiencelab-sdk-release-<SDK_VERSION>.aar`
- Maven coordinates: `ai.audiencelab:audiencelab-android-sdk:<SDK_VERSION>`

Distribution rules:

- Ship only `audiencelab-sdk-release-<SDK_VERSION>.aar`.
- Do not distribute a debug AAR.
- `dev` is controlled by the integrating app, not by the SDK AAR build type.

If you are integrating from source inside a local checkout, use the module directly.
If you are integrating into another Android project, prefer Maven consumption when the package feed is available to you. Use the AAR path when you are receiving manual artifacts directly.

## Requirements

- Min SDK supported by the SDK: `23`
- Kotlin or Java Android app
- Internet access for token fetch and webhook delivery

## Integration Options

### Option 1: Integrate From Source Module

Copy or include the `audiencelab-sdk` module in your Android project, then add it as a dependency in your app module:

```kotlin
dependencies {
    implementation(project(":audiencelab-sdk"))
}
```

Also include the module in `settings.gradle.kts`:

```kotlin
include(":audiencelab-sdk")
```

### Option 2: Integrate Using the Built AAR

Build the SDK:

```bash
./gradlew :audiencelab-sdk:assembleRelease
```

Then add the resulting AAR to your app project and include it as a local dependency.

Example:

```kotlin
dependencies {
    implementation(files("libs/audiencelab-sdk-release-1.1.11.aar"))
}
```

### Option 3: Integrate From Maven

If AudienceLab provides you access to the Maven package feed, add the package repository and dependency instead of managing AAR files manually.

Example dependency:

```kotlin
dependencies {
    implementation("ai.audiencelab:audiencelab-android-sdk:1.1.11")
}
```

Repository configuration depends on the hosted package source used for your release. For GitHub Packages, AudienceLab must also provide repository access/auth instructions.

## Android Manifest

Your app must have internet permission:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Where To Set The API Key

The SDK requires your AudienceLab API key during initialization.

Recommended:

- Store the API key in your app configuration layer or build config.
- Pass it into `AudienceLabSDK.initialize(...)` at app startup.

Avoid:

- scattering the API key across multiple activities
- committing production keys directly into shared sample code

### Recommended Setup Using `BuildConfig`

In your app module `build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        buildConfigField("String", "AUDIENCELAB_API_KEY", "\"replace-with-your-api-key\"")
    }
}
```

Then initialize the SDK from your app code:

```kotlin
import app.geeklab.audiencelab.sdk.AudienceLabOptions
import app.geeklab.audiencelab.sdk.AudienceLabSDK

val initialized = AudienceLabSDK.initialize(
    context = applicationContext,
    apiKey = BuildConfig.AUDIENCELAB_API_KEY,
    options = AudienceLabOptions(
        isDevelopmentMode = BuildConfig.DEBUG,
        isDebugEnabled = false
    )
)
```

### Current Sample App API Key Location

The sample app currently sets the API key here:

- [MainActivity.kt](/Users/nathan/Documents/GitHub/Audiencelab-SDKs/audiencelab_android_sdk/sample-app/src/main/java/app/geeklab/audiencelab/sample/MainActivity.kt)

It currently uses:

```kotlin
apiKey = "sample-api-key"
```

Replace that value or move it to `BuildConfig` before using a real backend.

## Build Variant Behavior

- Backend payload field `dev` follows the integrating app, not the SDK AAR build type.
- Preferred setup:

```kotlin
AudienceLabSDK.initialize(
    context = applicationContext,
    apiKey = BuildConfig.AUDIENCELAB_API_KEY,
    options = AudienceLabOptions(
        isDevelopmentMode = BuildConfig.DEBUG,
        isDebugEnabled = BuildConfig.DEBUG
    )
)
```

- If `isDevelopmentMode` is omitted and you initialize with `Context`, the SDK falls back to the host app debuggable flag.
- If you use the no-context initializer, set `isDevelopmentMode` explicitly if you need a non-default value.

## Recommended Initialization Point

Initialize the SDK once, as early as possible, using an application context.

Good locations:

- custom `Application` class
- game bootstrap activity
- engine startup bridge

Use this form:

```kotlin
AudienceLabSDK.initialize(
    context = applicationContext,
    apiKey = BuildConfig.AUDIENCELAB_API_KEY,
    options = AudienceLabOptions(
        isSdkEnabled = true,
        isMetricsEnabled = true,
        isDebugEnabled = false,
        appVersion = "1.0.0"
    )
)
```

## Basic Event Calls

### Custom Event

```kotlin
AudienceLabSDK.sendCustomEvent(
    name = "level_start",
    params = mapOf(
        "level" to 1,
        "mode" to "normal"
    )
)
```

### Purchase Event

```kotlin
AudienceLabSDK.sendPurchaseEvent(
    itemId = "starter_pack",
    itemName = "Starter Pack",
    value = 4.99,
    currency = "USD",
    status = "success",
    transactionId = "order-123"
)
```

### Ad Event

```kotlin
AudienceLabSDK.sendAdEvent(
    adId = "rewarded_video_1",
    name = "rewarded_video",
    source = "admob",
    watchTime = 30.0,
    reward = true,
    mediaSource = "campaign_a",
    channel = "rewarded",
    value = 0.75,
    currency = "USD"
)
```

## User Properties

The SDK supports both whitelisted (`wp`) and blacklisted (`bp`) user properties through:

- `setUserProperty(key, value, scope)`
- `unsetUserProperty(key, scope)`
- `clearUserProperties(scope)`
- `getWhitelistedUserProperties()`
- `getBlacklistedUserProperties()`

Example:

```kotlin
AudienceLabSDK.setUserProperty(
    key = "external_id",
    value = "user-123",
    scope = UserPropertyScope.BLACKLISTED
)
```

For blacklisted `email` and `phone` keys, the Android SDK automatically normalizes and SHA-256 hashes the value before it is persisted or sent.

```kotlin
AudienceLabSDK.setUserProperty(
    key = "email",
    value = "player@example.com",
    scope = UserPropertyScope.BLACKLISTED
)
```

## Optional Debug Hooks

You can observe high-level request outcomes:

```kotlin
AudienceLabSDK.setRequestResultCallback { success, errorMessage ->
    // inspect success / error
}
```

You can also observe request debug entries:

```kotlin
AudienceLabSDK.setRequestDebugListener { entry ->
    // entry.endpoint
    // entry.requestBody
    // entry.responseStatusCode
    // entry.responseBody
}
```

Additional debug snapshots are available for integration diagnostics:

```kotlin
val token = AudienceLabSDK.getTokenDebugSnapshot()
val identity = AudienceLabSDK.getIdentityDebugSnapshot()
val device = AudienceLabSDK.getDeviceDebugSnapshot()
val queue = AudienceLabSDK.getQueueDebugSnapshot()
```

These are intended for debug tooling and sample-app style verification screens, not for production UI.

## Deep Link Handling

If your app receives a deep link containing an AudienceLab token, pass the URL to the SDK:

```kotlin
AudienceLabSDK.handleDeepLink(intent?.dataString)
```

## GAID And App Set ID Behavior

The SDK supports two identity strategies for Android:

- app-supplied identity
  via `setAdvertisingId(...)`, `setAppSetId(...)`, `setLimitAdTracking(...)`,
  `setAdvertisingIdProvider(...)`, and `setAppSetIdProvider(...)`
- automatic reflective lookup when the host game already includes the relevant Google Play Services classes

The SDK does not add a hard dependency on Play Services for this.
For GAID, the SDK only attempts automatic lookup if the host app already declares the ad ID permission.
Automatic Play Services lookup can be disabled entirely during initialization with `AudienceLabOptions(autoResolveGooglePlayIds = false)`.
Provider-returned values are memoized in memory after the first resolution for the rest of the app process.
Call `AudienceLabSDK.clearIdentityCache()` when your app knows those app-supplied values changed and the SDK should resolve them again.

## Identity Settled State

The SDK exposes:

```kotlin
val settled = AudienceLabSDK.isIdentitySettled()
```

This is an observability API only. It tells you whether Android ID, GAID, and App Set ID collection paths have either resolved or been determined unavailable. It does not block event queuing or request dispatch on its own.

## Persistence Behavior

When initialized with `context`, the SDK persists:

- SDK enabled state
- metrics enabled state
- debug enabled state
- app version
- creative token
- cumulative totals
- retention markers
- session markers
- queued events

Creative token behavior in the current Android version:

- the token is persisted indefinitely
- blank or placeholder tokens such as `bin` are rejected
- webhook events remain queued until a valid creative token is available

## Verification Checklist

After integration:

1. Confirm `AudienceLabSDK.initialize(...)` returns `true`.
2. Trigger one custom event.
3. Verify `/fetch-token` succeeds and a valid creative token is present before expecting `/webhook` delivery.
4. Verify `AudienceLabSDK.getTokenDebugSnapshot()` and `AudienceLabSDK.getIdentityDebugSnapshot()` report the expected debug state.
5. Verify no crashes when the app backgrounds and resumes.
6. Verify queue replay works after restoring connectivity.

## Related Files

- Platform overview: [README.md](/Users/nathan/Documents/GitHub/Audiencelab-SDKs/audiencelab_android_sdk/README.md)
- Sample app launcher: [MainActivity.kt](/Users/nathan/Documents/GitHub/Audiencelab-SDKs/audiencelab_android_sdk/sample-app/src/main/java/app/geeklab/audiencelab/sample/MainActivity.kt)
