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

CI is five path-filtered workflows, one responsibility each:
[`ci.yml`](.github/workflows/ci.yml) (Android build, tests, lint, device audit),
[`security.yml`](.github/workflows/security.yml) (CodeQL, mobsfscan, Gitleaks, dependency graph),
[`ios.yml`](.github/workflows/ios.yml), [`worker.yml`](.github/workflows/worker.yml) and
[`release.yml`](.github/workflows/release.yml) (the only signed AAB/APK, from a `v*` tag or manual
run). Triggers, duplicate-execution analysis and root causes: [docs/CI_CD_AUDIT.md](docs/CI_CD_AUDIT.md);
process, gates and checklists: [docs/development-sop.md](docs/development-sop.md).

## Setup

- JDK 17, Android SDK 35.
- Optionally copy `.env.example` to `.env` and set `AI_PROXY_URL` to the deployed AI Scholar proxy. There is no built-in default: without it, the AI Scholar screen shows its "not configured" state.
- Never commit keystores or API keys; signing uses repository secrets.
