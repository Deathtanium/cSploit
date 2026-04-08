# AGENTS.md — cSploit revival (fork & modern Android port)

This document is for an **autonomous coding agent** (or human lead + agent) undertaking a **long‑horizon** effort: carry **cSploit‑class** Android network penetration tooling forward after upstream **EOL** and the **zAnti** product line ceasing to be a credible open‑source successor.

**Repository layout:** Treat this file as the **root `AGENTS.md`** of whatever git repo you create for the fork (copy this tree or init a new repo and paste this file). It is intentionally **not** tied to the OnePlus 5 NetHunter docs parent folder beyond living there as a bootstrap artifact.

---

## 1. Mission

- **Primary:** Produce a **buildable, installable** Android app (APK/AAB) that runs on **modern Android** (baseline target: **API 34**, minimum **API 26+** where technically unavoidable), on **rooted** devices (e.g. NetHunter / Magisk), and restores **core offensive workflows** historically associated with cSploit (network mapping, selective MITM helpers, script/plugin hooks, integration with **user‑supplied** tooling in a **Kali chroot** where appropriate).
- **Secondary:** Clear modular boundaries so **MITM / packet paths** can be swapped (legacy `iptables`/`nfqueue` assumptions vs **VpnService** / **eBPF**‑friendly designs) without rewriting the whole UI.
- **Non‑goal:** Shipping malware, Play Store evasion as a product goal, or targeting non‑consented networks. The app is for **authorized testing only**; documentation must say so prominently.

---

## 2. Historical context (do not re‑learn from scratch)

| Fact | Implication |
|------|-------------|
| Upstream **[cSploit/android](https://github.com/cSploit/android)** is **archived / EOL**; last meaningful release era ~2016. | Expect **Java** codebase, old Gradle, **Apache HttpClient**, **pre‑scoped‑storage**, **pre‑TLS 1.3** defaults, **synchronous networking** on main thread, etc. |
| Package historically **`org.csploit.android`**. | Bumping `applicationId` may be required to coexist with any legacy install; decide **fork namespace** early (e.g. `org.csploit.android.revived`). |
| Original author trajectory led to **zAnti** (commercial/enterprise), later **feature‑stripped**; **not** a dependency for this effort. | Do **not** wait on upstream moral rights; treat codebase as **read‑only archaeology** + **clean‑room reimplementation** where license conflicts appear. |
| **NetHunter** users often paired cSploit with **root + chroot**. | Revival should **not** assume cSploit *is* the chroot; integrate via **`su`**, explicit **binary paths**, or **exported intents** documented for NetHunter. |

**License:** Read upstream `LICENSE` and **retain required notices**. If you merge multiple forks, **track provenance per file**.

---

## 3. Hard constraints & ethics

1. **Authorization:** Features must be framed for **lab / contractual pentest** use; README and first‑run UI must require acknowledgment.
2. **Jurisdictions:** MITM/interception may be **illegal** without consent. Do not obfuscate that.
3. **Dependencies:** Prefer **auditable** deps; avoid prebuilt native blobs without checksum and source.
4. **No auto‑exploit against random networks:** Default to **manual target entry** and **lab profiles**.

---

## 4. Technical north star (architecture)

### 4.1 Phased delivery (do not skip phases)

**Phase 0 — Audit (read‑only)** 
- Map modules: UI, core, MITM engine, session/db, shell/commands, root bridge, any **native** code. 
- List **every** use of: `apache httpclient`, `AsyncTask`, `IntentService`, `apache legacy`, **single‑shared‑process**, **cleartext**, **file paths** on `/sdcard`, **Runtime.getRuntime().exec** without sanitization. 
- Document **current** exploit/metadata update channels (likely **broken**); mark for removal or HTTPS + pinning with operator‑controlled keys.

**Phase 1 — Build hygiene** 
- Modern **Gradle + Kotlin** (Kotlin for **new** code; incremental Java migration optional). 
- `compileSdk` / `targetSdk` raised; **namespace** in `build.gradle`. 
- **AndroidX** migration; **desugaring** if needed.

**Phase 2 — Runtime compatibility** 
- **Background work:** `WorkManager`, foreground services with **proper types** (dataSync / specialUse as applicable). 
- **Storage:** Scoped storage; app‑private dirs; **SAF** for user‑exported pcap/logs. 
- **Networking:** OkHttp / Retrofit; **network security config**; optional user CA for **lab** MITM (never silently trust public roots). 
- **Root bridge:** Abstract `RootShell` / `LibSu` / `runBlocking` IO dispatcher; **never** parse untrusted output unsafely.

**Phase 3 — MITM / engine rewrite (largest risk)** 
- Historical stack often assumed **iptables** + transproxy / **ARP** tricks on older kernels. 
- Modern direction: **VpnService** API for user‑approved routing, or documented **root‑only** path that adjusts `iptables`/`nft` on **specific maintained kernels** (NetHunter). 
- Provide **feature flags**: “Legacy iptables mode (root)” vs “VpnService lab mode (no global MITM)”.

**Phase 4 — cSploit‑like UX parity (incremental)** 
- Recreate **navigator** (host discovery, port scan) using **modern** libraries; throttle scans; require explicit scope. 
- Script/plugin surface: sandbox **heavily**; **no** `eval` of remote scripts.

**Phase 5 — NetHunter integration** 
- Document **shell recipes**: `bootkali` + `airodump-ng` etc. 
- Optional: thin **wrapper screens** that only **emit shell commands** the user reviews before run.

### 4.2 Testing matrix

| Environment | Purpose |
|-------------|---------|
| AVD API 34 | Non‑root UI / crash checks. |
| Rooted Pixel/OnePlus + **Magisk** | Root bridge, file ops. |
| **NetHunter** device | Real world: chroot paths, `su` policy, kernel tools. |

Automate: `./gradlew test`, static analysis (**lint**, **SpotBugs** if Java heavy), minimal **UI** test where stable.

---

## 5. Security review obligations (agent must schedule)

- **STRIDE** pass on MITM code paths. 
- **Command injection** audit on every `su` invocation. 
- **Certificate/Pinning** handling: default **no** trust of user CAs unless toggled in **lab mode**. 
- **Update mechanism:** If you re-enable “metasploit resource” style downloads, use **signature verification** and **allow‑list** hosts; prefer **operator‑hosted** mirrors.

---

## 6. Known upstream / reference URLs

- Archive app: https://github.com/cSploit/android 
- Organization index: https://github.com/cSploit 
- Evaluate **GitHub forks** graph for steal‑worthy commits (license‑compatible only); **do not** assume any fork is maintained—verify **last commit** and **buildability**.

---

## 7. Definition of done (per release milestone)

**Milestone R0 (tooling):** Clean `./gradlew assembleDebug` on JDK 17+, reproducible lockfiles, CI job green.

**Milestone R1 (device):** Installs on API 34 rooted device; core UI launches; no crash on rotation/storage permission denial.

**Milestone R2 (network lab):** User‑scoped **ping / port scan** against an explicit host in a **local lab** subnet; results logged.

**Milestone R3 (MITM beta):** One **documented** MITM path (VpnService **or** root iptables) works in lab; **rollback** on service stop.

**Milestone R4 (NetHunter doc):** Published runbook: “Install on NetHunter + Magisk; run helper script X; limitations Y.”

---

## 8. Agent operating rules

1. **Prefer small PRs:** Each PR must state **phase** + **risk** + **test evidence** (commands run). 
2. **Never silence security lint** without comment and issue link. 
3. **Feature flags** default **off** for destructive capabilities. 
4. **Compatibility:** If a change breaks NetHunter typical `su` path, document breakage in `CHANGELOG.md`. 
5. **When stuck >2 iterations:** Write an `ADR` (Architecture Decision Record) in `docs/adr/` and stop speculative refactors.

---

## 9. Out of scope for v1 revival

- Play Store publication (policy minefield). 
- iOS. 
- Full Metasploit RPC parity (optional far future). 
- **Bypassing** Play Integrity / bank apps (irrelevant to pentest tool integrity).

---

## 10. First commands for a fresh clone

```bash
git clone https://github.com/cSploit/android.git csploit-upstream-readonly
# Create your fork remote separately; do not force-push to archive.

cd csploit-upstream-readonly
./gradlew wrapper --gradle-version 8.7 # example only; verify compatibility before committing
# Expect failures until Phase 1–2; capture logs in docs/build-failures.md
```

---

**Summary for the agent:** Treat this as a **greenfield engineering** project **informed by** EOL cSploit, not a quick bump. **Author / zAnti** are not dependencies. Ship **provable** milestones; **MITM** work belongs behind flags and **lab‑only** docs. **NetHunter** is a **target platform**, not a CRUD dependency—integrate via **root shell** contracts you control and document.
