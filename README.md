# Audiencelab Android SDK

Native Android SDK source and release repository for Audiencelab.

## Repository layout

- `audiencelab-sdk/`: Android library module published as an AAR and Maven package.
- `sample-app/`: Integration and runtime verification app.
- `docs/INTEGRATION.md`: Client integration guide published with releases.
- `DEVELOPER_README.md`: SDK API and development guide.
- `DISTRIBUTION.md`: Artifact and release-process details.

## Build requirements

- JDK 17 or newer
- Android SDK platform 36
- Android build-tools 36.0.0

The SDK supports Android API 23 and newer.

## Local validation

Run the complete validation set from the repository root:

```bash
./gradlew --no-daemon :audiencelab-sdk:testDebugUnitTest
./gradlew --no-daemon :audiencelab-sdk:assembleRelease
./gradlew --no-daemon :audiencelab-sdk:publishReleasePublicationToMavenLocal
./gradlew --no-daemon :sample-app:assembleDebug
```

When the local Android toolchain is unavailable, use the Docker validation scripts:

```bash
./docker/build-image.sh
./docker/run-quick.sh
```

Use `./docker/run-tests.sh` for the full release-style validation pass.

## Distribution

Release outputs are:

- `audiencelab-sdk/build/outputs/versioned-aar/audiencelab-sdk-release-<version>.aar`
- `ai.audiencelab:audiencelab-android-sdk:<version>`

Only release AARs are intended for client distribution. Runtime development mode is controlled by the integrating app through `AudienceLabOptions`; it is not derived from the AAR build type.

See [DISTRIBUTION.md](DISTRIBUTION.md) for the maintainer release flow and [docs/INTEGRATION.md](docs/INTEGRATION.md) for client setup.
