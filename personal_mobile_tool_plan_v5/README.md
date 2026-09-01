# Personal Mobile Tool — V4 Complete Planning Pack

**Status:** Implementation planning baseline  
**Primary platform:** Android  
**Desktop reference platform:** Windows 10/11  
**Topology:** Android app + optional Desktop Bridge  
**Product:** Personal mobile toolbox / control center

This V4 pack supersedes all earlier V1/V2/V3 planning packs.

---

# 1. Product in one sentence

> A battery-conscious Android personal tool that records supported calls, downloads and archives authorized media, creates local transcripts, and later can securely monitor/control the user's own development computer—including full remote-desktop interaction.

---

# 2. Approved capability families

## Local mobile tools — build first

### A. Call Archive
- supported call recording;
- call archive;
- playback;
- on-demand transcription.

### B. Media Capture
- paste/share URL;
- inspect media;
- YouTube / Instagram / X adapters;
- choose video/audio format;
- download;
- archive;
- on-demand transcription.

### C. Unified Local Library
- calls;
- videos;
- audio;
- transcripts;
- local search.

### D. Power / Thermal Management
- event-driven call activation;
- no permanent hot loops;
- bounded background jobs;
- battery/network/thermal-aware scheduling;
- hardware codec use;
- explicit performance budgets.

---

## Remote desktop/dev tools — build last

### E. Remote Dev Control
- paired computers;
- projects;
- Git;
- coding-agent sessions;
- prompts;
- approvals.

### F. Remote Desktop
- view Windows desktop;
- multi-monitor selection;
- touch-to-mouse;
- keyboard input;
- click/drag/scroll;
- launch/close approved applications;
- clipboard/text transfer only if later approved;
- LAN first;
- remote Internet connection only after security gate.

---

# 3. Development priority

```text
M0  Core feasibility + power baseline
M1  Android foundation
M2  Media downloader
M3  Call archive
M4  Unified transcription
M5  Local product UX + security + power hardening
M6  Local-tool release qualification

---------------- REMOTE WORK STARTS HERE ----------------

M7  Desktop Bridge + Remote Dev read-only
M8  Remote Dev control
M9  Remote Desktop LAN
M10 Remote Internet control
```

Remote computer control is deliberately last.

---

# 4. Main architecture

```text
                         ANDROID APP
                              |
            +-----------------+------------------+
            |                                    |
       LOCAL TOOLING                        REMOTE TOOLING
            |                                    |
   +--------+--------+                    +------+------+
   |        |        |                    |             |
 Calls    Media    Library             Dev Control  Remote Desktop
   |        |        |                    |             |
   +--------+--------+                    +------+------+
            |                                    |
       Local Core                           Secure Client
            |                                    |
   +--------+---------+                          |
   |        |         |                          |
Storage  Security  Power                 DESKTOP BRIDGE
                                             |
                            +----------------+----------------+
                            |                |                |
                         Projects         Agents       Remote Desktop
                                                            |
                                             Screen Capture + Input
```

---

# 5. The battery rule

The app must not stay “awake” simply because it offers many tools.

Core principle:

```text
IDLE FEATURE
    =
NO CPU LOOP
NO ACTIVE MICROPHONE
NO SCREEN DECODER
NO CONSTANT NETWORK POLLING
NO WAKELOCK
```

Call recording is event-driven:

```text
phone idle
   -> recorder inactive

call begins
   -> capture subsystem activated

call ends
   -> finalize
   -> capture subsystem stops
```

Media/transcription work runs only when requested or safely queued.

Remote Desktop consumes significant power only while the user is actively viewing/controling the PC.

---

# 6. Read order

1. `00_MASTER_PRODUCT_CONTRACT.md`
2. `01_SCOPE_BOUNDARIES_AND_INVARIANTS.md`
3. `02_SYSTEM_ARCHITECTURE.md`
4. `03_SHARED_DOMAIN_MODEL.md`

### Calls / media
5. `04_CALL_RECORDING_SYSTEM.md`
6. `05_URL_INTAKE_AND_MEDIA_PROBE.md`
7. `06_MEDIA_EXTRACTION_AND_DOWNLOAD_ENGINE.md`
8. `07_FORMAT_SELECTION_AND_POSTPROCESSING.md`
9. `08_MEDIA_LIBRARY_AND_ARCHIVE.md`
10. `09_TRANSCRIPTION_ENGINE.md`
11. `10_STORAGE_SECURITY_AND_PRIVACY.md`
12. `11_BACKGROUND_JOBS_AND_LIFECYCLE.md`
13. `12_ANDROID_UI_UX.md`
14. `13_PLATFORM_ADAPTERS_YOUTUBE_INSTAGRAM_X.md`
15. `14_LEGAL_POLICY_AND_CONTENT_BOUNDARIES.md`
16. `15_TESTING_RELIABILITY_AND_DEVICE_MATRIX.md`

### Power
17. `26_POWER_THERMAL_AND_BATTERY_BUDGET.md`
18. `27_CALL_BACKGROUND_ACTIVATION_AND_IDLE_MODEL.md`
19. `28_RESOURCE_SCHEDULER_AND_CONCURRENCY.md`
20. `29_POWER_PERFORMANCE_BENCHMARK_PLAN.md`

### Remote dev / PC
21. `19_REMOTE_DEV_CONTROL_OVERVIEW.md`
22. `20_DESKTOP_BRIDGE_DAEMON.md`
23. `21_PROJECT_REGISTRY_AND_GIT.md`
24. `22_AGENT_TOOL_ADAPTERS.md`
25. `23_SECURE_PAIRING_AND_TRANSPORT.md`
26. `24_REMOTE_ACTIONS_APPROVALS_AND_SECURITY.md`
27. `25_REMOTE_ACCESS_BEYOND_LAN.md`

### Remote Desktop
28. `30_REMOTE_DESKTOP_PRODUCT_CONTRACT.md`
29. `31_WINDOWS_SCREEN_CAPTURE_AND_ENCODER.md`
30. `32_REMOTE_INPUT_AND_DESKTOP_CONTROL.md`
31. `33_REMOTE_DESKTOP_TRANSPORT_AND_CODEC.md`
32. `34_ANDROID_REMOTE_DESKTOP_CLIENT.md`
33. `35_REMOTE_DESKTOP_SECURITY_AND_SESSION_POLICY.md`
34. `36_REMOTE_DESKTOP_TESTING_AND_PERFORMANCE.md`

### Delivery rules
35. `16_MILESTONES_AND_GATES.md`
36. `17_AI_ENGINEERING_GUARDRAILS.md`
37. `18_ADR_POLICY.md`
38. `AGENTS.md`
39. active milestone under `milestones/`

---

# 7. External technical references

Planning assumptions are grounded in:

- Android `InCallService` / default dialer behavior:
  https://developer.android.com/reference/android/telecom/InCallService
- Android background/foreground-service restrictions:
  https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Android background optimization:
  https://developer.android.com/topic/performance/background-optimization
- Android network battery optimization:
  https://developer.android.com/develop/connectivity/network-ops/network-access-optimization
- WorkManager constraints:
  https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Windows Desktop Duplication API:
  https://learn.microsoft.com/windows-hardware/drivers/display/desktop-duplication-api
- Windows Graphics Capture:
  https://learn.microsoft.com/windows/apps/develop/media-authoring-processing/screen-capture
- Windows SendInput:
  https://learn.microsoft.com/windows/win32/api/winuser/nf-winuser-sendinput

These references do not override the product contract.

# Visual identity

The UI direction is locked in `38_RETRO_INDUSTRIAL_DESIGN_SYSTEM.md`.

Primary palette:

```text
#0D0C0A charcoal
#211B17 warm surface
#2A221D secondary surface
#4A3A31 structural border
#E9E1D6 aged ivory
#B3A79A secondary text
#7D7268 muted text
#BD6B45 copper
#A55234 dark rust
#7D3D29 deep rust
```

Target: a dark, warm, mechanical archival tool—not a generic modern SaaS interface.
