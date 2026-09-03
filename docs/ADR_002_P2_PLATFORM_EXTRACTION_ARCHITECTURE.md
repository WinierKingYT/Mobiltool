# ADR 002 (Final Revision): P2 Media Downloader — Platform Extraction Architecture

**Date**: 2026-09-03  
**Status**: PROPOSED — AWAITING USER APPROVAL  
**Deciders**: Engineering / User  
**Context**: Mobiltool Engineering Operating System (Part P2)  

---

## 1. Context & Operational Constraints

Mobiltool requires a secure, legally compliant, and technically truthful media extraction pipeline for public media streams across:
1. **Direct Public HTTP/HTTPS Streams** (Complete & verified in P2 baseline).
2. **YouTube** (Public videos, Shorts).
3. **Instagram** (Public Reels, Posts).
4. **X / Twitter** (Public Status Media).

### Operational Invariants & Legal Boundary:
- **Zero DRM / Paywall Bypass**: No decryption of DRM-protected streams (Widevine/FairPlay), private accounts, or authentication bypass.
- **Fail Closed**: If upstream platform APIs or player tokens change, the application must immediately return `PLATFORM_EXTRACTION_UNAVAILABLE` with `ErrorCode.EXTRACTION_FAILED` rather than fabricating formats or silent fallbacks.
- **Sequential Gate**: Parts P3+ and P7+ are strictly locked. P2 cannot depend on runtime implementation from later locked parts (e.g. desktop companion).

---

## 2. Extraction Runtime Analysis & Licensing

```text
+------------------------------------------+-----------------------+---------------------+-------------------+-----------------+
| Dimension                                | NewPipeExtractor      | youtubedl-android   | Public REST/OEmbed| Desktop Bridge  |
+------------------------------------------+-----------------------+---------------------+-------------------+-----------------+
| Primary License                          | GPL-3.0-or-later      | GPL-3.0 (Wrapper)   | N/A               | Proprietary/MIT |
| Android minSdk Requirement               | >= 33 or Desugaring   | >= 24               | >= 26             | N/A (Remote)    |
| Android 16 / 16KB Page-Size Risk         | Zero (Pure JVM)       | High (Native C/Py)  | Zero (Pure JVM)   | N/A (Remote)    |
| APK Footprint Increase                   | ~2 - 4 MB             | ~35 - 50 MB         | < 0.5 MB          | 0 MB on device  |
| YouTube Coverage                         | YES (Dedicated Target)| YES (yt-dlp core)   | NO                | YES (yt-dlp)    |
| Instagram Coverage                       | NO (0%)               | YES (yt-dlp core)   | Unresolved/Fragile| YES             |
| X (Twitter) Coverage                     | NO (0%)               | YES (yt-dlp core)   | Unresolved/Fragile| YES             |
| Maintenance Churn                        | High (BotGuard/SABR)  | High (yt-dlp core)  | Extreme           | Upstream CLI    |
+------------------------------------------+-----------------------+---------------------+-------------------+-----------------+
```

### 2.1 NewPipeExtractor Build & Licensing Details
- **License**: `GPL-3.0-or-later`.
- **Distribution & Copyleft Analysis**: Directly distributing an Android application that links a GPL-licensed library may impose GPL obligations on the combined distributed work. Distribution therefore requires an explicit licensing/legal decision. Private, non-distributed personal use does not trigger source-distribution obligations. Mark: `LEGAL REVIEW REQUIRED IF DISTRIBUTED`.
- **Mobiltool `minSdk` Compatibility (Section A)**:
  - Mobiltool is configured for `minSdk = 26`.
  - Official NewPipeExtractor requires Java 8+ / NIO APIs. For Android projects with `minSdk < 33`, it mandates core-library desugaring (`desugar_jdk_libs_nio`).
  - *Build Prerequisite*: Enabling `coreLibraryDesugaring` with `com.android.tools:desugar_jdk_libs_nio` in `app/build.gradle.kts` and `media-extractor-api/build.gradle.kts` is a mandatory prerequisite before any NewPipeExtractor code can compile on API 26–32.
- **Platform Coverage (Section B)**:
  - YouTube: **YES** (Actively maintained target).
  - Instagram: **NO** (0% supported by NewPipeExtractor).
  - X / Twitter: **NO** (0% supported by NewPipeExtractor).

### 2.2 youtubedl-android Ecosystem & Licensing Details (Section C)
- **Licensing Composition**:
  - Wrapper project (`yausername/youtubedl-android`): `GPL-3.0`.
  - Core extraction script (`yt-dlp`): `The Unlicense` (Public Domain equivalent).
  - Embedded Python C-runtime & FFmpeg binaries: `Python Software Foundation License` / `LGPL 2.1+` / `GPL`.
- **Android 15 / 16 Compatibility & 16 KB Page Alignment**: Android 15+ devices enforce 16 KB memory page size alignment. Python C-extension binaries (`.so`) bundled in the APK must be specifically compiled with `-z max-page-size=16384`.
- **Runtime Sandboxing & W^X**: Modern Android prohibits executing binaries from writable app storage (`/data/data/...`). Binaries must reside in the uncompressed APK native library directory (`/data/app/.../lib`).
- **Update Mechanism & Cadence**: `yt-dlp` updates several times per month. Updating python scripts dynamically at runtime on Android requires either dynamic script loading (which risks Google Play policy compliance if distributed) or frequent APK releases.
- **Memory & Process Overhead**: Spawning a Python runtime on a mobile device consumes 60MB–120MB RSS during extraction, compared to < 10MB for pure JVM streaming.

### 2.3 Upstream YouTube Churn & Reliability (Section D)
- **YouTube Churn**: YouTube frequently updates signature decyphering (`nsig`), streaming endpoints (SABR), and enforces Proof of Origin (PO Token) / BotGuard challenges.
- **Failure Mode**: When upstream breaks, extractors fail with HTTP 403 or signature decipher errors. Mobiltool must catch these cleanly, emit transparent diagnostics, and fall back to `PLATFORM_EXTRACTION_UNAVAILABLE`.

### 2.4 Desktop Handoff Boundary (Section H)
- Desktop Workstation Companion (P7/P8/P9) is documented as a future optional enhancement for offloading heavyweight scraping. Because P7+ is **LOCKED**, P2 does NOT depend on it for mobile operation.

---

## 3. Platform-by-Platform Architecture Decision

```text
+-------------------+-----------------------------------------------+-----------------------------------+
| Platform          | Selected Architecture Option                 | Runtime State                     |
+-------------------+-----------------------------------------------+-----------------------------------+
| 1. Direct HTTP    | SafeHttpTransport + RealHttpStreamDownloader  | ACTIVE / FULLY OPERATIONAL        |
| 2. YouTube        | Pure JVM Extractor (NewPipeExtractor engine)   | CONDITIONALLY APPROVABLE          |
| 3. Instagram      | Standalone Public REST / Scraper              | EXTRACTION_UNAVAILABLE            |
| 4. X (Twitter)    | Standalone Public Syndication API             | EXTRACTION_UNAVAILABLE            |
+-------------------+-----------------------------------------------+-----------------------------------+
```

### 1. Direct HTTP Media Streams:
- **Decision**: `APPROVED & INTEGRATED`.
- **Pipeline**: Strict SSRF defense + ValidatedDns bound to pre-validated IPs + Safe hop-by-hop redirect inspection + 32KB streaming to `.part` staging + Magic byte container verification + SHA-256 calculation + `StandardCopyOption.ATOMIC_MOVE`.

### 2. YouTube Decision (Section E):
- **Status**: `CONDITIONALLY APPROVABLE — NEWPIPEEXTRACTOR`.
- **Mandatory Approval Conditions**:
  1. User explicitly accepts `GPL-3.0-or-later` dependency for current personal-use scope.
  2. `minSdk = 26` core-library desugaring (`desugar_jdk_libs_nio`) build compatibility is implemented and tested.
  3. Adapter is strictly isolated behind `MediaExtractor` interface.
  4. Only extraction metadata and direct stream URLs are derived from NewPipeExtractor; all binary media streaming downloads continue exclusively through Mobiltool''s verified `SafeHttpTransport` + `RealHttpStreamDownloader`.
  5. Zero cookies, private account scraping, or DRM bypass.
  6. Real public YouTube fixtures must pass before status changes to SUPPORTED.
  7. Upstream NewPipe extraction failure maps transparently to `PLATFORM_EXTRACTION_UNAVAILABLE` with `ErrorCode.EXTRACTION_FAILED`.

### 3. Instagram Decision (Section F):
- **Status**: `EXTRACTION_UNAVAILABLE / ARCHITECTURE UNRESOLVED`.
- **Rationale**: No homemade or unproven scraping logic is approved until a concrete, maintained, current public source/API approach is researched and demonstrated.
- **Constraints**: No cookies, no login bypass, no checkpoint bypass.

### 4. X (Twitter) Decision (Section G):
- **Status**: `EXTRACTION_UNAVAILABLE / ARCHITECTURE UNRESOLVED`.
- **Rationale**: No unvetted scraping logic is approved without proving that a public syndication endpoint is currently available, stable, and suitable.
- **Constraints**: No cookie scraping, no authenticated API keys.

---

## 4. Status & Hard Stop

**STATUS**: `PROPOSED — AWAITING USER APPROVAL`  
*No platform extraction code (YouTube, Instagram, X) will be implemented until explicit user review and approval of this revised decision record.*
