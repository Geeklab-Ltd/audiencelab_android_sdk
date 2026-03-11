# Changelog

All notable changes to this package are documented in this file.

## [1.1.6] - 2026-03-11

### Added

- Full parity-oriented Android public API baseline across init, toggles, events, retention, user properties, callbacks, queue inspection, and version getters.
- `SharedPreferences`-backed persisted state for toggles, app version, creative token, cumulative counters, retention markers, session markers, and queued events.
- `HttpURLConnection` request layer for `/fetch-token` and `/webhook`.
- Canonical backend envelope mapping using `type`, `eid`, `dk`, and `created_at`.
- Session lifecycle tracking with 30-minute timeout, start/end events, duration payloads, and background-timeout rollover.
- Daily retention tracking with first-launch day `0` and `backfill_day` based on the previous retention-day value.
- Persisted offline queue with replay on init, foreground, connectivity restoration, and token availability.
- Request debug listener support for inspecting SDK network behavior.
- Identity diagnostics APIs, token diagnostics APIs, and device diagnostics APIs.
- Optional reflective Play Services lookup for GAID and App Set ID when those classes already exist in the host app.

### Changed

- SDK version track moved from bootstrap `0.1.0` to parity target `1.1.x`.
- Webhook delivery is gated on a valid creative token; queued events are no longer sent with placeholder or missing tokens.
- Ad and purchase `value` fields are emitted as numeric values.
- Reserved underscore-prefixed whitelisted properties are preserved across clear/unset operations.
- Creative token validation now rejects blank and placeholder tokens such as `bin` across fetch-token and deep-link flows.

## [0.1.0] - 2026-03-05

### Added

- Initial Android Native SDK scaffold in Kotlin.
- Basic initialization API and event tracking support.
