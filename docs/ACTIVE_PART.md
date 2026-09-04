# Active Part

```text
ACTIVE_PART = P3
STATUS = LOCKED / PASS
```

## Part Sequencing & Lifecycle State:
- **P0: Foundation & Invariant Truth-Pass** - LOCKED / PASS
- **P1: Call Recording** - LOCKED / TRUTH-LOCKED
  - Software Architecture: PASS / COMPLETE
  - Target Device Physical Preflight: UNSUPPORTED (No native call recorder exposed on tested SM-S901E/DS One UI 8.0)
  - Physical Qualification: NOT RUN (No legitimate capture source available)
  - Runtime Verdict: Metadata-only fail-closed mode (`RecordingQuality.UNSUPPORTED`)
- **P2: Media / Video Downloader** - LOCKED / PASS
  - Direct HTTP/HTTPS: SUPPORTED / QUALIFIED (URL Security -> DNS Binding -> Safe Redirects -> Stream -> .part Staging -> Content Validation -> SHA-256 -> `StandardCopyOption.ATOMIC_MOVE` -> Canonical File; Mandatory GET prefix payload proof)
  - YouTube (ADR_002 APPROVED): SUPPORTED / QUALIFIED FOR PUBLIC CONTENT (`NewPipeExtractor:v0.26.5` behind strict `YouTubeExtractor` adapter + Mobiltool verified atomic downloader)
  - Instagram & X (Twitter): EXTRACTION_UNAVAILABLE / NOT APPROVED (Fails closed with `ErrorCode.EXTRACTION_FAILED`)
  - Verification: 100% deterministic offline suite (`.\gradlew.bat test`), live qualification tasks (`realDirectMediaTest`, `realYouTubeExtractionTest`)
- **P3: Library & Playback** - LOCKED / PASS
  - Unified Vault: Reactive Room DB combine (`CallEntity` + `MediaEntity`), fast bounded-prefix file inspection (<= 768 bytes, zero SHA-256 in UI thread), two-stage pipeline (zero disk I/O on query/filtering).
  - Audio Playback: Full lifecycle coroutine controller (`AudioPlaybackController`), `sessionGeneration`, fail-closed seek, audio focus interruption handling, speed control (0.5x..2.0x), transcript position synchronization, industrial UI playback sheet.
  - Video Playback: Media3 ExoPlayer 1.5.1 engine (`AndroidMedia3VideoEngine`), `VideoPlaybackController`, action-time file validation (rejection of `.part`/`.tmp`), fullscreen HUD viewer (`VideoPlaybackViewer`), presentation mutual exclusion.
  - Verification: 267/267 unit tests passing across 22 suites, clean `assembleDebug`.
- **P4: Local Transcription** - LOCKED / AWAITING USER ACTIVATION
- **P5+: Core Foundation, Dev Bridge, Remote Desktop** - LOCKED

---

## P3 Retained Invariants:
* `PLAY_AUDIO != OPEN_TRANSCRIPT`
* `PLAY_VIDEO != OPEN_TRANSCRIPT`
* `EXISTS != PLAYABLE` (File must be validated before opening player)
* `STAGING / TEMP FILES (.part, .tmp) MUST NEVER BE OPENED IN PLAYERS`
* `ENGINE SEEK FAILURE -> CONTROLLER MUST NOT CLAIM SEEK SUCCESS`
* `VIDEO PLAYBACK != VLC / FFMPEG PLAYER` (Strict Media3 ExoPlayer integration)
* `SEARCH / FILTER QUERY CHANGE -> ZERO DISK I/O`
* `ROOM DB SCHEMA VERSION = 1` (Zero entity / DAO / DB version modifications in P3)

---

## P3 Evidence Record:
* **Evidence Document**: [docs/P3_PLAYBACK_EVIDENCE.md](file:///c:/Users/ahmet/Desktop/aramakay%C4%B1t/docs/P3_PLAYBACK_EVIDENCE.md)
* **Build**: PASS - LOCAL EXECUTION EVIDENCE (`.\gradlew.bat clean assembleDebug`)
* **Full Unit Tests**: PASS - LOCAL EXECUTION EVIDENCE (`.\gradlew.bat test` - 267 unit tests passing)
* **Attached Hardware Target**: Samsung Galaxy S22 (`SM-S901E`, Android 16 / SDK 36) attached via wireless ADB
* **Audio Interruption Tests**: PASS
* **Android Audio Focus Engine Integration**: CODE_REVIEWED_NOT_RUNTIME_TESTED
* **Media3 ExoPlayer Integration**: PASS
* **GitHub CI**: NOT CONFIGURED / NO REMOTE STATUS CHECK EVIDENCE
