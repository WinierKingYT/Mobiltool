# P3 — Library & Playback

Status: ACTIVE / IN_PROGRESS

## Goal
Unified media vault and audio/video playback engine.

## Execution Packages
1. `RealAudioPlayer` with Coroutine state engine (seek, speed 0.5x..2.0x, zero-duration safety).
2. `ExoPlayerVideoViewer` Media3 integration.
3. Multi-criteria search and reactive filtering (`ALL`, `CALLS`, `MEDIA`, `TRANSCRIPTS`).
4. File existence & integrity checks.

## Exit Gate (Pending P3 Verification)
```text
[ ] Real audio playback with accurate progress
[ ] Video viewer with Media3 ExoPlayer
[ ] Reactive Room DB flow combine
[ ] Dynamic search and category filtering
```
