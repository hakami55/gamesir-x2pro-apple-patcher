# Legacy X2 Pro MAIN protocol

This updater targets the measured 129.24 / hardware 2.0 / slave 129.3 profile. The device names containing "DFU" refer to GameSir's custom HID updater, not the USB DFU class standard.

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

No `03 FF` PD handoff, PD erase, slave write, arbitrary packet console, unverified image import or unsupported-version override is provided. The app keeps a recovery receipt before switching modes. The serial `001` is shared/nonunique, so the receipt is not cryptographic proof of physical device identity; the user must keep the same checked controller connected.

The bundled official image is an update image, not a complete device backup. Device-side verify acknowledgements plus post-update descriptors/version checks are the success criteria; the app does not independently read back every flash byte.
