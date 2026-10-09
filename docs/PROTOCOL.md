# Legacy X2 Pro MAIN protocol

Install and Restore target the measured 129.24 / hardware 2.0 / slave 129.3 profile. The separate experimental Upgrade action accepts numerically older MAIN reports subject to the checks below. The device names containing "DFU" refer to GameSir's custom HID updater, not the USB DFU class standard.

Normal mode: `3537:0102` (official) or `04b4:2412` (patch), vendor HID interface 1. MAIN DFU: `05ac:063c`, product `X2 PRO USB DFU`, interface 0. Both have 64-byte interrupt endpoints and the 24-byte vendor report descriptor:

```
0600ff0900a101150025ff750895400900810209009102c0
```

Android UsbRequest operates on raw USB payloads: commands are zero-padded to 64 bytes, **without** the synthetic leading report-ID byte required by Linux hidraw / Windows HID APIs. Replies can be shorter than 64 bytes. Input is queued before output. Each transfer has a deadline; uncompleted requests are cancelled and closed on failure.

| Command | Use | Expected reply |
| --- | --- | --- |
| `01 01` | MAIN / hardware query | At least 20 bytes; `C9` prefix; MAIN major/minor at 12/11; hardware at 8/7 |
| `20 9F` | Slave version | `20 9F 81 03` prefix |
| `09 00` | Read mode (no change) | `09 03` prefix |
| `03 01` | Enter MAIN updater | USB detach/re-enumeration; no reply required |
| `81` | Erase MAIN | `81 00` |
| `80 len addrLo addrHi data` | Write up to 60 transport bytes | `80 00` |
| `82 len addrLo addrHi data` | Loader verification of same block | `82 00` |
| `83` | Finish/restart | `83 00`, followed by normal-mode detection |

Image length is 33,388 bytes: 556 full 60-byte blocks plus a final 28-byte block at address `0x8250`. A valid image yields 1,116 request/reply exchanges including erase and finish. Every write is followed immediately by verification. No automatic retry follows an ambiguous timeout.

No `03 FF` PD handoff, PD erase, slave write, arbitrary packet console, unverified image import or force-flash switch is provided. The app keeps a recovery receipt before switching modes. The serial `001` is shared/nonunique, so the receipt is not cryptographic proof of physical device identity; the user must keep the same checked controller connected.

The bundled official image is an update image, not a complete device backup. Device-side verify acknowledgements plus post-update descriptors/version checks are the success criteria; the app does not independently read back every flash byte.

## Experimental older-firmware upgrade (alpha2)

The user must first Check Controller, then explicitly accept the separate risky-upgrade confirmation. No upgrade or Apple patch runs automatically.

- The source must use the original `3537:0102` GameSir identity, the same product/manufacturer/serial strings, hardware 2.0, slave 129.3, mode 3, and the same vendor HID updater interface/report descriptor.
- The accepted MAIN query has the same length/prefix/hardware fields. The version is an unsigned major/minor pair: major 1–128 with minor 0–255, or major 129 with minor 0–23. This is a numeric eligibility rule, **not a list of proven compatible GameSir releases**. Major 0, newer versions, and 144-series controllers are refused.
- Normal USB descriptors must match the official fixture byte-for-byte except that Check/Upgrade can tolerate a different two-byte `bcdDevice` revision. No configuration, interface, endpoint, identity or string-index differences are permitted. A current 129.24 device must still match all descriptor bytes.
- Fresh preflight repeats these checks before the handoff. The original MAIN version is persisted in the recovery receipt before DFU. Older-source receipts can authorize only another official Upgrade, never Install or Restore.
- MAIN DFU still requires the exact measured identity, USB revision, HID interface, 64-byte interrupt endpoints and vendor report descriptor. An already-DFU device without a valid pending receipt is refused.
- The same hash-pinned official image is erased/written/verified, with the same strict acknowledgements and no blind retries. No PD/slave update or bootloader programming is added.
- Success requires the restarted original GameSir identity, exact official USB descriptors, MAIN 129.24, hardware 2.0, slave 129.3 and mode 3. Only then is the separate Apple patch option available.

**No older physical controller has validated this upgrade.** Matching query/interface fields do not prove compatibility with an older loader, memory layout or calibration data. There is no backup of the previous version and no downgrade option. The confirmation and diagnostics disclose this risk; recovery after an incompatible flash is not guaranteed.
