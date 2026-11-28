# CI/CD Audit — ImanCore (Sakinah_Deen)

> [!WARNING]
> **Historical document.** This page describes the retired five-workflow CI pipeline, not the
> active CI. See the [README](../README.md#checks-and-releases) for the three workflows that run
> today.

Audit of every GitHub Actions workflow in the repository, the triggers that cause duplicate
executions, the failure modes reported against this project, and the root cause of each.

Scope: `.github/workflows/`, `.github/scripts/`, `.github/dependabot.yml`, `.gitleaks.toml`,
`gradle/`, `app/build.gradle.kts`, `shared/build.gradle.kts`, `ios/`, `worker/`.

## 0. Evidence and limits of this audit

| Item | Status |
| --- | --- |
| Workflow / script / Gradle / iOS / Worker source inspection | Done — every file read in full |
| Workflow syntax (`actionlint` 1.7.7) | Done — 0 findings on the pre-change files |
| Shell scripts (`bash -n`) | Done — 3/3 clean |
| Python scripts (`py_compile`) | Done — 4/4 clean |
| Action tag existence (`git ls-remote`) | Done — every pinned major exists |
| Recent GitHub Actions run history and full logs | **Not available** — this workspace has no GitHub token (`gh auth status`: *not logged into any GitHub hosts*), and `api.github.com` is rate-limited for the shared egress IP. Failure triage below is therefore derived from the code that produces the failure, not from a re-read of the run log |
| Local Gradle build, Android SDK, emulator | **Not possible in this workspace** — no JDK and no Android SDK are installed, and no root to install them |
| iOS / Xcode build | **Not possible** — Linux host, no macOS runner |

Anything below marked **confirmed** is confirmed by reading the code path that produces the
error. Anything marked **not reproducible here** needs a GitHub run to confirm end-to-end.

## 1. Workflow inventory

| Workflow | Trigger | Jobs | Responsibilities | Overlap | Problems |
| --- | --- | --- | --- | --- | --- |
| `ci.yml` | `push` → `main`; every `pull_request`; weekly cron (Mon 04:00); `workflow_dispatch` | `quality`, `static-audit`, `codeql`, `dependency-submission`, `apk-security`, `device`, `report`, `release` (verification only) | Android build/unit tests/lint/R8, audit checklist, mobsfscan, Gitleaks, CodeQL, dependency graph, MobSF APK scan, emulator (instrumented + a11y + UI/UX monkey), merged `AUDIT.md`, signed-APK verification | Builds the app 3× (`quality`, `codeql`, `dependency-submission` configure); `:shared:build` also runs in `ios.yml` | No path filters at all → a docs-only or Worker-only change runs a 75-minute emulator; a scheduled security run on `main` is cancelled by any later push (`cancel-in-progress: true`); depends on a repository feature that is disabled (dependency graph) |
| `ios.yml` | `push` → `main`; `workflow_dispatch` | `build-shared-and-ios` | Shared framework, XcodeGen project, simulator app build, `SakinahUITests` | `:shared:build`/`:shared:allTests` duplicated with `ci.yml` (different OS, genuine Kotlin/Native compile) | Runs on **every** main push (Android-only, Worker-only, docs-only) on a macOS runner; **no `pull_request` trigger**, so an iOS compile break is only discovered after merge; no path filters; same `cancel-in-progress: true` problem |
| `release.yml` | `push` → tags `v*`; `workflow_dispatch` | `release-bundle` | Signed AAB + APK, signature verification, artifacts, R8 mapping | Runs `:shared:allTests`, `:app:testDebugUnitTest`, `:app:lintDebug` again after CI already did | Correctly `cancel-in-progress: false`; `jarsigner -verify` only proves *some* signature exists — it does not prove the **production** key signed the bundle; no version-name/code assertion against the built artifact |
| `worker.yml` | `push` (all branches); every `pull_request`; `workflow_dispatch` | `test` | `node --check`, `node --test`, `wrangler deploy --dry-run` | None | `push` with no branch filter **and** `pull_request` → a branch push that has a PR runs the identical job twice; runs on every Android/docs change |

Supporting (non-workflow) configuration:

| File | Purpose | Problem |
| --- | --- | --- |
| `.github/dependabot.yml` | 3 ecosystems (gradle, github-actions, npm/worker), grouped, with documented pins | Every Dependabot PR triggers the full `ci.yml` including `device`; the Worker ecosystem has no group (single dependency today, cosmetic) |
| `.github/scripts/audit-checklist.py` (47 KB) | Embedded audit ratchet vs `.github/audit-baseline.json` | Runs only inside `static-audit`; fine |
| `.github/scripts/run-device-audit.sh` | One emulator boot, three legs | Failure classification is coarse (see §4.F) |
| `.gitleaks.toml` | Secret scanning config + allowlist | Allowlists `app/src/test`, `docs`, `ios/*Tests` — intentional |
| `gradle.properties` | `org.gradle.caching=true` | This is the direct cause of the CodeQL "no source seen" failure (§4.B) |
| Repository settings | Dependency graph, CodeQL default setup, branch protection, `Playstore` environment | Dependency graph is **disabled** (§4.A); requires an admin |

## 2. Why a single push runs multiple workflows

1. **`cancel-in-progress: true` + `github.ref` groups.** `ci-`, `ios-` and `worker-` groups are
   distinct, so they never cancel each other — but each cancels *its own* earlier run, including
   the weekly scheduled security run on `main`. A `main` push at 04:01 discards the Monday audit.
2. **`worker.yml` has both `push` (no branch filter) and `pull_request`.** Pushing a commit to a
   branch that has an open PR produces two identical Worker runs for the same SHA. This is the
   only *true* duplicate execution in the repository.
3. **No path filters anywhere.** Editing `docs/` or `worker/` schedules: Android `quality`
   (45 min), `codeql` (60 min), `device` (75 min), `apk-security`, `ios` (macOS), `worker`.
   Cost is not proportional to the change.
4. **PR run + post-merge `main` run.** A PR runs the whole pipeline; the squash-merge then runs
   it again on `main` for the same content. This is normal for gate-on-PR pipelines, but the
   expensive `device` leg is repeated for content that was already validated. Reducing it further
   would require either dropping the post-merge run (loses the `main`-branch baseline used by the
   audit ratchet and the weekly drift check) or reusing artifacts across workflows (fragile).
   It is kept and documented rather than removed.
5. **Every workflow edit runs every workflow**, because `.github/workflows/**` is inside every
   workflow's default path set. That is intended (CI changes must be validated) and is kept.
6. **Dependabot volume.** Gradle, actions and Worker bumps are grouped, so at most one PR per
   ecosystem per week. Each still runs the full pipeline; `device` (75 min) dominates. Grouping is
   correct; no further reduction without skipping real coverage.
7. **`workflow_dispatch`** is configured correctly in all four workflows (typed inputs in
   `ci.yml`, free-text version in `release.yml`). No fixes needed.

### Per-change trigger matrix (target behaviour, implemented in Phase 2)

| Change | Android CI | Security | iOS | Worker |
| --- | --- | --- | --- | --- |
| `app/**`, Android resources | Yes | Yes | No | No |
| `shared/**`, `gradle/**`, `*.gradle.kts`, `gradle.properties` | Yes | Yes | Yes | No |
| `ios/**` | No | Yes | Yes | No |
| `worker/**` | No | Yes (secret scan) | No | Yes |
| `docs/**`, `**.md` | No | No | No | No |
| `v*` tag | (via release) | (via release) | Yes | — |

## 3. Confirmed defects found while auditing (static)

| # | Defect | Where | Effect |
| --- | --- | --- | --- |
| 1 | `AccountBridge()` is not a valid Swift initializer | `shared/src/iosMain/.../AccountBridge.kt`, `ios/Sakinah/AccountStore.swift:19` | iOS app target does not compile |
| 2 | Kotlin `suspend` functions are called with `await` but without `try` | `ios/Sakinah/AccountStore.swift` (13 call sites) | iOS app target does not compile |
| 3 | No path filters | all workflows | 75-minute emulator + macOS runner on unrelated changes |
| 4 | `worker.yml` double trigger | `.github/workflows/worker.yml` | identical job twice per PR commit |
| 5 | Gradle build cache is warm during the traced CodeQL build | `ci.yml` `codeql`, `gradle.properties` | CodeQL extracts nothing |
| 6 | Dependency-graph submission has no unsupported-feature path | `ci.yml` `dependency-submission` | job fails on a repository setting a contributor cannot change |
| 7 | `apksigner` is invoked from `PATH` with no build-tools path | `ci.yml` `release` job | tool not found on a clean runner |
| 8 | `release.yml` verifies that a signature exists, not *which* key signed it | `release.yml` | a debug/CI key would pass |
| 9 | Device script cannot distinguish compile / APK / adb / install / test / crash | `.github/scripts/run-device-audit.sh` | a compile error is reported as "APK not found" |

## 4. Reported failure history — root cause per item

### A. Dependency submission — "The Dependency graph is disabled for this repository."

*Status: confirmed by configuration; not fixable in code.*

`gradle/actions/dependency-submission@v6` defaults to `generate-and-submit` and calls the
Dependency Submission API. That API returns this error when **Settings → Code security →
Dependency graph** is off for the repository. No workflow permission can turn it on; it is an
admin setting (and, on some plans, an organisation policy).

Root cause: the job has no path for "feature unavailable", so a repository setting a contributor
cannot change turns the whole job red.

Fix (Phase 3): always **generate** the snapshot (real work, always possible, uploaded as an
artifact), and **submit** only when the operator opts in with the repository variable
`DEPENDENCY_GRAPH_ENABLED=true` after enabling the graph. The job then reports
*"graph generated, submission disabled"* instead of pretending a scan passed or failing CI.

### B. CodeQL — "No source code seen during build."

*Status: confirmed by configuration; needs one green run to close.*

`ci.yml` uses `build-mode: manual` (correct for Gradle/Android) and runs

```
./gradlew :app:assembleDebug :shared:build --stacktrace --no-daemon
```

after `codeql-action/init`. The Java/Kotlin extractor instruments `javac`/`kotlinc` and records
what those compilers actually compile. Three things in the current workflow defeat that:

1. `gradle.properties` sets `org.gradle.caching=true`, and `actions/setup-java` with
   `cache: gradle` restores `~/.gradle/caches` — which **includes `build-cache-1`**. The first
   traced task in the project (`:shared`, `:app`) is restored `FROM-CACHE`, so no compiler runs.
2. `--no-build-cache` / `-Dorg.gradle.caching=false` is not passed on the traced build.
3. Nothing asserts that compilation actually happened before `analyze`, so the failure surfaces
   as the opaque "No source code seen during build" instead of "the build did not compile".

Root cause: **the traced build is a cache hit, not a compilation.**

Fix (Phase 3): traced build with `--no-build-cache -Dorg.gradle.caching=false`, a post-build
assertion that the debug APK exists (proof the Kotlin/Java compilers ran), and no other change to
the analysis. CodeQL is not disabled, downgraded or made advisory.

Secondary hazard, kept as is: `:shared:build` on Linux logs
*"The following Kotlin/Native targets cannot be built on this machine and are disabled:
iosArm64, iosSimulatorArm64"* and continues — expected, not a failure.

### C. iOS — "AccountBridge() initializer unavailable"

*Status: confirmed by source inspection.*

```
shared/src/iosMain/.../AccountBridge.kt
class AccountBridge(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
)
```

Kotlin default parameter values are **not** exported to Objective-C/Swift. The framework header
declares exactly one initializer, `-initWithScope:`. Swift therefore sees `init(scope:)` and the
call `AccountBridge()` in `AccountStore.swift:19` fails with *"initializer unavailable"*.

The correct design is a real, exported no-argument initializer — not a fake class, not a
duplicate bridge:

```kotlin
class AccountBridge(private val scope: CoroutineScope) {
    constructor() : this(CoroutineScope(SupervisorJob() + Dispatchers.Main))
}
```

which exports both `init()` and `initWithScope:`.

**Second, independent defect found in the same file:** every `suspend fun` is exported with a
completion handler that carries `NSError`, so Swift imports them as `async throws`. `AccountStore`
calls all 13 of them with `await` and no `try` → *"call can throw, but it is not marked with
'try'"*. Fixing only the initializer would still leave the target uncompilable.

Consequence of the two: `ios.yml` fails at *"Build the iOS app (simulator)"*; the UI tests and
their artifact are never reached.

Fix (Phase 4): the exported no-arg constructor plus `try await` at each call site (errors are
already surfaced through the existing `finish(_:)` helper and the `error:` state string).

### D. Kotlin serialization ABI — Kotlin 2.3.20 vs a 2.4.10-compiled artifact

*Status: already resolved in the current tree.*

`gradle/libs.versions.toml` pins `kotlin = "2.3.20"` and `kotlinxSerialization = "1.9.0"`, and
`.github/dependabot.yml` blocks `org.jetbrains.kotlinx:kotlinx-serialization-json >= 1.12.0`
because 1.12.0+ is published with Kotlin 2.4.10 (klib ABI 2.4.0), which a 2.3.20 compiler cannot
read. Room 2.8's migration bundle needs ≥ 1.8, so 1.9.0 satisfies both constraints.

No change is made. The constraint must only be lifted together with Kotlin, KSP and the Compose
compiler plugin — that is a planned upgrade (documented in the catalog and in `dependabot.yml`).

### E. Kotlin `commonMain` — "Unresolved reference 'System'"

*Status: already resolved in the current tree.*

A search of `shared/src` finds no `java.*` or `System.*` reference in `commonMain`. The only
`System` token is `Clock.System` in `shared/src/commonMain/.../UserProfile.kt`, which is
`kotlinx.datetime.Clock` — a real `commonMain` API. Date handling uses
`kotlinx.datetime.LocalDate`/`TimeZone`. No `expect/actual` is needed.

The one related risk is pinned: `kotlinx-datetime` 0.7.x removes `Clock` in favour of
`kotlin.time.Clock`, which is why Dependabot blocks `>= 0.7.0`. A migration, not a version bump.

### F. Android emulator — "APK not found at app/build/outputs/apk/debug/app-debug.apk"

*Status: confirmed as a downstream symptom, not an emulator problem.*

`run-device-audit.sh` runs `:app:connectedDebugAndroidTest` first (which builds the app and test
APKs), then calls `run-a11y-audit.sh`, which reports `::error::APK not found` when the path is
absent. If the Gradle build fell over — a Kotlin error, an asset download, a missing SDK package
— the APK never exists, and the failure surfaces as an emulator/APK error. `run-uiux-audit.sh`
then `adb install`s the same missing path under `set -e`.

So: the message is a **consequence of a compile failure (or of a failed APK-producing step)**, and
the workflow has no way to say so.

Fix (Phase 5): explicit staged device script — adb/emulator readiness → compile (with its own
log) → APK presence → install → instrumentation → accessibility → UI/UX — each stage emitting a
titled `::error::` annotation and a `device-report/status.md`, with `adb logcat -b crash` and
emulator properties captured on failure. Compilation failure is reported as
`ANDROID_COMPILATION_FAILED`, never as a missing APK.

## 5. Priority, risk and files to modify

| Priority | Item | Risk of the fix | Files |
| --- | --- | --- | --- |
| P0 | iOS app does not compile (C) | Low — 2 files, no API change | `shared/src/iosMain/.../AccountBridge.kt`, `ios/Sakinah/AccountStore.swift` |
| P0 | CodeQL extracts nothing (B) | Low — build flags only | `.github/workflows/ci.yml` → `security.yml` |
| P1 | Duplicate / irrelevant executions (§2) | Medium — triggers change what runs where | all four workflows + new `security.yml` |
| P1 | Dependency graph job always red (A) | Low — opt-in variable | `security.yml` |
| P1 | Device failure classification (F) | Medium — CI script rewrite | `.github/scripts/run-device-audit.sh` |
| P2 | Release signing identity not verified (§3.7/3.8) | Low | `ci.yml`, `release.yml` |
| P2 | Weekly audit cancelled on `main` (§2.1) | Low | concurrency expressions |
| — | Serialization ABI (D), `commonMain System` (E) | none — already correct | none |

## 6. Test-coverage gaps (documented, deliberately not changed)

These are real but out of scope for a CI repair; changing them would mean application changes:

- No `macrobenchmark` module or baseline profile (the audit checklist flags this as advisory).
- Compose has `androidTest` coverage but no screenshot/golden tests.
- `worker/` is covered by `node:test` only; no HTTP contract test against a recorded upstream.
- `ios/SakinahUITests` covers signed-out navigation only — by design, so it never touches
  account, payment or Zakat flows.

## 7. Required manual repository settings

These cannot be set from a workflow and must be done by a repository admin:

1. **Settings → Code security → Dependency graph: enable**, then add the repository variable
   `DEPENDENCY_GRAPH_ENABLED=true` so the submission job actually submits.
2. If the jobs that gain path filters are configured as **required status checks**, note that a
   path-skipped workflow reports *no* status and the required check stays *Pending*. Either keep
   those jobs out of the required list, or keep a single always-running required check.
3. Keep the `Playstore` environment's protection rules and its four signing secrets as they are
   (`docs/release-signing.md`).
