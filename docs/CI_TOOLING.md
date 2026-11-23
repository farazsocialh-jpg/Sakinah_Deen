# CI tooling: pinned versions

Every tool the pipeline downloads is pinned, so an unchanged commit produces the same pipeline
as yesterday. Nothing in CI resolves a floating tag (`latest`, `v4`, `brew install`) any more.

| Tool | Where | Pin | Verified by |
|---|---|---|---|
| Gitleaks | `security.yml` | image digest `zricethezav/gitleaks@sha256:c00b6bd0…` (v8.30.1) | local `detect` runs against the repository and controlled fixtures; 0 findings on a clean tree, 1 on a planted synthetic key |
| MobSFScan | `security.yml` | `mobsfscan==1.2.0` | installed locally and run over `app/src` + `shared/src` (14 findings, 1 accepted); `.github/security-scan-baseline.json` counts are from this release |
| MobSF | `ci.yml` | image digest `opensecurity/mobile-security-framework-mobsf@sha256:815bb96…` | this is the digest the previous `:latest` resolved to, so pinning changed no behaviour. The APK scan itself was not re-run locally (no Docker in the development workspace) |
| actionlint | `workflow-validation.yml` | 1.7.7, SHA-256 `023070a2…` checked with `sha256sum -c` | run locally over all workflow files, 0 findings |
| XcodeGen | `ios.yml` | 2.48.0 release archive, SHA-256 `620c9f0d…` checked with `shasum -c` | archive contents inspected (bin/xcodegen + share/xcodegen/SettingPresets) and installed with the project's own `install.sh`; **not executed on macOS from this workspace** |
| Wrangler | `worker.yml` | `npx --yes wrangler@4.166.0` | `wrangler deploy --dry-run` on `worker/` succeeded locally (16.54 KiB bundle, bindings resolved) |
| Android SDK packages | `ci.yml`, `security.yml` | `platforms;android-36 build-tools;36.0.0 platform-tools` | unchanged, already versioned |
| Gradle | `gradle/wrapper/gradle-wrapper.properties` | `gradle-9.3.1-bin.zip` via the wrapper | unchanged |
| JDK | all JVM jobs | `temurin` 17 via `setup-java` | unchanged |

GitHub Actions themselves remain referenced by major tag (`actions/checkout@v7`), which is the
convention GitHub itself recommends; `workflow-validation.yml` resolves every one of those refs
upstream on each run, so a deleted or renamed tag fails validation instead of failing silently
at execution time.

## Bumping a pin

1. Change the version/digest in the workflow.
2. Run the tool locally against this repository (the commands in
   `docs/SECURITY_SCANNING.md` for the scanners, `npx wrangler@<version> deploy --dry-run` in
   `worker/`, `actionlint` on the workflow directory).
3. If the output changes in a way that affects the baseline (`mobsfscan` rule ids or counts),
   update `.github/security-scan-baseline.json` in the same commit and say why.
4. Update the table above, including what you actually verified and what you could not.

A scanner upgrade that changes the accepted findings is a policy change: it belongs in its own
commit with the baseline diff visible.
