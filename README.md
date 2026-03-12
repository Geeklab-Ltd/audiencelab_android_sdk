# AudienceLab Android SDK

## Introduction

AudienceLab provides privacy-centric measurement for Android games and apps by connecting campaign performance to creative-level analytics.

## Objectives

- Enable Android developers to integrate AudienceLab into their apps with a lightweight native SDK.
- Capture session, retention, ad, purchase, and custom events for AudienceLab reporting.

## Prerequisites

Developers will need:

- an Android app or game project
- minimum SDK `23`
- internet permission in the app manifest
- an AudienceLab API key

Required manifest entry:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Integrating AudienceLab SDK into Android

This section provides the recommended ways to integrate the AudienceLab Android SDK into your project.

### Option 1: Install via Gradle/Maven (Recommended)

Add the package repository provided by AudienceLab:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/geeklab-ltd/audiencelab_android_sdk")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
            password = providers.gradleProperty("gpr.key").orNull
        }
    }
}
```

Add the dependency:

```kotlin
dependencies {
    implementation("ai.audiencelab:audiencelab-android-sdk:1.1.7")
}
```

Recommended `~/.gradle/gradle.properties` entries:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_PACKAGES_TOKEN
```

### Option 2: Manual Installation

If you received a release AAR instead of package-feed access:

1. Download `audiencelab-sdk-release-1.1.7.aar`
2. Place it in your app project, for example `app/libs/`
3. Add it as a file dependency

```kotlin
dependencies {
    implementation(files("libs/audiencelab-sdk-release-1.1.7.aar"))
}
```

## Configure the SDK

Initialize the SDK early in app startup:

```kotlin
import app.geeklab.audiencelab.sdk.AudienceLabOptions
import app.geeklab.audiencelab.sdk.AudienceLabSDK

AudienceLabSDK.initialize(
    context = applicationContext,
    apiKey = BuildConfig.AUDIENCELAB_API_KEY,
    options = AudienceLabOptions(
        isDevelopmentMode = BuildConfig.DEBUG,
        isDebugEnabled = BuildConfig.DEBUG
    )
)
```

Recommended app-module setup:

```kotlin
android {
    defaultConfig {
        buildConfigField("String", "AUDIENCELAB_API_KEY", "\"replace-with-your-api-key\"")
    }
}
```

For more detailed setup instructions, see the [Integration Guide](docs/INTEGRATION.md).

## Finalizing SDK Integration

After configuration:

1. Build your Android app
2. Launch the app and verify SDK initialization succeeds
3. Confirm the SDK receives a creative token successfully
4. Verify events are sent without runtime errors

## Event Tracking

### Purchase Events

Use purchase events to track completed purchases:

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

### Ad Events

Use ad events to track ad impressions and rewarded ads:

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

### Custom Events

Use custom events for game-specific or app-specific actions:

```kotlin
AudienceLabSDK.sendCustomEvent(
    name = "level_start",
    params = mapOf(
        "level" to 1,
        "mode" to "normal"
    )
)
```

## User Properties

The SDK supports both whitelisted (`wp`) and blacklisted (`bp`) user properties.

For blacklisted `email` and `phone` values, the Android SDK automatically SHA-256 hashes the normalized value before it is stored or sent.

## Version Information

Current SDK line:

- SDK version: `1.1.7`
- minimum SDK: `23`
- target SDK: `36`

## Conclusion

By integrating the AudienceLab Android SDK, developers can send the core event and attribution data required for AudienceLab reporting while keeping setup straightforward in native Android projects.
