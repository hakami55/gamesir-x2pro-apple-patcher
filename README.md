# GameSir X2 Pro Apple Patcher — Android

An experimental Android app to install the Apple compatibility patch or restore official firmware. Uses normal Android USB permission; no root required.

**[Download APK](https://github.com/hakami55/gamesir-x2pro-apple-patcher/releases/download/v0.1.0-alpha1/GameSir-X2-Pro-Patcher-0.1.0-alpha1.apk)** · [Releases](https://github.com/hakami55/gamesir-x2pro-apple-patcher/releases)

## Supported controller

| Item | Required |
| --- | --- |
| Controller | GameSir-X2 Pro-Xbox, manufacturer `GAMESIR` |
| Hardware | 2.0 |
| MAIN firmware | 129.24 |

## Apple compatibility

| OS | Reported result |
| --- | --- |
| iOS / iPadOS 26 | Works |
| iOS 27 | Does not work |

Results are from tested devices; compatibility may vary.

## How to use

1. Force-stop the GameSir app and connect the controller directly to Android.
2. Open **X2 Pro Patcher**, tap **Check Controller**, and allow USB access.
3. Tap **Install Apple Patch**. Keep it connected and approve USB prompts until completion.
4. Test the controller on your Apple device.

Use **Restore Official Firmware** to return to the bundled official firmware.

This is an unofficial experimental update. Do not disconnect during flashing.

## Reports

Tap **Save Diagnostic Report**, then attach it to a [GitHub test report](https://github.com/hakami55/gamesir-x2pro-apple-patcher/issues/new?template=test-report.yml). Reports are not uploaded automatically.

[Test details](docs/VALIDATION.md) · [Technical notes](docs/PROTOCOL.md) · [Firmware provenance](THIRD_PARTY_NOTICES.md)
