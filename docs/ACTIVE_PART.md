# Active Part

```text
ACTIVE_PART = P1
STATUS = AWAITING_CAPTURE_PATH_PREFLIGHT
```

Part Sequencing:
- P0: Foundation & Invariant Truth-Pass (LOCKED)
- P1: Call Recording (ACTIVE — PREFLIGHT)
- P2: Media / Video Downloader (LOCKED)
- P3: Library & Playback (LOCKED)
- P4: Local Transcription (LOCKED)

Scope (P1 - Call Recording):
- P1-PREFLIGHT-01: ADR Gate Enforcement (PROPOSED — PHYSICAL PREFLIGHT REQUIRED)
- P1-PREFLIGHT-02: Fix VERIFIED_BIDIRECTIONAL semantics (Truthful quality model)
- P1-PREFLIGHT-03: OEM Import Capability Truth (MediaStore / Confirmed evidence)
- P1-PREFLIGHT-04: Modern Android Storage Architecture (Scoped Storage / MediaStore.Audio)
- P1-PREFLIGHT-05: Foreground Service Architecture (Android 12-15 compliant FGS / background start)
- P1-PREFLIGHT-06: Privileged Companion Reality Check (UNLINKED candidate)
- P1-PREFLIGHT-07: Physical Capability Discovery Plan (Device Preflight Questionnaire)
- P1-PREFLIGHT-08: Security & Ambiguity Collision Prevention (Fail closed on ambiguous correlation)

Forbidden in P1:
- Advancing to P2 (Media / Video Downloader)
- Fake/simulated capture fallbacks (using MIC/VOICE_COMMUNICATION as bidirectional)
- Premature 10-call campaign before 1-2 call feasibility preflight
- Fabricating physical call qualification logs
