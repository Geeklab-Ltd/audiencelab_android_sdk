#!/usr/bin/env python3
"""Verify the Audiencelab Android native integration contract (GEE-518).

Checks:
1) Contract JSON parses and matches required identity/version fields.
2) Contract validates against its JSON Schema (draft 2020-12) when a validator
   is available; otherwise applies a strict structural fallback checker.
3) Optional verification evidence validates against the evidence schema and
   satisfies the blocking pass criteria encoded in the contract.

Exit codes:
  0 = pass
  1 = validation failure
  2 = usage / environment error
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Dict, List, Tuple

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONTRACT = ROOT / "contracts" / "android-native-integration.v1.json"
DEFAULT_CONTRACT_SCHEMA = ROOT / "contracts" / "android-native-integration.v1.schema.json"
DEFAULT_EVIDENCE_SCHEMA = ROOT / "contracts" / "examples" / "verification-evidence.schema.json"
DEFAULT_EXAMPLE_EVIDENCE = ROOT / "contracts" / "examples" / "verification-evidence.example.json"

EXPECTED_CONTRACT_ID = "audiencelab.android.native.integration"
EXPECTED_MAVEN = "ai.audiencelab:audiencelab-android-sdk:1.1.11"
EXPECTED_SDK_VERSION = "1.1.11"
EXPECTED_RELEASE_TAG = "android-v1.1.11"
REQUIRED_PERMISSION = "android.permission.INTERNET"


def load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def try_jsonschema_validate(instance: Any, schema: Dict[str, Any]) -> Tuple[bool, str]:
    try:
        import jsonschema
        from jsonschema import Draft202012Validator
    except Exception:
        return False, "jsonschema_unavailable"

    try:
        Draft202012Validator.check_schema(schema)
        Draft202012Validator(schema).validate(instance)
        return True, "jsonschema_ok"
    except Exception as exc:  # noqa: BLE001 - surface validator errors to agents
        return False, f"jsonschema_error: {exc}"


def require(condition: bool, message: str, errors: List[str]) -> None:
    if not condition:
        errors.append(message)


def structural_contract_checks(contract: Dict[str, Any], errors: List[str]) -> None:
    require(contract.get("contract_id") == EXPECTED_CONTRACT_ID, "contract_id mismatch", errors)
    require(contract.get("platform") == "android_native", "platform must be android_native", errors)
    require(isinstance(contract.get("contract_version"), str), "contract_version missing", errors)

    package = contract.get("package") or {}
    require(package.get("maven_coordinates") == EXPECTED_MAVEN, "package.maven_coordinates mismatch", errors)
    require(package.get("sdk_version") == EXPECTED_SDK_VERSION, "package.sdk_version mismatch", errors)
    require(package.get("release_tag") == EXPECTED_RELEASE_TAG, "package.release_tag mismatch", errors)
    require(package.get("public_api_root") == "app.geeklab.audiencelab.sdk", "public_api_root mismatch", errors)
    require(
        package.get("default_base_url") == "https://analytics.geeklab.app",
        "default_base_url mismatch",
        errors,
    )
    endpoints = package.get("endpoints") or {}
    require(endpoints.get("fetch_token") == "/fetch-token", "fetch_token endpoint mismatch", errors)
    require(endpoints.get("webhook") == "/webhook", "webhook endpoint mismatch", errors)

    compatibility = contract.get("compatibility") or {}
    require(compatibility.get("min_sdk") == 23, "min_sdk must be 23", errors)
    require(compatibility.get("target_sdk") == 36, "target_sdk must be 36", errors)

    manifest = contract.get("manifest") or {}
    require(
        REQUIRED_PERMISSION in (manifest.get("required_permissions") or []),
        "INTERNET permission missing from manifest.required_permissions",
        errors,
    )

    credentials = contract.get("credentials") or {}
    require(credentials.get("secret_name") == "AUDIENCELAB_API_KEY", "secret_name mismatch", errors)
    forbidden = credentials.get("forbidden") or []
    require(any("logs" in item.lower() for item in forbidden), "credentials.forbidden must ban logs", errors)

    for section in (
        "authority",
        "install",
        "initialization",
        "required_signals",
        "consent",
        "diagnostics",
        "verification",
        "rollback",
        "out_of_scope",
        "references",
    ):
        require(section in contract, f"missing section: {section}", errors)

    verification = contract.get("verification") or {}
    for key in ("local_checks", "runtime_checks", "backend_checks", "pass_criteria"):
        require(key in verification, f"verification.{key} missing", errors)
        require(isinstance(verification.get(key), list) and len(verification.get(key) or []) > 0,
                f"verification.{key} must be a non-empty list", errors)

    out_of_scope = " ".join(contract.get("out_of_scope") or []).lower()
    require("google play" in out_of_scope, "out_of_scope must mention Google Play submit automation", errors)


def evaluate_evidence(contract: Dict[str, Any], evidence: Dict[str, Any], errors: List[str]) -> Dict[str, bool]:
    results: Dict[str, bool] = {}
    package_version = (contract.get("package") or {}).get("sdk_version")
    local = evidence.get("local") or {}
    runtime = evidence.get("runtime") or {}
    backend = evidence.get("backend") or {}
    secrets = evidence.get("secrets") or {}
    token = runtime.get("token") or {}

    require(evidence.get("contract_id") == EXPECTED_CONTRACT_ID, "evidence.contract_id mismatch", errors)
    require(evidence.get("package_sdk_version") == package_version, "evidence.package_sdk_version mismatch", errors)
    require(secrets.get("raw_api_key_present") is False, "raw API key must not be present in evidence", errors)
    require(
        secrets.get("raw_creative_token_present") is False,
        "raw creative token must not be present in evidence",
        errors,
    )

    def mark(check_id: str, passed: bool, detail: str = "") -> None:
        results[check_id] = passed
        if not passed:
            suffix = f" ({detail})" if detail else ""
            errors.append(f"check failed: {check_id}{suffix}")

    mark(
        "package_version_pinned",
        local.get("resolved_maven_coordinate") == EXPECTED_MAVEN
        and local.get("sdk_version_runtime") == package_version
        and runtime_get_sdk_ok(local, package_version),
    )
    mark(
        "manifest_internet_present",
        REQUIRED_PERMISSION in (local.get("manifest_permissions") or []),
    )
    mark(
        "secret_not_in_vcs",
        bool(local.get("vcs_secret_scan_passed"))
        and str(local.get("secret_storage_mechanism") or "").lower() not in {"git", "vcs", "committed", "source"},
    )
    mark(
        "initialize_succeeded",
        bool(runtime.get("initialize_returned_true")) and bool(runtime.get("is_initialized")),
    )
    mark("creative_token_obtained", bool(token.get("has_valid_token")))
    mark(
        "signal_emitted_without_runtime_error",
        isinstance(runtime.get("signals_emitted"), list)
        and len(runtime.get("signals_emitted") or []) >= 1
        and runtime.get("runtime_errors") == [],
    )
    mark(
        "accepted_signal_observed",
        int(backend.get("accepted_signal_count") or 0) >= 1
        and bool(backend.get("freshness_ok"))
        and backend.get("environment") == evidence.get("environment"),
    )
    mark("no_blocking_validation_failures", backend.get("blocking_failures") == [])

    declared = evidence.get("checks") or {}
    for check_id, passed in results.items():
        if check_id in declared and isinstance(declared[check_id], dict):
            if bool(declared[check_id].get("passed")) != passed:
                errors.append(f"evidence.checks.{check_id}.passed does not match evaluator result ({passed})")

    expected_result = "pass" if all(results.values()) and not any(e.startswith("raw ") for e in errors) else "fail"
    # Recompute expected_result purely from check marks + secret flags.
    expected_result = "pass" if all(results.values()) and secrets.get("raw_api_key_present") is False and secrets.get("raw_creative_token_present") is False else "fail"
    if evidence.get("result") != expected_result:
        errors.append(f"evidence.result must be '{expected_result}' given check outcomes")

    return results


def runtime_get_sdk_ok(local: Dict[str, Any], package_version: str) -> bool:
    return local.get("sdk_version_runtime") == package_version


def main(argv: List[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    parser.add_argument("--contract-schema", type=Path, default=DEFAULT_CONTRACT_SCHEMA)
    parser.add_argument("--evidence", type=Path, default=None, help="Optional evidence JSON to evaluate")
    parser.add_argument("--evidence-schema", type=Path, default=DEFAULT_EVIDENCE_SCHEMA)
    parser.add_argument(
        "--example-evidence",
        action="store_true",
        help=f"Evaluate the checked-in example evidence at {DEFAULT_EXAMPLE_EVIDENCE}",
    )
    args = parser.parse_args(argv)

    errors: List[str] = []

    for path in (args.contract, args.contract_schema, args.evidence_schema):
        if not path.is_file():
            print(f"error: missing file: {path}", file=sys.stderr)
            return 2

    contract = load_json(args.contract)
    contract_schema = load_json(args.contract_schema)
    evidence_schema = load_json(args.evidence_schema)

    if not isinstance(contract, dict):
        print("error: contract root must be an object", file=sys.stderr)
        return 1

    ok, detail = try_jsonschema_validate(contract, contract_schema)
    if detail == "jsonschema_unavailable":
        print("note: jsonschema package unavailable; using structural fallback checks")
        structural_contract_checks(contract, errors)
    elif not ok:
        errors.append(detail)
    else:
        print("contract schema: pass")
        structural_contract_checks(contract, errors)

    evidence_path = args.evidence
    if args.example_evidence:
        evidence_path = DEFAULT_EXAMPLE_EVIDENCE
    if evidence_path is not None:
        if not evidence_path.is_file():
            print(f"error: missing evidence file: {evidence_path}", file=sys.stderr)
            return 2
        evidence = load_json(evidence_path)
        ok, detail = try_jsonschema_validate(evidence, evidence_schema)
        if detail == "jsonschema_unavailable":
            print("note: jsonschema package unavailable; evaluating evidence with contract rules only")
        elif not ok:
            errors.append(detail)
        else:
            print("evidence schema: pass")
        evaluate_evidence(contract, evidence, errors)
        print(f"evidence evaluated: {evidence_path}")

    if errors:
        print("FAIL")
        for err in errors:
            print(f"- {err}")
        return 1

    print("PASS")
    print(f"contract={args.contract}")
    print(f"sdk_version={contract['package']['sdk_version']}")
    print(f"maven={contract['package']['maven_coordinates']}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
