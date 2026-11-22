#!/usr/bin/env python3
"""Scan a built APK with MobSF (Mobile Security Framework) and publish the findings.

MobSF is the de-facto open-source standard for mobile application security testing and maps
its output to OWASP MASVS/MASTG. The REST API is served by the official container
(`opensecurity/mobile-security-framework-mobsf`). This script only uses the standard library
so the CI job needs no Python dependencies.

Exit codes: 0 = scan completed (or MobSF unavailable, reported as a warning),
            1 = findings at or above the gate severity.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

READY_ATTEMPTS = 60
SCAN_TIMEOUT_SECONDS = 900


def request(url: str, key: str, data: bytes | None = None, headers: dict | None = None,
            method: str | None = None, timeout: int = 120) -> bytes:
    merged = {
        "Authorization": key,
        "X-Mobsf-Api-Key": key,
        "User-Agent": "imanacore-ci-audit/1.0",
    }
    if headers:
        merged.update(headers)
    req = urllib.request.Request(url, data=data, headers=merged, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as response:
        return response.read()


def wait_ready(base: str, key: str) -> bool:
    for attempt in range(READY_ATTEMPTS):
        try:
            request(f"{base}/api/v1/scans", key, timeout=10)
            return True
        except urllib.error.HTTPError:
            # Any HTTP response proves the server is up and answering.
            return True
        except Exception:
            pass
        time.sleep(5)
        print(f"waiting for MobSF ({attempt + 1}/{READY_ATTEMPTS})", flush=True)
    return False


def upload(base: str, key: str, apk: Path) -> dict:
    boundary = f"----imanacore{uuid.uuid4().hex}"
    body = b"".join([
        f"--{boundary}\r\n".encode(),
        b'Content-Disposition: form-data; name="file"; filename="app.apk"\r\n',
        b"Content-Type: application/vnd.android.package-archive\r\n\r\n",
        apk.read_bytes(),
        f"\r\n--{boundary}--\r\n".encode(),
    ])
    raw = request(
        f"{base}/api/v1/upload",
        key,
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        timeout=300,
    )
    payload = json.loads(raw)
    if "hash" not in payload:
        raise RuntimeError(f"unexpected upload response: {payload}")
    return payload


def scan(base: str, key: str, uploaded: dict) -> None:
    form = urllib.parse.urlencode({
        "scan_type": uploaded.get("scan_type", "apk"),
        "file_name": uploaded["file_name"],
        "hash": uploaded["hash"],
    }).encode()
    request(
        f"{base}/api/v1/scan",
        key,
        data=form,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        timeout=SCAN_TIMEOUT_SECONDS,
    )


def fetch_report(base: str, key: str, file_hash: str) -> dict:
    form = urllib.parse.urlencode({"hash": file_hash}).encode()
    raw = request(
        f"{base}/api/v1/report_json",
        key,
        data=form,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        timeout=300,
    )
    return json.loads(raw)


SEVERITY_ORDER = {"high": 3, "medium": 2, "warning": 2, "low": 1, "info": 0, "secure": 0}


def normalise(report: dict) -> list[dict]:
    findings: list[dict] = []
    for item in report.get("appsec", []) or []:
        findings.append({
            "source": "appsec",
            "title": item.get("title") or item.get("description") or "Application security issue",
            "detail": (item.get("description") or "").strip(),
            "severity": (item.get("severity") or "info").lower(),
            "section": item.get("section") or "appsec",
        })
    for item in report.get("manifest_analysis", []) or []:
        findings.append({
            "source": "manifest",
            "title": item.get("title") or item.get("name") or "Manifest issue",
            "detail": (item.get("description") or item.get("stat") or "").strip(),
            "severity": (item.get("severity") or "info").lower(),
            "section": "manifest",
        })
    return findings


def to_sarif(findings: list[dict]) -> dict:
    level = {"high": "error", "medium": "warning", "warning": "warning", "low": "note"}
    rules = []
    results = []
    seen = set()
    for finding in findings:
        rule_id = "mobsf." + "".join(ch if ch.isalnum() else "-" for ch in finding["title"].lower())[:70].strip("-")
        if rule_id not in seen:
            seen.add(rule_id)
            rules.append({
                "id": rule_id,
                "name": rule_id,
                "shortDescription": {"text": finding["title"][:180]},
                "fullDescription": {"text": finding["detail"][:2000] or finding["title"]},
                "defaultConfiguration": {"level": level.get(finding["severity"], "note")},
            })
        results.append({
            "ruleId": rule_id,
            "level": level.get(finding["severity"], "note"),
            "message": {"text": f"{finding['title']}: {finding['detail']}"[:1000]},
            "locations": [{"physicalLocation": {"artifactLocation": {"uri": "app/build/outputs/apk"}}}],
        })
    return {
        "version": "2.1.0",
        "$schema": "https://json.schemastore.org/sarif-2.1.0.json",
        "runs": [{"tool": {"driver": {
            "name": "MobSF", "informationUri": "https://mobsf.github.io/docs/",
            "rules": rules,
        }}, "results": results}],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--url", default="http://localhost:8000")
    parser.add_argument("--key", default="ci-audit-key")
    parser.add_argument("--out-dir", default="audit-report")
    parser.add_argument("--gate", choices=["none", "medium", "high"], default="high")
    args = parser.parse_args()

    apk = Path(args.apk)
    out = Path(args.out_dir)
    out.mkdir(parents=True, exist_ok=True)
    if not apk.is_file():
        print(f"::error::APK not found: {apk}", file=sys.stderr)
        return 2

    if not wait_ready(args.url, args.key):
        message = "MobSF did not become ready; APK security scan skipped (not a pass)."
        print(f"::warning::{message}")
        (out / "mobsf.md").write_text(f"### MobSF\n\n{message}\n", encoding="utf-8")
        return 0

    try:
        uploaded = upload(args.url, args.key, apk)
        scan(args.url, args.key, uploaded)
        report = fetch_report(args.url, args.key, uploaded["hash"])
    except Exception as exc:  # tooling failure, not an app verdict
        message = f"MobSF scan could not complete: {exc}. APK security scan is UNVERIFIED."
        print(f"::warning::{message}")
        (out / "mobsf.md").write_text(f"### MobSF\n\n{message}\n", encoding="utf-8")
        return 0

    (out / "mobsf-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    findings = normalise(report)
    (out / "mobsf.sarif").write_text(json.dumps(to_sarif(findings), indent=2), encoding="utf-8")

    counts = {"high": 0, "medium": 0, "warning": 0, "low": 0, "info": 0}
    for finding in findings:
        counts[finding["severity"]] = counts.get(finding["severity"], 0) + 1

    score = report.get("app_score") or report.get("security_score") or "n/a"
    lines = [
        "### MobSF APK security scan",
        "",
        f"Security score: **{score}** · high {counts['high']} · medium {counts['medium']} · "
        f"low {counts['low']} · info {counts['info']}",
        "",
        "| Severity | Finding | Section |",
        "|---|---|---|",
    ]
    for finding in sorted(findings, key=lambda f: -SEVERITY_ORDER.get(f["severity"], 0))[:40]:
        title = finding["title"].replace("|", "\\|")[:160]
        lines.append(f"| {finding['severity']} | {title} | {finding['section']} |")
    lines += ["", "Full JSON and SARIF are attached to the run artifact.", ""]
    (out / "mobsf.md").write_text("\n".join(lines), encoding="utf-8")
    print("\n".join(lines))

    gate = {"none": 99, "medium": 2, "high": 3}[args.gate]
    blocking = [f for f in findings if SEVERITY_ORDER.get(f["severity"], 0) >= gate]
    if blocking:
        for finding in blocking[:20]:
            print(f"::error title=MobSF::{finding['title']} — {finding['detail'][:400]}")
        return 1
    return 0


if __name__ == "__main__":
    import urllib.parse  # noqa: E402  (used by scan)
    sys.exit(main())
