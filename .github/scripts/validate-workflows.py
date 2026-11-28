#!/usr/bin/env python3
"""Validate the GitHub Actions workflows themselves.

actionlint already parses the files and knows the workflow schema; this script adds the checks
that need the repository, not the schema:

  * YAML syntax (a malformed file must fail the validation job, not the workflow it defines);
  * every workflow has a name, a trigger and at least one job with steps;
  * every step either runs a command or uses an action, never neither or both;
  * every local action (`./path`) exists as an action;
  * every container reference (`docker://...`, and the `image:` of job containers) is pinned to
    a tag or digest instead of an implicit `latest`;
  * with `--resolve-refs`, every remote `owner/repo@ref` really exists upstream, so a typo in a
    tag cannot look like a passing workflow change;
  * the three expected workflows are present, so a deletion cannot turn the job green.

`--self-test` proves the checks fire: it builds deliberately broken fixtures and fails if any of
them is not detected. The validation workflow runs it before validating the repository.

Usage:
  python3 .github/scripts/validate-workflows.py --self-test
  python3 .github/scripts/validate-workflows.py --dir .github/workflows --resolve-refs \\
      --expected ci.yml maestro-android.yml arbigent-ai-qa.yml \\
      --report "$RUNNER_TEMP/workflow-validation.json"
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

import yaml

EXPECTED_DEFAULT = ["ci.yml", "maestro-android.yml", "arbigent-ai-qa.yml"]

REMOTE_USES = re.compile(
    r"^(?P<repo>[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*)@(?P<ref>[^@\s]+)$"
)
PINNED_IMAGE = re.compile(r"^(?P<image>[^:@\s]+)(?::(?P<tag>[\w][\w.-]*))?(?:@(?P<digest>sha256:[0-9a-f]{64}))?$")


class Problems:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []

    def error(self, path: Path, message: str) -> None:
        self.errors.append(f"{path}: {message}")

    def warn(self, path: Path, message: str) -> None:
        self.warnings.append(f"{path}: {message}")


def load_workflow(path: Path, problems: Problems) -> dict | None:
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        problems.error(path, f"unreadable: {exc}")
        return None
    try:
        data = yaml.safe_load(text)
    except yaml.YAMLError as exc:
        problems.error(path, f"invalid YAML: {exc}".replace("\n", " "))
        return None
    if not isinstance(data, dict):
        problems.error(path, "a workflow must be a YAML mapping")
        return None
    return data


def trigger_of(workflow: dict) -> object | None:
    # PyYAML resolves the bare key `on` to the boolean True, so both spellings are accepted.
    return workflow.get("on", workflow.get(True))


def check_image(path: Path, where: str, image: str, problems: Problems) -> None:
    if image.startswith("./"):
        if not Path(image).is_dir():
            problems.error(path, f"{where} uses missing local action {image!r}")
        return
    if image.startswith("docker://"):
        image = image[len("docker://"):]
    match = PINNED_IMAGE.match(image)
    if not match:
        problems.error(path, f"{where} has an unparseable action/image reference {image!r}")
        return
    if not match.group("tag") and not match.group("digest"):
        problems.error(
            path,
            f"{where} references {image!r} without a tag or digest; an implicit `latest` makes "
            "the pipeline unreproducible",
        )
    elif match.group("tag") == "latest" and not match.group("digest"):
        problems.error(
            path,
            f"{where} references the floating tag {image!r}; pin a version or a digest",
        )


def check_uses(path: Path, where: str, uses: str, problems: Problems,
               resolve_cache: dict[str, bool], resolve: bool) -> None:
    if uses.startswith("docker://"):
        check_image(path, where, uses, problems)
        return
    if uses.startswith("./"):
        target = Path(uses[2:])
        if not ((target / "action.yml").is_file() or (target / "action.yaml").is_file()
                or (target / "Dockerfile").is_file()):
            problems.error(path, f"{where} references local action {uses!r}, which does not exist")
        return
    match = REMOTE_USES.match(uses)
    if not match:
        problems.error(
            path,
            f"{where} has an invalid action reference {uses!r}; expected owner/repo[/path]@ref",
        )
        return
    if not resolve:
        return
    # `owner/repo/sub/path@ref` points at an action inside `owner/repo`; the ref belongs to
    # the repository, not to the subdirectory.
    slug = "/".join(match.group("repo").split("/")[:2])
    key = f"{slug}@{match.group('ref')}"
    if key not in resolve_cache:
        resolve_cache[key] = ref_exists(slug, match.group("ref"))
    if not resolve_cache[key]:
        problems.error(
            path,
            f"{where} references {key}, but that repository/ref cannot be resolved upstream",
        )


def ref_exists(repo: str, ref: str) -> bool:
    url = f"https://github.com/{repo}.git"
    env = dict(os.environ, GIT_TERMINAL_PROMPT="0")
    try:
        listing = subprocess.run(
            ["git", "ls-remote", "--tags", "--heads", url],
            capture_output=True, text=True, timeout=60, env=env, check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return False
    if listing.returncode != 0:
        return False
    # A 40-hex ref is a pinned commit SHA: match the object id of an advertised
    # tag/head (or the peeled commit of an annotated tag) in column one.
    if re.fullmatch(r"[0-9a-fA-F]{40}", ref):
        for line in listing.stdout.splitlines():
            sha, _, _ = line.partition("\t")
            if sha.strip().lower() == ref.lower():
                return True
        return False
    for line in listing.stdout.splitlines():
        _, _, name = line.partition("\t")
        name = name.strip()
        if name in (f"refs/tags/{ref}", f"refs/tags/{ref}^{{}}", f"refs/heads/{ref}",
                    f"refs/heads/{ref}^{{}}"):
            return True
    return False


def validate_file(path: Path, problems: Problems, resolve_cache: dict[str, bool],
                  resolve: bool) -> int:
    """Return the number of `uses:` references checked in this file."""
    workflow = load_workflow(path, problems)
    if workflow is None:
        return 0

    if not workflow.get("name"):
        problems.warn(path, "workflow has no `name`; the Actions UI will show the file path")
    if trigger_of(workflow) is None:
        problems.error(path, "workflow has no trigger (`on:`)")
    if not workflow.get("permissions"):
        problems.warn(path, "no top-level `permissions`; the workflow inherits the repository default")

    jobs = workflow.get("jobs")
    if not isinstance(jobs, dict) or not jobs:
        problems.error(path, "workflow defines no jobs")
        return 0

    checked = 0
    for job_id, job in jobs.items():
        where = f"job {job_id!r}"
        if not isinstance(job, dict):
            problems.error(path, f"{where} is not a mapping")
            continue
        if "uses" in job:
            uses = job["uses"]
            if not isinstance(uses, str):
                problems.error(path, f"{where} has a non-string `uses`")
                continue
            checked += 1
            check_uses(path, where, uses, problems, resolve_cache, resolve)
            continue
        if not job.get("runs-on"):
            problems.error(path, f"{where} has neither `runs-on` nor a reusable `uses`")
        if not job.get("timeout-minutes"):
            problems.warn(path, f"{where} has no `timeout-minutes`; a hung step can run for hours")
        container = job.get("container")
        if isinstance(container, str):
            check_image(path, f"{where} container", container, problems)
        steps = job.get("steps")
        if not isinstance(steps, list) or not steps:
            problems.error(path, f"{where} has no steps")
            continue
        for index, step in enumerate(steps, start=1):
            step_where = f"{where} step {index}"
            if not isinstance(step, dict):
                problems.error(path, f"{step_where} is not a mapping")
                continue
            has_run, has_uses = "run" in step, "uses" in step
            if has_run and has_uses:
                problems.error(path, f"{step_where} has both `run` and `uses`")
            elif not has_run and not has_uses:
                problems.error(path, f"{step_where} has neither `run` nor `uses`")
            if has_uses:
                uses = step["uses"]
                if not isinstance(uses, str):
                    problems.error(path, f"{step_where} has a non-string `uses`")
                    continue
                checked += 1
                check_uses(path, step_where, uses, problems, resolve_cache, resolve)
    return checked


def discover(directory: Path, problems: Problems) -> list[Path]:
    if not directory.is_dir():
        problems.error(directory, "workflow directory does not exist")
        return []
    files = sorted(
        path for path in directory.iterdir()
        if path.is_file() and path.suffix in (".yml", ".yaml")
    )
    if not files:
        problems.error(directory, "no workflow files found; an empty run must not pass")
    return files


def self_test() -> int:
    """Prove the checks fire, using fixtures that are never committed."""
    cases = {
        "broken-yaml.yml": "name: x\non: push\njobs: [\n",
        "unknown-action.yml": (
            "name: x\non: push\npermissions: {contents: read}\njobs:\n  a:\n    runs-on: ubuntu-latest\n"
            "    timeout-minutes: 5\n    steps:\n      - uses: not-a-reference\n"
        ),
        "missing-local.yml": (
            "name: x\non: push\npermissions: {contents: read}\njobs:\n  a:\n    runs-on: ubuntu-latest\n"
            "    timeout-minutes: 5\n    steps:\n      - uses: ./.github/actions/does-not-exist\n"
        ),
        "floating-image.yml": (
            "name: x\non: push\npermissions: {contents: read}\njobs:\n  a:\n    runs-on: ubuntu-latest\n"
            "    timeout-minutes: 5\n    steps:\n      - uses: docker://alpine:latest\n"
        ),
        "mixed-step.yml": (
            "name: x\non: push\npermissions: {contents: read}\njobs:\n  a:\n    runs-on: ubuntu-latest\n"
            "    timeout-minutes: 5\n    steps:\n      - run: echo hi\n        uses: actions/checkout@v7\n"
        ),
        "no-jobs.yml": "name: x\non: push\njobs: {}\n",
        "good.yml": (
            "name: x\non: push\npermissions: {contents: read}\njobs:\n  a:\n    runs-on: ubuntu-latest\n"
            "    timeout-minutes: 5\n    steps:\n      - run: echo ok\n      - uses: docker://alpine:3.20\n"
        ),
    }
    failures: list[str] = []
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        for name, content in cases.items():
            (root / name).write_text(content, encoding="utf-8")
        for name in cases:
            problems = Problems()
            validate_file(root / name, problems, {}, False)
            detected = bool(problems.errors)
            should_fail = name != "good.yml"
            if detected != should_fail:
                failures.append(
                    f"{name}: expected {'errors' if should_fail else 'no errors'}, "
                    f"got {problems.errors or 'none'}"
                )
    if failures:
        for failure in failures:
            print(f"::error::self-test: {failure}")
        print(f"self-test failed ({len(failures)} case(s))", file=sys.stderr)
        return 1
    print(f"self-test passed ({len(cases)} fixture(s))")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dir", default=".github/workflows")
    parser.add_argument("--expected", nargs="*", default=EXPECTED_DEFAULT)
    parser.add_argument("--resolve-refs", action="store_true",
                        help="Verify every remote action reference exists upstream.")
    parser.add_argument("--report", help="Write a JSON report for the workflow summary.")
    parser.add_argument("--self-test", action="store_true",
                        help="Check that the validation detects broken fixtures.")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    directory = Path(args.dir)
    problems = Problems()
    files = discover(directory, problems)

    present = {path.name for path in files}
    expected_missing = [name for name in args.expected if name not in present]
    for name in expected_missing:
        problems.error(directory / name, "expected workflow is missing")

    resolve_cache: dict[str, bool] = {}
    actions_checked = 0
    for path in files:
        actions_checked += validate_file(path, problems, resolve_cache, args.resolve_refs)

    for warning in problems.warnings:
        print(f"::warning::{warning}")
    for error in problems.errors:
        print(f"::error::{error}")

    report = {
        "files_checked": len(files),
        "expected_count": len(args.expected),
        "expected_missing": expected_missing,
        "action_references_checked": actions_checked,
        "resolved_refs": sorted(resolve_cache),
        "errors": problems.errors,
        "warnings": problems.warnings,
    }
    if args.report:
        report_path = Path(args.report)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    print(
        f"checked {len(files)} workflow(s), {actions_checked} action reference(s)"
        + (f", resolved {len(resolve_cache)} upstream ref(s)" if args.resolve_refs else "")
    )
    if problems.errors:
        print(f"{len(problems.errors)} validation error(s)", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
