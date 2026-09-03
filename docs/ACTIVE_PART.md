# Active Part

```text
ACTIVE_PART = P2
STATUS = IN_PROGRESS
```

## Part Sequencing & Lifecycle State:
- **P0: Foundation & Invariant Truth-Pass** — LOCKED / PASS
- **P1: Call Recording** — LOCKED / TRUTH-LOCKED
  - Software Architecture: PASS / COMPLETE
  - Target Device Physical Preflight: UNSUPPORTED (No native call recorder exposed on tested SM-S901E/DS One UI 8.0)
  - Physical Qualification: NOT RUN (No legitimate capture source available)
  - Runtime Verdict: Metadata-only fail-closed mode (`RecordingQuality.UNSUPPORTED`)
- **P2: Media / Video Downloader** — ACTIVE / IN_PROGRESS
  - Target: Direct public HTTP/HTTPS media downloading + Platform Extraction Architecture Decision (ADR_002)
  - Pipeline Invariants:
    - `RECOGNIZED URL != SUPPORTED EXTRACTION`
    - `DOWNLOAD STARTED != DOWNLOAD SUCCEEDED`
    - `HTTP 200 != VALID MEDIA`
    - `FILE EXISTS != VALID MEDIA`
    - `PLATFORM URL RECOGNIZED != PLATFORM MEDIA EXTRACTABLE`
- **P3: Library & Playback** — LOCKED
- **P4: Local Transcription** — LOCKED
- **P5+: Core Foundation, Dev Bridge, Remote Desktop** — LOCKED

## P2 Component Baseline Mapping:
- `UrlClassifier.kt`: HARDEN (Comprehensive RFC 1918 / IPv4 / IPv6 SSRF defense, strict host boundaries, userinfo rejection)
- `HttpMediaProber.kt`: HARDEN (Manual redirect re-validation, bounded byte-range fallback probe, safe filename derivation)
- `RealHttpStreamDownloader.kt`: HARDEN (Redirect-safe transport, bounded memory streaming, `.part` staging, crash-safe commit)
- `MediaFileValidator.kt`: HARDEN (Container magic bytes check, HTML/JSON error payload rejection, SHA-256 integrity)
- `DefaultMediaExtractor.kt`: HARDEN (Integrated validation -> hash -> commit pipeline, fail closed on unlinked platforms)
- `Platform Extractors (YouTube, Instagram, X)`: UNLINKED / PENDING ADR_002 APPROVAL

Forbidden in P2:
- Advancing to P3 (Library & Playback) or P4 (Transcription)
- DRM bypass, paywall bypass, private account / cookie scraping, CAPTCHA bypass
- Fabricating platform formats or claiming YouTube/Instagram/X are downloadable before ADR approval & real implementation
