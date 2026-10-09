# 0.1.0-alpha2 — Experimental older-firmware upgrade

Adds **Update to Official 129.24 (Risky)** before the separate Apple patch step. Requires **GameSir X2 Pro Xbox / hardware 2.0 / slave 129.3 / mode 3** and the matching legacy USB updater. Newer firmware and 144-series controllers are refused.

Download **GameSir-X2-Pro-Patcher-0.1.0-alpha2.apk** below. Android 8.0+ with USB host support is required. Install over alpha1; the signing key is unchanged.

- A separate risk confirmation, fresh hardware/version checks, and a saved recovery record before DFU.
- Older firmware can receive only the bundled official image. The app writes/verifies all 557 blocks and checks the restarted controller. The Apple patch remains locked until MAIN 129.24 is verified.
- No automatic upgrade or patch installation; no PD/slave updates or arbitrary firmware imports.
- All 17 automated tests passed; Android lint and signed APK verification passed.
- **No older physical controller has tested the upgrade, and alpha2 has not had a live Android/controller run.** Alpha1's successful restore/install on a 129.24 controller does not prove older-loader compatibility. [Validation and eligibility details](https://github.com/hakami55/gamesir-x2pro-apple-patcher/blob/v0.1.0-alpha2/docs/VALIDATION.md).

**An incompatible or interrupted upgrade can leave the controller unusable. Recovery is not guaranteed.** There is no backup or restore to your previous version. Force-stop GameSir and other updaters first, keep the same controller connected, and approve USB prompts until completion.

Reported working: iPhone 15 Pro Max, iPhone 17 Pro, and iPad mini 6. Reported not working: iPhone 16, 16 Plus, and 16 Pro. iOS version alone does not explain the results. Compatibility and complete button mapping are not guaranteed.

Use Save Diagnostic Report and submit a test-result issue with your Android host, Apple model/OS and results. Nothing uploads automatically.
