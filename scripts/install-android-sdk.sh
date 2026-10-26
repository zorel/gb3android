#!/usr/bin/env bash
set -euo pipefail

# Bootstrap the Android SDK used by this project.
#
# Installs the command-line tools if needed, accepts the Android SDK licences,
# then installs:
#   platform-tools
#   platforms;android-35
#   build-tools;35.0.0
#
# Usage:
#   ./scripts/install-android-sdk.sh
#   ANDROID_SDK_ROOT=/opt/android-sdk ./scripts/install-android-sdk.sh

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
CMDLINE_TOOLS_VERSION="11076708"

case "$(uname -s)" in
  Linux*)  HOST_TAG="linux" ;;
  Darwin*) HOST_TAG="mac" ;;
  *)
    echo "Unsupported host OS. Install Android Studio / the SDK manually." >&2
    exit 1
    ;;
esac

SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
  echo "Installing Android command-line tools in $SDK_ROOT"
  mkdir -p "$SDK_ROOT/cmdline-tools"
  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$TMP_DIR"' EXIT

  ARCHIVE="$TMP_DIR/cmdline-tools.zip"
  URL="https://dl.google.com/android/repository/commandlinetools-${HOST_TAG}-${CMDLINE_TOOLS_VERSION}_latest.zip"
  curl -fsSL "$URL" -o "$ARCHIVE"
  unzip -q "$ARCHIVE" -d "$TMP_DIR"

  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mv "$TMP_DIR/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"

# `yes` exits with SIGPIPE once sdkmanager stops reading; do not let pipefail
# turn that into a failure.
(yes | "$SDKMANAGER" --licenses >/dev/null) || true

"$SDKMANAGER" \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0"

printf 'sdk.dir=%s\n' "$SDK_ROOT" > "$REPO_ROOT/local.properties"

JAVAC=""
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  JAVAC="$JAVA_HOME/bin/javac"
elif command -v javac >/dev/null 2>&1; then
  JAVAC="$(readlink -f "$(command -v javac)")"
fi

cat <<MSG

Android SDK ready in: $SDK_ROOT
local.properties written.

Build with:
  export ANDROID_HOME="$SDK_ROOT"
  export ANDROID_SDK_ROOT="$SDK_ROOT"
  ./gradlew :app:assembleDebug

If you explicitly need Build-Tools 36:
  "$SDKMANAGER" "build-tools;36.0.0"
MSG

if [ -z "$JAVAC" ]; then
  cat >&2 <<'MSG'

WARNING: no full JDK with javac was found. Gradle cannot compile Android code
with a JRE alone. Install one, for example:
  sudo apt install openjdk-17-jdk
Then use JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 or ./scripts/build.sh
MSG
fi
