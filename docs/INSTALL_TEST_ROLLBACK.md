# Install, test and roll back EKO Home

Target: EKO K550USGQ, Android TV 11, approximately 1.5 GB RAM, ARM32 app support (user-supplied specifications). No physical television is connected to CI. Do not interpret emulator success as validation of this model.

## Get the debug APK

1. Open this repository's **Actions → Android debug APK** and choose a run with a successful build. Prefer a run where the emulator job also passed.
2. Download the **EKO-Home-debug-<commit>** artifact and extract it. The APK is at `app/build/outputs/apk/debug/app-debug.apk`. GitHub may require sign-in to download artifacts. Artifacts expire after 30 days; rerun the workflow if necessary.
3. Compare the APK's SHA-256 to `verification/SHA256SUMS` using `sha256sum app-debug.apk` (or PowerShell `Get-FileHash app-debug.apk -Algorithm SHA256`). Keep the commit ID, checksum and signature report with the APK. The artifact ZIP also has a GitHub digest; that is a different hash from the APK hash.
4. This is a debuggable, test-signed sideload build, not a Play Store or production-signed release. Hosted runners may generate different debug keys on different runs. If updating reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, uninstall only EKO Home and install again; this clears its input mappings. Never remove a stock package to solve that error.

## Install as an ordinary app first

Keep the stock launcher installed and enabled. Do not change the default Home until ordinary-app testing passes.

Option A: copy the extracted APK to USB and open it using your TV's existing file manager. Allow APK installation only for that installer if Android requests it, then turn that allowance off afterwards. Availability of USB/file installation depends on installed TV software.

Option B: use official Android SDK Platform Tools and the TV's developer-debugging method if available. Authorize your own computer only. For Android 11 televisions that expose classic network ADB, use the TV's actual local address and port, for example `adb connect TV_IP:5555`; this is firmware-dependent, not a guaranteed endpoint. Use a trusted home network, disconnect ADB and turn debugging off after testing.

```sh
adb devices
adb install app-debug.apk
adb shell am start -n com.eko.home/.MainActivity
```

Open EKO Home from the stock Apps row if not using ADB. Package: `com.eko.home`. Activity: `com.eko.home/.MainActivity`. No root, recovery changes, privileged permission grants, accessibility services, device administrator, boot receiver or package disabling are required or supported.

## Controls and HDMI

- D-pad moves focus; the selected tile becomes light. OK opens a tile. Back returns focus to PS5 at the launcher root; Back dismisses normal dialogs.
- The top row contains PS5 and Xbox. Select one to map an HDMI input reported by Android. Hold OK on the tile to change or clear its saved input.
- Input labels and IDs come from the firmware. EKO Home does not guess which connector has the console. Verify the selected input with the physical cable/TV input menu.
- A mapped tile sends an ACTION_VIEW request for an Android TV passthrough URI. The OEM TV app can ignore or reject it. Successfully starting an activity does **not** prove that the screen switched to the requested HDMI port.
- If no HDMI inputs are exposed, or switching fails, use the physical remote's Source/Input button. No hidden vendor intents, HDMI-CEC commands, console power control or capture/preview is implemented.
- Up to eight installed launchable apps are shown, known streaming packages first. All apps lists remaining launchable apps. Apps are refreshed on returning to EKO Home. Labels are used instead of downloaded artwork or large icon bitmaps.

## Optional Home selection (hardware-dependent)

In EKO Home select **Settings → Home app settings**, or use the TV's existing default-app settings if available. Android may offer a Home chooser when the remote Home button is pressed. Choose EKO Home only after testing, and keep the stock Home name recorded.

Some TV firmware ignores third-party HOME activities or reserves the remote Home button. If the setting is absent or ignored, keep EKO Home as an ordinary app. Do not force replacement by disabling the stock launcher, granting privileged permissions, or modifying firmware. No automatic boot or remote-key interception is implemented.

**Settings → Open stock / other Home** opens another visible Home activity explicitly; it does not change your saved default. Visibility and ability to launch that activity are also firmware-dependent.

## Physical TV acceptance checklist

Record date, firmware/build number, APK commit/hash and results. Leave each item unverified until observed on the television.

| Check | Procedure | Pass condition |
|---|---|---|
| ARM32 installation | Check `adb shell getprop ro.product.cpu.abilist` and install APK | Installs and launches on TV's 32-bit runtime |
| Layout and overscan | Inspect at actual resolution and TV picture settings | All text/controls visible, no clipping |
| Remote focus | Visit every tile and dialog using D-pad/OK/Back only | Clear focus, no trapped/unreachable control |
| Streaming handoff | Launch each streaming app, play content, return Home | Correct app opens and launcher resumes; DRM/playback is owned by that app |
| App changes | Install/remove an ordinary test app then return Home | App list refreshes without stale launch crash |
| Input mapping | Map each console to its known physical HDMI port | Saved mapping matches TV input menu |
| HDMI request | Start on a different source; select each mapped console | Correct physical input actually appears; otherwise record unsupported and use Source |
| Home chooser | Choose EKO Home only through exposed Android settings | Remote Home opens it without removing stock Home |
| Resume/power cycle | Return after streaming, standby and reboot | Expected behavior recorded; firmware may restore stock Home |
| Low memory | Run streaming apps, return repeatedly; capture meminfo | No launcher crash or persistent lag; record PSS, do not assume a budget |
| Rollback | Restore stock Home and uninstall EKO Home | Normal stock experience restored |

Optional read-only diagnostics:

```sh
adb shell dumpsys meminfo com.eko.home
adb shell dumpsys package com.eko.home
adb logcat -d -s AndroidRuntime:E
```

Review diagnostics for personal information before sharing. Do not clear or modify TV system packages.

## Rollback

1. Open TV settings (from EKO Home Settings or the remote), select the original Home app if the firmware exposes Home/default-app controls. Clear only EKO Home's defaults if needed and supported.
2. Open **Settings → Apps → EKO Home → Uninstall**, or run:

```sh
adb uninstall com.eko.home
```

3. Press Home and select the stock launcher if prompted. Reboot normally if needed. The original firmware, recovery and stock packages were not changed by this project. Removing EKO Home deletes its local input mappings only.
4. Disable developer/network debugging and any temporary unknown-app installation allowance. No factory reset should be needed.

## Build locally

Use JDK 17, Gradle 8.9, Android SDK platform 35 and build-tools 35.0.0. Versions are pinned in build files and CI. This repository currently uses an installed Gradle distribution rather than checking in a wrapper JAR; Android Studio can also import it with the matching Gradle version.

```sh
gradle --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python3 tools/verify_apk.py
```

Set ANDROID_HOME to the local SDK. The APK verifier checks the archive, application ID, API levels, TV launch entry, absence of requested permissions/native libraries, size under 5 MiB, signature validity and SHA-256. It does not prove that the app works on the physical TV.

## Implementation boundaries

Production dependencies are Kotlin stdlib and Android framework APIs. No Compose/WebView, advertising SDK, network permission, background service/job, boot receiver, wake lock or telemetry. One worker scans installed app metadata on resume and shuts down when the activity is destroyed; no periodic app scans run in the background. TextClock uses system time updates. The grid is capped at eight text-only tiles; remaining apps are in a dialog.

API 30 is the intentional target for this Android 11 sideload prototype; it is not a current Play Store submission configuration. DEX-only packaging avoids a 64-bit native ABI requirement, but ARM32 runtime and actual memory use still require the checklist above.

## API references

- [Android TV app manifest and launch entries](https://developer.android.com/training/tv/get-started/create)
- [TvInputManager input enumeration](https://developer.android.com/reference/android/media/tv/TvInputManager)
- [TvContract passthrough URI](https://developer.android.com/reference/android/media/tv/TvContract#buildChannelUriForPassthroughInput(java.lang.String))
