# AGENTS.md — Personal Mobile Tool V4

## Required reading

1. `00_MASTER_PRODUCT_CONTRACT.md`
2. `01_SCOPE_BOUNDARIES_AND_INVARIANTS.md`
3. `02_SYSTEM_ARCHITECTURE.md`
4. `26_POWER_THERMAL_AND_BATTERY_BUDGET.md`
5. active milestone
6. relevant subsystem
7. accepted ADRs

## ACTIVE MILESTONE

```text
ACTIVE_MILESTONE = M0
```

---

# SCOPE

The application has approved families:
- Calls
- Media Downloader / Library
- Transcription
- Remote Dev (late)
- Remote Desktop (last)

Do not invent unrelated features.

---

# POWER HARD RULES

- NO always-hot call recorder.
- NO permanent call-state polling.
- NO permanent wake lock.
- NO umbrella foreground service whose purpose is “keep app alive.”
- NO remote desktop video decode while UI is backgrounded by default.
- NO PC screen capture when no authenticated remote desktop session exists.
- NO simultaneous heavy STT + transcode by default.
- Call recording/finalization gets priority over optional work.

---

# MEDIA HARD RULES

- NO DRM bypass.
- NO private-account bypass.
- NO cookie/credential theft.
- NO CAPTCHA/anti-bot evasion.

---

# CALL HARD RULES

- NO Accessibility remote-call recording.
- NO forced-speakerphone fake capture.
- NO ambient microphone mislabeled as verified bidirectional audio.

---

# REMOTE DEV HARD RULES

- NO unauthenticated Bridge.
- NO unrestricted shell in initial releases.
- NO source paths outside registered roots.
- NO destructive Git in initial Remote Dev.
- NO UI scraping when supported APIs/SDKs are unavailable.

---

# REMOTE DESKTOP HARD RULES

- ONLY paired trusted computers.
- NO covert mode.
- NO UAC/secure-desktop bypass.
- NO persistent key logging.
- NO raw public port forwarding architecture.
- NO default session recording.
- NO software full-frame screenshot/JPEG polling architecture.
- Prefer hardware video encode/decode.
- Input messages are authenticated, session-bound and replay-protected.

---

# SUCCESS TRUTH

```text
call detected != recording verified
probe succeeded != download ready
file ready != transcript ready
PC online != control authorized
remote video connected != input authorized
request sent != action completed
```

---

# MILESTONE LOCK

Remote Dev:
- starts M7.

Remote Desktop:
- starts M9.

Do not scaffold implementation earlier “for future use.”

---

# ADR GATE

Architecture/dependency/permission/security changes require ADR.

---

# DONE

Task is done only when:
- build passes;
- relevant tests pass;
- power behavior is compatible with active workload class;
- resources release on lifecycle end;
- no undeclared permission/network behavior;
- no invariant violation;
- docs match implementation.

# VISUAL DESIGN LOCK

Before UI work read `38_RETRO_INDUSTRIAL_DESIGN_SYSTEM.md`.

Mandatory identity:

```text
retro-industrial
archival technical
warm charcoal
aged ivory
oxidized copper/rust
thin borders
near-square components
editorial + technical typography
```

UI work is incomplete until the design-system visual QA checklist passes.
