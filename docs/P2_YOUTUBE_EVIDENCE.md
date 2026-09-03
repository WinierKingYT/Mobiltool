# P2 YouTube Extraction Qualification Evidence

**Execution Date:** 2026-09-03  
**Module:** `:media-extractor-api`  
**Extractor Engine:** `NewPipeExtractor:v0.26.5` (JitPack)  
**Binary Streaming Engine:** `RealHttpStreamDownloader` (Pure JVM OkHttp + Staging + Validation + Atomic Move)  
**Security Boundary:** Internal NewPipe SSRF defense with `NetworkSecurityPolicy`, `ValidatedDns`, and hop-by-hop redirect inspection. Zero user credentials, zero private cookie scraping, zero DRM bypass.  
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
- **Requested Format ID:** `youtube:audio:itag:139`
- **Resolved Format ID:** `youtube:audio:itag:139`
- **Format Type:** `AUDIO` (itag: `139`)
- **Direct Stream URL Host:** `rr8---sn-u0g3uxax3-pnur.googlevideo.com` (Expiring signature tokens redacted)
- **Stream URL Extraction Result:** `SUCCESS` (Exact format matching: `requestedFormatId == resolvedFormatId`)
- **Binary Download Execution:** `SUCCESS` (Streamed in 32KB buffers to `.part` staging file)
- **Observed File Size (Bytes):** `117,526` bytes
- **Observed MIME Type:** `null` (MIME Truth: Generic ISO-BMFF without track inspector does not claim video/mp4)
- **Observed Container / Media Kind:** `MP4_ISO_BMFF` / `UNKNOWN` (Strict format decoupled from container)
- **SHA-256 Digest:** `193a32b41614362c159652d238dfebcce6029285614828cbeb67401cd1c78111`
- **Final Storage Commit Method:** `StandardCopyOption.ATOMIC_MOVE` (Derived dynamically from fileCommitter)
- **Final Result:** `PASS` (Canonical file present, staging file deleted, file length = 117,526 bytes)

---

### Case 2: Public YouTube Short Probe & Stream Resolution
- **Target URL:** `https://www.youtube.com/shorts/jNQXAC9IVRw`
- **Canonical Resolved URL:** `https://www.youtube.com/watch?v=jNQXAC9IVRw` (Normalized)
- **Observed Title:** `Me at the zoo`
- **Observed Duration:** `19000 ms`
- **Observed Formats Count:** `6`
- **Requested Format ID:** `youtube:video:itag:18`
- **Resolved Format ID:** `youtube:video:itag:18`
- **Stream Resolution Result:** `SUCCESS` (Direct `rr8---sn-u0g3uxax3-pnur.googlevideo.com` stream resolved; exact format match confirmed)
- **Final Result:** `PASS`

---

## 2. Platform Extraction Invariant Verification

| Platform | Approval Status | Extractor Adapter | Production Behavior |
| :--- | :--- | :--- | :--- |
| **YouTube** | **APPROVED (ADR_002)** | `NewPipeYouTubeExtractor` (`v0.26.5`) | Probes metadata & formats with stable itag IDs, extracts exact stream, downloads via `RealHttpStreamDownloader` |
| **Instagram** | **NOT APPROVED** | Unlinked (`null`) | `PLATFORM_EXTRACTION_UNAVAILABLE` (`ErrorCode.EXTRACTION_FAILED`) |
| **X / Twitter** | **NOT APPROVED** | Unlinked (`null`) | `PLATFORM_EXTRACTION_UNAVAILABLE` (`ErrorCode.EXTRACTION_FAILED`) |
| **Direct HTTP** | **APPROVED** | `HttpMediaProber` + `RealHttpStreamDownloader` | Direct binary download with SHA-256 + atomic move |

---

## 3. Licensing & Scope Boundary
- NewPipeExtractor dependency: `GPL-3.0-or-later`.
- Authorized use: **Private / Personal / Non-distributed use only** in accordance with User ADR Decision on ADR_002.
- Public distribution constraint: If Mobiltool is ever distributed externally, a legal/licensing review is required before release.
