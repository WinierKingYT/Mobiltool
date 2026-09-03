# Active Part

```text
ACTIVE_PART = P1
STATUS = AWAITING_CAPTURE_PATH_PREFLIGHT
```

Part Sequencing:
- P0: Foundation & Invariant Truth-Pass (LOCKED)
- P1: Call Recording (ACTIVE - PREFLIGHT)
- P2: Media / Video Downloader (LOCKED)
- P3: Library & Playback (LOCKED)
- P4: Local Transcription (LOCKED)

Scope (P1 - Call Recording Preflight & Invariant Hardening):
- P1-PREFLIGHT-01..08: Architecture Decision Record, Truthful Quality Model, Scoped Storage Ingestion
- P1-PREFLIGHT-09..14: Candidate vs Qualified Capability Separation, Pure Media Permission Checking, Duration Correlation
- P1-PREFLIGHT-15..19: WorkManager Ingestion, CallLifecycleJournal, Pure Correlation Engine
- P1-PREFLIGHT-22: Truthful Stale-Call Recovery (Never fabricate 4h duration, store durationMs = 0 with explicit unobserved termination diagnostic)
- P1-PREFLIGHT-23: Durable Startup Reconciliation (Never delete journal before database persistence transaction completes)
- P1-PREFLIGHT-24: Invalid OEM Audio Mapping (Fail-closed quarantine on invalid/corrupt/silent container, never expose invalid audio as valid recording)
- P1-PREFLIGHT-25: Duplicate IDLE Idempotency (First IDLE freezes callEndTimeMs, WorkManager unique task uses ExistingWorkPolicy.KEEP)
- P1-PREFLIGHT-26: Permission Restoration Semantics (Preserve permanent denial across recreation, refresh capability on screen resume)

Forbidden in P1:
- Advancing to P2 (Media / Video Downloader)
- Fake/simulated capture fallbacks (using MIC/VOICE_COMMUNICATION as bidirectional)
- Premature 10-call campaign before 1-2 call feasibility preflight
- Fabricating physical call qualification logs
