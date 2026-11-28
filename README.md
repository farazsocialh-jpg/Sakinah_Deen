# ImanCore — Islamic Companion (Android + iOS)

Kotlin Multiplatform project sharing business logic and data between the
Android (Jetpack Compose) and iOS (SwiftUI) apps.

## Modules

- `:shared` — Kotlin Multiplatform module (`commonMain`, `androidMain`, `iosMain`).
  Prayer times & Qibla, Zakat & wealth models, offline seed schemas, and the
  Ktor-based AI companion client (talks to a backend proxy; no raw API key in
  binaries).
- `app/` — Android application. Jetpack Compose, Room + SQLCipher, WorkManager,
  Adhan playback service, car Bluetooth receiver, R8 minification.
- `ios/Sakinah/` — SwiftUI application consuming the `:shared` framework. The Xcode project is
  generated from `ios/project.yml` with [XcodeGen](https://github.com/yonaskolb/XcodeGen)
  (`brew install xcodegen && cd ios && xcodegen generate`); it includes the `SakinahUITests`
  UI-test target used by CI.
- `worker/` — Cloudflare Worker that proxies the AI Scholar (see `worker/README.md`).
- `docs/` — resource provenance, signing, naming, etc.

## Naming

**ImanCore** is the product name. The `Sakinah` / `sakinah_deen` identifiers are legacy codenames
retained in package names, the iOS module, the Worker and the signing identity; see
[docs/branding.md](docs/branding.md) before renaming anything.

## Build

Android:
```bash
./gradlew :app:assembleDebug
./gradlew :app:bundleRelease
```

Shared (KMP):
```bash
./gradlew :shared:build
./gradlew :shared:allTests
```

iOS (macOS only):
```bash
./gradlew :shared:embedAndSignAppleFrameworkForXcode
# then open ios/Sakinah.xcodeproj in Xcode and build
```

## Checks and releases

Three workflows are active:

- [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — pushes to `main`, pull requests to
  `main`, and manual runs. It builds/uploads the debug APK (7-day artifact), renders the
  exhaustive Roborazzi UI snapshot matrix on **JDK 21**, and runs a `unit-tests` job
  (`:shared:allTests`, `:app:testDebugUnitTest`, `npm test --prefix worker`) that needs no secrets.
- [`.github/workflows/maestro-android.yml`](.github/workflows/maestro-android.yml) — Maestro
  Android E2E on an emulator for changes under `app/`, `.maestro/` or the workflow file, on
  pushes and pull requests plus manual runs. Reports and screenshots are uploaded for 7 days.
- [`.github/workflows/arbigent-ai-qa.yml`](.github/workflows/arbigent-ai-qa.yml) — **manual only**;
  the AI UX agent runs 11 scenarios (each run spends free-tier AI quota), with the report
  uploaded for 14 days.

**Not in CI:** the full test suites beyond the JVM unit tests and the two E2E flows above
(instrumented/device-audit tests), lint, MobSF/security scanning, iOS builds and tests, the
Worker deployment pipeline, and release signing. Run those locally as needed; see
[docs/development-sop.md](docs/development-sop.md).

The retired five-workflow pipeline and its audit are kept for historical context in
[docs/CI_CD_AUDIT.md](docs/CI_CD_AUDIT.md) and [docs/CI_CD_FINAL_REPORT.md](docs/CI_CD_FINAL_REPORT.md).

## Setup

- JDK 17, Android SDK 36 (the UI snapshot job in CI uses JDK 21).
- Optionally copy `.env.example` to `.env` and set `AI_PROXY_URL` to the deployed AI Scholar proxy. There is no built-in default: without it, the AI Scholar screen shows its "not configured" state.
- Never commit keystores or API keys; signing uses repository secrets.
