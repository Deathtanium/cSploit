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

- `plugins/mitm/hijacker/Hijacker.java` — `FacebookUserTask`, `XdaUserTask`
- `plugins/mitm/MITM.java` — `CheckForOpenPortsTask`

### `IntentService`

- `services/UpdateService.java`
- `core/MultiAttackService.java`

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

## Next steps (Phase 1)

- Replace wrapper / Android Gradle Plugin / repositories; raise `compileSdk` / `targetSdk` toward 34; namespace; dependency locking.
- Migrate `IntentService` → `WorkManager` + foreground service where needed; `AsyncTask` → coroutines / executors.
- Scoped storage for saves; network security config; ethics / lab acknowledgment in UI (per mission).
