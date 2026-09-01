# P2 — Media / Video Downloader

## Goal
Real progressive HTTP stream downloading with platform extraction and SSRF defense.

## Execution Packages
1. Platform URL validation (`UrlClassifier`) + SSRF protection.
2. Real HTTP HEAD probing (`HttpMediaProber`).
3. Progressive chunked streaming (`RealHttpStreamDownloader` with 32KB buffer + `.part` staging).
4. Media post-processing & file validation (`MediaFileValidator` + SHA-256).

## Exit Gate
```text
[x] Real HTTP range & progressive streaming
[x] SSRF private IP blocking active
[x] Atomic .part rename on success
[x] File validator enforces >4KB threshold
[x] Multi-platform URL classification tested
```
