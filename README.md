<h1 align="center">🔔 Doraemon</h1>
<p align="center"><b><i>The Ultimate Non-Root Android Power-User Ecosystem &amp; ADB Manager.</i></b></p>

<p align="center">
  <a href="https://github.com/aliyanhadi/doraemon/releases"><img src="https://img.shields.io/github/v/release/aliyanhadi/doraemon?style=for-the-badge&color=0096E0&label=Release" alt="GitHub Release"/></a>
  <img src="https://img.shields.io/badge/license-GPL--3.0-blue?style=for-the-badge" alt="License GPL-3.0"/>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 8.0+"/>
  <a href="https://github.com/aliyanhadi/doraemon/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/aliyanhadi/doraemon/build.yml?style=for-the-badge&label=Build" alt="Build Status"/></a>
  <img src="https://img.shields.io/badge/Safety-Guarded%20(protected%20list)-FFD500?style=for-the-badge" alt="Safety Rating"/>
</p>

---

## 1. What is Doraemon?

Doraemon is a single pocket full of "gadgets" for your phone. It turns **Wireless ADB** into a persistent, on-device
control hub, so you can debloat, tune, script, theme and inspect Android **without root and without a PC** (after the
one-time pairing).

* **UI:** Kotlin + Jetpack Compose, OLED-black with Doraemon blue / red / bell-yellow accents
* **Core:** Java foreground daemon + Kotlin shell layer (ADB → `su` fallback) + Dhizuku Device-Owner bridge
* **Min Android:** 8.0 (API 26). Wireless pairing needs Android 11+.

---

## 2. Feature Breakdown

| # | Gadget | Engine | What it does |
|---|--------|--------|--------------|
| 1 | **Debloater** | Canta-style | Lists system &amp; user packages. *Disable* (`pm disable-user`), *Uninstall for user 0* (`pm uninstall -k --user 0`) and *Restore* (`cmd package install-existing`). A built-in protected list blocks Play Services, telephony, SystemUI, Settings, providers, etc. |
| 2 | **Terminal** | aShell-style | Full ADB shell command runner with exit codes, history and quick-command chips. Falls back to `su` if ADB is offline and root exists. |
| 3 | **Performance** | Gamma-style | Eco / Balanced / Turbo profiles: battery saver, animation scales, fixed-performance mode (Android 12+). CPU governor switching when root is present. Battery temperature and load readout. |
| 4 | **Automation** | Geto-style | Saved shell macros (`sleep`, comments, one command per line). Run manually or automatically **whenever ADB (re)connects**. |
| 5 | **Customize** | Hex-style | Dark/light mode, font scale, display density, navigation mode and a toggle list of system overlays through `cmd overlay`. |
| 6 | **Apps &amp; Permissions** | App-Manager-style | Per-app permission inspector with **per-scope grant/revoke** (runtime permissions + AppOps), a scope audit log and a **split-APK installer** (streams `base.apk` + splits into one install session). |
| 7 | **Files** | Rootless explorer | Browse `/sdcard`, `Android/data`, `/data/local/tmp` and more through the ADB shell. |

### The TCP Watchdog &amp; "No-Reset" startup

Android turns Wireless Debugging off on reboot and whenever Wi-Fi changes. Doraemon fights that with three layers:

1. **`AdbDaemonService` (foreground, `connectedDevice`)** loops every 15 s: if the link is down it reconnects
   (explicit port → mDNS auto-discovery → saved port → `127.0.0.1:5555`).
2. **Self-granted `WRITE_SECURE_SETTINGS`.** On the first successful connection Doraemon runs
   `pm grant com.doraemon.adb android.permission.WRITE_SECURE_SETTINGS` on itself. From then on the daemon can flip
   `adb_wifi_enabled` back on by itself whenever you are on Wi-Fi, so you do not need to touch Developer options again.
3. **`WatchdogReceiver`** restarts the daemon after boot / app update / a 15-minute alarm, and nudges the loop on every
   Wi-Fi network-state change.

> **Honest limits:** Wireless Debugging only works while the phone is on a Wi-Fi network. Some OEMs revoke the pairing
> after a reboot, and Android 12+ restricts starting foreground services from the background. If the watchdog is blocked,
> open the app once and tap *Start daemon*.

---

## 3. Shizuku vs. Dhizuku vs. Doraemon

| Capability | Shizuku | Dhizuku | **Doraemon** |
|---|---|---|---|
| Connection persistence across reboots | Root: yes · Wireless: must be restarted manually | Needs Device Owner set once (persists) | **Automatic**: watchdog + self-toggling Wireless Debugging (Wi-Fi required) |
| Device Owner (Admin) API support | No | **Yes** (its purpose) | Yes, through the Dhizuku bridge (hide/unhide apps, experimental) |
| Integrated app hub &amp; debloater | No (API provider only) | No | **Yes** |
| Granular API scope management | Per-app authorization | Per-app authorization | **Per-permission / per-AppOp** scoping with audit log |
| Built-in ADB terminal &amp; script automation | No | No | **Yes** |
| Battery &amp; background overhead | Low (service only) | Very low | Low-moderate: one foreground service + 15 s check loop (stop it when idle) |

Doraemon does **not** replace Shizuku/Dhizuku as an *API provider for other apps*. It is a self-contained power tool that
also bridges to Dhizuku and `su` when they are available.

---

## 4. Safety Guidelines &amp; Rules ⚠️

**Who this is for:** power users, developers and customization enthusiasts who understand what ADB, packages and
permissions are and are comfortable recovering a phone.

**Who should NOT use it:** anyone who is not sure what a package does. A wrong removal can cause boot loops, lost
calls/SMS, a dead lock screen or a broken Google account.

**Rules:**
1. Never remove or disable core services: **Google Play Services / Services Framework, Telephony (`com.android.phone`,
   telecom), SystemUI, Settings, display/launcher components, package installer, permission controller, providers.**
   Doraemon blocks these, but OEM-specific critical packages may not be on the list.
2. Prefer **Disable** over **Uninstall**; use Uninstall only for packages you have researched. Both are per-user and can be
   reverted with **Restore**.
3. Make a backup and note the package names you touch. A factory reset also restores everything.
4. Revoke only permissions you understand; some apps crash if a core permission is revoked.
5. Density, overlay and navigation changes can make the UI unusable: keep an ADB-capable PC handy for `wm density reset`.
6. Only pair with devices you own. Never share your pairing code or leave Wireless Debugging on in public Wi-Fi.

---

## 5. Installation &amp; Pairing Guide

### Get the app
* **Releases:** <https://github.com/aliyanhadi/doraemon/releases> (download `Doraemon-*.apk`).
* **Build yourself:** push to GitHub — the *Build Doraemon APK* workflow produces a debug APK artifact, or run
  `gradle wrapper --gradle-version 8.7 && ./gradlew assembleDebug` locally (JDK 17 + Android SDK 34).

### Wireless ADB (Android 11+)
1. Settings → About phone → tap **Build number** 7 times.
2. Developer options → turn on **Wireless debugging** (join a Wi-Fi network first).
3. Open Doraemon → **Pair &amp; Connect** → **Start notification pairing**.
4. Go to Wireless debugging → **Pair device with pairing code**. A Doraemon notification appears with a code box
   (like Shizuku). Type the 6-digit code **in the notification**, so the Settings dialog never closes.
5. Doraemon pairs, connects and starts the daemon automatically.
   *(Fallback: enter the dialog's port and code manually under "Manual pairing".)*

### USB ADB (Android 8–10)
1. Enable **USB debugging** in Developer options and connect to a PC once.
2. Run `adb tcpip 5555` on the PC, then disconnect the cable.
3. In Doraemon tap **Connect** (it tries `127.0.0.1:5555`).
4. *This resets on reboot* — repeat step 2 after restarting the phone.

### Optional bridges
* **Root:** Pair &amp; Connect → *Check root* (used automatically if ADB is offline).
* **Dhizuku:** install Dhizuku, activate it, then Pair &amp; Connect → *Dhizuku* to grant access.

---

## 6. Rating &amp; Community Consensus

**Design rating: 9.5 / 10** (maintainer's assessment, pending real community scores — please open a Discussion with yours).

| Criterion | Score | Why |
|---|---|---|
| Utility | 10 | Seven day-to-day power tools in one app, no PC required after pairing |
| Persistence | 9 | Self-healing Wireless Debugging via the secure-settings trick |
| Integration | 10 | One shell layer shared by every gadget; ADB → root → Dhizuku fallback |
| Safety | 9 | Protected-package list, input sanitising, reversible actions |

**Engineering challenges (why not 10):** Wireless Debugging needs Wi-Fi; OEM battery managers can kill background
services; Android 12+ background-FGS limits; Dhizuku calls use hidden APIs that vary per Android version.

---

## 7. Roadmap

- [ ] Cloud backup/sync of automation scripts
- [ ] Web-UI terminal / remote desktop over LAN
- [ ] Automated OEM debloat presets (Samsung / Xiaomi / Realme / OnePlus)
- [ ] Interval and Wi-Fi-SSID triggers for automation
- [ ] Archive extraction + file push/pull in Files
- [ ] Per-game performance profiles (`cmd game`)

---

## Project structure

```
Doraemon/
├── README.md
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/doraemon/adb/
│           ├── MainActivity.kt, DoraemonApp.kt
│           ├── core/   AdbDaemonService.java · AdbManager.java · WatchdogReceiver.kt
│           │           DhizukuBridge.java · ShellBackend.kt · Connector.kt
│           ├── tools/  DebloaterEngine · TerminalEngine · PerformanceEngine
│           │           PackageEngine · AutomationEngine · CustomizationEngine
│           └── ui/     screens/ (Dashboard, Pair, Tools…) · theme/Theme.kt
└── .github/workflows/build.yml
```

## Credits &amp; license

Inspired by Shizuku, Dhizuku, Canta, App Manager, Geto, Hex Installer, ZArchiver and aShell.
Wireless pairing uses [libadb-android](https://github.com/MuntashirAkon/libadb-android).
Doraemon is an independent fan-style project and is not affiliated with Fujiko Pro / Shogakukan.
Licensed under **GPL-3.0**.
