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
    D --> G[MediaStore.Audio Ingestion & Correlation]
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
  - Modern Scoped Storage compliance via `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` with `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (Android $\le$ 12).
  - Explicit avoidance of `MANAGE_EXTERNAL_STORAGE` (no "All Files Access" required).
  - Strict time-window and phone-number correlation engine.
  - Atomic copy into Mobiltool internal vault (`filesDir/calls/`), followed by ISO MP4 container verification.
- **Decision**: Selected as the **Primary Candidate Pathway** for physical preflight feasibility testing if the user''s device supports native call recording.

### Option 3: Standard Userspace Mic Fallback (REJECTED)
- **Why Rejected**: Violates Mobiltool truth invariants. Fails to record downlink audio over earpiece or Bluetooth; captures only ambient room noise and low-quality speakerphone echo.

---

## 3. Truthful Quality Invariants & Physical Qualification

**Critical Invariant**: Valid audio file container structure $\neq$ Verified Bidirectional Audio.

1. **Pre-Qualification Quality**:
   - Audio files harvested via candidate `OEM_IMPORT` or candidate `PRIVILEGED_DIRECT` that pass technical inspection (valid `ftyp` atom, duration $\ge 500$ms, bitrate $\ge 8000$bps) are assigned **`RecordingQuality.MIXED_UNVERIFIED`** by default.
2. **Post-Qualification Quality (`VERIFIED_BIDIRECTIONAL`)**:
   - `VERIFIED_BIDIRECTIONAL` may ONLY be assigned once a physical qualification rule or verified profile confirms both local and remote parties are audible on that device and firmware build.

---

## 4. Foreground Service & Android 12–15 Background Lifecycle

1. **Elimination of Illegal Microphone FGS**:
   - Because `OEM_IMPORT` does not record microphone audio in real time during the call, Mobiltool does **not** launch a `foregroundServiceType="microphone"` service from the background on `PHONE_STATE` broadcasts.
2. **Post-Call Ingestion Model**:
   - Telephony state changes are tracked in userspace memory.
   - When the call transitions to `IDLE`, post-call harvesting and metadata indexing are executed immediately in background coroutines or a non-mic `dataSync` service if long-running processing is needed.
   - This complies 100% with Android 12–15 background start restrictions without bypassing platform security.

---

## 5. Physical Device Preflight Plan

Before full qualification, execute a **2-Phase Feasibility Gate**:

1. **Phase 1: Device Preflight Questionnaire**: Determine target model, Android OS version, root status, and native OEM call recording support.
2. **Phase 2: 1–2 Call Feasibility Test**:
   - Perform 1 incoming or outgoing call.
   - Verify OEM recording generation, MediaStore discovery, correlation, vault copy, and dual-party audio playback.
3. **Phase 3: 10-Call Qualification Matrix**: Only after Phase 2 passes 100%.
