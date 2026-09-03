<div align="center">

# ⏱️ Android Screen Timer

### TV, tablet, or phone — family screen time under control.

**Free family screen-time control for Android**<br>
with parent codes, viewing reminders, per-app limits, discreet launcher profiles, and uninstall protection.

**Hard to guess. Hard to disable through ordinary settings. Device Owner blocks ordinary uninstall attempts.**

[![CI](https://github.com/alexaistudio/tvtimer/actions/workflows/ci.yml/badge.svg)](https://github.com/alexaistudio/tvtimer/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/alexaistudio/tvtimer?sort=semver&display_name=tag&label=release)](https://github.com/alexaistudio/tvtimer/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/alexaistudio/tvtimer/total?label=downloads)](https://github.com/alexaistudio/tvtimer/releases)
[![License](https://img.shields.io/badge/license-PolyForm_Strict_1.0.0-2563eb)](LICENSE.md)

![Android](https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&logoColor=white)
![Android TV](https://img.shields.io/badge/Android_TV-supported-3DDC84)
![Google TV](https://img.shields.io/badge/Google_TV-supported-4285F4?logo=google&logoColor=white)
![Tablet](https://img.shields.io/badge/tablets-supported-2563eb)
![Phone](https://img.shields.io/badge/phones-supported-7c3aed)
![Controls](https://img.shields.io/badge/control-touch_%7C_remote-f59e0b)
![Languages](https://img.shields.io/badge/languages-RU_%7C_EN-7c3aed)
![Offline](https://img.shields.io/badge/data-local_only-059669)
![No ads](https://img.shields.io/badge/ads-none-16a34a)
![No tracking](https://img.shields.io/badge/tracking-none-16a34a)

[**⬇️ Download the APK**](https://github.com/alexaistudio/tvtimer/releases/latest) ·
[Maximum uninstall protection](#maximum-uninstall-protection-device-owner) ·
[💚 Support development](#support-development) ·
[🇷🇺 Русский README](README.md)

</div>

---

## Your child lost track of time? The timer will not.

Set the rules once. Android Screen Timer counts real viewing time on a TV, tablet, or phone, reminds your child to pause, and closes controlled apps when the limit is over.

- ⏱️ **One daily limit** for the whole device or only selected apps such as YouTube and games.
- 🔐 **A phone code works for up to 5 minutes**, making it practical to enter with a remote or touchscreen, and then it expires. A separate backup PIN remains available.
- 👨‍👩‍👧‍👦 **Several parents can connect** by scanning the same private QR setup on more than one phone.
- ➕ **Add extra time immediately** — 10, 15, 20, 30, 40, or 60 minutes, automatically or by choosing after every parent code.
- 🔔 **Simple viewing reminders** every 10, 20, or 30 minutes ask the child whether to continue or finish.
- 🥷 **Ten names and icons**: Android Screen Timer, Calculator, Media Service, Clock, Weather, Notes, Calendar, Files, Gallery, or Help.
- 🛡️ **A child cannot simply open settings and remove the limiter** — Android settings and the package installer require a parent code; Device Owner enables Android-level uninstall blocking.
- 🔌 **USB emergency recovery** gives a parent a physical way to clear local protection and settings on devices with USB host support.
- 📴 **No account and no tracking** — no ads, analytics, telemetry, cloud storage, or remote TOTP processing.
- 🌍 **Russian and English UI**, switchable from the first screen, settings, app picker, and every full-screen parent-code panel.

> Maximum uninstall protection requires Device Owner mode. A regular Android app cannot guarantee that it will block its own uninstall without this managed-device role. Launcher disguise changes the home-screen tile but does not hide the package from Android's system app list.

## Features

- A unique local QR setup for Google Authenticator, Microsoft Authenticator, Aegis, and compatible TOTP apps.
- The same active QR can be opened again in settings and added to several parents' phones without invalidating existing phones.
- A new six-digit phone code appears every 30 seconds. Each recently generated code is accepted for no longer than five minutes, so remote entry is comfortable without storing a cache of old codes.
- A 4–8 digit backup parent PIN stored only as a salted PBKDF2 hash.
- Daily limits from 1 to 1,440 minutes.
- A separate app-selection screen; pressing Back on the device or remote saves the checked apps immediately.
- Touchscreen or remote limit adjustment with `−15`, `−1`, `+1`, and `+15` buttons.
- A small remaining-time counter over the active controlled app.
- Optional automatic extra time or a choice of 10, 15, 20, 30, 40, or 60 minutes after successful parent verification.
- Optional child reminders every 10, 20, or 30 minutes of actual viewing.
- Usage pauses while the screen is off or a screensaver is active and is persisted every five seconds.
- Android settings and the package installer are covered by a parent-code overlay.
- Optional Device Admin and Device Owner protection.
- Ten built-in launcher profiles with localized names, separate icons, and TV banners.
- Manual secure update checking through GitHub Releases with SHA-256, package ID, version, and signing-certificate verification before the installer opens.
- No accounts, advertising, analytics, telemetry, or cloud service. Network access is used only after the parent selects “Check for updates”.

## Compatibility and limitations

Minimum supported version: Android 6.0 / API 23. The project targets Android SDK 35. The responsive interface supports Android TV, Google TV, tablets, and phones with touch, remote, mouse, or keyboard control.

Without root or Device Owner mode, a third-party Android app cannot guarantee that it will block its own uninstall. In regular mode, Android Screen Timer protects system settings and the installer with the PIN/phone-code screen, while Device Admin adds an extra system deactivation step. Device Owner provides the real Android-level package uninstall block. Safe mode, factory reset, and manufacturer-specific behavior must still be tested on the target device.

On devices with USB host support, the guaranteed emergency path is a USB flash drive: connecting one clears the local configuration and requests removal of Device Admin protection. USB keyboard, game-controller, and wireless-receiver events depend on the firmware. Leaving a flash drive connected may trigger recovery again when Android remounts it.

## Installation

1. Download the signed APK from [GitHub Releases](https://github.com/alexaistudio/tvtimer/releases).
2. Install it from a USB drive or through ADB:

   ```powershell
   adb install -r TVTimer-1.3.0.apk
   ```

3. Open Android Screen Timer, select `EN`, and scan the first QR code with an authenticator app on a parent's phone.
4. Set the backup PIN, daily limit, and controlled app scope.
5. Confirm the accessibility-service disclosure.
6. On Android 13+, a GitHub-sideloaded APK may require `App info` → `⋮` → `Allow restricted settings` first.
7. Enable “Android Screen Timer control” in the Android system page that opens.
8. Return to Android Screen Timer and confirm that the control service is enabled.

### Maximum uninstall protection (Device Owner)

Device Owner is intended for an Android device that can be prepared as a managed device. Android normally refuses this command on an existing profile with accounts, so a clean profile or factory reset may be required. Install the APK before adding accounts, then run:

```powershell
adb shell dpm set-device-owner dev.tvtimer.app/.TimerDeviceAdminReceiver
adb shell dpm list-owners
```

Open Android Screen Timer and verify that the settings page reports Android-level uninstall blocking. On devices with USB host support, a USB flash drive remains the emergency key that clears the configuration, releases the uninstall block, and removes Device Owner ownership.

Launcher disguise is selected after parent authentication in Android Screen Timer settings. Android does not let an installed APK choose an arbitrary name or icon during installation, so the app includes ten safe built-in profiles. The package remains visible in Android's system app list; some launchers may need a Home-screen restart before the new icon appears.

## Building

The project requires JDK 17, Android SDK Platform 35, and Build Tools 35.0.0. The Gradle wrapper is included; the Android SDK and signing secrets are not.

Windows PowerShell:

```powershell
$env:ANDROID_HOME='C:\Android\Sdk'
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Linux/macOS:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The complete local verification command on Windows is:

```powershell
.\scripts\verify.ps1
```

Release signing is read from the ignored `signing/release.properties` file or the `TVTIMER_KEYSTORE_*` environment variables. The release task fails intentionally when signing is incomplete, preventing an unsigned or debug APK from being published as stable.

## Data and permissions

Settings, the TOTP secret, PIN hash, selected language, and daily counter stay in private app storage with backup and device transfer disabled. TOTP codes and QR data are never sent over the network.

- `RECEIVE_BOOT_COMPLETED` restores the daily state after boot without opening an Activity.
- `INTERNET` is used only for a parent-triggered GitHub release check and download.
- `REQUEST_INSTALL_PACKAGES` passes an already verified APK to the Android installer after separate parent approval.
- The accessibility service receives only the active package name and displays an accessibility overlay.
- Narrow launcher queries populate the app-selection list.

Android Screen Timer does not read window contents, request Usage Access, or use the regular “draw over other apps” permission.

## Project structure and verification

See [Architecture](docs/ARCHITECTURE.md), [Research](docs/RESEARCH.md), and the [Changelog](CHANGELOG.md). CI runs privacy checks, unit tests, Android Lint, and a debug build.

## Support development

Android Screen Timer stays free for personal and family use. If it helps your family, you can voluntarily support development with **USDT TRC-20**.

**Network:** TRON<br>
**Address:** `TMoM4t1JsevXo42cRBiYue51NXrsjuGhqd`

<p align="center">
  <img src="docs/usdt-trc20-qr.png" alt="QR code for the USDT TRC-20 address on TRON" width="240">
</p>

The QR code contains only the address shown above. Before sending, make sure the wallet network is **TRON (TRC-20)**.

## License

Android Screen Timer is free for unmodified personal, family, and other noncommercial use under the [PolyForm Strict License 1.0.0](LICENSE.md). Commercial use, modified redistribution, and derivative works require separate written permission. The license text takes precedence over this summary.
