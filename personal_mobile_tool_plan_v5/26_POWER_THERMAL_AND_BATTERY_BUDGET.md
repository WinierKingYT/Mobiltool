# 26 — Power, Thermal and Battery Budget

## 1. Purpose

This application contains features that can individually be expensive:
- call capture;
- media download;
- FFmpeg/postprocessing;
- local STT;
- remote desktop video decode;
- persistent networking.

Without explicit budgets, combining them can destroy battery life.

Battery efficiency is therefore an architectural requirement.

---

# 2. Power classes

## Class 0 — Idle
Target:
- no active CPU loop;
- no active audio capture;
- no video codec;
- no persistent remote socket by default;
- no wake lock.

Features:
- call capability waiting on system events;
- archive idle.

## Class 1 — Light
Examples:
- browsing local library;
- reading Git/project cache;
- metadata probe.

## Class 2 — Network
Examples:
- download;
- remote-dev event stream.

## Class 3 — Compute
Examples:
- transcription;
- audio/video conversion.

## Class 4 — Real-time
Examples:
- Remote Desktop;
- active call recording.

Only one optional Class 3/4 workload should normally dominate at once.

---

# 3. Priority order under pressure

```text
1. Active call recording/finalization
2. Data integrity / encryption finalization
3. User-visible Remote Desktop interaction
4. User-visible download
5. Transcription
6. Media conversion
7. Cleanup/indexing
```

Lower priority work yields first.

---

# 4. Battery policy

Recommended settings:

```text
Heavy processing
[ Balanced ]
[ Only when charging ]
[ Always ]

Downloads
[ Any network ]
[ Wi-Fi preferred ]
[ Wi-Fi only ]

Remote Desktop quality
[ Auto ]
[ Data Saver ]
[ Balanced ]
[ Quality ]
```

Default should be Balanced/Auto.

---

# 5. Thermal policy

Use Android thermal status APIs where appropriate.

Behavior:
- NONE/LIGHT -> normal;
- MODERATE -> reduce background heavy concurrency;
- SEVERE -> do not begin transcription; lower Remote Desktop stream;
- CRITICAL/EMERGENCY -> stop optional compute; preserve active call recording if technically safe.

Do not allow thermal throttling to corrupt source media.

---

# 6. Remote Desktop power adaptation

Auto controller may adjust:

```text
resolution
FPS
bitrate
decoder low-latency mode
frame request cadence
```

Example targets:

### Good LAN + cool device
- 1080p
- 30 FPS
- adaptive bitrate

### Warm / battery low
- 720p
- 20 FPS

### Static desktop
- encode/send only changed frames where transport permits;
- effective frame rate falls naturally.

No need to send 60 identical frames per second.

---

# 7. Call recorder idle target

Between calls:
- capture engine fully inactive;
- call-related continuous CPU usage effectively zero;
- no file writer open;
- no encoder;
- no active microphone.

Call readiness is implemented via platform lifecycle integration.

---

# 8. Downloads

Energy rules:
- connection reuse;
- chunk size bounded;
- no busy wait;
- pause/defer optional work on low battery if user did not actively request it;
- large background job can honor charging/Wi-Fi constraints.

---

# 9. Transcription

Rules:
- one model loaded only while needed where practical;
- release model/memory after idle timeout;
- chunk long media;
- no entire long video decoded into RAM;
- charging-only option;
- avoid simultaneous FFmpeg transcode + STT unless benchmark proves safe.

---

# 10. Metrics

Debug/performance builds record:
- battery delta;
- CPU time;
- process memory;
- network bytes;
- average device temperature/thermal status;
- wake-lock duration;
- codec hardware/software path;
- time spent per workload class.

No sensitive media content in metrics.

---

# 11. Acceptance target philosophy

Do not use one fake universal number.

Measure:
- idle 8h;
- one 30-min call;
- one 1GB download;
- 30-min transcription;
- 30-min Remote Desktop.

Compare against baseline device idle/use and track regressions by release.
