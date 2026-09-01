# P3 — Library & Playback

## Goal
Unified media vault and audio/video playback engine.

## Execution Packages
1. `RealAudioPlayer` with Coroutine state engine (seek, speed 0.5x..2.0x, zero-duration safety).
2. `ExoPlayerVideoViewer` Media3 integration.
3. Multi-criteria search and reactive filtering (`ALL`, `CALLS`, `MEDIA`, `TRANSCRIPTS`).
4. File existence & integrity checks.

## Exit Gate
```text
[x] Real audio playback with accurate progress
[x] Video viewer with Media3 ExoPlayer
[x] Reactive Room DB flow combine
[x] Dynamic search and category filtering
```
