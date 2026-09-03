# ADR 002: P2 Media Downloader — Platform Extraction Architecture

**Date**: 2026-09-03  
**Status**: APPROVED — YOUTUBE ONLY  
**Deciders**: User / Engineering  
**Context**: Mobiltool Engineering Operating System (Part P2)  

---

## 1. Context & Operational Constraints

Mobiltool requires a secure, legally compliant, and technically truthful media extraction pipeline for public media streams across:
1. **Direct Public HTTP/HTTPS Streams** (APPROVED & IMPLEMENTED).
2. **YouTube** (APPROVED FOR IMPLEMENTATION — NewPipeExtractor pure JVM engine).
3. **Instagram** (NOT APPROVED — EXTRACTION_UNAVAILABLE / ARCHITECTURE UNRESOLVED).
4. **X / Twitter** (NOT APPROVED — EXTRACTION_UNAVAILABLE / ARCHITECTURE UNRESOLVED).

### Operational Invariants & Legal Boundary:
- **GPL Scope**: The user explicitly accepts NewPipeExtractor''s `GPL-3.0-or-later` dependency for the **CURRENT PRIVATE / PERSONAL / NON-DISTRIBUTED** Mobiltool scope.
- **Distribution Condition**: If Mobiltool is ever distributed externally, a comprehensive **LEGAL / LICENSING REVIEW IS REQUIRED** prior to release.
- **Zero DRM / Paywall Bypass**: No decryption of DRM-protected streams (Widevine/FairPlay), private accounts, cookies, or authentication bypass.
- **Fail Closed**: If upstream platform APIs, player tokens, or BotGuard challenges break extraction, the application immediately returns `PLATFORM_EXTRACTION_UNAVAILABLE` with `ErrorCode.EXTRACTION_FAILED`.
- **Sequential Gate**: Parts P3+ and P7+ remain strictly **LOCKED**. P2 does not depend on desktop companion or transcription.

---

## 2. Build Prerequisite & minSdk Compatibility

- **Mobiltool minSdk**: `26`
- **NewPipeExtractor minSdk Requirement**: Requires Java 8+ and NIO APIs. When `minSdk < 33`, Android Gradle Plugin mandates core-library desugaring (`com.android.tools:desugar_jdk_libs_nio`).
- **Implementation**: Enabled `coreLibraryDesugaring` with `desugar_jdk_libs_nio` in `app/build.gradle.kts`.

---

## 3. Platform Architecture Decisions

```text
+-------------------+-----------------------------------------------+-----------------------------------+
| Platform          | Selected Architecture Option                 | Status                            |
+-------------------+-----------------------------------------------+-----------------------------------+
| 1. Direct HTTP    | SafeHttpTransport + RealHttpStreamDownloader  | APPROVED & INTEGRATED             |
| 2. YouTube        | Pure JVM Extractor (NewPipeExtractor adapter) | APPROVED FOR IMPLEMENTATION       |
| 3. Instagram      | Standalone Public Scraper (Unresolved)        | EXTRACTION_UNAVAILABLE            |
| 4. X (Twitter)    | Standalone Public API (Unresolved)            | EXTRACTION_UNAVAILABLE            |
+-------------------+-----------------------------------------------+-----------------------------------+
```

### 1. Direct HTTP Media Streams:
- **Decision**: `APPROVED & INTEGRATED`.
- **Pipeline**: Strict SSRF defense + ValidatedDns + Hop-by-hop redirects + 32KB streaming to `.part` staging + Magic byte container verification + SHA-256 calculation + `StandardCopyOption.ATOMIC_MOVE`.

### 2. YouTube Decision:
- **Status**: `APPROVED FOR IMPLEMENTATION`.
- **Implementation Rules**:
  1. Adapter is isolated behind `MediaExtractor` / `YouTubeExtractor` interface. NewPipe types do NOT leak into UI, ViewModel, or Room database.
  2. NewPipeExtractor is used exclusively for **metadata and stream URL extraction**. All binary video/audio streams are downloaded via Mobiltool''s verified `SafeHttpTransport` + `RealHttpStreamDownloader`.
  3. No cookies, login scraping, or BotGuard/DRM bypass.
  4. Extracted formats map to truthful Mobiltool-owned `MediaFormatOption` without fabricating resolutions, bitrates, or codecs.
  5. Deterministic offline adapter unit tests in standard test suite; live YouTube fixtures run via opt-in `realYouTubeExtractionTest`.
  6. Upstream failures map to `PLATFORM_EXTRACTION_UNAVAILABLE` + `ErrorCode.EXTRACTION_FAILED`.

### 3. Instagram Decision:
- **Status**: `NOT APPROVED — EXTRACTION_UNAVAILABLE / ARCHITECTURE UNRESOLVED`.

### 4. X (Twitter) Decision:
- **Status**: `NOT APPROVED — EXTRACTION_UNAVAILABLE / ARCHITECTURE UNRESOLVED`.

### 5. Desktop Fallback Companion:
- **Status**: `FUTURE OPTIONAL ADAPTER (P7+ LOCKED)`. Zero P2 runtime coupling.
