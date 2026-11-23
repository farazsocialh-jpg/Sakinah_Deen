# Development SOP

The standard operating procedure for changing, auditing and releasing ImanCore. Everything that
can be automated runs in CI; the manual items below are the ones a machine cannot decide.

## 1. The pipeline

Four workflows, no overlap. Each responsibility lives in exactly one place.

| Workflow | Trigger | Owns |
| --- | --- | --- |
| [`ci.yml`](../.github/workflows/ci.yml) | push to `main`, every PR, weekly (Mon 04:00), manual | Build, unit tests, Android Lint, R8 release check, audit checklist, mobsfscan, Gitleaks, CodeQL, MobSF APK scan, instrumented tests, accessibility, UI/UX audit, merged report |
| [`release.yml`](../.github/workflows/release.yml) | `v*` tag, manual | The **only** signed AAB + APK |
| [`ios.yml`](../.github/workflows/ios.yml) | push to `main`, manual | Shared framework + iOS build and UI tests |
| [`worker.yml`](../.github/workflows/worker.yml) | push, PR, manual | AI Scholar proxy: syntax, tests, Wrangler bundle |

`ci.yml` jobs: `quality` (the merge gate) → `static-audit`, `codeql`, `apk-security` (reuses the
APK `quality` built), `device` (one emulator boot: tests, then accessibility, then UI/UX monkey)
→ `report` (one `AUDIT.md`).

### CI verification of release APK

If repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` are
configured, the new `release` job in `ci.yml` will build and signature-verify a release APK using
the real signing key. This is **not** the production release workflow — that remains `release.yml`
triggered on `v*` tags. The CI release job is for verification only.

A signed release is *never* produced on a feature or `main` push. Signing material is used by CI
only to verify that R8 can build; that keystore is ephemeral.

## 2. Changing the app

1. Branch from `main`: `feat/…`, `fix/…`, `chore/…`.
2. Run the fast checks locally before pushing:
   ```bash
   ./gradlew :shared:allTests :app:testDebugUnitTest :app:lintDebug
   python3 .github/scripts/audit-checklist.py        # add --strict to see every known gap
   ```
3. Open a PR. `ci.yml` + `worker.yml` must be green before merge; `ios.yml` runs on `main`
   (and manually) because it pins a macOS runner for the whole iOS toolchain.
4. Squash-merge to `main`. Never push release commits straight to `main`.

### Pre-PR checklist

- [ ] Shared tests and app unit tests pass locally.
- [ ] No new Android Lint error (warnings are triaged, not ignored).
- [ ] New user-visible strings exist in `values/`, `values-ar/` and `values-ur/` — not copied English
      (the checklist counts the untranslated ones against the baseline).
- [ ] New interactive elements are ≥ 48 dp and carry a `contentDescription` or visible label.
- [ ] No permission, exported component or network endpoint added without a note in the PR.
- [ ] No secret, keystore, `keystore.properties` or API key in the diff (Gitleaks enforces this).
- [ ] Room schema change comes with a migration and an `androidTest` that opens the database.

## 3. Releasing

1. Confirm the weekly CI run is green on `main`.
2. Tag and push — `release.yml` builds, signs, verifies and uploads:
   ```bash
   git tag v1.2.0 && git push origin v1.2.0
   ```
   Or Actions → **Release** → *Run workflow* with a version name.
3. Download `sakinah-release-aab-<version>` (Play), `sakinah-release-apk-<version>` (direct
   install) and `sakinah-mapping-<version>` (upload to Play for readable crash reports).
4. Register the signing SHA-1 printed in the run summary with the Google OAuth Android client if
   the keystore changed. See [release-signing.md](release-signing.md).

### Pre-release checklist

- [ ] `app/build.gradle.kts` `versionCode` increases (CI uses the run number for CI builds; the
      release workflow resolves name/code as documented in [release-signing.md](release-signing.md)).
- [ ] Audit report for the tagged commit has no **new** finding versus `.github/audit-baseline.json`.
- [ ] Data-safety answers in Play Console still match the permissions in the built APK
      (`audit-report/apk.md` lists them).
- [ ] Privacy policy URL and store listing screenshots are current.
- [ ] Rollout plan chosen (internal → closed → production), and the mapping file is uploaded.

## 4. Audit results

Every run attaches `app-audit-report/AUDIT.md` and uploads SARIF to **Security → Code scanning**.
The audit is a ratchet, not a pass/fail on the whole backlog:

- A **regression** against `.github/audit-baseline.json` fails `static-audit`.
- A **hard invariant** (missing translation file, icon without `contentDescription`, test without
  a tag, unhandled WorkManager retry) always fails.
- Known gaps are counted, reported and only fail with `--strict` (manual run).
- After fixing a gap, run `python3 .github/scripts/audit-checklist.py --update-baseline` and commit
  the lower baseline in the same PR.

Escaping either gate (adding to the baseline, lowering a threshold, disabling a rule) requires the
reason in the PR description. Silencing a check is a code change and is reviewed like one.

### Device legs

One emulator runs three phases (`.github/scripts/run-device-audit.sh`):

- **Instrumented tests** — gate the job.
- **Accessibility** — report-only: 48 dp targets and unlabelled clickables are measured and
  published (job summary, annotations, `audit-device` artifact). Once the reported violations are
  confirmed as real, run the workflow with `a11y_gate=true` to make them block.
- **UI/UX monkey** — gates on a crash or ANR; screenshots and logcat in `audit-device`.

### Deep audit checks

The checklist also covers:

| Area | Checks | Gate |
| --- | --- | --- |
| **Security/Privacy** | `backup_disabled`, `cleartext_disabled`, `network_security_config`, `data_extraction_rules`, `hardcoded_secrets`, `exported_components`, `dangerous_permissions`, `flag_secure`, `encrypted_prefs` | Block (S1/S2) or advise (S3/S4) |
| **Performance** | `baseline_profile`, `macrobenchmark`, `network_timeouts`, `http_cache`, `startup_measurement` | Advise |
| **Compatibility** | `target_sdk`, `min_sdk`, `rtl`, `optional_features`, `predictive_back`, `dark_mode`, `dpi_configuration` | Block or advise |
| **Observability** | `crash_reporting`, `analytics`, `log_stripping`, `pii_in_logs` | Block (S3) or advise |
| **Release readiness** | `signing_fail_closed`, `no_baked_config`, `account_deletion`, `store_assets`, `privacy_copy`, `release_notes_versioned` | Block (S2/S3) |
| **E2E flow** | `e2e_onboarding`, `e2e_auth`, `e2e_prayer`, `e2e_quran`, `e2e_settings`, `e2e_profile` | Advise |

### Weekly triage (15 minutes)

1. Read the Monday run's `AUDIT.md`.
2. File an issue per new finding; label `audit`.
3. Update the baseline only for findings you have accepted, with a linked issue.
4. Review Dependabot PRs (grouped: one for Gradle groups, one for actions, one for the Worker).

## 5. Changing CI itself

- One responsibility per job; do not add a third full Gradle build to a workflow without removing
  another.
- Validate before pushing: `actionlint`, `bash -n` for scripts, `python3 -m py_compile` for Python.
- Keep the device work in the single `device` job — every extra emulator costs ~10 minutes and a
  queue slot.
