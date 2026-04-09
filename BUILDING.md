# Building cSploit from this repository

This document describes **how this repo is put together**, **repository layout**, and **how to produce a release APK** on your machine.

## How this repository is organized

| Piece | Role |
|--------|------|
| **`cSploit/`** | Android application module (Gradle `:cSploit`). Java UI, plugins, JNI client, assets. |
| **`cSploit/src/main/jniLibs/`** | Prebuilt **`libcSploitClient.so`** and **`libcSploitCommon.so`** (JNI bridge to the core daemon). In this fork they are refreshed from the official **[cSploit android v1.6.6-rc.2](https://github.com/cSploit/android/releases/tag/v1.6.6-rc.2)** APK (`armeabi`, `armeabi-v7a`). |
| **`cSploit/src/main/assets/core_bundled.xz`** | Prebuilt **native core** tarball (daemon, handlers, `nmap`, etc.) from **[cSploit/android.native](https://github.com/cSploit/android.native)** releases. On first launch the app extracts it so users do not need to download core from GitHub. Default: `v1.0.11`, API 16, `armeabi-v7a`. |
| **`cSploit/jni/`** (git submodule) | **[android.native](https://github.com/cSploit/android.native)** — full NDK tree used upstream. **You do not need a successful full NDK build** to compile the APK in this fork; the submodule is optional for reference or advanced work. |
| **`scripts/`** | Helpers to repopulate bundled core and JNI libraries from published artifacts. |

### Historical upstream layout (for context)

Original cSploit split work across several repositories:

- **Android app** (formerly `cSploit/android`) — this tree.
- **`cSploit/android.native`** — NDK build producing the core payload and (when built) JNI `.so` files; pulls **arpspoof**, **common**, **network-radar**, **daemon**, and many vendored tools as nested submodules.
- **Metasploit on device** was optional (`android.MSF`, Ruby, etc.). **This fork** uses **RPC only**: connect to an existing `msfrpcd` (see app settings). On-device MSF/Ruby update checks are removed from the UI and updater.

---

## Prerequisites

1. **JDK 8** (OpenJDK 8).  
   Android Gradle Plugin **3.3.x** used here does not run correctly on Java 11+ (JAXB / module errors on Java 17+).

   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64   # Linux example
   export PATH="$JAVA_HOME/bin:$PATH"
   java -version   # should show 1.8.x
   ```

2. **Android SDK** with:
   - **Platform:** `android-28`
   - **Build-tools:** `28.0.3`

   Install via [command-line tools](https://developer.android.com/studio#command-tools) or Android Studio, then set:

   ```properties
   # File: local.properties (in repo root; do not commit secrets)
   sdk.dir=/path/to/Android/sdk
   ```

   Non-interactive license acceptance (example):

   ```bash
   export ANDROID_SDK_ROOT=/path/to/Android/sdk
   mkdir -p "$ANDROID_SDK_ROOT/licenses"
   echo 24333f8a63b6825ea9c5514f83c2829b004d1fee > "$ANDROID_SDK_ROOT/licenses/android-sdk-license"
   ```

3. **Optional:** `curl`, `unzip` — only if you run the helper scripts below.

---

## Simple build procedure

From the repository root:

```bash
git clone --recurse-submodules https://github.com/Deathtanium/cSploit.git
cd cSploit

export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

# Ensure SDK path (create local.properties if missing)
echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties

# Refresh bundled artifacts (recommended after clone or when changing versions)
./scripts/fetch-bundled-core.sh
./scripts/extract-jni-from-v166-rc2.sh

# Debug or release APK
./gradlew :cSploit:assembleDebug
./gradlew :cSploit:assembleRelease
```

Or run the bundled one-liner (same steps):

```bash
./scripts/build-apk.sh
```

Outputs:

- Debug: `cSploit/build/outputs/apk/debug/cSploit-debug.apk`
- Release: `cSploit/build/outputs/apk/release/cSploit-release.apk`  
  (If no release keystore env vars are set, release is signed with the **debug** keystore.)

### Submodule note

```bash
git submodule update --init --recursive
```

This pulls **`cSploit/jni`** (large). It is **not required** for the Gradle steps above if you only use the prebuilt `jniLibs` and `core_bundled.xz`.

### Script environment variables

**`scripts/fetch-bundled-core.sh`** (optional overrides):

| Variable | Default | Meaning |
|----------|---------|---------|
| `BUNDLED_CORE_TAG` | `v1.0.11` | android.native release tag |
| `BUNDLED_CORE_API` | `16` | `android{N}` in asset filename |
| `BUNDLED_CORE_ABI` | `armeabi-v7a` | ABI segment in asset filename |

---

## Repository structure (top level)

```
.
├── README.md                 # Project overview
├── BUILDING.md               # This file
├── settings.gradle           # include ':cSploit'
├── build.gradle              # Root Gradle config
├── gradle/                   # Wrapper
├── cSploit/
│   ├── build.gradle          # App module
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/             # Application code
│   │   ├── res/              # Resources, preferences
│   │   ├── assets/           # core_bundled.xz, certs, …
│   │   └── jniLibs/          # libcSploit*.so per ABI
│   └── jni/                  # Submodule → android.native (optional for APK build)
└── scripts/
    ├── fetch-bundled-core.sh
    └── extract-jni-from-v166-rc2.sh
```

---

## Full native build (advanced)

Rebuilding **everything** from `cSploit/jni` requires the **legacy NDK** and toolchain matching **`Application.mk`** (e.g. `gnustl_static`, older GCC). That path is fragile on modern hosts and is **not** what the CI-friendly procedure above uses. Use the submodule only if you are porting or debugging native code.

Long-term goal: fold all native sources and JNI into one always-buildable tree—see **[TODO.md](TODO.md)** in the repo root.

---

## Legal use

Use only on networks and systems you own or are authorized to test.
