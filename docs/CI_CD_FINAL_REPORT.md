# CI/CD Remediation — Final Report

Result of the seven-phase remediation of the ImanCore GitHub Actions setup, following the audit
in [CI_CD_AUDIT.md](CI_CD_AUDIT.md).

Branch: `freebuff/77c019a927e2a743e48bc353` (not `main`; see §9).

## 1. Before / after architecture

### Before

```
push:main ─┐
PR (any)  ─┼─► ci.yml ── quality → apk-security, device(75m), report, release-verify
           │            ├─ static-audit
           │            ├─ codeql
           │            └─ dependency-submission  (always red: graph disabled)
           ├─► worker.yml           (push:all-branches + PR → same commit ran twice)
           └─► ios.yml              (push:main only → every merge, incl. Android/docs merges)
tag v*    ───► release.yml
```

### After

```
Android/shared/Gradle/CI change ─► ci.yml       quality → apk-security, device, report, release-verify
any code change                 ─► security.yml static-audit, codeql, dependency-submission → report
iOS/shared/Gradle change, tag   ─► ios.yml      shared framework → XcodeGen → app build → UI tests
worker change                   ─► worker.yml   syntax → tests → wrangler dry-run
tag v*                          ─► release.yml  AAB + APK, signer identity + version verified
weekly                          ─► security.yml (never cancelled by a push)
```

Five workflows, one responsibility each, every one path-filtered to the changes that can break
it. Trigger matrix: [CI_CD_AUDIT.md §2](CI_CD_AUDIT.md).

## 2. Modified files

| File | Change |
| --- | --- |
| `.github/workflows/ci.yml` | Renamed to *Android CI*; path filters; security jobs moved out; per-job permissions; `cancel-in-progress` only for PRs; unit-test-ran assertion + test-report artifact; `apksigner` resolved from build-tools and signer certificate matched against the keystore |
| `.github/workflows/security.yml` | **New** — `static-audit` (checklist, mobsfscan, Gitleaks), `codeql`, `dependency-submission`, merged `SECURITY_AUDIT.md`; broad path filters; weekly cron moved here |
| `.github/workflows/ios.yml` | `pull_request` + `v*` tag triggers; path filters; PR-only cancellation; build and test split; `pipefail` + `xcodebuild.log` artifact |
| `.github/workflows/worker.yml` | `push` limited to `main` (removes the double run); path filters; conditional `npm ci`; test file syntax check |
| `.github/workflows/release.yml` | Signer identity checked against the production keystore; APK `versionName`/`versionCode` asserted against the resolved version; AAB verification requires "jar verified"; step summary records the verified artifact |
| `.github/scripts/run-device-audit.sh` | Rewritten: emulator/adb → compile → APK presence → install → instrumentation → accessibility → UI/UX, each with its own error code, `device-report/status.md`, `failed-stages.txt` and logcat capture |
| `.github/scripts/merge-audit-report.py` | `--workflow` option so each workflow's merged report names its own source |
| `shared/src/iosMain/.../AccountBridge.kt` | Explicit no-argument secondary constructor (exported as `init()`); primary takes the scope |
| `ios/Sakinah/AccountStore.swift` | `try await` on all 13 Kotlin `suspend` calls |
| `README.md`, `docs/development-sop.md` | Workflow table, triggers and PR gate updated to the five-workflow layout |
| `docs/CI_CD_AUDIT.md`, `docs/CI_CD_FINAL_REPORT.md` | Audit and this report |

No application UI, business logic, database, dependency version, API or crypto code was changed.

## 3. Root causes fixed

| # | Root cause | Fix | Evidence |
| --- | --- | --- | --- |
| 1 | Kotlin default parameter values are not exported to Objective-C, so the framework declared only `init(scope:)` and `AccountBridge()` did not exist in Swift | Explicit secondary constructor exporting `init()` | Source-level: Kotlin/Native export rules; change is 1 constructor |
| 2 | Kotlin `suspend` functions are imported by Swift as `async throws`; `AccountStore` called them without `try` | `try await` at all 13 call sites | Same file, no signature change |
| 3 | The traced CodeQL build was served from the Gradle build cache (`org.gradle.caching=true` + restored `~/.gradle/caches`), so no compiler ran and the extractor saw no source | `--no-build-cache -Dorg.gradle.caching=false` on the traced build + assertion that the APK and classes exist | Flags + guard step |
| 4 | Dependency submission hard-failed on a repository setting ("The Dependency graph is disabled") that no workflow permission can change | Graph always generated (artifact); submission only with `vars.DEPENDENCY_GRAPH_ENABLED=true`; the skip is reported as skipped, with a `::warning::` | Two conditional steps + status step |
| 5 | `worker.yml` ran on `push` (all branches) **and** `pull_request` | `push` limited to `main` | Trigger diff |
| 6 | No path filters: a docs change scheduled the 75-minute emulator and a macOS runner | Path filters on all five workflows | Trigger diff |
| 7 | The scheduled weekly security run on `main` was cancelled by the next push | `cancel-in-progress: ${{ github.event_name == 'pull_request' }}` | Concurrency diff |
| 8 | A compile error surfaced as "APK not found" | Staged device script with `ANDROID_COMPILATION_FAILED`, `APK_NOT_PRODUCED`, `EMULATOR_UNREACHABLE`, `APK_INSTALL_FAILED`, `INSTRUMENTATION_TESTS_FAILED`, `INSTRUMENTATION_CRASH`, `UIUX_CRASH_OR_ANR` | Script |
| 9 | `apksigner` was invoked from `PATH` with no build-tools path | Resolved with `find "$ANDROID_HOME/build-tools"` | Both signing jobs |
| 10 | A valid signature did not prove the *production* key signed the artifact | Certificate SHA-256 compared with the keystore entry; version asserted from the APK badging | `ci.yml` + `release.yml` |
| 11 | A green test task could have run zero tests | Result XML counted; the job fails when no test case exists | `ci.yml` |

## 4. Root causes already resolved before this work (verified, left alone)

- **Kotlin serialization ABI.** `kotlin = 2.3.20` with `kotlinxSerialization = 1.9.0`;
  `dependabot.yml` blocks `>= 1.12.0` because those klibs are built with Kotlin 2.4.10 and a
  2.3.20 compiler cannot read ABI 2.4.0. Room 2.8's migration bundle needs ≥ 1.8, so 1.9.0
  satisfies both. Verified by reading the catalog and the Dependabot ignore rules.
- **`commonMain` "Unresolved reference 'System'".** No `java.*`/`System.*` reference exists in
  `commonMain`; the only `System` token is `kotlinx.datetime.Clock.System`, a real common API.
  `kotlinx-datetime` is pinned below 0.7.0 for the same reason (0.7 drops `Clock`).
- **Dependabot volume.** Grouped to ≤ 1 PR per ecosystem per week; unchanged.

## 5. Root causes not fixed (and why)

| Item | Why not fixed | What is required |
| --- | --- | --- |
| Dependency graph is **disabled** on the repository | A repository setting, not code. Turned into an explicit, reported skip instead of a red job | Admin: Settings → Code security → Dependency graph → enable, then set variable `DEPENDENCY_GRAPH_ENABLED=true` |
| Path-filtered workflows report **no status** when skipped | GitHub behaviour. If these jobs are required checks, a docs-only PR would wait forever on a check that never runs | If `CI`/`Security` are required checks, either keep them out of the required list or add an always-running gate job |
| `device` re-run on the post-merge `main` push | Removing it would drop the `main` baseline the audit ratchet and the weekly drift check compare against | Decision, not a defect |
| No crash reporting / macrobenchmark module | Application scope, explicitly out of bounds for a CI repair. The audit checklist reports both every run | Product work (`macrobenchmark_deep`, `crash_reporting_deep`) |
| Dependabot PRs still run the full Android + device pipeline | Dropping them would skip real coverage on the change most likely to break compilation | Decision |
| MobSF `opensecurity/mobile-security-framework-mobsf:latest` is unpinned | Pinning was not requested and would need a digest choice; the scan is advisory-gated by `mobsf_gate` | Optional follow-up |

## 6. Execution reduction

Jobs scheduled per change, and the sum of the configured job timeouts as an upper bound
(**not** measured durations — see §7 for why run history was unavailable):

| Change | Before (jobs / timeout-min) | After (jobs / timeout-min) | Effect |
| --- | --- | --- | --- |
| docs-only PR | 8 / 270 | **0 / 0** | no runner at all |
| docs-only push to `main` | 9 / 330 | **0 / 0** | no runner, no macOS slot |
| Worker-only PR | 8 / 270 | 5 / 120 | no Android build, no emulator, no macOS |
| Worker-only push to `main` | 9 / 330 | 5 / 120 | one Worker run instead of two + no Android/macOS |
| iOS-only PR | 8 / 270 | 6 / 165 | no Android build or emulator |
| Android-only push to `main` | 9 / 330 | 8 / 265 | Worker and iOS no longer run |
| Shared KMP PR | 8 / 270 | 9 / 325 | **up** — iOS now validates shared changes on the PR instead of after merge (deliberate: this is the gap that let the iOS break reach `main`) |

The one true duplicate execution — `worker.yml` running twice for the same commit because both
`push` (all branches) and `pull_request` matched — is gone. The weekly security run is no longer
cancelled by the next push.

## 7. Validation actually performed

| Check | Result |
| --- | --- |
| `actionlint` 1.7.7 on all five workflows | **0 findings** |
| YAML parse of all five workflows | Pass |
| `bash -n` on all three `.github/scripts/*.sh` | Pass |
| `python3 -m py_compile` on all `.github/scripts/*.py` | Pass |
| Worker test suite (`cd worker && npm test`, Node 26) | **23/23 pass** |
| Embedded audit checklist (`python3 .github/scripts/audit-checklist.py`) | **pass** (exit 0; two tracked advisory gaps: no macrobenchmark module, no crash-reporting SDK) |
| Action tag existence for every `uses:` | Verified with `git ls-remote` |
| Gradle / Android build, unit tests, lint, R8 | **Not run** — this workspace has no JDK and no Android SDK, and no root to install them |
| iOS framework, Xcode project, simulator build, UI tests | **Not run** — Linux host, no macOS/Xcode |
| CodeQL analysis, MobSF, mobsfscan, Gitleaks | **Not run locally** — CodeQL needs the traced Gradle build; MobSF/mobsfscan need Docker/pip plus the APK |
| GitHub Actions results after pushing | **Not inspected** — no GitHub token in this workspace (`gh auth status` reports no login) and `api.github.com` is rate-limited for the shared egress IP |

Consequences, stated plainly:

- The two iOS fixes are reasoned from Kotlin/Native's export rules, not from a compiler run. The
  first CI run of `iOS` on this branch is the real verification.
- The CodeQL cache root cause is the strongest available explanation for "No source code seen
  during build": with a cold build cache the compilers must run, and the new guard step fails
  loudly if they do not. If a run still reports no source, the guard will show compilation
  succeeded, which isolates the problem to the extractor rather than the build.
- Nothing here claims a workflow run passed. The security scans are unchanged in substance; this
  change only makes their invocation correct and their skips honest.

## 8. Remaining warnings and risks

- **`apk-security` and `device` remain long jobs.** They are real coverage (MobSF on the release
  APK, instrumented tests, accessibility measurement, crash monkey) and were not shortened.
- **Accessibility stays report-only** unless the workflow is dispatched with `a11y_gate=true`.
  Intentional: the measurement is noisy until the confirmed violations are triaged.
- **MobSF and mobsfscan findings do not fail the build**; the audit checklist (a ratchet against
  `.github/audit-baseline.json`) is the merge gate. Unchanged behaviour, by design.
- **`brew install xcodegen` is unpinned**, so a new XcodeGen release can change the generated
  project. Pre-existing.
- **`macos-latest` moves.** A new macOS/Xcode image can break the iOS build with no code change;
  the UI tests are the canary. Pre-existing.
- **The iOS fixes are uncompiled** (§7) — treat the first `iOS` run as a validation, not a
  formality.
- **Fork PRs** get `GITHUB_TOKEN` read-only; `dependency-submission` is skipped there by design,
  and SARIF uploads from forks may be rejected (`continue-on-error` keeps that from failing the
  run, unchanged).

## 9. Branch, commits and runs

Commits on `freebuff/77c019a927e2a743e48bc353` (one per phase, in order):

| Phase | Commit |
| --- | --- |
| 1 | `docs(ci): add comprehensive CI/CD audit` |
| 2 | `ci: optimize workflow triggers and execution architecture` |
| 3 | `ci(security): fix CodeQL and dependency graph integration` |
| 4 | `fix(kmp): repair shared module and iOS interoperability` |
| 5 | `ci(android): improve build and emulator diagnostics` |
| 6 | `ci(release): strengthen release verification and signing` |
| 7 | `docs(ci): document final CI/CD remediation and validation` |

The branch was pushed to `origin/freebuff/77c019a927e2a743e48bc353`; open a pull request from it
rather than pushing to `main` directly. Because the working copy has no GitHub credentials, the
Actions runs for these commits were **not** inspected; the first run is the end-to-end validation
of the workflow changes (see §7).

## 10. Required manual repository settings

1. **Dependency graph**: Settings → Code security → Dependency graph → enable. Then add repository
   variable `DEPENDENCY_GRAPH_ENABLED` = `true` so `dependency-submission` submits instead of only
   generating.
2. **Required status checks**: if the workflow jobs are required, remove the path-filtered ones
   from the required list (a skipped workflow reports no status and the check stays *Pending*),
   or add a single always-running gate job.
3. **Code scanning**: confirm CodeQL is *not* also enabled as a "default setup" for the
   repository — default setup and an advanced (`codeql.yml`-style) workflow cannot run together;
   this repository uses the advanced workflow inside `security.yml`.
4. Keep the `Playstore` environment, its protection rules and the four signing secrets
   (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) as documented in
   [release-signing.md](release-signing.md).
