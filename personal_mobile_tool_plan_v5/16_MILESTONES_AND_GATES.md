# 16 — Milestones and Delivery Gates V4

Remote PC work is deliberately last.

---

# M0 — Core Feasibility + Power Baseline

## Call
Prove legitimate call-capture route or explicit unsupported result.

## Media
Prove URL probe/download on reference Android.

## Power
Measure baseline:
- 4–8h idle prototype;
- confirm no permanent recorder;
- confirm no polling design.

No Desktop Bridge required.

Detailed:
`milestones/M0_CORE_FEASIBILITY_POWER.md`

---

# M1 — Android Foundation

- Compose shell;
- Room;
- AssetStore;
- job journal;
- ResourceCoordinator skeleton;
- library/playback;
- security basics.

---

# M2 — Media Downloader

- URL share/paste;
- formats;
- download;
- postprocess;
- validation;
- library.

---

# M3 — Call Archive

- call lifecycle;
- selected capture engine;
- event-driven activation;
- recording;
- validation;
- archive;
- idle battery test.

Detailed:
`milestones/M3_CALL_ARCHIVE_POWER_AWARE.md`

---

# M4 — Unified Local Transcription

- calls;
- downloaded video/audio;
- on-device STT;
- chunking;
- scheduler integration;
- charging/low-battery policy.

---

# M5 — Local Productization + Security + Power Hardening

- complete local UX;
- encryption;
- storage;
- delete;
- background policy;
- thermal behavior;
- resource conflicts;
- diagnostics.

---

# M6 — Local Tool Release Qualification

Qualify:
- downloader fixtures;
- call supported device matrix;
- transcription;
- 8h idle;
- low battery;
- thermal;
- process death;
- reboot.

At this point the Android app is already useful.

---

# M7 — Desktop Bridge + Remote Dev Read-Only

ONLY NOW start computer integration.

- Windows Bridge;
- secure pairing;
- registered projects;
- Git status;
- adapter/session monitoring.

---

# M8 — Remote Dev Control

- start agent tasks;
- send prompts;
- cancel;
- approvals.

No full desktop yet.

---

# M9 — Remote Desktop LAN

- Windows screen capture;
- hardware encoder;
- LAN real-time transport;
- Android hardware decoder;
- touch/mouse/keyboard;
- monitor selection;
- security indicators;
- battery adaptive streaming.

Detailed:
`milestones/M9_REMOTE_DESKTOP_LAN.md`

---

# M10 — Remote Internet Control

Only after M9 security/performance gate.

- rendezvous/signaling;
- E2EE relay/TURN-like transport;
- NAT traversal;
- remote reconnect;
- remote notifications.

No direct public port-forward recommendation.

---

# Future

Possible separate decisions:
- remote desktop audio;
- clipboard;
- file transfer;
- semantic registered app launcher;
- remote screenshot;
- multiple monitors simultaneously.

These are not M9 requirements.

---

# Global gate

No milestone pulls features forward.

Remote Desktop must not delay:
- call recorder;
- downloader;
- transcription.

A feature failing feasibility remains unsupported; it does not weaken security/battery invariants.
