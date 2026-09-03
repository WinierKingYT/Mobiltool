# P2 YouTube Extraction Qualification Evidence

**Execution Date:** 2026-09-03  
**Module:** `:media-extractor-api`  
**Extractor Engine:** `NewPipeExtractor:v0.26.5` (JitPack)  
**Binary Streaming Engine:** `RealHttpStreamDownloader` (Pure JVM OkHttp + Staging + Validation + Atomic Move)  
**Security Boundary:** Zero user credentials, zero private cookie scraping, zero DRM bypass, fail-closed SSRF defense.  
**Opt-in Gradle Verification Task:** `.\gradlew.bat :media-extractor-api:realYouTubeExtractionTest`

---

## 1. Test Execution & Observed Runtime Facts

### Case 1: Public Standard Video (Full End-to-End Extraction & Download)
- **Target URL:** `https://www.youtube.com/watch?v=jNQXAC9IVRw`
- **Canonical Resolved URL:** `https://www.youtube.com/watch?v=jNQXAC9IVRw`
- **Observed Title:** `Me at the zoo`
- **Observed Duration:** `19000 ms` (19 seconds)
- **Observed Uploader:** `jawed`
- **Observed Formats Count:** `6` formats (Video + Audio streams extracted truthfully)
- **Selected Format:** `yt-audio-0-m4a` (`YouTube Audio (m4a)`)
- **Direct Stream URL Extraction:** `SUCCESS` (Resolved direct `googlevideo.com` CDN stream URL; query token/signature redacted)
- **Binary Download Execution:** `SUCCESS` (Streamed in 32KB buffers to `.part` staging file)
- **Observed File Size (Bytes):** `117,526` bytes
- **Observed MIME Type:** `video/mp4`
- **Observed Container / Media Kind:** `UNKNOWN` (Strict format decoupled from container)
- **Final Storage Commit Method:** `StandardCopyOption.ATOMIC_MOVE` (Zero renameTo/copy fallback)
- **Final Result:** `PASS` (Canonical file present, staging file deleted, file length = 117,526 bytes)

---

### Case 2: Public YouTube Short Probe
- **Target URL:** `https://www.youtube.com/shorts/jNQXAC9IVRw`
- **Canonical Resolved URL:** `https://www.youtube.com/watch?v=jNQXAC9IVRw` (Normalized)
- **Observed Title:** `Me at the zoo`
- **Observed Duration:** `19000 ms`
- **Observed Formats Count:** `6`
- **Stream URL Extraction Result:** `SUCCESS`
- **Final Result:** `PASS`

---

## 2. Platform Extraction Invariant Verification

| Platform | Approval Status | Extractor Adapter | Production Behavior |
| :--- | :--- | :--- | :--- |
| **YouTube** | **APPROVED (ADR_002)** | `NewPipeYouTubeExtractor` (`v0.26.5`) | Probes metadata & formats, extracts direct stream URL, downloads via `RealHttpStreamDownloader` |
| **Instagram** | **NOT APPROVED** | Unlinked (`null`) | `PLATFORM_EXTRACTION_UNAVAILABLE` (`ErrorCode.EXTRACTION_FAILED`) |
| **X / Twitter** | **NOT APPROVED** | Unlinked (`null`) | `PLATFORM_EXTRACTION_UNAVAILABLE` (`ErrorCode.EXTRACTION_FAILED`) |
| **Direct HTTP** | **APPROVED** | `HttpMediaProber` + `RealHttpStreamDownloader` | Direct binary download with SHA-256 + atomic move |

---

## 3. Licensing & Scope Boundary
- NewPipeExtractor dependency: `GPL-3.0-or-later`.
- Authorized use: **Private / Personal / Non-distributed use only** in accordance with User ADR Decision on ADR_002.
- Public distribution constraint: If Mobiltool is ever distributed externally, a legal/licensing review is required before release.
