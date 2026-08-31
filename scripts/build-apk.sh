#!/usr/bin/env bash
#
# Builds an installable Android APK for Arohi and verifies the result.
#
# This is the exact recipe the GitHub Actions workflow
# (.github/workflows/build-apk.yml) runs on ubuntu-latest, packaged so it can be
# executed on a developer machine:
#
#   1. require JDK 17+
#   2. locate Gradle (the repo has no wrapper jar committed)
#   3. generate ./debug.keystore if it is missing (the debug buildType signs with it)
#   4. gradle :app:assembleDebug
#   5. verify the APK with aapt + apksigner
#
# Usage:
#   ./scripts/build-apk.sh            # debug APK (default, installable anywhere)
#   ./scripts/build-apk.sh --release  # release APK (needs a real upload keystore)
#
set -euo pipefail

BUILD_TYPE="debug"
if [ "${1:-}" = "--release" ]; then
  BUILD_TYPE="release"
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

fail() { echo "ERROR: $*" >&2; exit 1; }

# ---------------------------------------------------------------- 1. JDK 17+ --
command -v java >/dev/null 2>&1 || fail "java not found on PATH. Install a JDK 17 or newer (Temurin recommended)."
JAVA_VERSION_OUTPUT="$(java -version 2>&1 | head -n 1)"
JAVA_MAJOR="$(echo "$JAVA_VERSION_OUTPUT" | sed -E 's/.*version "([0-9]+).*/\1/')"
case "$JAVA_MAJOR" in
  ''|*[!0-9]*) fail "Could not parse Java major version from: $JAVA_VERSION_OUTPUT" ;;
esac
[ "$JAVA_MAJOR" -ge 17 ] || fail "Java $JAVA_MAJOR detected; this build requires Java 17 or newer."
echo "==> Java: $JAVA_VERSION_OUTPUT"

# ---------------------------------------------------------------- 2. Gradle --
# The repository intentionally does not commit gradle-wrapper.jar, so a local
# Gradle install is used. `gradle wrapper --gradle-version 9.3.1` will create a
# wrapper afterwards if you prefer ./gradlew.
if [ -x "./gradlew" ] && [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
  GRADLE_CMD="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD="gradle"
else
  fail "No Gradle found. Install Gradle 9.3.1 (https://gradle.org/releases/) or run it once as: gradle wrapper --gradle-version 9.3.1"
fi
echo "==> Gradle: $("$GRADLE_CMD" --version 2>/dev/null | sed -n 's/^Gradle //p' | head -1)"

# ------------------------------------------------------------ 3. Android SDK --
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK_DIR" ]; then
  for candidate in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" /usr/local/lib/android/sdk /opt/android-sdk; do
    [ -d "$candidate" ] && SDK_DIR="$candidate" && break
  done
fi
[ -n "$SDK_DIR" ] && [ -d "$SDK_DIR" ] || fail "Android SDK not found. Set ANDROID_HOME to your SDK directory."
echo "==> Android SDK: $SDK_DIR"

# ------------------------------------------------------------- 4. Keystore ----
KEYSTORE_FILE="$REPO_ROOT/debug.keystore"
if [ "$BUILD_TYPE" = "debug" ]; then
  if [ ! -f "$KEYSTORE_FILE" ]; then
    echo "==> debug.keystore missing - generating a local debug keystore"
    command -v keytool >/dev/null 2>&1 || fail "keytool not found; cannot generate debug.keystore"
    keytool -genkey -v \
      -keystore "$KEYSTORE_FILE" \
      -storepass android \
      -alias androiddebugkey \
      -keypass android \
      -dname "CN=Android Debug,O=Android,C=US" \
      -keyalg RSA \
      -keysize 2048 \
      -validity 10000
  else
    echo "==> Using existing debug.keystore"
  fi
else
  RELEASE_KEYSTORE="${KEYSTORE_PATH:-$REPO_ROOT/my-upload-key.jks}"
  [ -f "$RELEASE_KEYSTORE" ] || fail "Release keystore not found at $RELEASE_KEYSTORE (set KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD)."
  : "${STORE_PASSWORD:?STORE_PASSWORD must be set for a release build}"
  : "${KEY_PASSWORD:?KEY_PASSWORD must be set for a release build}"
  echo "==> Using release keystore at $RELEASE_KEYSTORE"
fi

# ---------------------------------------------------------------- 5. Build ----
echo "==> Building $BUILD_TYPE APK"
if [ "$BUILD_TYPE" = "debug" ]; then
  ASSEMBLE_TASK=":app:assembleDebug"
else
  ASSEMBLE_TASK=":app:assembleRelease"
fi
"$GRADLE_CMD" "$ASSEMBLE_TASK" --stacktrace

APK_PATH="app/build/outputs/apk/$BUILD_TYPE/app-$BUILD_TYPE.apk"
[ -s "$APK_PATH" ] || fail "Expected APK not produced at $APK_PATH"

# -------------------------------------------------------------- 6. Verify -----
AAPT="$(ls "$SDK_DIR"/build-tools/*/aapt 2>/dev/null | sort -V | tail -1 || true)"
APKSIGNER="$(ls "$SDK_DIR"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"

if [ -n "$AAPT" ]; then
  echo "==> APK identity (aapt dump badging)"
  "$AAPT" dump badging "$APK_PATH" \
    | grep -E "^(package|application-label|sdkVersion|targetSdkVersion|launchable-activity):" || true
else
  echo "WARNING: aapt not found under $SDK_DIR/build-tools - skipping identity check"
fi

if [ -n "$APKSIGNER" ]; then
  echo "==> Signature check (apksigner verify)"
  "$APKSIGNER" verify --verbose --print-certs "$APK_PATH"
else
  echo "WARNING: apksigner not found under $SDK_DIR/build-tools - skipping signature check"
fi

echo
echo "==========================================="
echo " APK READY"
echo "   path:    $REPO_ROOT/$APK_PATH"
echo "   size:    $(stat -c%s "$APK_PATH" 2>/dev/null || stat -f%z "$APK_PATH") bytes"
echo "   sha256:  $(sha256sum "$APK_PATH" 2>/dev/null | awk '{print $1}' || shasum -a 256 "$APK_PATH" | awk '{print $1}')"
echo "==========================================="
echo "Install with: adb install -r $APK_PATH"
