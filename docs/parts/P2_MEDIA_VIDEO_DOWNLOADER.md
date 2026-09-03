# P2 - Media / Video Downloader

**Status:** `LOCKED / PASS`  
**Baseline HEAD:** `1613ba8c2f8651cefdd4a2fead72e3d7ce3836fd`

---

## 1. Goal & Architecture
Real progressive HTTP stream downloading with strict SSRF defense, verified content integrity, atomic commit semantics, and compliant platform adapters.

### Supported Platforms & Capabilities:
* **Direct Public HTTP/HTTPS**: SUPPORTED / QUALIFIED (`HttpMediaProber` + `RealHttpStreamDownloader` with staging, validation, SHA-256 calculation, and `StandardCopyOption.ATOMIC_MOVE`).
* **YouTube**: SUPPORTED / QUALIFIED FOR PUBLIC CONTENT (`ADR_002` APPROVED under private/personal/non-distributed scope; pure JVM `NewPipeExtractor:v0.26.5` adapter with exact upstream stable itags, zero stream substitution fallbacks, internal SSRF defense, and Mobiltool verified atomic downloader).
* **Instagram**: EXTRACTION_UNAVAILABLE / NOT APPROVED (Fails closed with `ErrorCode.EXTRACTION_FAILED`).
* **X / Twitter**: EXTRACTION_UNAVAILABLE / NOT APPROVED (Fails closed with `ErrorCode.EXTRACTION_FAILED`).

---

## 2. Locked Invariants:
* `RECOGNIZED URL != SUPPORTED EXTRACTION`
* `EXTRACTED STREAM != SUCCESSFUL DOWNLOAD`
* `HTTP 200 != VALID MEDIA`
* `FILE EXISTS != VALID MEDIA`
* `TRACK TYPE UNKNOWN -> MIME MUST NOT CLAIM AUDIO/VIDEO`
* `USER SELECTED FORMAT == EXACT RESOLVED ITAG`
* `NO EXACT FORMAT -> FAIL CLOSED`
* `UNKNOWN / UNSTABLE ITAG -> DO NOT EXPOSE AS SELECTABLE FORMAT`
* `CANONICAL MEDIA -> ONLY AFTER VALIDATION + HASH + ATOMIC COMMIT`

---

## 3. Evidence Record:
* **Build**: PASS (`.\gradlew.bat clean assembleDebug`)
* **Unit Tests**: PASS (`.\gradlew.bat test` - 219 tasks passing)
* **Real Direct Media Test**: PASS (`.\gradlew.bat :media-extractor-api:realDirectMediaTest`)
* **Real YouTube Extraction Test**: PASS (`.\gradlew.bat :media-extractor-api:realYouTubeExtractionTest`)
* **Independent Review**: PASS
* **GitHub CI**: NOT CONFIGURED / NO REMOTE STATUS CHECK EVIDENCE