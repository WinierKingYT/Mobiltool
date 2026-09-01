# 29 — Power / Performance Benchmark Plan

## 1. Reference devices

At minimum:
- one modern flagship Android;
- one mid-range Android;
- the user's primary device class when available.

Record:
- model;
- Android build;
- battery health;
- chipset;
- RAM;
- hardware codecs.

---

# 2. Scenario A — Idle

Duration: 8 hours.

State:
- app installed;
- call capability ready;
- no calls;
- no remote session;
- no downloads.

Measure:
- battery delta;
- CPU time;
- wakeups;
- network bytes;
- foreground-service duration.

Pass principle:
app should behave close to idle application baseline.

---

# 3. Scenario B — Call

30-minute controlled call.

Measure:
- capture CPU;
- battery;
- thermal;
- dropped audio;
- finalization time.

---

# 4. Scenario C — Download

1GB media file over Wi-Fi.

Measure:
- throughput;
- battery;
- CPU;
- postprocess cost;
- temp storage.

---

# 5. Scenario D — Transcription

30 minutes of Turkish media.

Measure:
- realtime factor;
- peak RAM;
- battery;
- thermal states;
- model load/unload cost.

---

# 6. Scenario E — Remote Desktop

30 minutes:
- static coding desktop;
- browsing;
- video/motion stress.

Profiles:
- 720p20;
- 1080p30;
- Auto.

Measure:
- Android decoder hardware path;
- battery;
- bandwidth;
- latency;
- dropped frames;
- PC encoder load.

---

# 7. Combined stress

Test:
- active remote desktop;
- incoming call;
- download running.

Expected:
- call capture remains reliable;
- remote desktop may degrade quality;
- optional heavy local jobs defer.

---

# 8. Regression gates

A release cannot introduce an unexplained material battery regression.

Store benchmark JSON artifacts per release, not media content.
