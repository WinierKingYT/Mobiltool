# 17 — AI Engineering Guardrails

## 1. Role

The AI is an implementer under contract.

Before each coding task:

```text
ACTIVE MILESTONE:
TASK:
FILES EXPECTED TO CHANGE:
PLATFORM/SOURCE AFFECTED:
NEW PERMISSIONS:
NEW DEPENDENCIES:
NETWORK BEHAVIOR CHANGE:
STORAGE BEHAVIOR CHANGE:
ADR REQUIRED:
```

---

# 2. Downloader prohibitions

AI MUST NOT:

- implement DRM circumvention;
- scrape private social app storage;
- steal cookies;
- intercept credentials;
- add hidden WebView login harvesting;
- build CAPTCHA bypass;
- build proxy rotation to evade platform controls;
- use browser fingerprint spoofing without explicit ADR;
- download entire profiles/channels by default;
- hide source-platform failures behind generic success;
- present a probe as a completed download.

---

# 3. Call prohibitions

AI MUST NOT:

- use Accessibility for remote call audio;
- force speakerphone;
- label MIC ambient capture as call audio;
- lower targetSdk to bypass platform restrictions;
- add root tooling to consumer build.

---

# 4. Media truth rules

A download is READY only after:
- file exists;
- media validation passes;
- source/item association is correct;
- final hash recorded.

A transcript is READY only after:
- source asset still exists;
- segments committed;
- model/version recorded.

---

# 5. Dependency boundary

AI may not add:
- yt-dlp wrapper;
- embedded Python;
- FFmpeg;
- STT runtime/model;
- new database;
- crypto library

without accepted ADR.

---

# 6. No raw tool syntax leakage

Forbidden outside adapter modules:

```text
raw yt-dlp arguments
raw ffmpeg commands
vendor private paths
```

Domain uses typed requests.

---

# 7. No future-scope implementation

If current milestone is M2 downloader:
- do not add playlist UI;
- do not add AI summarization;
- do not add TikTok-specific adapter;
- do not add login/cookie import;
- do not add cloud sync.

---

# 8. Security

AI MUST NOT:
- log tokens/signed media URLs;
- store secret keys in source;
- request MANAGE_EXTERNAL_STORAGE for convenience;
- download arbitrary executable code and run it;
- disable certificate validation;
- accept all TLS certificates.

---

# 9. Testing requirement

Changes require relevant tests:
- extractor normalization -> fixture tests;
- format mapping -> unit tests;
- download finalizer -> crash/failure tests;
- DB -> migration tests;
- transcript -> timestamp tests;
- URL handling -> malicious URL cases.

---

# 10. Blocker over workaround

When a source breaks, correct behavior may be:

```text
SITE_CHANGED
```

not:
- arbitrary scraping;
- security downgrade;
- feature misrepresentation.

The AI is expected to stop early when constraints make the requested implementation invalid.


# 11. Remote Dev prohibitions

AI MUST NOT:

- create a generic unauthenticated HTTP shell endpoint;
- bind Bridge publicly for convenience;
- implement desktop UI scraping for coding tools;
- copy tool API keys to Android without approved design;
- read browser cookies or credential stores;
- expose filesystem outside registered roots;
- add force-push/hard-reset/clean actions in MVP;
- treat agent text as a trusted approval request;
- claim ChatGPT consumer-session integration via OpenAI API;
- add a cloud relay before LAN security gates pass.

# 12. Remote adapter rule

Use official supported surfaces first:

```text
OpenCode -> official server/OpenAPI
Claude Code -> supported CLI/SDK structured modes
Antigravity -> official SDK / official Remote Control boundary
Codex/OpenAI -> official SDK/API
```

If official programmatic control is unavailable, report unsupported instead of scraping UI.


# 13. Power guardrails

AI MUST NOT:
- add a permanent foreground service for convenience;
- poll call state continuously;
- hold microphone/audio capture while no call exists;
- hold wake lock without bounded timeout;
- run transcription and heavy transcode concurrently by default;
- keep Remote Desktop decoder running while UI is backgrounded;
- keep PC screen capture/encoder active when no session exists;
- bypass Battery Saver/background restrictions by asking user to disable them as first solution.

# 14. Remote Desktop guardrails

AI MUST NOT:
- implement unauthenticated screen streaming;
- expose Bridge directly to public Internet;
- create a keylogger/history of remote key events;
- bypass Windows UAC secure desktop;
- run Bridge as admin merely for convenience;
- add hidden screen recording;
- use arbitrary command execution as mouse/app-launch implementation;
- accept stale/replayed input events;
- continue pointer/keyboard control when the video session is not authenticated;
- use high-resolution software encoding by default when hardware path is available.

# 15. Remote Desktop implementation priority

Remote Desktop code is forbidden before active milestone M9 unless the current task is a bounded feasibility spike explicitly approved by ADR/user.

# 16. Visual design guardrails

AI MUST follow `38_RETRO_INDUSTRIAL_DESIGN_SYSTEM.md`.

AI MUST NOT:
- ship default Material 3 colors/shapes;
- enable system dynamic colors by default;
- introduce large rounded/pill SaaS UI;
- introduce blue/purple/neon visual identity;
- add glassmorphism or heavy blur;
- use copper as the dominant surface;
- add light theme without separate approval;
- hardcode repeated feature colors instead of semantic design tokens;
- sacrifice accessibility/touch targets for visual density.
