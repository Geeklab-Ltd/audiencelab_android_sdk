# Android Native Integration Verification Path

This is the machine-checkable verification path for **GEE-518**.

## Artifacts

| Artifact | Role |
| --- | --- |
| `contracts/android-native-integration.v1.json` | Source-of-truth contract |
| `contracts/android-native-integration.v1.schema.json` | Schema for the contract |
| `contracts/examples/verification-evidence.schema.json` | Schema for agent evidence |
| `contracts/examples/verification-evidence.example.json` | Example evidence envelope |
| `scripts/verify_integration_contract.py` | Local verifier |

## Quick check (docs repo)

From the repository root:

```bash
python3 scripts/verify_integration_contract.py --example-evidence
```

Expected stdout ends with `PASS`.

Optional: install `jsonschema` for full Draft 2020-12 validation.

```bash
python3 -m pip install --user jsonschema
python3 scripts/verify_integration_contract.py --example-evidence
```

Without `jsonschema`, the script still applies structural fallback checks that encode the blocking contract invariants.

## Host-app certification sequence

An integrating agent must complete these steps with no private side instructions:

1. **Read** `contracts/android-native-integration.v1.json` and `docs/AGENT_INTEGRATION_CONTRACT.md`.
2. **Install** the pinned package `ai.audiencelab:audiencelab-android-sdk:1.1.11` (or matching AAR).
3. **Inject credentials** through a one-time / short-lived handoff into CI secrets or BuildConfig. Do not commit or log the raw key (GEE-481).
4. **Initialize** `AudienceLabSDK` early and confirm `initialize(...) == true`.
5. **Collect diagnostics** using preview/snapshot APIs only.
6. **Emit** at least one certification signal (`custom`, `purchase`, or `ad`) after token availability.
7. **Observe backend/MCP acceptance** for the same environment; record freshness and blocking failures.
8. **Write evidence** matching `verification-evidence.schema.json`.
9. **Run** `python3 scripts/verify_integration_contract.py --evidence <file>`.
10. **Pass only if** the verifier prints `PASS` and no raw secrets are present.

## Blocking checks

Local:

- package version pinned to `1.1.11`
- `INTERNET` permission present
- API key not in VCS

Runtime:

- initialize succeeded
- creative token obtained
- >=1 signal emitted with empty `runtime_errors`

Backend:

- accepted signal count >= 1
- freshness ok
- environment matches evidence environment
- no blocking validation failures

## Sandbox vs analysis-ready

| Outcome | Meaning |
| --- | --- |
| Verifier `PASS` on sandbox evidence | Integration mechanics certified |
| Analysis-ready | Requires production/prod-like credentials, accepted production signals, freshness, and no blocking failures (backend authoritative; GEE-481 / GEE-525) |

Sandbox success never implies analysis-ready.

## Evidence redaction rules

Allowed in evidence:

- boolean statuses
- counts
- redacted previews (`ak_****`, `ct_****`)
- permission lists
- Maven coordinates / SDK versions

Forbidden in evidence:

- raw `AUDIENCELAB_API_KEY`
- full creative token
- GitHub Packages tokens
- unredacted request/response bodies containing secrets

## Rollback verification

After rollback, regenerate evidence against the restored pinned version and re-run the verifier. Record `previous_version`, `restored_version`, `key_rotated`, and `verification_rerun_passed` in notes or a follow-up evidence file.
