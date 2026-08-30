# Building the Arohi APK

This project is a native Android app (Kotlin + Jetpack Compose, AGP 9.1.1, Gradle 9.3.1,
`compileSdk` 36.1, `minSdk` 24). There are two supported ways to get an installable `.apk`.

---

## Option 1 — GitHub Actions (no local Android tooling required)

`.github/workflows/build-apk.yml` builds, verifies, and publishes a debug APK.

It runs automatically on every push to `main`, and can be triggered manually on any branch:

```bash
# from a checkout, with the GitHub CLI authenticated
gh workflow run "Build & Publish Debug APK" --ref <branch-name>

# follow the run
gh run watch
```

When it finishes, the APK is attached to a GitHub Release:

```bash
gh release list
gh release download <tag> --pattern "*.apk"
```

The workflow does not just compile — before publishing it checks the artifact it produced:

| Check | Tool |
| --- | --- |
| package id, `versionName`, min/target SDK, launchable activity | `aapt dump badging` |
| signature present and valid (i.e. installable) | `apksigner verify --print-certs` |
| integrity fingerprint for the release notes | `sha256sum` |

If any of those checks fail the run fails, so a published release always corresponds to a
signed, launchable APK. The values are written into the release notes as a table.

> The CI APK is signed with a **temporary debug keystore** generated per run. That is fine for
> sideloading and testing; it is not a Play Store artifact.

### Hardened workflow (drop-in)

`ci/build-apk.hardened.yml` is an improved version of the workflow. It is kept outside
`.github/workflows/` because automated pushes to this repo are made with a GitHub App token that
has no `workflows` permission, so it cannot create or update workflow files. To adopt it, copy it
over the active workflow as a normal commit from an account with write access:

```bash
cp ci/build-apk.hardened.yml .github/workflows/build-apk.yml
git add .github/workflows/build-apk.yml
git commit -m "ci: verify APK signature and identity before publishing"
```

What it adds on top of the current workflow:

- **Identity check** — `aapt dump badging` reads the package id, `versionName`, min/target SDK and
  the launchable activity out of the built APK; the run fails if the manifest looks incomplete.
- **Signature check** — `apksigner verify --verbose --print-certs` proves the APK is signed and
  therefore installable; the run fails if no signing certificate is reported.
- **Traceability** — SHA-256, size and all of the above are published in the release notes, and the
  `versionName` is included in the release asset filename.
- `actions/setup-java` bumped to v5 (v4 is flagged deprecated by the runner).

---

## Option 2 — Local build

```bash
./scripts/build-apk.sh              # debug APK  -> app/build/outputs/apk/debug/app-debug.apk
./scripts/build-apk.sh --release    # release APK (requires a real upload keystore)
```

The script performs the same steps as CI: verify JDK 17+, locate Gradle and the Android SDK,
generate `./debug.keystore` if it is absent, run `:app:assembleDebug`, then verify the APK with
`aapt` and `apksigner`.

### Prerequisites

| Requirement | Notes |
| --- | --- |
| JDK 17+ | `java -version` — Temurin works well |
| Gradle 9.3.1 | The repo does **not** commit `gradle-wrapper.jar`. Either install Gradle 9.3.1, or create the wrapper once with `gradle wrapper --gradle-version 9.3.1` and then use `./gradlew`. |
| Android SDK | `ANDROID_HOME` set; platform **36** with minor API level **36.1** and build-tools installed (Android Studio's SDK Manager does this) |
| ~6 GB disk | Gradle + SDK + dependency downloads |

First build downloads dependencies from Google's Maven and Maven Central, so network access is
required.

---

## Installing on a device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy the `.apk` to the phone and open it; Android will ask to allow installs from unknown
sources.

Arohi needs runtime permissions to be useful — microphone (voice), notification access, and the
accessibility service. The in-app permission setup screen walks through them; without them the
voice and automation features stay inactive.

---

## Release / Play Store builds

`app/build.gradle.kts` defines a `release` signing config that reads:

| Variable | Meaning |
| --- | --- |
| `KEYSTORE_PATH` | path to the upload keystore (default `<repo>/my-upload-key.jks`) |
| `STORE_PASSWORD` | keystore password |
| `KEY_PASSWORD` | key password (alias `upload`) |

Never commit the keystore or these values. For CI, store them as repository secrets.

---

## API key

`GEMINI_API_KEY` is injected at build time by the `secrets` Gradle plugin from a `.env` file
(falling back to `.env.example`). Without a real key the app still builds and runs — its local
engine handles battery, calls, apps, volume, and media — but Gemini-backed reasoning is
disabled. Add a `.env` containing `GEMINI_API_KEY=...` before building to enable it.
