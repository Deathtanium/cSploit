# cSploit is now considered EOL and is not being updated by the developers. Current version might not be functional on newer Android versions. We appreciate everyone who contributed to cSploit over the years.

## Revival fork (this repository)

This tree is an **independent fork** for a **modern Android port** (see root `AGENTS.md`). Git remote **`upstream`** points at the archived [cSploit/android](https://github.com/cSploit/android) repository; add your own `origin` when you publish a fork. Work is organized in phases (audit → Gradle/target SDK → runtime → MITM). Use only on networks you own or are explicitly authorized to test.

### Overhaul summary (this fork, developer-facing)

- **NetHunter / tools:** Native core tarball flow is bypassed when tools run from the **NetHunter Kali chroot** (`NetHunterRuntime`, `NethunterChildRunner`, `ChildManager`). Manifest includes a **package visibility** query for `com.offsec.nethunter`. Chroot path is configurable in settings; see `docs/NETHUNTER_PACKAGES.md` and `docs/NETHUNTER_ONEPLUS5_RUNBOOK.md`.
- **Themes:** **Dark theme** aligned with the Hijacker-style palette (toolbar, status bar, accents). `AppTheme` / `DarkTheme` use consistent **surface / on-surface** and text attributes so lists, plugins, and settings stay readable; preference-specific text appearances for dark settings.
- **Layout / API 35+:** **`MainLayoutHelper`** centralizes the main shell (toolbar as support action bar, no accidental edge-to-edge overlap) and **`installInsetsForActivity`** so plugin activities, console, file pickers, MITM screens, etc. get **padding from system bars + app bar (+ IME)** instead of drawing under the in-app toolbar on high `targetSdk` devices.
- **Toasts:** **`ToastHelper`** wraps toasts with a **light overlay** where needed so messages are not white-on-white on dark activities.
- **Notifications:** Builders avoid **forced colorized** styling that produced unreadable contrast in places.
- **Alert dialogs:** **`Theme.CSploit.AlertDialog`** plus **`alertDialogTheme`** on app themes fixes **positive button (OK) text** inheriting the activity’s white `textColorPrimary` on a light dialog background.

---

# cSploit: Android network pentesting suite

<img src="http://i.imgur.com/cFll5P9.jpg" width="250" />

[cSploit](http://www.csploit.org) is a [free/libre](https://gnu.org/philosophy/free-sw.html) and open source (GPLed) Android network analysis and penetration suite which aims to be
**the most complete and advanced professional toolkit** for IT security experts/geeks to perform network security assessments on a mobile device.

See more at [www.cSploit.org](http://www.csploit.org).

## Features

* Map your local network
* Fingerprint hosts' operating systems and open ports
* Add your own hosts outside the local network
* Integrated traceroute
* **Integrated [Metasploit](https://www.metasploit.com/) framework RPCd**
  * Search hosts for **known vulnerabilities** via integrated Metasploit daemon
  * Adjust exploit settings, launch, and create shell consoles on exploited systems
  * More coming
* Forge TCP/UDP packets
* Perform man in the middle attacks (MITM) including:
  * Image, text, and video replacement-- replace your own content on unencrypted web pages
  * JavaScript injection-- add your own javascript to unencrypted web pages.
  * **password sniffing** ( with common protocols dissection )
  * Capture pcap network traffic files
  * Real time **traffic manipulation** to replace images/text/inject into web pages
  * DNS spoofing to redirect traffic to different domain
  * Break existing connections
  * Redirect traffic to another address
  * Session Hijacking-- listen for unencrypted cookies and clone them to take Web session

## Tutorials:

<img src="https://i.imgur.com/c0dxvXv.jpg" width="250" />

* [Use cSploit to get root shell on Metasploitable2](https://github.com/cSploit/android/wiki/%5BTutorial%5D-Use-cSploit-to-get-root-shell-on-Metasploitable2)
* [Use cSploit for simple Man-in-the-Middle (MITM security demos](https://github.com/cSploit/android/wiki/%5BTutorial%5D-Use-cSploit-for-simple-Man-In-The-Middle-(MITM)-security-demos)


Also see the [wiki](https://github.com/cSploit/android/wiki) for instructions on building, [reporting issues](https://github.com/cSploit/android/wiki/How-to-open-an-issue), and more.

## Requirements

* A **ROOTED** Android version 2.3 (Gingerbread) or a newer version
* The Android OS must have a [BusyBox](http://www.busybox.net/about.html) **full installation** with **every** utility installed (not the partial installation).  If you do not have busybox already, you can get it [here](https://play.google.com/store/apps/details?id=stericson.busybox) or [here](https://play.google.com/store/apps/details?id=com.jrummy.busybox.installer) (note cSploit does not endorse any busybox installer, these are just two we found).
* You must install SuperSU (it will work __only__ if you have it)

## Downloads

The latest release and pre-release versions are [available on GitHub](https://github.com/cSploit/android/releases).

Or to save a click, [this link](https://github.com/cSploit/android/releases/latest) should always point to the most recent release.

Additionally, you can get a fresh-from-the-source nightly at [www.cSploit.org/downloads](http://www.csploit.org/downloads).  These nightly builds are generated more frequently than the releases.  And while they may have the very latest features, they may also have the latest bugs, so be careful running them!

Moreover, the app is available in [the official F-Droid repo](https://f-droid.org/repository/browse/?fdid=org.csploit.android).

## How to contribute

All contributions are welcome, from code to documentation to graphics to design suggestions to bug reports.  Please use GitHub to its fullest-- contribute Pull Requests, contribute tutorials or other wiki content-- whatever you have to offer, we can use it!

## License

This program is free software: you can redistribute it and/or modify it under the terms of the [GNU General Public License](https://www.gnu.org/licenses/gpl) as published by [the Free Software Foundation](https://www.fsf.org/), either version 3 of the License, or (at your option) any later version.

## Copyright

Copyleft Margaritelli of Simone aka evilsocket and then fused with zANTI2 continued by @tux-mind and additional contributors.

## Support us

[![Click here to lend your support to:  cSploit, an open source penetration testing suite and make a donation at pledgie.com !](https://pledgie.com/campaigns/30393.png?skin_name=chrome)](https://pledgie.com/campaigns/30393)

[![Click here to lend your support to: cSploit and make a donation at www.paypal.com](https://www.paypalobjects.com/en_GB/i/btn/btn_donate_LG.gif?skin_name=chrome)](https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=FTKXDCBEDMW9G&lc=GB&item_name=cSploit&currency_code=EUR&bn=PP%2dDonationsBF%3abtn_donate_LG%2egif%3aNonHosted)

## Disclaimer

***Note: cSploit is intended to be used for legal security purposes only, and you should only use it to protect networks/hosts you own or have permission to test. Any other use is not the responsibility of the developer(s).  Be sure that you understand and are complying with the cSploit licenses and laws in your area.  In other words, don't be stupid, don't be an asshole, and use this tool responsibly and legally.***
