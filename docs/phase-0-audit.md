# Phase 0 — Read-only audit (initial snapshot)

Date: 2026-04-08. Upstream snapshot: [cSploit/android](https://github.com/cSploit/android) full history; `AGENTS.md` and this file are revival additions.

## Module map (`org.csploit.android`)

| Area | Packages / locations | Role |
|------|------------------------|------|
| Application shell | `MainActivity`, `CSploitApplication`, `SettingsActivity`, `WifiScannerActivity` | Entry, prefs, Wi‑Fi UI |
| Core | `core/` (`System`, `Child`, `MultiAttackService`, …) | Paths, root bridge, settings, child processes |
| GUI | `gui/` | Console, directory picker, MSF prefs, dialogs |
| Network / RPC | `net/` (`RemoteReader`, `GitHubParser`, `metasploit/`, `http/`, …) | HTTP to GitHub API, Metasploit MSGpack |
| Tools (root helpers) | `tools/` (`Shell`, `IPTables`, `Msf`, `NMap`, `ArpSpoof`, `Ettercap`, …) | Wraps busybox / bundled binaries via `su` |
| MITM plugins | `plugins/mitm/` | Hijacker, sniffer, DNS spoof, password sniffer, … |
| Other plugins | `plugins/` (exploit finder, port scanner, login cracker, …) | Feature plugins |
| Updates | `update/`, `services/UpdateService`, `services/UpdateChecker` | APK / core / ruby / MSF downloads from GitHub releases |
| Wi‑Fi keygens | `wifi/` | Router key algorithms (legacy dSploit lineage) |
| Events / helpers | `events/`, `helpers/` | Bus, test helpers |
| Native (JNI) | None in this repo | Native payloads come from **`android.native`** releases pulled at runtime |

## AGENTS.md checklist (grep / review)

### Apache HttpClient / legacy HTTP stack

- No `org.apache.http` usage found in Java.
- Networking uses **`java.net.HttpURLConnection`** (`UpdateService`, `RemoteReader`) and GitHub JSON via `RemoteReader`.

### `AsyncTask`

- **Removed (revival):** session enrichment in **`Hijacker`** uses **`ExecutorService`** + **`runOnUiThread`**; **`MITM`** port check uses a **background `Thread`** + **`runOnUiThread`**.

### `IntentService` (removed)

- **`UpdateService`** / **`MultiAttackService`** extend **`Service`** with a dedicated **`HandlerThread`** + serial **`Handler`** queue; **`stopSelf(startId)`** matches queued starts.

### Shared process / cleartext manifest

- No `android:process` overrides found in scanned manifest excerpt; full manifest review in Phase 1.
- No `usesCleartextTraffic` / network security config in current tree (expect failures on modern strict defaults once targetSdk rises).

### External storage / `/sdcard`

- `core/System.java` — `Environment.getExternalStorageDirectory()` for `PREF_SAVE_PATH` default and error log path
- `gui/DirectoryPicker.java` — starts at external storage root

### `Runtime.getRuntime().exec` / process execution

- `core/System.java` — `Runtime.getRuntime().exec("su")` (root bridge; **command-injection audit** required per `AGENTS.md`)
- Additional command execution likely via `Shell`, `Child`, `Tool` classes (follow-up pass)

### Update / download channels

- `net/GitHubParser.java` — fixed release API: `https://api.github.com/repos/%s/%s/releases`; default repos: `cSploit/android`, `cSploit/android.native`, `cSploit/android.native.ruby`, `cSploit/android.MSF` (MSF username/project overridable in prefs).
- **Risk:** Unpinned TLS to GitHub; no artifact signature verification beyond whatever GitHub serves. Revival should use **operator-controlled mirrors**, **pinning**, or **signature checks** if downloads are re-enabled (`AGENTS.md` §5).
- Legacy cleartext URL in `wifi/algorithms/ThomsonKeygen.java`: `http://www.dsploit.net/files/RKDictionary.dic` (dead / undesirable for modern policy).

### Build baseline

- Root Gradle **3.3.0-alpha12**, `compileSdkVersion` / `targetSdkVersion` **28**, `minSdkVersion` **14**, Gradle wrapper **4.10.2**, **`jcenter()`** (EOL).
- **No** dedicated `jni/` tree; core native tarball from separate repo at install/update time.

## Baseline build attempt (2026-04-08)

- `./gradlew assembleDebug` **did not run to completion**: host has JDK **21**; Gradle **4.10.2** starts, but the project requires **`ANDROID_HOME` / `local.properties` `sdk.dir`** (Android SDK not configured in this environment). Phase 1 should document SDK/JDK expectations (often JDK 17 + API 34 platform for modern AGP).

## Phase 1 (build hygiene) — applied 2026-04-08 (updated for API 35 / Android 15)

- **Gradle 8.9**, **AGP 8.7.x**; `settings.gradle` with `pluginManagement` + `dependencyResolutionManagement` (Google + Maven Central; **jcenter removed**).
- **`compileSdk` / `targetSdk` 35** (Android 15; **LineageOS 22** class devices), **`minSdk` 26**, `namespace` in Gradle; manifest **`package` removed** (namespace-only).
- **`android.nonFinalResIds=false`** so legacy `switch (R.id…)` / `switch (R.string…)` still compiles.
- **Manifest**: `android:exported` on all activities/services; **`usesCleartextTraffic`** (interim; replace with targeted network security config in Phase 2); **`POST_NOTIFICATIONS`**; **`FOREGROUND_SERVICE`** + **`FOREGROUND_SERVICE_DATA_SYNC`**; **`UpdateService`** / **`MultiAttackService`** use **`foregroundServiceType="dataSync"`** with **`ServiceCompat.startForeground`** + immutable **`PendingIntent`** flags (Android 12–15).
- **Runtime**: **`MainActivity`** requests **`POST_NOTIFICATIONS`** on API 33+ (required for update / multi-attack notifications).
- **HijackerWebView**: removed **`setAppCacheEnabled`** (removed from API 33).
- **Tests**: `NetworkHelperTest` no longer uses **`getLocalHost()`** (sandbox-safe).
- **Verify**: `./gradlew assembleDebug` and `./gradlew test` succeed (JDK 21 on host; Java 8 source/target retained for now).

## Phase 2 (partial) — scoped storage & network policy — 2026-04-08

- Default **`PREF_SAVE_PATH`** / **`DirectoryPicker`** root: **`Context.getExternalFilesDir(null)`** (fallback **`getFilesDir()`**), via **`System.getDefaultSaveDirectory()`**. Debug error log file uses the same tree when **`PREF_DEBUG_ERROR_LOGGING`** is on.
- **`WRITE_EXTERNAL_STORAGE`** limited to **`maxSdkVersion="32"`**; **`MainActivity`** only requests it on **API ≤ 32** (app-scoped paths need no broad storage grant on Android 13+).
- **`network_security_config.xml`**: documents cleartext for lab use; application references it (**`usesCleartextTraffic`** removed as redundant). Narrow per-domain / lab-toggle later.

## Phase 2 (continued) — lab acknowledgment & AsyncTask removal — 2026-04-08

- **First launch:** **`MainActivity`** shows non-cancelable **`AlertDialog`** with **`csploit_disclaimer`**; accepting sets **`PREF_LAB_USE_ACK_V1`** in default prefs, then **`Exit`** closes the app. Strings: **`lab_ack_title`**, **`lab_ack_accept`**.
- **MITM / Hijacker:** no remaining **`AsyncTask`** in those flows (see audit note above).

## Phase 2 (continued) — Service migration — 2026-04-08

- Replaced deprecated **`IntentService`** with **`Service` + `HandlerThread`**: **`UpdateService`**, **`MultiAttackService`**. Foreground **`dataSync`** behavior unchanged; intents processed serially per service instance.

## Device testing & MSF integration

- **`docs/NETHUNTER_ONEPLUS5_RUNBOOK.md`**: install paths for **debug/release APK**, **OnePlus 5 / NetHunter**, **core/ruby/msf** layout, and **Metasploit RPC** (local `msfrpcd` vs remote + port forward). Script: **`scripts/install-device-debug.sh`**.

## Next steps (Phase 2+)

- Optional: **`WorkManager`** for deferrable work; keep **`Service` + FGS** for long-running downloads / multi-attack (user-visible).
- **SAF** for user-exported pcap/logs; optional **MANAGE_EXTERNAL_STORAGE** doc-only path for power users choosing arbitrary dirs.
- Raise **Java language level** / toolchain once code is ready (AGP warns on source/target 8 under JDK 21).
- **Phase 3–5** per **`AGENTS.md`** (MITM engine, UX parity, NetHunter wrappers, formal security review).
