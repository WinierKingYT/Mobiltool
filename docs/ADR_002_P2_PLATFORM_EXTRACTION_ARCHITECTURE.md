# ADR 002 (Revised): P2 Media Downloader — Platform Extraction Architecture

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
- **Sequential Gate**: Parts P3+ and P7+ are strictly locked. P2 cannot depend on runtime implementation from later locked parts (e.g. desktop handoff).

---

## 2. In-Depth Evaluation of Extraction Runtimes

```text
+------------------------------------------+----------------+---------------------+-------------------+-----------------+
| Dimension                                | NewPipeExtractor| youtubedl-android  | Public REST/OEmbed| Desktop Bridge  |
+------------------------------------------+----------------+---------------------+-------------------+-----------------+
| License                                  | GPL-3.0-or-later| MIT / Unlicense / GPL| Proprietary/Apache| Proprietary/MIT |
| Android 16 / 16KB Page-Size Risk         | Zero (Pure JVM)| High (Native C/Py)  | Zero (Pure JVM)   | N/A (Remote)    |
| APK Footprint Increase                   | ~2 - 4 MB      | ~35 - 50 MB         | < 0.5 MB          | 0 MB on device  |
| YouTube Coverage                         | High (Dedicated| High (yt-dlp core)  | None              | High (yt-dlp)   |
| Instagram Coverage                       | None (0%)      | Moderate (yt-dlp)   | Low/Fragile       | Moderate        |
| X (Twitter) Coverage                     | None (0%)      | Moderate (yt-dlp)   | Low/Fragile       | Moderate        |
| Maintenance Churn                        | High (BotGuard)| High (yt-dlp core)  | Very High         | Upstream CLI    |
+------------------------------------------+----------------+---------------------+-------------------+-----------------+
```

### 2.1 Licensing Analysis: NewPipeExtractor (GPL-3.0-or-later)
- **License**: `GPL-3.0-or-later`.
- **Implications**: NewPipeExtractor is a copyleft library. If Mobiltool statically or dynamically links NewPipeExtractor and distributes the combined Android binary, the entire Mobiltool Android codebase must be licensed under GPL-3.0-or-later and made available in source form upon request.
- **Non-Distribution Exception**: If Mobiltool remains an unreleased private personal tool, GPL copyleft distribution clauses are not triggered. However, if distribution is planned, an explicit licensing decision (either full open-source GPL-3.0 release or using a non-GPL alternative) is mandatory.

### 2.2 Native Python Runtime on Android: youtubedl-android (Seal-style)
- **Mechanism**: Bundles CPython + `yt-dlp` scripts compiled for Android architectures (arm64-v8a, armeabi-v7a, x86_64).
- **Android 15 / 16 Compatibility & 16 KB Page Alignment**: Android 15+ devices enforce 16 KB memory page size alignment. Python C-extension binaries (`.so`) bundled in the APK must be specifically compiled with `-z max-page-size=16384`.
- **Runtime Sandboxing & W^X**: Modern Android prohibits executing binaries from writable app storage (`/data/data/...`). Binaries must reside in the uncompressed APK native library directory (`/data/app/.../lib`).
- **Update Mechanism & Cadence**: `yt-dlp` updates several times per month. Updating python scripts dynamically at runtime on Android requires either dynamic script loading (which risks Google Play policy compliance if distributed) or frequent APK releases.
- **Memory & Process Overhead**: Spawning a Python runtime on a mobile device consumes 60MB–120MB RSS during extraction, compared to < 10MB for pure JVM streaming.

### 2.3 Upstream Platform Reliability & Churn (YouTube PO Token / BotGuard)
- **YouTube Churn**: YouTube frequently updates signature decyphering (`nsig`), streaming endpoints (SABR), and enforces Proof of Origin (PO Token) / BotGuard challenges.
- **Failure Mode**: When upstream breaks, extractors fail with HTTP 403 or signature decipher errors. Mobiltool must catch these cleanly, emit transparent diagnostics, and fall back to `PLATFORM_EXTRACTION_UNAVAILABLE`.

### 2.4 Desktop Handoff Boundary (Future Architecture)
- Desktop Workstation Companion (P7/P8/P9) is documented as a future optional enhancement for offloading heavyweight scraping. Because P7+ is **LOCKED**, P2 does NOT depend on it for mobile operation.

---

## 3. Platform-by-Platform Architecture Decision

```text
+-------------------+-----------------------------------------------+-----------------------------------+
| Platform          | Selected Architecture Option                 | Runtime State                     |
+-------------------+-----------------------------------------------+-----------------------------------+
| 1. Direct HTTP    | SafeHttpTransport + RealHttpStreamDownloader  | ACTIVE / FULLY OPERATIONAL        |
| 2. YouTube        | Pure JVM Extractor (NewPipeExtractor engine)   | PENDING ADR APPROVAL (UNLINKED)   |
| 3. Instagram      | Standalone Public REST Metadata Adapter       | PENDING ADR APPROVAL (UNLINKED)   |
| 4. X (Twitter)    | Standalone Syndication API Adapter            | PENDING ADR APPROVAL (UNLINKED)   |
+-------------------+-----------------------------------------------+-----------------------------------+
```

### Decision for Direct HTTP Media Streams:
- **Decision**: `APPROVED & INTEGRATED`.
- **Pipeline**: Strict SSRF defense + Bound DNS to pre-validated IPs + Safe hop-by-hop redirect inspection + 32KB streaming to `.part` staging + Magic byte container verification + SHA-256 calculation + Atomic destination move.

### Decision for YouTube:
- **Proposed Architecture**: Pure JVM YouTube Extractor (NewPipeExtractor engine for public stream URL extraction only; all binary video/audio streams are downloaded via Mobiltool''s verified `RealHttpStreamDownloader`).
- **GPL License Acceptance**: Requires user acknowledgment of GPL-3.0-or-later license for NewPipeExtractor dependency.
- **Current State**: `UNLINKED / AWAITING USER APPROVAL`.

### Decision for Instagram:
- **Proposed Architecture**: Standalone pure Kotlin public post/reel metadata extractor utilizing public GraphQL/oEmbed endpoints without login/cookie scraping. If Instagram enforces login/checkpoint, fail closed with `PLATFORM_EXTRACTION_UNAVAILABLE`.
- **Current State**: `UNLINKED / AWAITING USER APPROVAL`.

### Decision for X (Twitter):
- **Proposed Architecture**: Standalone pure Kotlin public tweet video parser utilizing public syndication endpoints without cookie scraping. If rate-limited or blocked, fail closed with `PLATFORM_EXTRACTION_UNAVAILABLE`.
- **Current State**: `UNLINKED / AWAITING USER APPROVAL`.

---

## 4. Status & Hard Stop

**STATUS**: `PROPOSED — AWAITING USER APPROVAL`  
*No platform extractor code will be activated until explicit user review and approval of the licensing and platform decisions.*
