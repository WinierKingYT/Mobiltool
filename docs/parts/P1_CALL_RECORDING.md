# P1 — Call Recording

## Goal
Reliable phone call capture with explicit hardware capability truth.

## Execution Packages
1. Hard capability gate (Tier 1 AOSP rejected before recording).
2. Telephony lifecycle state machine (`CallSessionTracker`).
3. Audio stream inspector (ISO MP4 `ftyp` box + RMS speech level check).
4. Live capability diagnostic UI strip.

## Exit Gate
```text
[x] Tier 1 AOSP rejected upfront
[x] Telephony state machine synchronized
[x] ISO MP4 ftyp container verified
[x] Non-zero RMS speech detected
[x] Zero silent dummy files
```
