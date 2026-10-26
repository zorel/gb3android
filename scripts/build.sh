#!/usr/bin/env bash
set -euo pipefail

# Build the Android project with a JDK that actually provides javac.
#
# Usage:
#   ./scripts/build.sh :app:assembleDebug
#   ./scripts/build.sh :app:testDebugUnitTest

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
if [ ! -d "$SDK_ROOT" ]; then
  echo "Android SDK not found in $SDK_ROOT" >&2
  echo "Run $REPO_ROOT/scripts/install-android-sdk.sh first." >&2
  exit 1
fi
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"

find_jdk() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    printf '%s\n' "$JAVA_HOME"
    return
  fi
  if command -v javac >/dev/null 2>&1; then
    local resolved
    resolved="$(readlink -f "$(command -v javac)")"
    printf '%s\n' "$(dirname "$(dirname "$resolved")")"
    return
  fi
  local candidate
  for candidate in /usr/lib/jvm/*; do
    if [ -x "$candidate/bin/javac" ]; then
      printf '%s\n' "$candidate"
      return
    fi
  done
}

JDK="$(find_jdk || true)"
if [ -z "$JDK" ]; then
  echo "No full JDK found (one containing javac)." >&2
  echo "Install one, for example: sudo apt install openjdk-17-jdk" >&2
  exit 1
fi
export JAVA_HOME="$JDK"
export PATH="$JAVA_HOME/bin:$PATH"

if [ "$#" -eq 0 ]; then
  set -- :app:assembleDebug
fi
exec "$REPO_ROOT/gradlew" "$@"
