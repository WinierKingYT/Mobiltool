# ADR 002: P2 Media Downloader — Platform Extraction Architecture

**Date**: 2026-09-03  
**Status**: PROPOSED — AWAITING USER APPROVAL  
**Deciders**: Engineering / User  
**Context**: Mobiltool Engineering Operating System (Part P2)  

---

## 1. Context & Problem Statement

Mobiltool requires a truthful, robust media extraction pipeline for public media streams across:
1. **Direct Public HTTP/HTTPS Streams** (Complete in P2 baseline).
2. **YouTube** (Public videos, Shorts).
3. **Instagram** (Public Reels, Posts).
4. **X / Twitter** (Public Status Media).

### Operational Invariants & Constraints:
- **Zero DRM / Paywall Bypass**: No decryption of DRM-protected streams (Widevine/FairPlay), private accounts, or authentication bypassing.
- **Fail Closed**: If a platform changes its internal API/tokens or blocks client extraction, the application must immediately return `PLATFORM_EXTRACTION_UNAVAILABLE` rather than fabricating formats or silent errors.
- **Local-First / Standalone**: The Android application should operate locally without mandatory external cloud infrastructure where feasible.
- **Binary Footprint & Security**: Must avoid massive APK size inflation (+50MB) and unvetted native binaries.

---

## 2. Evaluation of Architectural Options

```text
+-------------------------------------------------+-------------+----------------+-----------------+---------------+
| Architecture Option                             | APK Size    | Maintenance    | Platform Breadth| Android Safety|
+-------------------------------------------------+-------------+----------------+-----------------+---------------+
| Option A: Embedded Python yt-dlp (Chaquopy)     | +35-50 MB   | High (PyBridge)| High (YouTube+) | Medium/Heavy  |
| Option B: Pure Kotlin / Java Scrapers (NewPipe) | +2-4 MB     | Medium         | High (YouTube)  | High / Clean  |
| Option C: Workstation Companion Handoff         | 0 MB (App)  | Low on phone   | Full (Desktop)  | High (Remote) |
| Option D: Modular Hybrid (NewPipe + Desktop)    | +3 MB       | Balanced       | Full            | High / Clean  |
+-------------------------------------------------+-------------+----------------+-----------------+---------------+
```

### Option A: Embedded Python `yt-dlp` via Chaquopy / Python-for-Android
- **Mechanism**: Embeds a full CPython runtime inside the Android APK and invokes `yt-dlp` scripts over JNI.
- **Pros**: Access to `yt-dlp` extractor ecosystem and frequent upstream patches.
- **Cons**: Severe APK bloat (+35MB to +50MB), high memory footprint during extraction, slow process initialization, complex JNI lifecycle management on modern Android (API 34+ 16KB page size restrictions).

### Option B: Pure Kotlin / JVM Extraction Engine (e.g. NewPipeExtractor)
- **Mechanism**: Uses pure JVM Kotlin/Java stream parsers directly integrated into the `media-extractor-api` module.
- **Pros**: Extremely lightweight (+2MB to +4MB), fast synchronous/coroutine parsing, zero native C-library dependencies, full Android compatibility.
- **Cons**: Non-YouTube platforms (Instagram, X) require separate maintenance as web layouts evolve.

### Option C: Desktop Workstation Companion Handoff (Remote Bridge)
- **Mechanism**: When connected to the user''s desktop (Part P7/P9/P10), delegates platform URL extraction to the desktop daemon running native `yt-dlp`.
- **Pros**: Zero maintenance overhead on mobile, always up-to-date desktop binaries, no battery drain on phone.
- **Cons**: Only functions when paired with an active desktop session; unavailable standalone.

### Option D: Modular Engine — Standalone Pure JVM Parser + Optional Workstation Handoff (RECOMMENDED)
- **Mechanism**:
  1. **Primary On-Device**: Pure Kotlin/JVM extractor (utilizing `NewPipeExtractor` for YouTube and lightweight public REST scrapers for Instagram/X).
  2. **Secondary/Fallback**: When paired with a Desktop Workstation (P7+), complex or throttling platform streams can optionally be dispatched to the desktop companion.
  3. **Strict Fail-Closed Gate**: If local parsing fails due to upstream platform token updates (e.g. YouTube PO Token / BotGuard), cleanly return `PLATFORM_EXTRACTION_UNAVAILABLE` with an actionable diagnostic.

---

## 3. Proposed Decision

**Recommend Option D (Modular Pure JVM Extraction Engine)**:
1. Integrate pure JVM extraction for public YouTube streams into `media-extractor-api`.
2. Implement bounded public JSON/OEmbed probes for Instagram and X without cookie scraping.
3. Retain the strict `SafeHttpTransport` pipeline for all underlying media binary stream downloads.
4. Keep the platform extractors gated until explicit user review and ADR approval.

---

## 4. Status

**STATUS**: `PROPOSED — AWAITING USER APPROVAL`  
*No platform extractor code (YouTube/Instagram/X) will be implemented until this ADR is approved by the user.*
