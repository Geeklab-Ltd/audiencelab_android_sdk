# AudienceLab Android SDK

Public distribution surface for the AudienceLab Android SDK.

This repository is intended for Android game and app developers integrating released SDK versions. It does not publish the Android SDK source code.

This repository is automation-managed from the internal AudienceLab monorepo during Android releases.

## Distribution

Primary distribution channel:

- Maven artifact: `ai.audiencelab:audiencelab-android-sdk:<SDK_VERSION>`

Fallback distribution channel:

- versioned release AAR: `audiencelab-sdk-release-<SDK_VERSION>.aar`

Current Android SDK line:

- version track: `1.1.x`
- minimum SDK: `23`
- target SDK: `36`

## Integration

Start here:

- [Integration Guide](docs/INTEGRATION.md)
- [Changelog](CHANGELOG.md)

## Release Artifacts

Each Android SDK release may include:

- GitHub Release entry
- versioned release AAR asset
- changelog updates
- Maven package for Gradle integration

Release tag format:

- `android-v<version>`

## Gradle Dependency

```kotlin
dependencies {
    implementation("ai.audiencelab:audiencelab-android-sdk:<SDK_VERSION>")
}
```

Repository/auth setup depends on the package feed access AudienceLab provides you.

## Notes

- Use Maven integration when package feed access is available.
- Use the release AAR only as a fallback/manual path.
- Backend `dev` is controlled by the integrating app, not by the SDK artifact build type.
- Release notes and fallback AAR assets are attached to GitHub Releases in this repository.
