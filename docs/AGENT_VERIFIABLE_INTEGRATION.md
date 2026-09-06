# Agent-verifiable Android integration contract

This document is the human companion to the machine-readable contract at [`contracts/android-sdk.integration.v1.json`](../contracts/android-sdk.integration.v1.json). It is written so an agent can integrate and certify a supported Android build **without hidden steps**.

Related: [GEE-518](https://linear.app/geeklabltd/issue/GEE-518/android-sdk-publish-an-agent-verifiable-native-integration-contract), [GEE-481](https://linear.app/geeklabltd/issue/GEE-481/approval-approve-app-integration-credential-and-readiness-contract).

## Contract identity

| Field | Value |
| --- | --- |
| Contract ID | `audiencelab.android_sdk.integration` |
| Contract version | `1.0.0` |
| Platform | `native_android` |
| SDK module | `AudienceLabSDK` (`app.geeklab.audiencelab.sdk`) |
| SDK version | `1.1.11` (`android-v1.1.11`) |
| Maven | `ai.audiencelab:audiencelab-android-sdk:1.1.11` |
| Min / target SDK | `23` / `36` |
| Repository authority | `Geeklab-Ltd/audiencelab_android_sdk` |

Deterministic version sources that must stay aligned:

1. `contracts/android-sdk.integration.v1.json` → `sdk.version`
2. `README.md` / `CHANGELOG.md` documented `1.1.11`
3. GitHub Release `android-v1.1.11` AAR `BuildConfig.SDK_VERSION`

## Credentials (GEE-481)

- Credential kind: environment-scoped **application API key**.
- Runtime header: `geeklab-api-key`.
- Handoff is **one-time / direct** into the integration secret store for the entitled workspace+app.
- Agents may retrieve the key when authorized for that tenant+app, but must **never** write the raw key into git, MCP transcripts, ordinary logs, analytics events, or shared audit payloads.
- Audit may record issuance/use/rotation/presence metadata only.
- Sandbox and production keys are distinct. **Sandbox never implies analysis-ready.**

Inject at runtime only (placeholder in docs):

```kotlin
AudienceLabSDK.initialize(
    context = applicationContext,
    apiKey = BuildConfig.AUDIENCELAB_API_KEY, // from CI/secret store — never commit
    options = options
)
```

Use placeholder `YOUR_AUDIENCELAB_API_KEY` in docs and samples only.

## Install steps

### Preferred: Gradle / GitHub Packages

1. Obtain the environment API key via one-time handoff; store outside git.
2. Add repository `https://maven.pkg.github.com/geeklab-ltd/audiencelab_android_sdk` with `gpr.user` / `gpr.key`.
3. Pin `implementation("ai.audiencelab:audiencelab-android-sdk:1.1.11")`.
4. Ensure `android.permission.INTERNET` is in the host manifest.

### Fallback: Release AAR

Download `audiencelab-sdk-release-1.1.11.aar` from `android-v1.1.11` and add:

```kotlin
implementation(files("libs/audiencelab-sdk-release-1.1.11.aar"))
```

## Initialize

```kotlin
import app.geeklab.audiencelab.sdk.AudienceLabOptions
import app.geeklab.audiencelab.sdk.AudienceLabSDK

AudienceLabSDK.initialize(
    context = applicationContext,
    apiKey = BuildConfig.AUDIENCELAB_API_KEY,
    options = AudienceLabOptions(
        isDevelopmentMode = BuildConfig.DEBUG,
        isDebugEnabled = BuildConfig.DEBUG,
        autoResolveGooglePlayIds = true
    )
)
```

Production certification builds must use production (or approved prod-like) credentials and `isDevelopmentMode = false`.

## Required signals

### Automatic (SDK-emitted)

| Signal | Wire / check | Notes |
| --- | --- | --- |
| Creative token | `getTokenDebugSnapshot().hasValidToken` / `getCreativeToken()` | Required before webhook flush |
| Session lifecycle | `session` | 30-minute timeout |
| Retention | `retention` | After token availability; `retention_day` on envelopes |

### Integrator-emitted (minimum one for certification)

Call at least one of:

- `AudienceLabSDK.sendCustomEvent(...)` → `custom`
- `AudienceLabSDK.sendAdEvent(...)` → `custom.ad`
- `AudienceLabSDK.sendPurchaseEvent(...)` → `custom.purchase`

## Consent behavior

- Host owns Play Data safety answers and advertising-ID consent UX.
- Prefer `autoResolveGooglePlayIds = false` when consent is unavailable; supply IDs only through consented setters/providers.
- Blacklisted `email` / `phone` are SHA-256 hashed by the SDK.
- Reserved `_`-prefixed whitelisted properties are AudienceLab-managed.

## Diagnostics

Safe for evidence (previews only):

- `isInitialized()`, `getSdkVersion()`, `getQueueSize()`
- `getTokenDebugSnapshot()`, `getIdentityDebugSnapshot()`, `getDeviceDebugSnapshot()`
- `getConfiguredApiKeyPreview()`, `setRequestDebugListener(...)`

Never put raw API keys or full creative tokens in evidence, MCP logs, or PR text.

## Verification

```bash
python3 scripts/verify_android_integration_contract.py
```

Writes `verification/evidence/latest.json`. Exit `0` = pass.

Sandbox evidence certifies integration mechanics only. Analysis-ready still requires production/prod-like accepted signals and freshness (GEE-481 / GEE-525).

## Rollback

1. `setSdkEnabled(false)` / disable metrics.
2. Clear local SDK state / remove API key from runtime config.
3. Remove Maven/AAR dependency and rebuild.
4. Rotate/revoke the application API key if exposure is possible.
5. Optionally re-pin the last known-good SDK version.

## Out of scope

- Google Play submission automation (Phase 3 / GEE-527)
- Signed CI attestations (GEE-528)
- Backend readiness state machine (GEE-525)
