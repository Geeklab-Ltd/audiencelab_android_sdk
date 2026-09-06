#!/usr/bin/env python3
"""Verify the AudienceLab Android agent-verifiable integration contract (GEE-518).

Runs without Android SDK or network. Writes machine-checkable evidence to
verification/evidence/latest.json. Exit 0 on pass, 1 on failure.
Never prints or records raw API key material.
"""

from __future__ import annotations

import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
CONTRACT_PATH = ROOT / "contracts" / "android-sdk.integration.v1.json"
SCHEMA_PATH = ROOT / "contracts" / "android-sdk.integration.v1.schema.json"
EVIDENCE_DIR = ROOT / "verification" / "evidence"
EVIDENCE_PATH = EVIDENCE_DIR / "latest.json"
AGENT_DOC = ROOT / "docs" / "AGENT_VERIFIABLE_INTEGRATION.md"
INTEGRATION_DOC = ROOT / "docs" / "INTEGRATION.md"
README = ROOT / "README.md"
CHANGELOG = ROOT / "CHANGELOG.md"

SECRETISH = re.compile(
    r"(?i)(geeklab-api-key\s*[:=]\s*['\"](?!YOUR_|<.*>|\$\{)[^'\"]{8,}['\"])"
    r"|(api[_-]?key\s*[:=]\s*['\"](?!YOUR_|<.*>|\$\{|apiKey|AUDIENCELAB_API_KEY|replace-with)[A-Za-z0-9_\-]{16,}['\"])"
)

EXPECTED_CONTRACT_ID = "audiencelab.android_sdk.integration"
EXPECTED_VERSION = "1.1.11"
EXPECTED_MAVEN = "ai.audiencelab:audiencelab-android-sdk:1.1.11"
EXPECTED_TAG = "android-v1.1.11"


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def check(results: list[dict[str, Any]], check_id: str, ok: bool, detail: str) -> None:
    results.append({"id": check_id, "status": "pass" if ok else "fail", "detail": detail})


def require_keys(obj: dict[str, Any], keys: list[str], path: str = "$") -> list[str]:
    return [f"{path}.{key}" for key in keys if key not in obj]


def finish(results: list[dict[str, Any]], contract: dict[str, Any] | None) -> int:
    passed = all(item["status"] == "pass" for item in results) and bool(results)
    evidence = {
        "contract_id": (contract or {}).get("contract_id", EXPECTED_CONTRACT_ID),
        "contract_version": (contract or {}).get("contract_version"),
        "sdk_version": ((contract or {}).get("sdk") or {}).get("version"),
        "generated_at": utc_now(),
        "harness": "scripts/verify_android_integration_contract.py",
        "passed": passed,
        "checks": results,
        "notes": [
            "Evidence intentionally omits secrets and network calls.",
            "Google Play submission automation is out of scope for GEE-518.",
        ],
    }
    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    EVIDENCE_PATH.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")

    failed = [c for c in results if c["status"] != "pass"]
    print(f"AudienceLab Android integration contract verification: {'PASS' if passed else 'FAIL'}")
    print(f"Evidence: {EVIDENCE_PATH.relative_to(ROOT)}")
    for item in results:
        print(f"  [{item['status'].upper()}] {item['id']}: {item['detail']}")
    if failed:
        print(f"{len(failed)} check(s) failed", file=sys.stderr)
        return 1
    return 0


def main() -> int:
    results: list[dict[str, Any]] = []

    # 1) Contract parses
    try:
        contract = json.loads(read_text(CONTRACT_PATH))
        check(results, "contract_parses", True, f"Parsed {CONTRACT_PATH.relative_to(ROOT)}")
    except Exception as exc:  # noqa: BLE001
        check(results, "contract_parses", False, str(exc))
        return finish(results, None)

    # 2) Schema shape (+ optional jsonschema)
    required = [
        "contract_id",
        "contract_version",
        "schema_ref",
        "issue_refs",
        "platform",
        "status",
        "sdk",
        "install_steps",
        "public_api",
        "credentials",
        "required_signals",
        "privacy_and_consent",
        "diagnostics",
        "rollback",
        "verification",
        "out_of_scope",
        "human_docs",
    ]
    missing = require_keys(contract, required)
    shape_ok = not missing and contract.get("contract_id") == EXPECTED_CONTRACT_ID
    shape_ok = shape_ok and contract.get("platform") == "native_android"
    detail = "Required top-level keys present" if shape_ok else f"Missing/invalid: {missing or contract.get('contract_id')}"
    if SCHEMA_PATH.is_file():
        try:
            schema = json.loads(read_text(SCHEMA_PATH))
            try:
                from jsonschema import Draft202012Validator

                Draft202012Validator(schema).validate(contract)
                detail = "Draft 2020-12 schema validation passed"
            except ImportError:
                detail = f"{detail}; jsonschema unavailable (structural only)"
            except Exception as exc:  # noqa: BLE001
                shape_ok = False
                detail = f"jsonschema error: {exc}"
        except Exception as exc:  # noqa: BLE001
            shape_ok = False
            detail = f"schema load error: {exc}"
    check(results, "contract_schema_shape", shape_ok, detail)

    # 3) Version consistency
    sdk = contract.get("sdk") or {}
    version_ok = (
        sdk.get("version") == EXPECTED_VERSION
        and sdk.get("version_tag") == EXPECTED_TAG
        and sdk.get("maven_coordinates") == EXPECTED_MAVEN
        and EXPECTED_VERSION in read_text(README)
        and f"## [{EXPECTED_VERSION}]" in read_text(CHANGELOG)
    )
    check(
        results,
        "version_consistency",
        version_ok,
        f"sdk.version={sdk.get('version')} maven={sdk.get('maven_coordinates')} tag={sdk.get('version_tag')}",
    )

    # 4) Docs present
    human_docs = contract.get("human_docs") or []
    docs_ok = all((ROOT / rel).is_file() for rel in human_docs) and AGENT_DOC.is_file()
    check(
        results,
        "docs_present",
        docs_ok,
        "Human docs present" if docs_ok else f"Missing docs among {human_docs}",
    )

    # 5) Credential rules
    creds = contract.get("credentials") or {}
    handoff = creds.get("handoff") or {}
    injection = creds.get("injection") or {}
    never_persist = set(handoff.get("never_persist_in") or [])
    cred_ok = (
        creds.get("header_name") == "geeklab-api-key"
        and handoff.get("mode") in {"one_time_or_direct", "one_time"}
        and handoff.get("agent_may_retrieve") is True
        and handoff.get("cross_tenant_forbidden") is True
        and {"git", "ordinary_logs", "mcp_transcripts"}.issubset(never_persist)
        and injection.get("runtime_only") is True
    )
    check(
        results,
        "credential_rules_explicit",
        cred_ok,
        "Credential one-time handoff and secret hygiene rules present"
        if cred_ok
        else "Credential handoff rules incomplete",
    )

    # 6) Rollback explicit
    rollback = contract.get("rollback") or {}
    steps = rollback.get("steps") or []
    rollback_ids = {s.get("id") for s in steps if isinstance(s, dict)}
    rollback_ok = rollback.get("required") is True and {
        "disable_sdk",
        "reset_local_state",
        "remove_package",
        "revoke_credential",
    }.issubset(rollback_ids)
    check(
        results,
        "rollback_rules_explicit",
        rollback_ok,
        "Rollback steps explicit" if rollback_ok else f"Rollback incomplete: {sorted(rollback_ids)}",
    )

    # 7) Required signals
    signals = contract.get("required_signals") or {}
    signals_ok = (
        isinstance(signals.get("automatic"), list)
        and len(signals.get("automatic") or []) >= 1
        and isinstance(signals.get("integrator_emitted"), dict)
        and isinstance(signals.get("first_signal_checks"), list)
        and len(signals.get("first_signal_checks") or []) >= 1
    )
    check(
        results,
        "required_signals_defined",
        signals_ok,
        "Required signals defined" if signals_ok else "Required signals incomplete",
    )

    # 8) Manifest INTERNET declared in contract
    privacy = contract.get("privacy_and_consent") or {}
    permissions = (privacy.get("permissions") or {}).get("required_manifest_permissions") or []
    manifest_ok = "android.permission.INTERNET" in permissions
    check(
        results,
        "manifest_internet_declared",
        manifest_ok,
        "INTERNET permission declared" if manifest_ok else f"permissions={permissions}",
    )

    # 9) Secret hygiene across contract + docs
    scan_targets = [CONTRACT_PATH, AGENT_DOC, INTEGRATION_DOC, README]
    offenders: list[str] = []
    for path in scan_targets:
        if path.is_file() and SECRETISH.search(read_text(path)):
            offenders.append(str(path.relative_to(ROOT)))
    check(
        results,
        "no_secret_literals_in_contract_docs",
        not offenders,
        "No secret-like literals found" if not offenders else f"Suspicious literals in: {offenders}",
    )

    # 10) Out of scope store submit declared
    out_of_scope = contract.get("out_of_scope") or []
    store_declared = any(
        isinstance(item, dict) and item.get("id") in {"google_play_submit", "store_submit"}
        for item in out_of_scope
    )
    check(
        results,
        "out_of_scope_store_submit_declared",
        store_declared,
        "Google Play submit declared out of scope"
        if store_declared
        else "Missing out_of_scope Google Play submit declaration",
    )

    # 11) Issue refs include GEE-518
    refs = set(contract.get("issue_refs") or [])
    check(results, "issue_refs_gee518", "GEE-518" in refs and "GEE-481" in refs, f"issue_refs={sorted(refs)}")

    # 12) Install steps include credential + verify
    install_ids = {s.get("id") for s in (contract.get("install_steps") or []) if isinstance(s, dict)}
    install_ok = {"obtain_credential", "add_package", "initialize_sdk", "verify_locally"}.issubset(install_ids)
    check(
        results,
        "install_steps_complete",
        install_ok,
        "Install steps complete" if install_ok else f"install_ids={sorted(install_ids)}",
    )

    return finish(results, contract)


if __name__ == "__main__":
    sys.exit(main())
