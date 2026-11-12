# Release signing & Play-ready builds

Sakinah builds a signed **AAB** (Android App Bundle) in CI from repository secrets.
No keystore, password, or key is ever committed to this repository.

## 1. Create the upload keystore (once)

Run this on your own machine and keep the file plus passwords somewhere safe.
Losing the upload key means you must request a key reset from Google Play.

```bash
mkdir -p keystore
keytool -genkeypair -v \
  -keystore keystore/sakinah_release.jks \
  -alias sakinah_key \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype JKS
```

Recommended: use an **upload key** (Play App Signing holds the real app signing key).
`-validity 10000` comfortably exceeds Play's 25-year requirement.

## 2. Add the repository secrets

GitHub → your repository → **Settings → Secrets and variables → Actions → New repository secret**.
Create exactly these:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | Base64 of the `.jks` file (see below) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | `sakinah_key` (or your alias) |
| `KEY_PASSWORD` | Key password |
| `GEMINI_API_KEY` | Optional — free Google AI Studio key baked into `BuildConfig` |

Encode the keystore:

```bash
# macOS
base64 -i keystore/sakinah_release.jks | tr -d '\n'
# Linux
base64 -w0 keystore/sakinah_release.jks
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore\sakinah_release.jks"))
```

Or with the GitHub CLI:

```bash
base64 -w0 keystore/sakinah_release.jks > /tmp/keystore.b64
gh secret set KEYSTORE_BASE64 < /tmp/keystore.b64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS --body "sakinah_key"
gh secret set KEY_PASSWORD
gh secret set GEMINI_API_KEY
```

> A leaked `KEYSTORE_BASE64` plus passwords lets someone publish as you. Treat them
> as production credentials, and rotate with a Play upload-key reset if exposed.

## 3. Run the release build

The workflow is `.github/workflows/release.yml` and supports two triggers:

- **Tag push** — build a versioned release:
  ```bash
  git tag v1.2.0
  git push origin v1.2.0
  ```
- **Manual** — Actions → *Release* → *Run workflow*. Optionally pass a version name;
  otherwise the tag (minus `v`) or `1.0.0` is used. `versionCode` comes from the
  workflow run number, so it always increases.

The job fails immediately with a clear `::error::` annotation if any signing secret
is missing, then verifies the produced AAB and APK signatures with `jarsigner` and
`apksigner` before uploading.

Artifacts: `sakinah-release-aab-<version>`, `sakinah-release-apk-<version>`, and
`sakinah-mapping-<version>` (R8 `mapping.txt`; upload it to Play for readable
crash reports).

## 4. Build a signed release locally (optional)

`app/build.gradle.kts` reads credentials from `keystore.properties` (git-ignored) or
environment variables. Environment variables win when both are present.

```bash
cp keystore.properties.example keystore.properties   # then edit the values
export VERSION_NAME=1.2.0 VERSION_CODE=42
./gradlew :app:bundleRelease
```

Or entirely from the environment:

```bash
export KEYSTORE_PATH="$PWD/keystore/sakinah_release.jks"
export KEYSTORE_PASSWORD='…' KEY_ALIAS=sakinah_key KEY_PASSWORD='…'
./gradlew :app:bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`.

If no keystore is configured, `bundleRelease`/`assembleRelease` still build but the
output is **unsigned** (`app-release-unsigned.apk`), which Play will reject. This is
deliberate: the build never silently signs with a default password.

## 5. Upload to Play

1. Play Console → your app → **Testing → Internal testing → Create new release**.
2. Upload the `.aab` from the workflow artifact.
3. Paste the version's `mapping.txt` under **App bundle explorer → Downloads** for
   deobfuscated stack traces.
