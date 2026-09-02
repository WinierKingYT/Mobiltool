# Architecture Decision Record (ADR) 001: P1 Production Call Capture Architecture

**Status**: PROPOSED — PHYSICAL PREFLIGHT REQUIRED  
**Date**: 2026-09-02  
**Part**: P1 — Call Recording  
**Author**: Antigravity Engineering System  

---

## 1. Context & Technical Problem Statement

On Android 9 (API 28) through Android 15 (API 35), the Android Open Source Project (AOSP) enforces strict SELinux policies and `AudioPolicyService` constraints that forbid unprivileged third-party applications (running under standard userspace UIDs $\ge 10000$) from capturing the downlink (remote party) audio stream during cellular (GSM/VoLTE/VoWiFi) voice calls.

Standard Android APIs provide:
- `AudioSource.VOICE_COMMUNICATION`: Intended for VoIP (WebRTC). During telephony calls, AOSP audio policy routes only the uplink (microphone) stream or an unverified acoustic mix when loudspeaker is active.
- `AudioSource.VOICE_CALL` / `VOICE_DOWNLINK`: Protected by `android.permission.CAPTURE_AUDIO_OUTPUT`, which is restricted strictly to system-signed applications (`signature|privileged`) or root (UID 0).

**Mobiltool Engineering Invariant**: Production call capture must yield genuine bidirectional audio. Fallbacks using microphone or acoustic speakerphone echo are strictly forbidden for bidirectional call archiving.

---

## 2. Evaluated Architectural Pathways & Candidate Status

```mermaid
graph TD
    A[Incoming / Outgoing Phone Call] --> B{Capability Detection Gate}
    B -->|Verified Companion Daemon| C[Path 1: PRIVILEGED_DIRECT (UNLINKED CANDIDATE)]
    B -->|Confirmed OEM Recording File| D[Path 2: OEM_IMPORT (CANDIDATE)]
    B -->|Standard Unprivileged Userspace| E[Path 3: UNSUPPORTED Fail-Closed]
    
    C --> F[Dual-Stream PCM/AAC via UNIX Domain Socket]
    D --> G[WorkManager OemPostCallImportWorker & MediaStore Ingestion]
    E --> H[Metadata-Only Session: UNSUPPORTED Diagnostic]
    
    F --> I[Audio File Inspector & Quality Validation]
    G --> I
    I -->|Post-Validation / Unqualified| J[Durable Vault Archive: MIXED_UNVERIFIED]
    I -->|Physical Qualification Complete| K[Durable Vault Archive: VERIFIED_BIDIRECTIONAL]
```

### Option 1: `PRIVILEGED_DIRECT` (Native Companion Daemon over UNIX Domain Socket)
- **Status**: **UNLINKED / CANDIDATE IMPLEMENTATION** (No native binary currently compiled/shipped in repository).
- **Mechanism**: A standalone native companion binary (`mobiltool_companion`) executed under Root (UID 0) or System (UID 1000).
- **Communication Protocol**:
  - Structured UNIX Domain Socket (`/dev/socket/mobiltool_companion` or `/data/local/tmp/mobiltool_companion.sock`).
  - Challenge-response handshake (`MOBILTOOL_COMPANION_V1` + PING/PONG).
  - Bidirectional ping/pong heartbeat for liveness.
  - Raw audio stream transfer via shared memory or streaming UNIX socket pipe.
- **Decision**: Kept explicitly **UNLINKED** until the target device requires root architecture and a verified daemon package is approved and implemented.

### Option 2: `OEM_IMPORT` (Automated Samsung / Xiaomi MediaStore Ingestion)
- **Status**: **CANDIDATE IMPLEMENTATION** (Requires physical preflight validation on target device).
- **Mechanism**: Samsung OneUI (in supported CSC regions) and Xiaomi HyperOS include proprietary in-call recorders built into the system dialer (`com.samsung.android.incallui`). These recorders output dual-channel audio directly to MediaStore / external storage.
- **Storage & Ingestion Pipeline**:
  - Modern Scoped Storage compliance via `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` with runtime `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (Android $\le$ 12).
  - Explicit avoidance of `MANAGE_EXTERNAL_STORAGE` (no "All Files Access" required).
  - Pure correlation engine (`OemCorrelationEngine`) enforcing strict timestamp window $[T_{start}-15s \dots T_{end}+25s]$ + Call Duration Matching + Anti-Collision Phone Matching.
  - Atomic copy into Mobiltool internal vault (`filesDir/calls/`), followed by ISO MP4 container verification.
- **Decision**: Selected as the **Primary Candidate Pathway** for physical preflight feasibility testing if the user''s device supports native call recording.

### Option 3: Standard Userspace Mic Fallback (REJECTED)
- **Why Rejected**: Violates Mobiltool truth invariants. Fails to record downlink audio over earpiece or Bluetooth; captures only ambient room noise and low-quality speakerphone echo.

---

## 3. Truthful Quality Invariants & Candidate vs. Qualified Semantics

**Critical Invariants**:
1. `OEM_CANDIDATE_DETECTED != OEM_RECORDING_CONFIRMED != OEM_PROFILE_QUALIFIED`.
2. `isTwoWaySupported` MUST remain `false` until the physical device profile is proven and qualified (`OEM_PROFILE_QUALIFIED` / `PRIVILEGED_QUALIFIED`).
3. Audio files harvested via candidate `OEM_IMPORT` or candidate `PRIVILEGED_DIRECT` that pass technical inspection yield **`RecordingQuality.MIXED_UNVERIFIED`** by default.
4. `RecordingQuality.VERIFIED_BIDIRECTIONAL` is strictly assigned only after human verification on a qualified profile.

---

## 4. Background Execution, WorkManager & Process-Death Recovery

1. **Elimination of Long-Lived In-Call FGS**:
   - `OEM_IMPORT` does not record audio in userspace during the call. The OEM dialer handles recording independently.
   - On `PHONE_STATE/OFFHOOK`: Mobiltool persists minimal active-call state in `CallLifecycleJournal` without holding an FGS or wakelock.
   - On `PHONE_STATE/IDLE`: Post-call ingestion is scheduled as a durable unique background task (`OemPostCallImportWorker`) via Android WorkManager with OEM flush delay.
2. **Process-Death Recovery**:
   - If the application process is terminated between OFFHOOK and IDLE, `CallLifecycleJournal` maintains state on disk. The IDLE receiver recovers the exact same `callId` and start time, and enqueues the worker seamlessly.
   - Abandoned sessions on startup are reconciled as transparent metadata records without fabricating files or claiming corruption.
3. **Platform Compliance**:
   - Fully compliant with Android 12–15 background execution limits without relying on illegal while-in-use microphone foreground service starts.
   - `CallStateReceiver` is secured with `android:permission="android.permission.READ_PHONE_STATE"`.

---

## 5. Physical Device Preflight Plan

1. **Phase 1: Device Preflight Questionnaire**: Determine target model, Android OS version, root status, and native OEM call recording support.
2. **Phase 2: 1–2 Call Feasibility Test**:
   - Perform 1 incoming or outgoing call.
   - Verify OEM recording generation, MediaStore discovery, correlation, vault copy, and dual-party audio playback.
3. **Phase 3: 10-Call Qualification Matrix**: Only after Phase 2 passes 100%.
