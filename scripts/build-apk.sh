#!/usr/bin/env bash
# Convenience: refresh bundled artifacts and build release APK.
# Requires: JDK 8 on PATH or JAVA_HOME, Android SDK, and ../local.properties (sdk.dir).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
"$ROOT/scripts/fetch-bundled-core.sh"
"$ROOT/scripts/extract-jni-from-v166-rc2.sh"
./gradlew :cSploit:assembleRelease
ls -la "$ROOT/cSploit/build/outputs/apk/release/"*.apk
