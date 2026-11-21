#!/usr/bin/env python3
"""Convert an Android Lint XML report into SARIF 2.1.0 for GitHub code scanning.

Android Lint is the official static analyser for Android and ships ~500 checks covering
security, correctness, accessibility, internationalisation, performance and manifest
configuration. Running it in CI and publishing SARIF puts its findings next to CodeQL and
mobsfscan in the repository's Security tab instead of in an unread HTML artifact.
"""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SEVERITY = {"Fatal": "error", "Error": "error", "Warning": "warning", "Information": "note", "Ignore": "none"}


def convert(report: Path) -> dict:
    root = ET.parse(report).getroot()
    rules: dict[str, dict] = {}
    results: list[dict] = []

    for issue in root.iter("issue"):
        rule_id = issue.get("id", "AndroidLint")
        severity = issue.get("severity", "Warning")
        message = issue.get("message", "")
        category = issue.get("category", "")
        explanation = issue.get("explanation", "")

        if rule_id not in rules:
            rules[rule_id] = {
                "id": rule_id,
                "name": rule_id,
                "shortDescription": {"text": message[:180] or rule_id},
                "fullDescription": {"text": (explanation or message)[:2000] or rule_id},
                "defaultConfiguration": {"level": SEVERITY.get(severity, "warning")},
                "properties": {"category": category, "tags": ["android-lint"]},
            }

        location = issue.find("location")
        uri = "."
        line = 1
        if location is not None:
            uri = location.get("file", ".").lstrip("/")
            try:
                line = int(location.get("line", "1"))
            except ValueError:
                line = 1

        results.append({
            "ruleId": rule_id,
            "level": SEVERITY.get(severity, "warning"),
            "message": {"text": message or rule_id},
            "locations": [{
                "physicalLocation": {
                    "artifactLocation": {"uri": uri},
                    "region": {"startLine": line},
                }
            }],
            "properties": {"category": category, "severity": severity},
        })

    return {
        "$schema": "https://json.schemastore.org/sarif-2.1.0.json",
        "version": "2.1.0",
        "runs": [{
            "tool": {"driver": {
                "name": "Android Lint",
                "informationUri": "https://developer.android.com/studio/write/lint",
                "rules": list(rules.values()),
            }},
            "results": results,
        }],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("report", help="lint-results-*.xml")
    parser.add_argument("--out", required=True, help="SARIF output path")
    parser.add_argument("--summary", help="optional markdown summary path")
    args = parser.parse_args()

    report = Path(args.report)
    if not report.is_file():
        print(f"::warning::Android Lint report not found at {report}", file=sys.stderr)
        Path(args.out).write_text(json.dumps({"version": "2.1.0", "runs": []}), encoding="utf-8")
        return 0

    sarif = convert(report)
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    Path(args.out).write_text(json.dumps(sarif, indent=2), encoding="utf-8")

    results = sarif["runs"][0]["results"]
    errors = sum(1 for r in results if r["level"] == "error")
    warnings = sum(1 for r in results if r["level"] == "warning")
    print(f"Android Lint: {len(results)} finding(s), {errors} error(s), {warnings} warning(s)")

    if args.summary:
        counts: dict[str, int] = {}
        for result in results:
            key = f"{result['ruleId']} ({result['level']})"
            counts[key] = counts.get(key, 0) + 1
        lines = [
            "### Android Lint",
            "",
            f"{len(results)} findings · {errors} errors · {warnings} warnings",
            "",
            "| Check | Count |",
            "|---|---|",
        ]
        for key, value in sorted(counts.items(), key=lambda kv: -kv[1])[:25]:
            lines.append(f"| `{key}` | {value} |")
        Path(args.summary).write_text("\n".join(lines) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
