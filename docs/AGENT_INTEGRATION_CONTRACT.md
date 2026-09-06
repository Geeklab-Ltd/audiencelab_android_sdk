# Agent Integration Contract (Android Native)

This document is the human-readable companion to the machine-readable Android native integration contract published for **GEE-518**.

Primary artifacts:

- `contracts/android-native-integration.v1.json` — deterministic contract instance
- `contracts/android-native-integration.v1.schema.json` — JSON Schema for the contract
- `contracts/examples/verification-evidence.schema.json` — evidence envelope schema
- `contracts/examples/verification-evidence.example.json` — example evidence
- `scripts/verify_integration_contract.py` — local machine checker
- `docs/VERIFICATION.md` — verification path

## Goal

An authorized agent can integrate and certify a supported native Android app against Audiencelab **without hidden steps**. Package/version, install, credentials, required signals, consent, diagnostics, rollback, and verification evidence are explicit.

## Authority (GEE-481)

Locked by GEE-481:

1. Backend-authoritative app identity. Phase 1 platforms include native Android.
2. Machine-readable app-specific manifests; minimum events are policy-defined per platform.
3. Credentials may be retrieved by an entitled agent through a **short-lived / one-time handoff**. Raw long-lived keys must not appear in ordinary logs, analytics events, or shared audit payloads.
4. Ready for analysis requires accepted production (or approved prod-like) signals plus freshness — not merely app creation or sandbox initialize success.
5. Google Play submit automation is **out of scope** for this contract (Phase 3).

## Deterministic package

| Field | Value |
| --- | --- |
| Maven coordinate | `ai.audiencelab:audiencelab-android-sdk:1.1.11` |
| Release tag | `android-v1.1.11` |
| AAR asset | `audiencelab-sdk-release-1.1.11.aar` |
| Public API root | `app.geeklab.audiencelab.sdk` |
| Min SDK | `23` |
| Target SDK | `36` |
| Default base URL | `https://analytics.geeklab.app` |
| Token endpoint | `/fetch-token` |
| Event endpoint | `/webhook` |

Do not certify integrations that use dynamic Gradle version ranges (`+`, `latest.release`).

## Install path (no hidden steps)

Recommended:

1. Add GitHub Packages Maven repo `https://maven.pkg.github.com/geeklab-ltd/audiencelab_android_sdk` with `gpr.user` / `gpr.key` from Gradle properties or CI secrets.
2. Pin `implementation("ai.audiencelab:audiencelab-android-sdk:1.1.11")`.
3. Ensure `android.permission.INTERNET` is in the host manifest.
4. Inject `AUDIENCELAB_API_KEY` via BuildConfig / CI secret / sealed config — never commit it.
5. Call `AudienceLabSDK.initialize(context, apiKey, options)` early in `Application` startup.

Fallback: download the release AAR and add `implementation(files("libs/audiencelab-sdk-release-1.1.11.aar"))`.

See `docs/INTEGRATION.md` for code samples.

## Required signals

Automatic after successful initialize with metrics enabled:

- session lifecycle
- retention day markers
- creative token fetch (`/fetch-token`)

Minimum for integration certification:

1. `initialize` succeeds and a valid creative token is obtained.
2. At least one of `sendCustomEvent`, `sendPurchaseEvent`, or `sendAdEvent` is emitted after token availability without runtime errors.

Recommended when the host app has the corresponding product surfaces: purchase, ad, custom events, and RevenueCat `getRevenueCatAttributes()` stitching.

## Consent behavior

- Host app owns privacy disclosures, Play Data safety answers, and advertising-ID consent UX.
- Use `autoResolveGooglePlayIds = false` plus manual/provider setters when consent or network constraints require it.
- SDK SHA-256 hashes blacklisted `email` / `phone` before persistence or send.
- Reserved `_`-prefixed whitelisted properties are AudienceLab-managed.

## Diagnostics

Use only redacted diagnostics for evidence:

- `isInitialized()`
- `getTokenDebugSnapshot()`
- `getIdentityDebugSnapshot()`
- `getDeviceDebugSnapshot()`
- `getQueueDebugSnapshot()`
- `setRequestDebugListener(...)`
- `getConfiguredApiKeyPreview()`
- `getSdkVersion()`

Never place raw API keys or full creative tokens in evidence files, MCP logs, or PR text.

## Verification

1. Run `python3 scripts/verify_integration_contract.py --example-evidence`.
2. Integrate the host app using the contract install steps.
3. Produce an evidence JSON conforming to `contracts/examples/verification-evidence.schema.json`.
4. Run `python3 scripts/verify_integration_contract.py --evidence path/to/evidence.json`.
5. Treat sandbox evidence as integration mechanics only. Analysis-ready still requires production/prod-like accepted signals and freshness per GEE-481 / GEE-525.

## Rollback

1. Restore the previous pinned Maven/AAR version.
2. Remove or no-op initialize/event call sites if uninstalling.
3. Rotate the application API key if exposure is possible.
4. Rebuild and re-run verification.
5. Record rollback evidence: previous version, new version, key rotation, verification result.

## Out of scope

- Google Play submission automation (GEE-527, Phase 3)
- Signed CI attestations (GEE-528, Phase 3)
- Backend readiness state machine implementation (GEE-525)
- Unity / iOS contracts (GEE-516 / GEE-510)
