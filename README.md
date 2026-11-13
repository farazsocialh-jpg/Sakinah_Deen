# Sakinah — Sunni Islamic Companion (Android + iOS)

Kotlin Multiplatform project sharing business logic and data between the
Android (Jetpack Compose) and iOS (SwiftUI) apps.

## Modules

- `:shared` — Kotlin Multiplatform module (`commonMain`, `androidMain`, `iosMain`).
  Prayer times & Qibla, Zakat & wealth models, offline seed schemas, and the
  Ktor-based AI companion client (talks to a backend proxy; no raw API key in
  binaries).
- `app/` — Android application. Jetpack Compose, Room + SQLCipher, WorkManager,
  Adhan playback service, car Bluetooth receiver, R8 minification.
- `ios/Sakinah/` — SwiftUI application consuming the `:shared` framework.
- `docs/` — resource provenance, signing, etc.

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

## Setup

- JDK 17, Android SDK 35.
- Optionally copy `.env.example` to `.env` and set `AI_PROXY_URL` for the online AI Scholar proxy. Without it, the app falls back to built-in grounded answers.
- Never commit keystores or API keys; signing uses repository secrets.
