# 00 — Master Product Contract V4

## 1. Authority

```text
Explicit user decision
    >
Master Product Contract
    >
Accepted ADR
    >
Subsystem plan
    >
Milestone plan
    >
Implementation detail
```

The AI is never allowed to silently broaden or weaken this contract.

---

# 2. Product mission

Build the user's own Android mobile toolbox.

Current approved capabilities:

```text
LOCAL
├── Call Archive
├── Media Downloader
├── Media Library
└── Local Transcription

REMOTE — later
├── Project / Agent Control
└── Full Remote Desktop Control
```

---

# 3. Product priorities

## Priority 1 — Reliability
The app must not lose recordings, downloads or transcripts.

## Priority 2 — Battery / thermal health
A feature that is idle must have near-zero active cost.

## Priority 3 — Privacy / local-first
Sensitive data stays local unless transport is required by a user-requested remote session.

## Priority 4 — Truthfulness
Unsupported, failed and degraded states are explicit.

## Priority 5 — Extensibility
New modules may be added later without forcing current modules to depend on them.

---

# 4. Battery invariant

The application must never use a permanent foreground service merely to “keep the app alive.”

Allowed background work is event-driven or scheduler-driven.

Forbidden architecture:

```text
BOOT
 -> FOREGROUND SERVICE
 -> while(true)
      poll calls
      poll clipboard
      poll network
      poll PC
```

Required architecture:

```text
SYSTEM / USER EVENT
 -> activate bounded subsystem
 -> perform work
 -> stop / return idle
```

---

# 5. Call behavior

Call recorder conceptually remains available all day, but capture resources are not.

```text
IDLE
- no recording
- no microphone/audio capture
- no active call encoder
- no recorder wake lock

CALL EVENT
- call lifecycle arrives
- capability checked
- recording activates

CALL END
- bounded finalize
- storage commit
- recording service stops
```

If privileged-system integration is used, prefer system/Telecom event hooks over polling.

---

# 6. Media behavior

Downloader:
- inactive until user initiates;
- bounded concurrent jobs;
- user may prefer Wi-Fi;
- heavy postprocessing may be deferred on low battery.

Transcription:
- explicit user request;
- one heavy local transcription job by default;
- optional charging-only mode;
- pause/refuse on severe thermal condition.

---

# 7. Remote Desktop behavior

Remote Desktop is an active-session feature.

```text
NOT CONNECTED
 -> no video decoder
 -> no frame stream
 -> no high-frequency network traffic

CONNECTED, VIEW ONLY
 -> adaptive stream

CONNECTED, INTERACTIVE
 -> adaptive stream + input channel

APP BACKGROUNDED
 -> session pauses video by default
 -> control session remains resumable for short grace interval
 -> then disconnect according to policy
```

No hidden all-day desktop video stream.

---

# 8. Desktop Bridge

Bridge can remain lightweight in background on the PC because PC power constraints differ, but it must:

- idle without screen capture when no remote-desktop session exists;
- idle without agent polling where events are available;
- capture/encode desktop only for an authenticated active session;
- stop encoder immediately after session termination.

---

# 9. Remote Desktop product boundary

Allowed:
- see own PC desktop;
- tap/click;
- pointer movement;
- scroll;
- drag;
- keyboard input;
- monitor selection;
- open/close applications through ordinary user input or approved desktop actions.

Not initial release:
- Windows lock-screen bypass;
- UAC secure-desktop bypass;
- credential harvesting;
- hidden/unattended third-party access;
- persistence designed to evade the PC user;
- disabling Windows security indicators;
- covert screen capture.

---

# 10. Canonical data

```text
Call recording       -> canonical local audio
Downloaded media     -> canonical local file
Transcript           -> derived
Desktop project      -> canonical on desktop
Remote desktop frame -> ephemeral, never archive by default
```

Remote desktop video is transport data, not automatically recorded.

---

# 11. Build ordering

Remote PC features are not allowed to delay local tool MVP.

```text
LOCAL FIRST
CALL + MEDIA + TRANSCRIPTION + POWER

THEN
REMOTE DEV

THEN
REMOTE DESKTOP
```

---

# 12. Release slices

## Local Tool Release
May ship without any PC control.

## Remote Dev Release
May ship after local tooling.

## Remote Desktop Release
Separate security/performance qualification.

Remote Desktop is not required for the first useful application.

# 13. Visual identity invariant

The product visual direction is locked to **Retro-industrial / archival technical**. The authoritative specification is `38_RETRO_INDUSTRIAL_DESIGN_SYSTEM.md`.

Generic Material styling, modern SaaS card language, glassmorphism, neon/cyberpunk styling and default dynamic colors are not acceptable replacements.
