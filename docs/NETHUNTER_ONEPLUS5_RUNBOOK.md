# NetHunter / OnePlus 5 — cSploit revival test & integration

This document is for **authorized security testing only** (labs, contracts, networks you own or have explicit permission to test).

## What is “done” vs. full `AGENTS.md` vision

You get a **modern API 35 build**, **installable APKs**, first-run **authorization acknowledgment**, **scoped default storage**, **foreground-typed services**, and **working in-app wiring** to the same Metasploit RPC (MsgPack) client the legacy app used.

**Not finished** as defined in `AGENTS.md`: full **MITM engine rewrite** (VpnService vs documented iptables/nft feature flags), **signed/operator-controlled update channel**, **STRIDE / full `su` audit**, **SAF export UX**, **NetHunter wrapper screens**, etc. Treat this as a **revival build + integration guide**, not a compliance sign-off.

---

## APK outputs (after `./gradlew assembleDebug assembleRelease`)

| Variant | Path |
|--------|------|
| **Debug** (recommended for device testing) | `cSploit/build/outputs/apk/debug/cSploit-debug.apk` |
| **Release** (unsigned) | `cSploit/build/outputs/apk/release/cSploit-release-unsigned.apk` |

**Install (debug, typical):**

```bash
adb install -r cSploit/build/outputs/apk/debug/cSploit-debug.apk
```

If a legacy cSploit is installed with the same package, **uninstall it first** or bump `applicationId` in `cSploit/build.gradle` for side-by-side installs.

**Release unsigned:** Many devices still allow `adb install` of unsigned release APKs for sideloading; if install fails, sign with your own keystore or use the **debug** APK.

**Version:** Check `versionName` / `versionCode` in `cSploit/build.gradle` (revival uses `2.0.0-revival` / `5` after the revival bump).

---

## OnePlus 5 + NetHunter checklist

1. **Root / NetHunter kernel** available; **Magisk** or NetHunter su stack as you already use.
2. **BusyBox / tools** on `PATH` for `su` sessions (historic cSploit expectation; your ROM may vary).
3. Grant **notification** permission on Android 13+ when prompted (updates / multi-attack use foreground notifications).
4. Accept the **first-run disclaimer** (stored in default `SharedPreferences` as `PREF_LAB_USE_ACK_V1`).

---

## How components connect inside the app

### 1. Save path (`PREF_SAVE_PATH`)

- Default is **app-specific external storage** (see `System.getDefaultSavePath()`), not legacy `/sdcard` root.
- Large trees (**core native tarball**, **ruby**, **msf**) are normally under the app’s **private `filesDir`** and paths derived in `System.java`:
  - **Core daemon / socket:** `getCorePath()` → `Context.getFilesDir()` (extracted `core.tar.xz` layout).
  - **Ruby:** `getRubyPath()` → preference `RUBY_DIR` or `filesDir/ruby`.
  - **Metasploit framework tree:** `getMsfPath()` → preference **`MSF_DIR`** or default `filesDir/msf`.

Use **Settings → General → Save path** if you need a different base (understanding **scoped storage** limits on Android 10+).

### 2. Native **core** (`cSploit` daemon)

- The app talks to a Unix socket: `getCorePath()/cSploitd.sock` (see `System.init` / `Client.Connect`).
- **Core binaries** come from the historical **GitHub release** pipeline (`cSploit/android.native` via `GitHubParser.getCoreRepo()`). Upstream is **EOL**; blobs may be **stale or missing** for newest ABIs. For lab use, plan to host your own **compatible** `android.native` release assets or sideload the extracted tree into `filesDir` with matching `VERSION` files—this is **operator responsibility**.

### 3. **Metasploit Framework** inside the app

- **Toggle:** `MSF_ENABLED` (Settings).
- **Directory:** `MSF_DIR` (default `…/files/msf`).
- **Version file:** `getMsfPath()/VERSION` (semver) — used to decide if MSF features are “installed”.
- **Updates:** `UpdateChecker` / `UpdateService` can pull **MSF bundles** from the configured GitHub repo (`GitHubParser.getMsfRepo()`, overridable via settings **`MSF_GITHUB_USERNAME`** / **`MSF_GITHUB_PROJECT`** in code/preferences where exposed).

### 4. **Metasploit RPC API** (how the app “hooks” Metasploit)

The app uses a **MessagePack-over-HTTP(S)** client (`org.csploit.android.net.metasploit.RPCClient`) against **`/api/`** on the RPC daemon (same protocol family as **msfrpcd**).

**Preference keys** (see `preferences.xml` / `MsfRpcdService`):

| Key | Role | Default / notes |
|-----|------|----------------|
| `MSF_RPC_HOST` | RPC host | `127.0.0.1` = app may **spawn** local `msfrpcd` via `System.getTools().msfrpcd` |
| `MSF_RPC_USER` | Login user | `msf` |
| `MSF_RPC_PSWD` | Login password | `msf` |
| `MSF_RPC_PORT` | Port | Synced from `System.MSF_RPC_PORT` (default **55553** in `System.java`) |
| `MSF_RPC_SSL` | Use HTTPS | `false` for classic msfrpcd |

**Two integration modes:**

#### A) **Local RPC** (default host `127.0.0.1`)

1. Ensure **`MSF_DIR`** points at a **working Metasploit install** the **root shell** can execute (Ruby env, `msfconsole` / `msfrpcd` as wired in `ExecChecker` / `Msf` tool wrappers).
2. From the **main menu**, use **Start MSFRPCD** / status actions (`MsfRpcdService`): the app launches `msfrpcd` with **user/password/port** and **bind `-a 127.0.0.1`**, then connects `RPCClient` locally.

Implementation detail: `MsfRpcd.async()` builds the daemon command in `org.csploit.android.tools.MsfRpcd` (flags `-P`, `-U`, `-p`, `-a 127.0.0.1`, optional `-S` for SSL).

#### B) **Remote RPC** (NetHunter **chroot** or laptop)

Use this when Metasploit runs **outside** the Android app filesystem (e.g. only inside **Kali chroot**).

1. In **chroot** (or on your PC), start `msfrpcd` listening on an address reachable from Android.
2. **Port forward** to Android loopback if needed, e.g. with **socat** or SSH `-L` so that **`127.0.0.1:55553`** on the phone reaches the daemon.
3. In app **Settings → Metasploit framework**, set:
   - `MSF_RPC_HOST` = `127.0.0.1` (if forwarded) **or** the LAN IP of the machine running `msfrpcd`.
   - Match **user / password / port / SSL** to the daemon.
4. Use the menu action **Connect to Metasploit** (remote path in `MsfRpcdService`: no local `msfrpcd` start; only `RPCClient` connect).

**Example (conceptual) inside NetHunter chroot:**

```bash
# Inside Kali chroot — adjust paths and auth
msfrpcd -U msf -P msf -p 55553 -a 127.0.0.1 -n -f
```

Then from **Android side**, forward chroot’s port to the Android network namespace if they don’t share loopback (typical with NetHunter: use `bootkali` helpers or `socat` from a root shell to forward `tcp-listen:55553` to `chroot_TCP:55553` — exact commands depend on your NetHunter image).

If **`MSF_RPC_HOST` is not `127.0.0.1`**, the app treats RPC as **remote** and will **not** try to spawn `msfrpcd` locally (`MsfRpcdService.isLocal()`).

### 5. **GitHub updates** (APK / core / ruby / MSF)

Defaults target archived **`cSploit/*`** repos via **`https://api.github.com`** (`GitHubParser.java`). Expect **missing assets** or **very old** artifacts. For a lab:

- Mirror releases yourself and point **custom** MSF manifest / repo settings where the UI exposes them, **or**
- Disable automatic update checks and **side-load** components you trust.

---

## Suggested test sequence on device

1. Install **debug APK**; open app; accept disclaimer; grant permissions.
2. **Settings:** confirm **Save path** and **MSF_DIR** / **RUBY_DIR** if not using defaults.
3. Run **core** install flow if offered (or verify `cSploitd.sock` connects after manual core drop-in).
4. **MSF:** either install via in-app updater **or** point `MSF_DIR` at a prepared tree; verify **VERSION** files.
5. **RPC:** start **local msfrpcd** or **connect remote** per §4; confirm status notification / menu shows connected.
6. Exercise **port scan** / **exploit finder** against a **lab target** only.

---

## Limitations & safety

- **MITM / ARP / iptables** paths are still **legacy-oriented**; kernels, **iptables vs nft**, and **scoped Wi‑Fi** APIs on Android 15 may break or need manual commands.
- **Cleartext HTTP** is still permitted broadly via `network_security_config.xml` for lab convenience; tighten for real deployments.
- **No warranty:** EOL upstream + revival fork — verify every payload on disposable lab gear.

---

## Related files in tree

- `org.csploit.android.services.MsfRpcdService` — menu + start/connect logic.
- `org.csploit.android.tools.MsfRpcd` — `msfrpcd` command line.
- `org.csploit.android.net.metasploit.RPCClient` — MsgPack RPC wire format.
- `org.csploit.android.net.GitHubParser` — release API URLs.
- `org.csploit.android.core.System` — paths, `MSF_RPC_PORT`, `init`.
