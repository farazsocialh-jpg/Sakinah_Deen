# Security scanning: what blocks a merge

> [!WARNING]
> **Historical document.** This page describes the retired five-workflow CI pipeline, not the
> active CI. See the [README](../README.md#checks-and-releases) for the three workflows that run
> today.

Two scanners in `.github/workflows/security.yml` produce their own findings: **mobsfscan**
(OWASP MASVS static analysis) and **Gitleaks** (committed secrets). CodeQL runs alongside them
and reports through GitHub code scanning instead of this policy.

Every scanner result passes through `.github/scripts/security-scan-gate.py`. The gate exists
because a scanner has three possible outcomes, and only two of them are findings:

| Outcome | What the gate does |
|---|---|
| Scanner ran, no findings | Pass. |
| Scanner ran, findings | Apply the policy below; exit 1 when a finding blocks. |
| Scanner did not run | Fail with **`SCANNER FAILURE — result unverified`**. Never a clean scan. |

## Why the exit code is not enough

* **Gitleaks** exits `1` when it finds a leak and `0` when it does not — but it also exits `1`
  when its configuration cannot be parsed. A nonzero exit on its own cannot tell "a secret was
  found" from "the scanner never scanned".
* **mobsfscan** exits `1` when it has findings and `0` when it has none — and it *also* exits
  `0`, writing an empty SARIF, when every path it was given does not exist. Verified with
  mobsfscan 1.2.0: scanning a nonexistent directory produced `executionSuccessful: true`,
  zero results and exit code 0.

So the gate proves the scan happened before it reads a single result:

1. the SARIF file exists, is non-empty and parses as JSON with at least one run;
2. the SARIF driver is the expected scanner;
3. every SARIF invocation is marked `executionSuccessful`;
4. every target directory exists and contains files (mobsfscan), so "zero findings" cannot
   mean "zero files scanned";
5. the workflow itself rejects an exit code above `1` as an execution failure.

`audit-report/<tool>.md` records the proof (`scanned N file(s)`) together with the findings, and
the merged `SECURITY_AUDIT.md` keeps the same text.

## Policy

### Blocking

| Scanner | Blocks the merge |
|---|---|
| Gitleaks | Every reported leak. `.gitleaks.toml` is the only allowlist. |
| mobsfscan | SARIF results with level `error` that are not accepted in `.github/security-scan-baseline.json`. |

### Advisory

| Scanner | Reported, does not block |
|---|---|
| mobsfscan | SARIF results with level `warning` or `note`, and results with no level (SARIF defines an absent level as `warning`). |
| Both | Nothing else — an advisory finding is listed in the run summary and the merged report so it stays visible. |

### Accepted findings (ratchet)

`.github/security-scan-baseline.json` lists blocking findings that have been reviewed and
accepted, per tool and rule:

```json
{ "mobsfscan": { "android_task_hijacking2": 1 } }
```

* More blocking findings of a rule than the baseline accepts **fails** the run.
* Fewer than the baseline asks you to **lower the number** (a warning, not a failure) so the
  accepted set cannot grow stale.
* `android_task_hijacking2` (StrandHogg 2.0 task hijacking) is accepted because the app is a
  single-activity launcher with no task-affinity aliases; changing the manifest to silence the
  rule is an application-level decision, not a CI decision. The finding remains in every report.

### Documented trade-off: `google-services.json`

Firebase ships `google-services.json` as *client* configuration: the GCP API key in it
identifies the project, is not a server credential and is compiled into every APK. `.gitleaks.toml`
allowlists that path inside the shared `[allowlist]` block, because Gitleaks cannot scope an
allowlist to one rule on top of `extend.useDefault`. Consequence: other rules no longer scan
that single file, so a private key pasted into it would not be caught by Gitleaks. The audit
checklist check `hardcoded_secrets` still scans application sources for key-shaped literals.

## Running the scanners locally

```bash
# Gitleaks (same version as CI; see the pinned image in security.yml)
docker run --rm -v "$PWD:/repo" \
  zricethezav/gitleaks@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f \
  detect --source=/repo --config=/repo/.gitleaks.toml \
  --report-format=sarif --report-path=/repo/audit-report/gitleaks.sarif \
  --no-banner --redact --exit-code 1
python3 .github/scripts/security-scan-gate.py --tool gitleaks \
  --sarif audit-report/gitleaks.sarif --report audit-report/gitleaks.md

# mobsfscan
python3 -m venv /tmp/mobsfscan-venv
/tmp/mobsfscan-venv/bin/pip install mobsfscan
/tmp/mobsfscan-venv/bin/mobsfscan --sarif -o audit-report/mobsfscan.sarif app/src shared/src
python3 .github/scripts/security-scan-gate.py --tool mobsfscan \
  --sarif audit-report/mobsfscan.sarif --report audit-report/mobsfscan.md \
  --target app/src --target shared/src
```

Both scanners were exercised against controlled fixtures before this policy was adopted: a
planted (synthetic, never committed) cloud key in a normal path fails the gate, the same key in
an allowlisted test path passes, an unparseable Gitleaks configuration is reported as a scanner
failure rather than a clean scan, and mobsfscan with a nonexistent target is reported as a
scanner failure.

## When a scanner fails

The job fails with `SCANNER FAILURE — result unverified`. Do not rerun until it is green and do
not lower the policy to get past it: read the step log, fix the invocation (missing image,
network, permission, unreadable report) and rerun. A red scanner is information; a green
scanner that never ran is not.

## Dependency graph (transitive CVEs)

Dependabot's Gradle ecosystem only reads the versions declared in the catalog; it cannot see
what those resolve to. Submitting the resolved graph is what makes a CVE in a transitive
artifact (OkHttp behind ktor, protobuf behind Firebase, ExoPlayer internals) raise an alert
instead of staying invisible.

**Status today: not operational.** The workflow always *generates* the snapshot (real work, uploaded
as an artifact) and only *submits* it when an administrator has enabled the feature and opted in
with the repository variable `DEPENDENCY_GRAPH_ENABLED=true`. Until a real submission succeeds,
dependency-graph coverage is missing, not passing, and the run prints
`::warning::Dependency graph submission is disabled...`.

### Exact administrator action required

1. Open the repository on GitHub: **Settings → Code security → Dependency graph** and turn it on.
2. Create the repository variable **Settings → Secrets and variables → Actions → Variables →
   New repository variable**: name `DEPENDENCY_GRAPH_ENABLED`, value `true`.
3. Re-run the `Security` workflow (or wait for the next push to `main`). The
   `dependency-submission` job will then call `gradle/actions/dependency-submission@v6` with
   `dependency-graph: generate-and-submit` and report success in the step summary.
4. Confirm the submission step actually executed and succeeded — a skipped step is not evidence
   of success. Check the run's `dependency-submission` job conclusion and the
   `### Gradle dependency graph` step summary on `main`.
5. Only after that succeeds, treat transitive CVEs as monitored.

No workflow or code change can enable the dependency graph: `contents: write` on the job is not
enough, and the Dependency Submission API answers "The Dependency graph is disabled for this
repository" until step 1 is done. Do not set `DEPENDENCY_GRAPH_ENABLED=true` before step 1.

`docs/CI_CD_FINAL_REPORT.md` tracks this as an open setting; it is not marked operational.
