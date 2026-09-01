# 15 — Testing, Reliability and Device Matrix

## 1. Test categories

- unit;
- database migration;
- filesystem;
- network;
- extractor fixtures;
- download integration;
- postprocess;
- transcription;
- Android lifecycle;
- physical-device call tests.

---

# 2. Download fixture matrix

Per platform:

### Source type
- normal video;
- short-form video;
- high-resolution;
- audio available;
- unavailable/deleted;
- unsupported;
- auth-required;
- DRM when safely identifiable (must fail closed).

### Network
- Wi-Fi;
- mobile data;
- interruption;
- airplane transition;
- DNS/network failure;
- slow connection;
- cancellation.

### Storage
- enough storage;
- low storage;
- file system failure;
- app restart.

---

# 3. Format validation

For every output:
- media parser opens;
- expected audio/video track exists;
- duration sensible;
- selected max resolution respected;
- size/hash recorded;
- no `.part` left as canonical.

---

# 4. Postprocess tests

- DASH merge;
- HLS where supported by engine;
- remux;
- audio extract;
- MP3 conversion;
- cancel during merge;
- process death;
- corrupt input.

---

# 5. Transcription tests

Corpora:
- phone-call quality Turkish;
- clear YouTube-style speech;
- noisy social video;
- music-heavy video;
- two-hour long file;
- overlapping speakers.

Measure:
- WER/CER on controlled corpus;
- RAM;
- time/audio minute;
- thermal;
- battery.

---

# 6. Call device matrix

Carry forward:
- device model/build;
- call transport;
- route;
- OEM phone version;
- capture engine.

No brand-wide support claim.

---

# 7. Regression suite for extractor updates

Every extractor/library update must run:
- YouTube fixture suite;
- Instagram fixture suite;
- X fixture suite;
- generic unsupported/error suite;
- format normalization tests.

Do not update extraction engine in release without fixtures passing.

---

# 8. Security tests

- no unexpected exported component;
- no Accessibility service;
- no MANAGE_EXTERNAL_STORAGE;
- logs contain no tokens/cookies;
- URL scheme validation;
- no arbitrary local-file access through URL extractor;
- no self-update arbitrary executable code;
- backup exclusion;
- delete cascade.

---

# 9. Reliability targets

Downloader:
- no wrong file attached to source;
- no canonical corrupted file marked READY;
- cancellation deterministic;
- crash recovery deterministic.

Call:
- same V1 qualification targets.

Transcript:
- source never lost;
- timestamps within acceptable drift;
- cancellation safe.
