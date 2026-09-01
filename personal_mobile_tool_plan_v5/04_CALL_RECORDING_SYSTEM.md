# 04 — Call Recording System

This file carries forward the call-capture contract from V1.

## 1. Production capture modes

```text
PRIVILEGED_DIRECT
OEM_IMPORT
UNSUPPORTED
```

No ambient microphone fallback as production success.

---

# 2. Privileged direct

For a controlled Android system image:

```text
Telephony audio
 -> privileged Call Memory component
 -> AudioRecord/framework path
 -> RecordingWriter
 -> validation
 -> encrypted canonical audio
```

Requirements:

- privileged permission allowlist;
- reference-device qualification;
- no SELinux-permissive production dependency;
- no hidden consumer API tricks;
- separate/mixed track truth model;
- 100-call qualification before “supported.”

---

# 3. OEM import

For stock devices with a manufacturer recorder:

```text
OEM Phone recorder
 -> user-authorized accessible file/folder
 -> stable file detector
 -> call pairing
 -> managed copy
 -> validate
 -> archive
```

Forbidden:
- reading private app directories;
- root scraping;
- Accessibility automation.

---

# 4. Call recording statuses

```text
VERIFIED_BIDIRECTIONAL
MIXED_UNVERIFIED
ONE_SIDED
SILENT
CORRUPT
UNSUPPORTED
UNKNOWN
```

A transcript created from `ONE_SIDED` must show a warning.

No transcript from `SILENT`, `CORRUPT`, `UNSUPPORTED`.

---

# 5. Call capture is isolated

The presence of downloader network permissions must not alter call privacy.

Call capture has no network dependency.

---

# 6. Detailed qualification

Test:

- incoming/outgoing;
- earpiece/speaker/Bluetooth;
- local/remote hangup;
- hold;
- screen lock;
- process death;
- reboot recovery;
- VoLTE;
- Wi-Fi calling if claimed.

Call feature is independently releasable as unsupported on devices where no safe capture path exists.
