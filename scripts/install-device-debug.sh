#!/usr/bin/env bash
# Install revival debug APK to a connected device (adb). Authorized testing only.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="$ROOT/cSploit/build/outputs/apk/debug/cSploit-debug.apk"
if [[ ! -f "$APK" ]]; then
  echo "APK not found. Run: (cd \"$ROOT\" && ./gradlew assembleDebug)"
  exit 1
fi
adb install -r "$APK"
echo "Installed: $APK"
