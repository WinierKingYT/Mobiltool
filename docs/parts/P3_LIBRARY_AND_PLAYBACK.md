# P3 — Library & Playback

Status: ACTIVE / IN_PROGRESS

## Goal
Unified media vault and audio/video playback engine.

## Execution Packages
1. `RealAudioPlayer` with Coroutine state engine (seek, speed 0.5x..2.0x, zero-duration safety).
2. `AndroidMedia3VideoEngine` Media3 ExoPlayer 1.5.1 integration with `VideoPlaybackViewer`.
3. Multi-criteria search and reactive filtering (`ALL`, `CALLS`, `MEDIA`, `TRANSCRIPTS`) with zero-disk-I/O stage-2 pipeline.
4. Vault file existence & fast bounded-prefix integrity checks.
5. Action-time preflight revalidation (container verification, size match, `NOT_READY` precedence) and presentation mutual exclusion in `MainScreen`.
6. Authoritative Media3 playing state and asynchronous seek confirmation.

## Exit Gate Checklist
```text
[X] Real audio playback with accurate progress & lifecycle control
[X] Video viewer with Media3 ExoPlayer & HUD overlay
[X] Authoritative Media3 playing state drives controller & polling
[X] Asynchronous seek discontinuity confirmation
[X] Action-time container & size preflight validation
[X] ExoPlayer setup exception release protection
[X] Reactive Room DB flow combine (Calls + Media)
[X] Dynamic multi-criteria search and category filtering
[X] 273/273 unit tests passing across all modules
[X] assembleDebug APK compilation succeeded
[ ] Physical Device Playback Qualification on Samsung Galaxy S22
```
