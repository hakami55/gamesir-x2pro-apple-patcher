# 0.1.0-alpha1 — Android public-test preview

An offline Android app to install the experimental Apple-recognition patch on the tested **GameSir X2 Pro Xbox / hardware 2.0 / MAIN 129.24 / slave 129.3 / mode 3** profile, or restore official MAIN 129.24.

Download **GameSir-X2-Pro-Patcher-0.1.0-alpha1.apk** below. Android 8.0+ with USB host support is required. The app requests normal USB access, not root.

- Controller/version checks and pinned firmware hashes; unsupported versions are refused.
- Write + verify for every block; normal-mode verification after restart.
- Explicit restore option and diagnostic export.
- 10 automated protocol tests passed. The Android app restored official firmware and reinstalled the patch on an Odin3 without requesting root. The Odin3 itself is rooted; a clean unrooted host has not yet been tested.
- Report export tested. [Full validation details](https://github.com/hakami55/gamesir-x2pro-apple-patcher/blob/v0.1.0-alpha1/docs/VALIDATION.md).

**Experimental firmware can leave a controller unusable if an update fails.** Force-stop GameSir and other updaters in Android App info first. Keep the same controller connected throughout and approve USB permission again when prompted. Restore is a known manufacturer MAIN image, not a complete controller backup. Do not use on 144-series controllers or other GameSir models.

Patch reports: iPadOS 26.6.2 and iPhone 17 Pro / iOS 26.5.1 work; iPhone 16 Pro / iOS 27.0.1 still fails. Compatibility and complete button mapping are not guaranteed.

Use Save Diagnostic Report and submit a test-result issue with your Android host, Apple model/OS and results. Nothing uploads automatically.
