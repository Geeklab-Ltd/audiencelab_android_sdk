# AudienceLab Android SDK Distribution

This document describes how the Android SDK is built, what artifact is shared, and how game/app developers consume it.

## Purpose

The Android SDK can now be distributed either as:

- a release AAR built from the `audiencelab-sdk` library module
- a Maven artifact published from the same release component

This document covers:

- how AudienceLab maintainers build the Android SDK artifact
- what gets shared with client developers
- how client developers integrate the shared artifact into their app/game project

## Distribution Model

Current distribution artifact:

- release AAR: `audiencelab-sdk/build/outputs/versioned-aar/audiencelab-sdk-release-<SDK_VERSION>.aar`
- Maven coordinates: `ai.audiencelab:audiencelab-android-sdk:<SDK_VERSION>`

Current sharing model:

- AudienceLab builds the AAR internally from this repository
- AudienceLab shares the release AAR and integration docs with the client developer
- the client developer adds that AAR to their Android app/game project

Current Maven publishing targets:

- `mavenLocal()` for local validation
- GitHub Packages for hosted package distribution

## What Gets Built

Source module:

- `audiencelab-sdk/`

Release build command:

```bash
./gradlew :audiencelab-sdk:assembleRelease
```

Full local validation command:

```bash
./gradlew :audiencelab-sdk:testDebugUnitTest :audiencelab-sdk:assembleRelease :sample-app:assembleDebug
```

Docker validation command:

```bash
./docker/run-tests.sh
```

The Docker path is the preferred fallback when the local machine does not have a working JDK 17 + Android SDK setup.

## Build Requirements

- JDK 17+
- Android SDK platform `android-36`
- Android build-tools `36.0.0`
- Gradle wrapper from this repository

Android SDK settings:

- Min SDK: `23`
- Target SDK: `36`

## Artifact Rules

Primary distribution outputs:

- `audiencelab-sdk-release-<SDK_VERSION>.aar`
- `ai.audiencelab:audiencelab-android-sdk:<SDK_VERSION>`

Do not distribute:

- debug AARs
- `sample-app`
- Gradle caches or build directories
- source snapshots unless a source integration has been explicitly agreed

Important behavior rule:

- backend `dev` is not derived from the SDK AAR build type
- backend `dev` is controlled by the integrating app through `AudienceLabOptions.isDevelopmentMode`, or inferred from the host app debuggable flag when the context initializer is used

That means the SDK must be built as a release AAR for distribution, while still allowing the consuming game/app to declare whether it is a development build.

## What To Share With Clients

Minimum handoff package:

1. `audiencelab-sdk-release-<SDK_VERSION>.aar`
2. integration instructions from [DEVELOPER_README.md](/Users/nathan/Documents/GitHub/Audiencelab-SDKs/audiencelab_android_sdk/DEVELOPER_README.md)
3. version number / release notes
4. any environment-specific base URL or API key provisioning instructions

Recommended handoff format:

- zipped package containing the AAR and a short release note
- or GitHub release artifact plus docs link
- or internal client delivery channel with the exact SDK version called out

## Maintainer Release Flow

From `audiencelab_android_sdk/`:

1. Update Android SDK version sources and changelog.
2. Run validation:
   - `./docker/run-tests.sh`
3. Build release artifact:
   - `./gradlew :audiencelab-sdk:assembleRelease`
4. Publish Maven package when releasing through the package feed:
   - `./gradlew :audiencelab-sdk:publishReleasePublicationToMavenLocal`
   - or CI publish via GitHub Actions to GitHub Packages
5. Confirm output exists:
   - `audiencelab-sdk/build/outputs/versioned-aar/audiencelab-sdk-release-<SDK_VERSION>.aar`
6. Share the AAR or package coordinates together with integration instructions.
7. Record the delivered version and release notes.

## Client Integration Flow

Client developers integrate the SDK into their Android app/game project.

### Option 1: Manual AAR Integration

1. Copy `audiencelab-sdk-release-<SDK_VERSION>.aar` into the client app project, usually under `app/libs/`.
2. Add the AAR as a dependency.

Example:

```kotlin
dependencies {
    implementation(files("libs/audiencelab-sdk-release-1.1.3.aar"))
}
```

3. Ensure internet permission is declared:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

4. Initialize the SDK early in app startup.

Example:

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

### Option 2: Source Module Integration

This is for cases where the client is intentionally given source instead of a compiled artifact.

1. Include the `audiencelab-sdk` module in the client Android project.
2. Add `implementation(project(":audiencelab-sdk"))`.
3. Use the same runtime initialization pattern as above.

Default recommendation remains compiled AAR distribution, not source sharing.

### Option 3: Maven Integration

When the client has access to the hosted package feed, use Maven coordinates instead of distributing files manually.

Dependency:

```kotlin
dependencies {
    implementation("ai.audiencelab:audiencelab-android-sdk:1.1.3")
}
```

Repository example for GitHub Packages:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/Geeklab-Ltd/Audiencelab-SDKs")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
            password = providers.gradleProperty("gpr.key").orNull
        }
    }
}
```

Use Maven integration when:

- the client already consumes internal/private package feeds
- you want version upgrades through Gradle dependency management
- you want to avoid manual AAR redistribution each release

## API Key Handling

The client developer must provide the API key from the app/game side.

Recommended:

- place the key in app-side build config or secure environment wiring
- pass it to `AudienceLabSDK.initialize(...)`

Do not rely on the AAR itself to encode customer-specific API key configuration.

## Verification After Integration

Client developer should verify:

1. app builds successfully with the AAR present
2. `AudienceLabSDK.initialize(...)` returns `true`
3. `/fetch-token` succeeds
4. a valid creative token becomes available
5. queued webhook events flush after token availability
6. ad, purchase, and custom event calls execute without runtime errors

## Current Limitations Of Distribution Model

Current model limitations:

- GitHub Packages access still requires repository/package credentials
- no Maven Central pipeline yet
- no Android Studio installer/plugin flow
- AAR updates still need manual redistribution when Maven is not used
- transitive dependency management remains the consuming app's responsibility if future Android SDK dependencies are added

If distribution volume grows, the next likely step is a hosted Maven artifact pipeline rather than changing the runtime SDK configuration model.

## Related Docs

- platform overview: [README.md](/Users/nathan/Documents/GitHub/Audiencelab-SDKs/audiencelab_android_sdk/README.md)
- developer integration guide: [DEVELOPER_README.md](/Users/nathan/Documents/GitHub/Audiencelab-SDKs/audiencelab_android_sdk/DEVELOPER_README.md)
- Android platform notes: [docs/sdks/android.md](/Users/nathan/Documents/GitHub/Audiencelab-SDKs/docs/sdks/android.md)
- general release process: [docs/release-process.md](/Users/nathan/Documents/GitHub/Audiencelab-SDKs/docs/release-process.md)
