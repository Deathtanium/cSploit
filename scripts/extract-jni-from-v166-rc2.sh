#!/usr/bin/env bash
# Repopulate libcSploitClient.so / libcSploitCommon.so from the official v1.6.6-rc.2 APK
# (same JNI the historical release shipped). Requires curl and unzip.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK_URL="https://github.com/cSploit/android/releases/download/v1.6.6-rc.2/cSploit-release.apk"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
curl -fsSL -o "$TMP/apk" "$APK_URL"
for abi in armeabi armeabi-v7a; do
  mkdir -p "$ROOT/cSploit/src/main/jniLibs/$abi"
  unzip -jo "$TMP/apk" "lib/$abi/libcSploitCommon.so" "lib/$abi/libcSploitClient.so" \
    -d "$ROOT/cSploit/src/main/jniLibs/$abi/"
done
echo "OK -> cSploit/src/main/jniLibs/"
