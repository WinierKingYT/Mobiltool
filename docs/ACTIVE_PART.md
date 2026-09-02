# Active Part

```text
ACTIVE_PART = P1
STATUS = IN_PROGRESS
```

Scope (P1 - Call Recording):
- P1-E01: Call Truth Model freeze (PRIVILEGED_DIRECT / OEM_IMPORT / UNSUPPORTED)
- P1-E02: Telephony Lifecycle (RINGING / OFFHOOK / ACTIVE / FINALIZING / IDLE)
- P1-E03: Capability Gate (Zero fake companion/mic elevation)
- P1-E04: Capture Architecture Investigation & ADR
- P1-E05: Capture Implementation (Post-ADR)
- P1-E06: Recording Journal & Crash Safety
- P1-E07: Audio Health & Verification
- P1-E08: Atomic Finalization & Archive Handoff
- P1-E09: Playback & Storage Persistence
- P1-E10: Idle / Battery Profile Validation
- P1-E11: Physical Qualification Protocol

Forbidden in P1:
- Advancing to P2 (Media Extractor Expansion)
- Fake/simulated capture fallbacks (using MIC/VOICE_COMMUNICATION as bidirectional)
- Bypassing ADR for privileged capture path
- Fabricating physical call qualification logs

