# Release signing & Play-ready builds

Sakinah builds a signed **AAB** (Android App Bundle) in CI from repository secrets.
No keystore, password, or key is ever committed to this repository.

## 1. Create the upload keystore (once)

Run this on your own machine and keep the file plus passwords somewhere safe.
Losing the upload key means you must request a key reset from Google Play.

```bash
mkdir -p keystore
keytool -genkeypair -v \
  -keystore keystore/sakinah_deen_aqmirza.jks \
  -alias sakinah_deen_aqmirza \
  -storepass sakinah_deen_aqmirza \
  -keypass sakinah_deen_aqmirza \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype JKS
```

Recommended: use an **upload key** (Play App Signing holds the real app signing key).
`-validity 10000` comfortably exceeds Play's 25-year requirement.

## 2. Add the repository secrets

GitHub → your repository → **Settings → Secrets and variables → Actions → Repository secrets → New repository secret**.
Create exactly these (under **Repository secrets**, not Environment secrets):

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | Base64 of `keystore/sakinah_deen_aqmirza.jks` (see below) |
| `KEYSTORE_PASSWORD` | `sakinah_deen_aqmirza` |
| `KEY_ALIAS` | `sakinah_deen_aqmirza` |
| `KEY_PASSWORD` | `sakinah_deen_aqmirza` |
| `AI_PROXY_URL` | Optional — backend proxy for the AI Scholar (raw Gemini key stays server-side) |
| `NISAB_RATE_URL` | Optional — metal-price endpoint for live nisab rates |

Encode the keystore:

```bash
# macOS
base64 -i keystore/sakinah_deen_aqmirza.jks | tr -d '\n'
# Linux
base64 -w0 keystore/sakinah_deen_aqmirza.jks
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore\sakinah_deen_aqmirza.jks"))
```

Or with the GitHub CLI:

```bash
base64 -w0 keystore/sakinah_deen_aqmirza.jks > keystore/sakinah_deen_aqmirza.b64
gh secret set KEYSTORE_BASE64 < keystore/sakinah_deen_aqmirza.b64
gh secret set KEYSTORE_PASSWORD --body "sakinah_deen_aqmirza"
gh secret set KEY_ALIAS --body "sakinah_deen_aqmirza"
gh secret set KEY_PASSWORD --body "sakinah_deen_aqmirza"
```

> A leaked `KEYSTORE_BASE64` plus passwords lets someone publish as you. Treat them
> as production credentials, and rotate with a Play upload-key reset if exposed.

## 3. Run the release build

The workflows are `.github/workflows/release.yml` (AAB + APK) and `.github/workflows/signed-release-apk.yml` (signed APK), and support two triggers:

- **Tag push** — build a versioned release:
  ```bash
  git tag v1.2.0
  git push origin v1.2.0
  ```
- **Manual** — Actions → *Release* (or *Signed Release APK*) → *Run workflow*. Optionally pass a version name;
  otherwise the tag (minus `v`) or `1.0.0` is used. `versionCode` comes from the
  workflow run number, so it always increases.

The job fails immediately with a clear `::error::` annotation if any signing secret
is missing, then verifies the produced AAB and APK signatures with `jarsigner` and
`apksigner` before uploading.

Artifacts: `sakinah-release-aab-<version>`, `sakinah-release-apk-<version>`, and
`sakinah-mapping-<version>` (R8 `mapping.txt`; upload it to Play for readable
crash reports).

## 4. Build a signed release locally (optional)

`app/build.gradle.kts` reads credentials from environment variables or `keystore.properties` (git-ignored).
Environment variables win when both are present.

```bash
cp keystore.properties.example keystore.properties   # already prefilled with sakinah_deen_aqmirza
export VERSION_NAME=1.2.0 VERSION_CODE=42
./gradlew :app:bundleRelease :app:assembleRelease
```

Or entirely from the environment:

```bash
export KEYSTORE_PATH="$PWD/keystore/sakinah_deen_aqmirza.jks"
export KEYSTORE_PASSWORD=sakinah_deen_aqmirza
export KEY_ALIAS=sakinah_deen_aqmirza
export KEY_PASSWORD=sakinah_deen_aqmirza
./gradlew :app:bundleRelease :app:assembleRelease
```

Outputs:
- AAB (Play Store): `app/build/outputs/bundle/release/app-release.aab`
- APK (Direct install): `app/build/outputs/apk/release/app-release.apk`

If no keystore is configured, `bundleRelease`/`assembleRelease` still build but the
output is **unsigned** (`app-release-unsigned.apk`), which Play will reject. This is
deliberate: the build never silently signs with a default password.

## 5. Upload to Play

1. Play Console → your app → **Testing → Internal testing → Create new release**.
2. Upload the `.aab` from the workflow artifact.
3. Paste the version's `mapping.txt` under **App bundle explorer → Downloads** for
   deobfuscated stack traces.

## 6. Google Sign-In (Credential Manager)

The app signs in with Google through Android's Credential Manager and a Google ID token —
no Firebase project or billing is involved. Two OAuth clients are configured once in
[Google Cloud Console](https://console.cloud.google.com/apis/credentials):

1. **Android client** — *Create credentials → OAuth client ID → Android*.
   - Package name: `com.sakinah.deen`
   - SHA-1: the fingerprint of the keystore that signs the build
     (`keytool -list -v -keystore <your.jks> -alias <alias>`). Add one entry per key
     (debug, upload, Play App Signing).
2. **Web application client** — *Create credentials → OAuth client ID → Web application*.
   No redirect URI is needed for the ID-token flow. Its **Client ID** is the value the app
   passes to Credential Manager as `serverClientId`.

Supply that Client ID — never hardcode it — in one of these places:

```properties
# local.properties (git-ignored)
GOOGLE_WEB_CLIENT_ID=1234567890-abcdef.apps.googleusercontent.com
```

```bash
# or the environment
export GOOGLE_WEB_CLIENT_ID="1234567890-abcdef.apps.googleusercontent.com"
```

The build reads `local.properties` first, then the environment (and `keystore.properties`
inside CI). If the value is blank the app still builds; tapping **Continue with Google**
shows an inline "not configured for this build" message instead of crashing.

Google returns identity only (`uniqueId`, email, name, photo). Gender, date of birth, city
and country are collected in the profile-completion step after the first sign-in; city and
country are pre-filled from the device's last known prayer location via `Geocoder`.
