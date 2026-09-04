# P3 — Library & Playback Verification Evidence

## Baseline & Verification Status

```text
ACTIVE_PART = P3
STATUS = LOCKED / PASS (Software & Physical Device Qualification Complete)
PREVIOUS PARTS = P0 (LOCKED/PASS), P1 (LOCKED/TRUTH-LOCKED), P2 (LOCKED/PASS)
FUTURE PARTS = P4+ (LOCKED)
ROOM DB SCHEMA VERSION = 1 (STRICTLY UNCHANGED)
```

---

## 1. Test Suite Execution Evidence

* **Command**: `.\gradlew.bat test` / `.\gradlew.bat testDebugUnitTest`
* **Result**: `BUILD SUCCESSFUL` (0 failures, 0 errors, 0 skipped)
* **Total Repository Unit Tests**: 277 tests across 31 unique suites

### Breakdown by Module & Suite:

| Module | Test Suite | Tests | Result |
|---|---|---|---|
| `:app` | `AudioPlaybackControllerTest` | 25 | PASS |
| `:app` | `RealAudioPlayerTest` | 3 | PASS |
| `:app` | `VideoPlaybackControllerTest` | 33 | PASS |
| `:app` | `VaultItemEvaluatorTest` | 21 | PASS |
| `:app` | `LibraryViewModelTest` | 15 | PASS |
| `:app` | `TranscriptViewModelTest` | 13 | PASS |
| `:app` | `CallLifecycleJournalTest` | 5 | PASS |
| `:app` | `CallSessionTrackerTest` | 6 | PASS |
| `:app` | `OemCorrelationEngineTest` | 7 | PASS |
| `:app` | `OemPermissionManagerTest` | 8 | PASS |
| `:app` | `OemRecordingImporterTest` | 5 | PASS |
| `:app` | `AudioFileInspectorTest` | 7 | PASS |
| `:app` | `CallCaptureCapabilityDetectorTest` | 3 | PASS |
| `:app` | `PrivilegedCompanionClientTest` | 2 | PASS |
| `:call-capture-api` | `AudioQualityValidatorTest`, `DefaultCaptureEngineTest` | 9 | PASS |
| `:core-jobs` | `OemPowerDiagnosticTest` | 4 | PASS |
| `:core-model` | `RecordingQualityTest` | 6 | PASS |
| `:core-security` | `DirectBootVaultManagerTest` | 2 | PASS |
| `:core-storage` | `EntityConversionTest` | 3 | PASS |
| `:desktop-bridge` | `PtyTerminalNormalizerTest`, `VirtualScreenCoordinateTransformerTest` | 4 | PASS |
| `:media-extractor-api` | `HttpMediaProberTest`, `MediaFileValidatorTest`, `NetworkSecurityPolicyTest`, `RealHttpStreamDownloaderTest`, `SafeHttpTransportTest`, `UrlClassifierTest`, `YouTubeExtractorTest` | 89 | PASS |
| `:transcription-api` | `DefaultTranscriptionEngineTest`, `TranscriptExporterTest` | 7 | PASS |
| **Total** | **31 Unique Suites** | **277** | **PASS** |

---

## 2. Audio Playback Subsystem Architecture & Evidence

* **Core Components**:
  - `AudioPlaybackController`: Orchestrates foreground audio lifecycle, generation counters, state management, progress polling (250ms while `PLAYING`), fail-closed seek, audio focus interruption handling, and transcript synchronization.
  - `RealAudioPlayer`: Real Android `MediaPlayer` integration with async preparation, `PlaybackParams` speed adjustment (0.5x..2.0x), and safe lifecycle teardown.
  - `AudioPlaybackSheet`: Industrial-styled Compose audio playback UI surface.
* **Evidence Labels**:
  - `AUDIO INTERRUPTION CONTRACT TESTS`: **PASS**
  - `ANDROID AUDIO FOCUS ENGINE INTEGRATION`: **CODE_REVIEWED_NOT_RUNTIME_TESTED**
  - `PHYSICAL AUDIO FOCUS INTERRUPTION`: **NOT RUN**
  - `SEEK TRUTH / FAIL-CLOSED INVARIANT`: **PASS** (Seek failure rejects state change and aborts play-after-seek)
  - `TRANSCRIPT POSITION DERIVATION`: **PASS** (Derives active cue dynamically from verified audio position)

---

## 3. Video Playback Subsystem Architecture & Evidence (Truth-Lock Hardened)

* **Core Components**:
  - `AndroidMedia3VideoEngine`: Media3 ExoPlayer (v1.5.1) engine implementing `VideoPlaybackEngine` with `C.AUDIO_CONTENT_TYPE_MOVIE`, `Player.Listener`, `onIsPlayingChanged`, `onPositionDiscontinuity`, `onVideoSizeChanged` -> `onVideoMetadataChanged`, `onPlaybackStateChanged`, and setup exception leak release protection.
  - `VideoPlaybackController`: Action-time file validation (`VideoPlaybackSource` checking container signatures, size match, `NOT_READY` precedence), `sessionGeneration` stale callback dropping, authoritative playing activity handling, asynchronous seek confirmation, replay rewind confirmation tolerance (`0L..250L`), decoupled metadata updates, buffering-to-paused transition safety, and progress tracking.
  - `VideoPlaybackViewer`: Fullscreen Compose HUD overlay surface with `AndroidView(factory = { PlayerView(...) })`, video aspect ratio preservation, retro-industrial controls (play/pause, seek slider, speed selector, timecode displays, system telemetry).
  - `MainScreen`: Presentation mutual exclusion (video viewer releases audio player; audio player / transcript viewer releases video player).
* **Evidence Labels**:
  - `MEDIA3 AUTHORITATIVE PLAYING`: **PASS** (Controller `PLAYING` phase and progress polling are driven strictly by engine `onActivityChanged(PLAYING)`)
  - `MEDIA3 ASYNCHRONOUS SEEK CONFIRMATION`: **PASS** (`currentPositionMs` updates only upon engine `onPositionDiscontinuity`)
  - `COMPLETED REPLAY REWIND CONFIRMATION`: **PASS** (Replay requests rewind to 0 and starts only upon confirmed discontinuity within `0..250ms`; non-zero discontinuity rejects start and preserves completed state)
  - `DECOUPLED VIDEO METADATA UPDATE`: **PASS** (Late video dimensions update dimensions without resetting position or phase)
  - `BUFFERING TO PAUSED RESILIENCE`: **PASS** (Transition from `LOADING` / buffering to `PAUSED` captures current position and transitions phase safely)
  - `ACTION-TIME PREFLIGHT VALIDATION`: **PASS** (Rejects random-byte `.mp4`, size mismatch, `NOT_READY`, `.part`, `.tmp`, or missing files)
  - `EXOPLAYER SETUP LEAK PROTECTION`: **PASS** (Guarantees local player release on setup exception before assignment)
  - `VIDEO ACTION ROUTING`: **PASS** (`VaultPrimaryAction.PLAY_VIDEO` bound strictly to `MediaType.VIDEO` + `VaultFileState.AVAILABLE`)

---

## 4. Reactive Vault & Search / Filter Architecture & Evidence

* **Core Components**:
  - `DefaultVaultItemEvaluator`: Fast bounded prefix inspection (<= 768 bytes) to verify container signatures without full-file SHA-256 computation during UI evaluation.
  - `LibraryViewModel`: Pure two-stage reactive pipeline:
    - Stage 1: Room DB flow combined with async file inspection on `Dispatchers.IO`.
    - Stage 2: In-memory multi-criteria search and category filtering on `StateFlow` (`SharingStarted.WhileSubscribed(5000)`).
* **Search / Filter Truth**:
  - Search fields: Calls (`contactName`, `phoneNumber`, `direction`), Media (`title`, `uploader`, `sourcePlatform`, `resolution`, `formatSelected`).
  - Filtering: 0 disk I/O on query/tab filter changes.
  - State evaluations: `AVAILABLE`, `NOT_READY`, `MISSING`, `UNREADABLE`, `INVALID_MEDIA`, `SIZE_MISMATCH`, `NO_LOCAL_FILE`.
  - `NOT_READY` precedence enforced when `downloadStatus != DownloadStatus.COMPLETED`.

---

## 5. Physical Device Gate Status

* **Target Hardware**:
  - Samsung Galaxy S22 (`SM-S901E`, Android 16 / SDK 36, product: `r0qxtur`, device: `r0q`)
  - Fingerprint: `samsung/r0qxtur/r0q:16/BP2A.250605.031.A3/S901EXXSCGZA2:user/release-keys`
* **Build Verification**:
  - `.\gradlew.bat clean assembleDebug`: **PASS**
* **Physical Device Runtime Status**:
  - `PHYSICAL DEVICE CONNECTIVITY`: **CONNECTED & VERIFIED**
  - `BUILD COMPILATION & PACKAGING`: **PASS** (`app-debug.apk` installed successfully)
  - `PHYSICAL AUDIO PLAYBACK QUALIFICATION`: **PASS** (Audio tone verified, position advancement, pause/resume, seek, speed, completion, replay, clean release)
  - `PHYSICAL VIDEO PLAYBACK QUALIFICATION`: **PASS** (Moving frames animation verified, stereo audio verified, position advancement, pause/resume, seek, speed, completion, replay, clean release)
  - `HUMAN OBSERVATION CONFIRMATION`: **PASS** (Confirmed: moving frames present, video audio present, audio tone present, clean playback stop on close)

---

## 6. Global Sequence & Scope Invariants

* `P0 = LOCKED / PASS`
* `P1 = LOCKED / TRUTH-LOCKED`
* `P2 = LOCKED / PASS`
* `P3 = LOCKED / PASS`
* `P4 = LOCKED / AWAITING USER ACTIVATION` (Local Transcription untouched)
* `P5+ = LOCKED`
* `ROOM DB SCHEMA = 1` (Zero entity/DAO/table modifications)
