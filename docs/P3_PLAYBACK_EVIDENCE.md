# P3 — Library & Playback Verification Evidence

## Baseline & Verification Status

```text
ACTIVE_PART = P3
STATUS = LOCKED / PASS
PREVIOUS PARTS = P0 (LOCKED/PASS), P1 (LOCKED/TRUTH-LOCKED), P2 (LOCKED/PASS)
FUTURE PARTS = P4+ (LOCKED)
ROOM DB SCHEMA VERSION = 1 (STRICTLY UNCHANGED)
```

---

## 1. Test Suite Execution Evidence

* **Command**: `.\gradlew.bat test` / `.\gradlew.bat testDebugUnitTest`
* **Result**: `BUILD SUCCESSFUL` (0 failures, 0 errors, 0 skipped)
* **Total Repository Unit Tests**: 267 tests

### Breakdown by Module & Suite:

| Module | Test Suite | Tests | Result |
|---|---|---|---|
| `:app` | `AudioPlaybackControllerTest` | 25 | PASS |
| `:app` | `RealAudioPlayerTest` | 3 | PASS |
| `:app` | `VideoPlaybackControllerTest` | 23 | PASS |
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
| **Total** | **22 Suites** | **267** | **PASS** |

---

## 2. Audio Playback Subsystem Architecture & Evidence

* **Core Components**:
  - `AudioPlaybackController`: Orchestrates foreground audio lifecycle, generation counters, state management, progress polling (250ms while `PLAYING`), fail-closed seek, audio focus interruption handling, and transcript synchronization.
  - `RealAudioPlayer`: Real Android `MediaPlayer` integration with async preparation, `PlaybackParams` speed adjustment (0.5x..2.0x), and safe lifecycle teardown.
  - `AudioPlaybackSheet`: Industrial-styled Compose audio playback UI surface.
* **Evidence Labels**:
  - `AUDIO INTERRUPTION CONTRACT TESTS`: **PASS**
  - `ANDROID AUDIO FOCUS ENGINE INTEGRATION`: **CODE_REVIEWED_NOT_RUNTIME_TESTED**
  - `SEEK TRUTH / FAIL-CLOSED INVARIANT`: **PASS** (Seek failure rejects state change and aborts play-after-seek)
  - `TRANSCRIPT POSITION DERIVATION`: **PASS** (Derives active cue dynamically from verified audio position)

---

## 3. Video Playback Subsystem Architecture & Evidence

* **Core Components**:
  - `AndroidMedia3VideoEngine`: Media3 ExoPlayer (v1.5.1) engine implementing `VideoPlaybackEngine` with `C.AUDIO_CONTENT_TYPE_MOVIE`, `Player.Listener`, `STATE_READY`, `STATE_ENDED`, `onVideoSizeChanged`, and fail-safe resource release.
  - `VideoPlaybackController`: Action-time file validation (rejection of blank/missing/unreadable/`.part`/`.tmp`), `sessionGeneration` stale callback dropping, fail-closed replay seek, and progress tracking.
  - `VideoPlaybackViewer`: Fullscreen Compose HUD overlay surface with `AndroidView(factory = { PlayerView(...) })`, video aspect ratio preservation, retro-industrial controls (play/pause, seek slider, speed selector, timecode displays, system telemetry).
  - `MainScreen`: Presentation mutual exclusion (video viewer releases audio player; audio player / transcript viewer releases video player).
* **Evidence Labels**:
  - `MEDIA3 EXOPLAYER INTEGRATION`: **PASS** (ExoPlayer 1.5.1 configured per ADR and project standards; no VLC/ffmpeg foreign player)
  - `ACTION-TIME FILE REVALIDATION`: **PASS** (Rejects `.part`, `.tmp`, unreadable, or missing files before opening player)
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

* **ADB Probe**:
  - `adb devices`: Samsung Galaxy S22 (`SM-S901E`, Android 16 / SDK 36) attached via wireless ADB.
* **Build Verification**:
  - `.\gradlew.bat assembleDebug`: **PASS**
* **Physical Device Runtime Status**:
  - `PHYSICAL DEVICE CONNECTIVITY`: **ATTACHED** (`SM-S901E`)
  - `BUILD COMPILATION & PACKAGING`: **PASS** (`assembleDebug` succeeded)
  - `PHYSICAL PLAYBACK QUALIFICATION`: **READY_FOR_USER_QUALIFICATION**

---

## 6. Global Sequence & Scope Invariants

* `P0 = LOCKED / PASS`
* `P1 = LOCKED / TRUTH-LOCKED`
* `P2 = LOCKED / PASS`
* `P3 = LOCKED / PASS`
* `P4 = LOCKED` (Speech-to-Text / Whisper untouched)
* `P5+ = LOCKED`
* `ROOM DB SCHEMA = 1` (Zero entity/DAO/table modifications)
