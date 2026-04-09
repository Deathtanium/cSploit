#!/usr/bin/env bash
# Downloads a cSploit/android.native core tarball into cSploit/src/main/assets/core_bundled.xz
# Default: API 16 + armeabi-v7a (matches most devices; arm64 devices typically run 32-bit app ABIs too).
set -euo pipefail
DEST_DIR="$(cd "$(dirname "$0")/.." && pwd)/cSploit/src/main/assets"
mkdir -p "$DEST_DIR"
DEST="$DEST_DIR/core_bundled.xz"
API_LEVEL="${BUNDLED_CORE_API:-16}"
ABI="${BUNDLED_CORE_ABI:-armeabi-v7a}"
TAG="${BUNDLED_CORE_TAG:-v1.0.11}"
NAME="core-${TAG}.android${API_LEVEL}.${ABI}.tar.xz"
URL="https://github.com/cSploit/android.native/releases/download/${TAG}/${NAME}"
echo "Fetching ${URL}"
curl -fsSL -o "$DEST" "$URL"
ls -la "$DEST"
