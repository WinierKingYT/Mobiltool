# 01 — Scope, Boundaries and Invariants

## 1. Hard invariants

### INV-001 — No DRM circumvention

The downloader must stop when content is DRM-protected or when accessing it requires defeating a technical protection measure.

No DRM key extraction.
No Widevine bypass.
No manifest/key interception intended to defeat protection.

Violation: STOP-SHIP.

### INV-002 — No private-content bypass

Do not use:
- stolen cookies;
- private app storage;
- browser database scraping;
- session/token extraction;
- hidden WebView login interception.

If authorized authenticated support is added later, it requires a dedicated ADR and explicit user consent flow.

### INV-003 — No CAPTCHA/anti-bot bypass system

If a platform blocks the request:
- show a stable error;
- allow engine update;
- use only an approved official/authenticated path when available.

Do not build CAPTCHA farms, stealth browsers, fingerprint spoofing, or proxy rotation into the product.

### INV-004 — No Accessibility for remote call audio

Same restriction as call-only plan.

### INV-005 — No microphone fallback pretending to be a call recording

Ambient microphone capture can never be mislabeled as verified bidirectional call capture.

### INV-006 — No automatic mega-download

A pasted link may not silently start:
- playlists;
- channels;
- profile archives;
- all-post downloads.

MVP downloads one user-selected media item/post at a time.

### INV-007 — Preserve canonical source

Do not delete original downloaded source after:
- format conversion;
- audio extraction;
- transcription.

Deletion occurs only by retention/user policy.

### INV-008 — Network belongs only to network-required features

Call recordings and local transcription do not gain network dependency because downloader feature exists.

### INV-009 — No hidden upload

Downloader traffic goes to the content source/approved extractor paths.
Local media is not uploaded to our server because there is no server in MVP.

### INV-010 — No invented metadata

If uploader/date/title cannot be extracted, store unknown.
Do not infer fake source metadata.

### INV-011 — Stable item identity

Never identify an archive item only by filename/title.

Use UUID + source identifiers/hashes.

### INV-012 — Extractor is replaceable

UI/domain code must never construct raw yt-dlp CLI arguments.

Only the extractor adapter knows yt-dlp syntax.

### INV-013 — Postprocessor is replaceable

UI/domain code must not know FFmpeg command strings.

### INV-014 — Feature isolation

`feature-calls` cannot use `feature-download` internals.
`feature-download` cannot use call database internals.

Shared behavior moves to core interfaces only when two real features need it.

### INV-015 — No speculative platform

Do not add TikTok/Facebook/Reddit/etc. adapters before a milestone explicitly includes them.

Generic extractor support may work incidentally but is not a certified platform.

---

# 2. Explicitly supported product-domain expansion

Future features may include:

- more media source adapters;
- local-file import;
- batch queue;
- playlists;
- subtitles;
- chapter metadata;
- thumbnail archive;
- source-page text snapshot where lawful;
- transcript search;
- subtitle export;
- media conversion;
- audio normalization.

Future features do NOT automatically include unrelated personal productivity features.

---

# 3. Permission budget

Expected possible permissions:

- INTERNET
- POST_NOTIFICATIONS
- foreground service types required by current Android rules
- default dialer role / phone permissions when call feature uses them
- contacts only if call display names are enabled
- user-selected document/tree permissions via SAF where required

Not allowed without ADR:

- AccessibilityService
- MANAGE_EXTERNAL_STORAGE
- overlay permission
- VPN
- device admin
- SMS
- location
- camera
- broad package visibility
- install packages

Prefer Storage Access Framework / MediaStore over all-files access.

---

# 4. Dependency rules

Every production dependency needs:

- reason;
- license review;
- current maintenance review;
- binary-size impact;
- native ABI impact;
- security surface;
- update strategy.

Particularly strict for:
- yt-dlp wrapper;
- embedded Python;
- FFmpeg build;
- transcription model/runtime.

---

# 5. AI stop conditions

Stop and write BLOCKER when:

- target URL is DRM-protected;
- only solution requires scraping private account/session data;
- platform behavior changed and current extractor no longer works;
- extractor requires a browser-fingerprint bypass not approved by ADR;
- call capture becomes one-sided/silent;
- a new high-risk permission is required;
- download file cannot be verified;
- format merger would destroy the source;
- license compatibility is unresolved;
- app-store distribution terms conflict with implementation.

BLOCKER format:

```text
BLOCKER
Feature:
Source/platform:
Observed behavior:
Evidence:
Safe attempts:
Why current contract prevents workaround:
Available compliant options:
Recommended decision:
```


# 6. Remote Dev invariants

### INV-RD-001 — No unrestricted shell in initial release

The mobile protocol has no generic `exec(command: string)` capability in initial release.

Every action is a typed operation.

### INV-RD-002 — Registered-project sandbox

Remote project actions are allowed only inside explicitly registered project roots.

Path traversal outside the root is rejected.

### INV-RD-003 — Bridge owns credentials

API keys, CLI auth tokens and desktop tool credentials stay on the desktop.
The Android app must not copy Claude/OpenAI/Google/OpenCode credentials unless an explicit future architecture requires it.

### INV-RD-004 — Destructive actions require explicit approval

Examples:
- discard changes;
- reset;
- checkout with overwrite;
- delete files;
- commit/push;
- tool approval that grants write/shell access.

Read-only inspection does not imply write permission.

### INV-RD-005 — No desktop-app UI scraping

Do not automate Claude/Antigravity/ChatGPT/OpenCode by:
- screen scraping;
- mouse automation;
- Accessibility on desktop/mobile;
- DOM injection into authenticated consumer apps.

Prefer official SDK/API/CLI/remote-control surfaces.

### INV-RD-006 — Phone loss must be revocable

Every paired phone has a device identity that can be revoked from the desktop without having the phone.

### INV-RD-007 — No source-code relay storage

Any future relay transports ciphertext only.
It does not persist source code, prompts, terminal output or secrets as application data.

### INV-RD-008 — Project cache is non-canonical

Mobile project snapshots are disposable.
Desktop repository state is authoritative.

### INV-RD-009 — Adapter capability truth

An adapter advertises only operations verified against its installed version.

### INV-RD-010 — ChatGPT consumer history is unsupported unless official

Do not implement browser-cookie/session scraping to expose existing ChatGPT chats.



# 7. V4 Power and Remote Desktop invariants

### INV-PWR-001 — No always-hot call recorder

The app may be logically ready for calls all day, but no active audio capture loop may run while there is no call.

### INV-PWR-002 — No permanent polling

No 1-second/5-second repeated polling for:
- call state;
- Desktop Bridge status;
- Git;
- downloads;
- remote desktop availability.

Use events, connectivity callbacks, push/stream only while relevant, or coarse bounded refresh.

### INV-PWR-003 — No permanent wake lock

Wake locks are scoped to the smallest critical operation and always timeout/release.

### INV-PWR-004 — Heavy jobs are serialized by default

Default:
- 1 local transcription;
- 1 heavy media conversion;
- remote desktop hardware decode can coexist only after device benchmark;
- no transcription while remote desktop is active unless user explicitly overrides and device budget allows.

### INV-PWR-005 — Thermal protection

On severe/critical thermal status:
- pause new transcription;
- reduce/stop postprocessing;
- remote desktop reduces FPS/resolution/bitrate;
- call recording reliability is prioritized over optional heavy work.

### INV-PWR-006 — Battery-low protection

Optional work does not begin when battery is low unless user explicitly starts/overrides it.

Call finalization remains reliability-critical.

### INV-RD-011 — Remote desktop only for paired trusted machines

No public host discovery/browser.

### INV-RD-012 — Desktop screen capture is session-scoped

Bridge does not continuously capture desktop while no authenticated remote desktop session exists.

### INV-RD-013 — Input is session-scoped

Mouse/keyboard injection is accepted only:
- from a paired device;
- during an active authenticated session;
- with replay protection;
- when control permission is enabled.

### INV-RD-014 — Secure Desktop is not bypassed

UAC secure desktop, Windows sign-in desktop and protected system surfaces are not bypassed by tricks or privilege escalation.

### INV-RD-015 — No default remote recording

Remote screen/audio stream is not persisted unless a separately approved recording feature exists.

### INV-RD-016 — Hardware codecs preferred

Remote desktop must prefer platform hardware encoder/decoder when available.
CPU software encoding at high resolution/FPS is fallback, not default.

### INV-RD-017 — Adaptive quality

Stream quality adapts to:
- device thermals;
- Android battery state;
- network bandwidth/latency;
- display size;
- user selected quality.

### INV-RD-018 — App background policy

When Android Remote Desktop UI is no longer visible:
- video stream pauses quickly by default;
- input channel is disabled;
- session disconnects after bounded grace period unless user explicitly chose background continuation.

