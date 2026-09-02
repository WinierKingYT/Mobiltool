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

Scope (P1 - Call Recording Preflight & Invariant Hardening):
- P1-PREFLIGHT-01..08: Architecture Decision Record, Truthful Quality Model, Scoped Storage Ingestion
- P1-PREFLIGHT-09..14: Candidate vs Qualified Capability Separation, Pure Media Permission Checking, Duration Correlation
- P1-PREFLIGHT-15: Complete Runtime OEM Media Permission UX (Compose ActivityResult flow, permission state tracking)
- P1-PREFLIGHT-16: Durable Post-Call Ingestion via WorkManager (OemPostCallImportWorker with callId continuity)
- P1-PREFLIGHT-17: Call Lifecycle State Journal & Crash Recovery (Cross-process lifecycle persistence, no fake temp files)
- P1-PREFLIGHT-18: Architecture Remnant Cleanup (Removed obsolete NEW_OUTGOING_CALL intent-filter and unused FGS)
- P1-PREFLIGHT-19: Extracted Pure OEM Correlation Decision Engine (OemCorrelationEngine & direct unit test matrix)

Forbidden in P1:
- Advancing to P2 (Media / Video Downloader)
- Fake/simulated capture fallbacks (using MIC/VOICE_COMMUNICATION as bidirectional)
- Premature 10-call campaign before 1-2 call feasibility preflight
- Fabricating physical call qualification logs
