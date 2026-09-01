# Mobiltool — Sequential Development Plans V1

Repository: `WinierKingYT/Mobiltool`

Current active part:

```text
ACTIVE_PART = P0
```

Development order:

```text
P0  Truth Pass & Baseline
P1  Call Recording
P2  Media / Video Downloader
P3  Library & Playback
P4  Local Transcription
P5  Power / Background / Thermal
P6  Security / Storage / Recovery
P7  Remote Dev — Read Only
P8  Remote Dev — Control
P9  Remote Desktop — LAN
P10 Remote Desktop — Internet
```

Rule:

> A later part cannot begin until the current part has a PASS exit report and the user explicitly activates the next part.

Existing code does not count as verified capability.

```text
class exists != feature verified
UI exists != engine works
request sent != task completed
file exists != valid artifact
```

Read:
1. `00_SEQUENCE_MASTER.md`
2. `01_EXECUTION_PROTOCOL.md`
3. `02_CURRENT_REPO_MAPPING.md`
4. `ACTIVE_PART.md`
5. the active plan under `parts/`
