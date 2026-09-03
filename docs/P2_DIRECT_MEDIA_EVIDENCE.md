# P2 Direct Media Integration Evidence

**Verification Date**: 2026-09-03  
**Module**: `media-extractor-api`  
**Execution Environment**: Local Android / JVM Sandbox with Real Network Connectivity  
**Execution Task**: `.\gradlew.bat :media-extractor-api:realDirectMediaTest` (Opt-in live network test)  
**Pipeline**: `SafeHttpTransport` (OkHttp + ValidatedDns + Hop-by-Hop SSRF Inspection) -> `RealHttpStreamDownloader` -> `MediaFileValidator` -> StandardAtomicFileCommitter (`StandardCopyOption.ATOMIC_MOVE`)  

---

## 1. Real Public Direct Media Test Observed Execution Record

```text
DATE: 2026-09-03
SOURCE URL: https://raw.githubusercontent.com/mdn/learning-area/master/html/multimedia-and-embedding/video-and-audio-content/rabbit320.mp4
FINAL SAFE URL: https://raw.githubusercontent.com/mdn/learning-area/master/html/multimedia-and-embedding/video-and-audio-content/rabbit320.mp4
HTTP STATUS: 200
CONTENT LENGTH OBSERVED: 834563
BYTES DOWNLOADED: 834563
CONTAINER: MP4_ISO_BMFF
MEDIA KIND: UNKNOWN
DETECTED MIME: video/mp4
FINAL FILE SIZE: 834563
SHA-256: d0502ba7824940e90424847cd6094c858bab778703e382a0fbb71db533e4ad30
VALIDATION RESULT: VALID (ISO-BMFF header verified, no HTML, no JSON errors, size >= 1024 bytes)
COMMIT RESULT: StandardCopyOption.ATOMIC_MOVE
```

---

## 2. Invariant & Verification Matrix

| Invariant / Check | Expected Production Behavior | Observed Verification Result | Status |
|---|---|---|---|
| **P2-DIRECT-FINAL-01** | `ATOMIC_MOVE` only. No non-atomic fallback. Fail closed, preserve staging. | `StandardCopyOption.ATOMIC_MOVE` succeeded; failure preserves `.part` staging | **PASS** |
| **P2-DIRECT-FINAL-02** | Container type decoupled from track type. `DetectedMediaKind` UNKNOWN for generic containers without track proof. | `MP4_ISO_BMFF` mapped to `DetectedMediaKind.UNKNOWN` / `video/mp4` | **PASS** |
| **P2-DIRECT-FINAL-03** | Pure `ValidatedDns` component bound to approved IPs. Immune to rebinding. | `ValidatedDns` unit tested and bound into OkHttp | **PASS** |
| **P2-DIRECT-FINAL-04** | Standard test task `test` is 100% deterministic and offline. Live network test is separate opt-in task `realDirectMediaTest`. | `test` excludes live tests; `realDirectMediaTest` executes opt-in live stream | **PASS** |
| **P2-DIRECT-FINAL-05** | Evidence logging uses only observed runtime fields from `DownloadedFileInfo`. | All fields (URL, HTTP code, bytes, hash, commit method) derived from transaction | **PASS** |
