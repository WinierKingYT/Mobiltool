# Active Part

```text
ACTIVE_PART = P3
STATUS = IN_PROGRESS
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
- **P3: Library & Playback** - ACTIVE / IN_PROGRESS
- **P4: Local Transcription** - LOCKED
- **P5+: Core Foundation, Dev Bridge, Remote Desktop** - LOCKED

---

## P2 Final Capability Matrix:
* **Direct Public HTTP/HTTPS**: SUPPORTED / QUALIFIED (SSRF defense, `ValidatedDns`, .part staging, SHA-256 verification, `StandardCopyOption.ATOMIC_MOVE`).
* **YouTube**: SUPPORTED / QUALIFIED FOR PUBLIC CONTENT (ADR_002 approved, stable upstream itags, pure JVM `NewPipeExtractor:v0.26.5` adapter, zero stream fallbacks, fail-closed on unknown itags).
* **Instagram**: EXTRACTION_UNAVAILABLE / NOT APPROVED.
* **X / Twitter**: EXTRACTION_UNAVAILABLE / NOT APPROVED.

---

## P2 Retained Invariants:
* `RECOGNIZED URL != SUPPORTED EXTRACTION`
* `EXTRACTED STREAM != SUCCESSFUL DOWNLOAD`
* `HTTP 200 != VALID MEDIA`
* `FILE EXISTS != VALID MEDIA`
* `TRACK TYPE UNKNOWN -> MIME MUST NOT CLAIM AUDIO/VIDEO`
* `USER SELECTED FORMAT == EXACT RESOLVED ITAG`
* `NO EXACT FORMAT -> FAIL CLOSED`
* `UNKNOWN / UNSTABLE ITAG -> DO NOT EXPOSE AS SELECTABLE FORMAT`
* `CANONICAL MEDIA -> ONLY AFTER VALIDATION + HASH + ATOMIC COMMIT`
* `HTTP CONTENT-TYPE != MEDIA PAYLOAD PROOF`

---

## P2 Evidence Record:
* **Baseline HEAD**: `1613ba8c2f8651cefdd4a2fead72e3d7ce3836fd`
* **Post-Lock Regression Hardening Commits**: `e15a893`, `970c66e`
* **Build**: PASS - LOCAL EXECUTION EVIDENCE (`.\gradlew.bat clean assembleDebug`)
* **Media Unit Tests**: PASS - LOCAL EXECUTION EVIDENCE (`.\gradlew.bat :media-extractor-api:test` - 77 offline tests)
* **Full Unit Tests**: PASS - LOCAL EXECUTION EVIDENCE (`.\gradlew.bat test` - 219 tasks across all modules)
* **Real Direct Evidence**: PASS - LOCAL EXECUTION EVIDENCE (`.\gradlew.bat :media-extractor-api:realDirectMediaTest` - 834,563 bytes, SHA-256 `d0502ba7...`, `MEDIA KIND: UNKNOWN`, `DETECTED MIME: null`, `ATOMIC_MOVE`)
* **Real YouTube Evidence**: PASS - LOCAL EXECUTION EVIDENCE (`.\gradlew.bat :media-extractor-api:realYouTubeExtractionTest` - Short probe & Video download 117,526 bytes, SHA-256 `193a32b4...`, `MEDIA KIND: UNKNOWN`, `MIME TYPE: null`, `ATOMIC_MOVE`)
* **Independent Remote Code Review**: PASS
* **GitHub CI**: NOT CONFIGURED / NO REMOTE STATUS CHECK EVIDENCE