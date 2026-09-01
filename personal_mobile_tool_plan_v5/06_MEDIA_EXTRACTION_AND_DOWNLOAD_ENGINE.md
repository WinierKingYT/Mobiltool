# 06 — Media Extraction and Download Engine

## 1. Goal

Provide a replaceable media extraction layer capable of resolving public/authorized web-media URLs into formats and downloads.

---

# 2. Candidate implementation

First candidate:
- yt-dlp-based engine embedded/wrapped for Android.

Possible Android integration approaches:

1. maintained Android wrapper bundling Python/yt-dlp;
2. Chaquopy + controlled yt-dlp embedding;
3. purpose-built native/official API adapters for selected platforms.

Decision requires ADR after:
- size;
- license;
- reliability;
- update mechanism;
- ABI;
- Android 16KB page compatibility;
- security review.

The domain API must remain independent.

---

# 3. Why an extractor engine is necessary

A social-media URL often does not point directly to an MP4.

Typical:

```text
page URL
 -> metadata/API/page extraction
 -> adaptive streams
 -> video-only stream
 -> audio-only stream
 -> signed/temporary URLs
 -> download
 -> merge
```

The extractor handles source-specific discovery.
The download/postprocess layers handle normalized work.

---

# 4. Engine operations

```text
probe(url)
download(format selection)
cancel(job)
diagnostics(job)
```

Preferred internal design separates `probe` and `download`.

---

# 5. Download states

```text
CREATED
PROBING
READY_FOR_SELECTION
QUEUED
DOWNLOADING
MERGING
VALIDATING
COMMITTING
READY
FAILED
CANCELLED
```

---

# 6. Network behavior

Requirements:

- HTTPS where source supports it;
- bounded connect/read timeouts;
- resumable partial download where engine/source supports it;
- no unbounded retry;
- network type visible to scheduler;
- optionally Wi-Fi-only setting;
- respect metered-network user setting;
- pause/cancel semantics explicit.

---

# 7. Temporary files

Use app-private job staging:

```text
staging/<job-id>/
```

Possible:
- video.part
- audio.part
- merged.tmp
- metadata.json (sanitized)

On successful commit:
- canonical asset moves atomically to archive store;
- staging cleaned.

On crash:
- recovery examines journal;
- resume or delete according to job state.

---

# 8. Engine update strategy

Critical because sites change frequently.

MVP safe strategies:

### Strategy A — app release updates engine
Simplest trust model.

### Strategy B — separately signed extractor bundle
Future, requires ADR/signature verification.

No arbitrary remote Python/code download and execution.

Do not add self-updating executable code without a security ADR.

---

# 9. Diagnostics

Store safe diagnostic fields:

- extractor version;
- extractor/site adapter;
- source host;
- error code;
- HTTP class/status where safe;
- format id;
- engine stderr redacted.

Never log:
- cookies;
- authorization headers;
- full signed media URLs;
- tokens.

---

# 10. Rate/concurrency limits

MVP default:
- max 2 active downloads;
- max 1 postprocess-heavy merge at a time;
- max 1 transcription-heavy job at a time by default.

Make configurable only after performance testing.

---

# 11. Download verification

After download:
- file exists;
- size > sane minimum;
- media parser can open;
- duration roughly matches probe when known;
- expected track type exists;
- hash computed;
- no temp extension.

Only then status = READY.
