# NetHunter Kali packages for cSploit revival

cSploit runs **only** inside the NetHunter Kali chroot (`bootkali_init` + `chroot`). Install the following **inside the chroot** (e.g. `apt update && apt install …` from a root shell in Kali, or use NetHunter’s package manager / `chroot` helper).

Use only on networks you own or have **written permission** to test.

## Required (core scanning / discovery)

| Debian package | Provides | Used for |
|----------------|----------|----------|
| `nmap` | `nmap` | Port scan, traceroute, service/OS detection |
| `arp-scan` | `arp-scan` | Network radar (host discovery on the LAN) |

## Strongly recommended (MITM / spoofing stack)

| Debian package | Provides | Used for |
|----------------|----------|----------|
| `dsniff` | `arpspoof` | ARP spoofing (often pulled in with deps) |
| `ettercap-common` | `ettercap`, plugins | Ettercap MITM (UI may be TUI; cSploit drives CLI flags) |

On some images the meta-package `ettercap-text-only` or installing `ettercap-common` without full GUI is enough; match what your Kali image supports.

## Optional (password attacks, capture, MSF)

| Debian package | Provides | Used for |
|----------------|----------|----------|
| `hydra` | `hydra` | Login cracker |
| `tcpdump` | `tcpdump` | Packet capture |
| `metasploit-framework` | `msfrpcd`, `msfconsole` | Local Metasploit RPC (or point the app at a **remote** `msfrpcd` instead) |

## Rare / advanced

| Debian package | Provides | Used for |
|----------------|----------|----------|
| `fuse` / fuse helpers | `fusemounts` | Only if you use features that invoke the legacy `fusemounts` handler |

If a tool is missing, the corresponding plugin may fail at runtime with a non‑zero exit or empty output.

## One-liner (typical lab chroot)

```bash
sudo apt update && sudo apt install -y \
  nmap arp-scan tcpdump hydra dsniff ettercap-common
```

Add `metasploit-framework` if you want **local** `msfrpcd` (large download).

## Verify from Android (root)

After NetHunter `bootkali` / chroot:

```bash
chroot /data/local/nhsystem/kali-arm64 /bin/bash -lc 'which nmap arp-scan hydra arpspoof ettercap tcpdump msfrpcd 2>/dev/null'
```

Paths must resolve inside the chroot. Adjust `kali-arm64` if your rootfs name differs; mirror the path in **Settings → Kali chroot directory**.
