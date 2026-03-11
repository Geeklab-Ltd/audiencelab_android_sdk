# AudienceLab Android SDK Integration

This guide is for Android game/app developers integrating the released AudienceLab Android SDK.

## Requirements

- Android app or game project
- Min SDK `23` or newer
- Internet permission in the app manifest
- AudienceLab API key

Manifest requirement:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Recommended Integration: Gradle/Maven

Add the package repository provided by AudienceLab.

GitHub Packages example:

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
    implementation("ai.audiencelab:audiencelab-android-sdk:<SDK_VERSION>")
}
```

Recommended `~/.gradle/gradle.properties` entries:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_PACKAGES_TOKEN
```

## Fallback Integration: Release AAR

If you received a release AAR instead of package-feed access:

1. Place `audiencelab-sdk-release-<SDK_VERSION>.aar` in your app project, for example `app/libs/`.
2. Add it as a file dependency.

```kotlin
dependencies {
    implementation(files("libs/audiencelab-sdk-release-<SDK_VERSION>.aar"))
}
```

## Configuration

Configure the SDK from the integrating app. This includes:

- the AudienceLab API key
- whether events should be sent as development traffic or release traffic
- whether SDK debug behavior should be enabled locally

Do not hardcode customer-specific keys inside shared SDK code.

Recommended app-module setup:

```kotlin
android {
    defaultConfig {
        buildConfigField("String", "AUDIENCELAB_API_KEY", "\"replace-with-your-api-key\"")
    }
}
```

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

## Sending Events

Initialize the SDK before sending events:

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

## Minimal Verification

After integration, verify:

1. the app builds successfully
2. `AudienceLabSDK.initialize(...)` succeeds
3. `/fetch-token` succeeds with your API key/environment
4. a creative token is obtained
5. queued webhook events flush after token availability
6. ad, purchase, and custom events can be sent without runtime errors
