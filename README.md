# GameSir X2 Pro Apple Patcher — Android

An experimental, offline Android USB updater for **one tested GameSir X2 Pro Xbox revision**. It installs a small Apple-recognition patch or restores official MAIN firmware 129.24. The app uses Android's USB permission dialog. It contains no root commands, advertisements, analytics, account system or network permission.

**This is community software, not an official GameSir update.** Interrupted or incompatible flashing can leave a controller unusable. Recovery is not guaranteed. Do not use it on another GameSir model or on 144-series firmware.

## Download and use

Get the APK from [Releases](https://github.com/hakami55/gamesir-x2pro-apple-patcher/releases). Check the release's test status before installing. Android may ask you to allow installation from the browser/file manager you use.

1. **Force stop** the GameSir app and other controller updaters in Android Settings → Apps → App info. Swiping away their window can leave a USB service running. If another updater asks for access during this test, decline its prompt; allow access only for **X2 Pro Patcher**.
2. Connect the controller's **movable, phone-facing USB-C plug** directly to an Android device with USB host support. Leave hubs and chargers disconnected.
3. Open **X2 Pro Patcher**, tap **Check Controller**, and allow USB access.
4. If the checks pass, tap **Install Apple Patch** and read the confirmation.
5. Keep the same controller connected and the app open. Allow USB access again when its update interface appears and after it restarts. The app must confirm both block verification and the restarted controller.
6. Disconnect only after completion. Test your buttons, sticks and triggers on your Apple device.

Use **Restore Official Firmware** to restore the bundled official MAIN 129.24 image. This is a manufacturer update image, **not a backup of your individual controller**. This app does not update the PD/charging controller, slave controller, calibration storage or bootloader directly.

## Supported starting configuration

All checks must match; there is no force-flash option.

| Item | Required |
| --- | --- |
| Controller | GameSir-X2 Pro-Xbox, manufacturer `GAMESIR` |
| Hardware | 2.0 |
| MAIN firmware | 129.24 |
| Slave firmware | 129.3 |
| Controller mode | 3 |
| Normal identity | Official `3537:0102` or this patch's `04b4:2412` |
| USB configuration | Exact tested descriptors and 24-byte vendor HID report |
| Android | Android 8.0 / API 26 or later, USB host support |

The tested unit reports serial `001`; this is checked but is **not treated as a unique per-device identity**. Do not swap controllers during an operation or its recovery. Version/descriptor checks do not establish a byte-for-byte backup of the installed firmware.

## Compatibility observations

These describe the firmware, not universal compatibility or the validation status of each APK release.

| Apple device | OS | Reported outcome |
| --- | --- | --- |
| iPad (`iPad14,1`) | iPadOS 26.6.2 | Controls work; normal enumeration also captured |
| iPhone 17 Pro | iOS 26.5.1 | User reports controls work |
| iPhone 16 Pro | iOS 27.0.1 | No controls; blinking light |

Every button mapping has not been validated on every device. In particular, Home, stick clicks and triggers may map differently. The change does not add independent rear-button support. GameSir's own apps may stop recognizing the changed USB identity. This is not the retired DS5-protocol experiment.

## What the patch changes

The MAIN image remains 33,388 bytes. Exactly 29 bytes change, all in the normal USB/HID descriptors: device class, VID/PID/revision and declared button fields. Executable code, updater report descriptor and bootloader handoff code are unchanged. The identity corresponds to the published Flydigi mobile-controller recognition approach; this is not a claim that the hardware is a Flydigi controller or that Apple certifies it.

| Image | SHA-256 of updater transport bytes |
| --- | --- |
| Official MAIN 129.24 | `5ff4c283062edb95c63ff1fa8f0cb2a90568089cc464ce08b803558c6b76567a` |
| Apple patch | `f48477d9abb2a1db369b66103b46533060bf01d0b0c7262758cb7b5756e7da18` |

These files use the legacy updater's **transport encoding**. They are not plaintext MCU images. Do not flash them with a generic programmer or another model's updater.

## Recovery and errors

- A stopped version check sends no erase command. Close competing USB apps, reconnect, and check again.
- The app verifies the image hash before entering MAIN DFU. It sends a write followed by a verify command for each of 557 blocks. A missing or rejected reply stops the operation; there is no blind automatic retry.
- Before entering DFU, the app stores a private recovery record. If interrupted, keep the same controller, reopen the app, check it, then explicitly choose Install or Restore. An already-DFU device without this record is refused because its hardware/version cannot be established from its DFU name alone.
- Do not clear app data or uninstall during recovery; that removes the record. A missing record or inaccessible updater requires investigation outside this app. Do not short board pads or substitute a different firmware.
- The separate `X2 PRO PD DFU` interface (`05ac:063d`) is deliberately unsupported.

## Report a result

Use **Save Diagnostic Report**, optionally enter your Apple model and OS, and attach the text to a [test report](https://github.com/hakami55/gamesir-x2pro-apple-patcher/issues/new?template=test-report.yml). Reports are saved through Android's document picker; nothing uploads automatically. Review your optional free-text entries before posting. No account, IP address, USB path or device serial is collected by the report.

## Build

Requires JDK 17 and Android SDK platform 36. The Gradle wrapper pins Gradle 8.13 and Android Gradle Plugin 8.13.1.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

On Windows use `gradlew.bat`. The debug APK is under `app/build/outputs/apk/debug/`.

For a signed release, set `PATCHER_KEYSTORE`, `PATCHER_STORE_PASSWORD`, and `PATCHER_KEY_PASSWORD`. The key alias is `patcher`. Then run `./gradlew :app:assembleRelease`. Keep signing keys and passwords outside the repository. Published release APKs use a private release key, not the debug key.

## Evidence and scope

See [protocol notes](docs/PROTOCOL.md), [validation status](docs/VALIDATION.md), and [firmware provenance](THIRD_PARTY_NOTICES.md). Pure-Java tests replay both real image fixtures, compare independent first/last packet vectors and inject failures at erase, write, verify and finish stages. Tests do not substitute for real Android USB permission, detach/reconnect and recovery tests.

Recognition references: [Flydigi macOS bridge](https://github.com/Sisyphu5s/flydigi-back-buttons-macos-bridge) and [GameSir G7 Pro macOS work](https://github.com/arcataroger/gamesir-g7-pro-bluetooth-for-macos/blob/main/src/Core.swift). Neither project certifies this patch.
