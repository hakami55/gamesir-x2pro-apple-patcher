# Validation status

Validated on 2026-10-09. Release: **0.1.0-alpha1**.

## Build and package

- Signed release APK built successfully with JDK 17 / Gradle 8.13 / AGP 8.13.1.
- 10 automated protocol tests passed, zero failures. Tests include both real payloads, independent captured packet vectors, corrupt images, unsupported version fields, failed acknowledgements and timeouts.
- Android lint passed with zero errors. Advisory warnings remain for the chosen target/build versions and backup configuration.
- Release APK SHA-256: `6e0c6b777f3953d0e9b613625f5f04cd809c6afabd9c7e57eba58f7e5d9067e4`.
- APK signing certificate SHA-256: `1a40915c097a9b90b30de7b2c9ecbb140fdb74ffc3ea0fe625aa6c48e5bb0920`.
- No root commands, Internet permission, native helper, telemetry, or external storage permission. The document picker saves reports.

## Physical-controller test

Host: Odin3, Android 15 / API 35. This host is rooted, but **the app ran as an ordinary application and never requested or used root**. A clean, unrooted Android device has not yet been tested.

Controller: GameSir X2 Pro Xbox, hardware 2.0, MAIN 129.24, slave 129.3, mode 3.

1. Normal Android USB permission granted. Exact device/configuration and vendor HID descriptor checks passed; all three version/mode queries succeeded.
2. Restore Official Firmware: the app entered MAIN DFU and wrote/verified all 557 blocks. Finish was acknowledged. The GameSir app's competing permission windows and an automatic-launch prompt delayed the final permission check. After granting access, a fresh Check Controller verified official `3537:0102` descriptors and all supported version fields.
3. Removed this app's automatic USB-launch filter to prevent duplicate launch/permission prompts. Added immediate handling of a declined USB permission and clarified the instruction to force-stop competing updaters.
4. Installed the final signed APK. Check Controller passed on official firmware. Install Apple Patch entered DFU with an ordinary USB permission dialog, wrote and verified all 557 blocks, received finish acknowledgement, then verified the restarted `04b4:2412` controller and its MAIN/slave/hardware/mode fields in the same operation.
5. Save Diagnostic Report successfully wrote a text file through Android's document picker. See [the exported session report](hardware-test.txt).

**Final controller state: Apple-recognition patch installed and verified.** No PD or slave firmware was written.

## Limits

- This is one physical controller and one Android host. Other supported-version units and Android vendors remain public-test work.
- A deliberately interrupted live erase/write, sudden battery loss, and recovery after Android kills the process have not been physically tested. Failure handling is exercised by automated protocol tests; persistent DFU recovery is not a guarantee.
- The final APK's install cycle passed end-to-end. Restore's USB protocol passed on the immediately preceding app build; its final check was repeated after a permission-dialog interruption. Restore uses the same protocol engine with the other pinned image.
- Firmware controls were tested previously on PC/iPad. iPhone 17 Pro success is an owner report. No new Apple-device gameplay test was performed during app validation.
- Every button mapping on every Apple model is not validated. The iPhone 16 Pro / iOS 27.0.1 failure remains unresolved.
