#!/usr/bin/env python3
"""Decide whether a security scanner produced a trustworthy result.

The scanners in `.github/workflows/security.yml` fail in two very different ways, and CI
must never confuse them:

  * the scanner ran and found something  -> a *finding*, handled by the policy below;
  * the scanner did not really run (bad arguments, missing mount, crashed container,
    unpacked/invalid SARIF) -> a *scanner failure*, which is never reported as a clean scan.

MobSFScan is a good example of why the exit code alone is not enough: it returns 1 when it
has findings and 0 when it has none, and it also returns 0 when every path it was given is
missing (it simply scans nothing). So this script proves the work happened before it reads
a single result: every target must exist and contain source files, the SARIF must parse,
the tool driver must be the expected scanner, and the run must be marked successful.

Policy (documented in docs/SECURITY_SCANNING.md):

  * gitleaks   every reported leak blocks. `.gitleaks.toml` is the only allowlist.
  * mobsfscan  SARIF `error` results block unless the rule is accepted in
               `.github/security-scan-baseline.json` with a documented reason;
               `warning`/`note` results (and results with no level, which SARIF defines as
               warning) are advisory and are reported in the run summary.

The baseline is a ratchet, like `.github/audit-baseline.json`: a new blocking finding fails
the build, a known one stays visible, and fixing it asks you to lower the baseline.

Usage:
  security-scan-gate.py --tool mobsfscan --sarif audit-report/mobsfscan.sarif \\
      --report audit-report/mobsfscan.md --target app/src --target shared/src
  security-scan-gate.py --tool gitleaks --sarif audit-report/gitleaks.sarif \\
      --report audit-report/gitleaks.md
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from collections import Counter
from pathlib import Path

# Results at these SARIF levels block a merge (subject to the accepted-findings baseline).
BLOCKING_LEVELS = {"error"}

# SARIF leaves `level` optional; the specification says an absent level means "warning".
DEFAULT_LEVEL = "warning"


class ScannerFailure(Exception):
    """The scanner did not produce a result that can be trusted."""


def load_sarif(path: Path) -> dict:
    if not path.is_file():
        raise ScannerFailure(
            f"{path} was not produced; the scanner did not complete, so this is a scanner "
            "failure and not a clean scan."
        )
    if path.stat().st_size == 0:
        raise ScannerFailure(f"{path} is empty; the scanner produced no report.")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise ScannerFailure(f"{path} is not valid SARIF: {exc}") from exc
    runs = data.get("runs")
    if not isinstance(runs, list) or not runs:
        raise ScannerFailure(f"{path} contains no SARIF runs.")
    return data


def check_run(sarif: dict, tool: str) -> dict:
    """Validate the SARIF run really came from the expected scanner and executed."""
    run = sarif["runs"][0]
    driver = (run.get("tool") or {}).get("driver") or {}
    name = (driver.get("name") or "").lower()
    if tool not in name:
        raise ScannerFailure(
            f"SARIF driver is {name or 'missing'!r}, expected {tool!r}; refusing to treat an "
            "unrelated report as this scanner's result."
        )
    invocations = run.get("invocations") or []
    if invocations and any(inv.get("executionSuccessful") is False for inv in invocations):
        raise ScannerFailure(f"{tool} reported executionSuccessful=false in its SARIF run.")
    return run


def check_targets(targets: list[str], tool: str) -> int:
    """Prove the scanner had sources to look at.

    MobSFScan quietly scans nothing when a path does not exist, and a report with zero
    results is then indistinguishable from a clean scan.
    """
    total = 0
    for target in targets:
        path = Path(target)
        if not path.exists():
            raise ScannerFailure(
                f"scan target {target!r} does not exist; {tool} would have scanned nothing."
            )
        if path.is_file():
            total += 1
            continue
        count = sum(1 for item in path.rglob("*") if item.is_file())
        if count == 0:
            raise ScannerFailure(
                f"scan target {target!r} contains no files; {tool} would have scanned nothing."
            )
        total += count
    if total == 0:
        raise ScannerFailure(f"no source files were available for {tool} to scan.")
    return total


GITLEAKS_MODE = False


def collect_results(run: dict) -> list[dict]:
    rows = []
    for result in run.get("results") or []:
        rule = result.get("ruleId") or (result.get("rule") or {}).get("id") or "unknown"
        level = (result.get("level") or DEFAULT_LEVEL).lower()
        # Gitleaks emits no SARIF levels; every leak it reports is blocking.
        if GITLEAKS_MODE:
            level = "error"
        message = ((result.get("message") or {}).get("text") or "").strip()
        locations = result.get("locations") or []
        path = "—"
        if locations:
            artifact = ((locations[0].get("physicalLocation") or {}).get("artifactLocation") or {})
            path = artifact.get("uri") or "—"
        rows.append({"rule": rule, "level": level, "message": message, "path": path})
    return rows


def load_baseline(path: Path, tool: str) -> dict[str, int]:
    if not path.is_file():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ScannerFailure(f"{path} is not valid JSON: {exc}") from exc
    accepted = data.get(tool) or {}
    if not isinstance(accepted, dict):
        raise ScannerFailure(f"{path} has a malformed {tool!r} section.")
    return {str(k): int(v) for k, v in accepted.items()}


def render(tool: str, title: str, scanned: int, rows: list[dict], blocking: list[dict],
           accepted: Counter, baseline: dict[str, int], source: str) -> str:
    blocking_counts = Counter(r["rule"] for r in blocking)
    blocking_ids = {id(r) for r in blocking}
    advisory = [r for r in rows if id(r) not in blocking_ids]
    lines = [f"### {title}", ""]
    scope = (
        f"scanned {scanned} file(s)" if scanned
        else "scanned the repository including git history"
    )
    lines.append(
        f"`{tool}` {scope} and reported **{len(rows)} finding(s)**: "
        f"{len(blocking)} blocking, {len(advisory)} advisory."
    )
    lines.append("")
    if blocking_counts:
        lines += ["#### Blocking findings", "", "| Rule | Level | Count | Baseline | Location |",
                  "|---|---|---|---|---|"]
        seen: Counter = Counter()
        for row in blocking:
            seen[row["rule"]] += 1
            if seen[row["rule"]] > 1:
                continue
            lines.append(
                f"| `{row['rule']}` | {row['level']} | {blocking_counts[row['rule']]} | "
                f"{baseline.get(row['rule'], 0)} | `{row['path']}` |"
            )
        lines.append("")
    if advisory:
        lines += ["#### Advisory findings (reported, do not block)", "",
                  "| Rule | Level | Count | Example location |", "|---|---|---|---|"]
        for rule, count in Counter(r["rule"] for r in advisory).most_common():
            example = next(r["path"] for r in advisory if r["rule"] == rule)
            level = next(r["level"] for r in advisory if r["rule"] == rule)
            lines.append(f"| `{rule}` | {level} | {count} | `{example}` |")
        lines.append("")
    if accepted:
        lines += ["Accepted in `.github/security-scan-baseline.json`: "
                  + ", ".join(f"`{rule}` × {count}" for rule, count in sorted(accepted.items())), ""]
    lines.append(f"_Policy: `{source}`._")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tool", required=True, choices=["mobsfscan", "gitleaks"])
    parser.add_argument("--sarif", required=True)
    parser.add_argument("--report", required=True)
    parser.add_argument("--baseline", default=".github/security-scan-baseline.json")
    parser.add_argument("--target", action="append", default=[],
                        help="A path the scanner was asked to scan (repeatable).")
    args = parser.parse_args()

    global GITLEAKS_MODE
    GITLEAKS_MODE = args.tool == "gitleaks"

    sarif_path = Path(args.sarif)
    report_path = Path(args.report)
    report_path.parent.mkdir(parents=True, exist_ok=True)

    title = "mobsfscan (OWASP mobile static analysis)" if args.tool == "mobsfscan" else "Gitleaks (committed secrets)"
    try:
        sarif = load_sarif(sarif_path)
        run = check_run(sarif, args.tool)
        scanned = check_targets(args.target, args.tool) if args.target else 0
    except ScannerFailure as exc:
        message = f"{args.tool} scanner failure: {exc}"
        report_path.write_text(
            f"### {title}\n\n**SCANNER FAILURE — result unverified.**\n\n{exc}\n\n"
            "This is not a clean scan. See `docs/SECURITY_SCANNING.md`.\n",
            encoding="utf-8",
        )
        print(f"::error::{message}")
        _summary(f"### {title}\n\n**SCANNER FAILURE — result unverified.** {exc}\n")
        return 1

    rows = collect_results(run)
    # Gitleaks has no baseline and no severity levels: every leak it reports blocks.
    blocking = rows if args.tool == "gitleaks" else [
        r for r in rows if r["level"] in BLOCKING_LEVELS
    ]
    baseline = load_baseline(Path(args.baseline), args.tool)
    counts = Counter(r["rule"] for r in blocking)

    accepted = Counter({rule: count for rule, count in counts.items()
                        if baseline.get(rule, 0) >= count})
    unaccepted = Counter({rule: count for rule, count in counts.items()
                          if baseline.get(rule, 0) < count})
    stale = {rule: count for rule, count in baseline.items() if counts.get(rule, 0) < count}

    title_line = title
    markdown = render(args.tool, title_line, scanned, rows, blocking, accepted, baseline,
                      "docs/SECURITY_SCANNING.md")
    report_path.write_text(markdown, encoding="utf-8")
    print(markdown)
    _summary(markdown)

    failed = False
    for rule, count in sorted(unaccepted.items()):
        failed = True
        print(
            f"::error title={args.tool}/{rule}::{count} blocking finding(s) for {rule} "
            f"(accepted baseline: {baseline.get(rule, 0)})."
        )
    for rule, count in sorted(stale.items()):
        print(
            f"::warning title={args.tool}/{rule}::{rule} now has {counts.get(rule, 0)} "
            f"finding(s) but the baseline accepts {count}; lower .github/security-scan-baseline.json."
        )

    if failed:
        print(f"{sum(unaccepted.values())} blocking finding(s) not accepted by the baseline",
              file=sys.stderr)
        return 1

    print(f"{args.tool}: {len(rows)} finding(s); no blocking finding outside policy")
    return 0


def _summary(markdown: str) -> None:
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write(markdown + "\n")


if __name__ == "__main__":
    sys.exit(main())
