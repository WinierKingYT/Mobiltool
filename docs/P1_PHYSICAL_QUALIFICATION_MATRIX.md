# P1 Call Recording — Physical Qualification Matrix

**Status**: READY FOR PHYSICAL VALIDATION  
**Active Part**: P1 — Call Recording  
**Baseline**: Truth-gated Multi-Tier Architecture (`PRIVILEGED_DIRECT`, `OEM_IMPORT`, `UNSUPPORTED_USERSPACE`)  

---

## 1. Physical Device Verification Protocol (10 Real Calls Matrix)

| Test ID | Scenario Description | Audio Route | Screen & App State | Termination Trigger | Expected Tier & Quality | Verification Criteria | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-01** | Incoming Cellular Call | Earpiece | Screen ON, App Foreground | Remote Party Hangup | Verified Bidirectional | File $>2$KB, valid `ftyp` atom, clear uplink & downlink audio | PENDING PHYSICAL RUN |
| **TC-02** | Outgoing Cellular Call | Earpiece | Screen ON, App Background | Local User Hangup | Verified Bidirectional | Duration matches $\pm 1$s, Room DB entry created, non-silent | PENDING PHYSICAL RUN |
| **TC-03** | Incoming Cellular Call | Loudspeaker | Screen ON, App Foreground | Local User Hangup | Verified Bidirectional | Downlink & uplink audible without severe acoustic clipping | PENDING PHYSICAL RUN |
| **TC-04** | Outgoing Cellular Call | Loudspeaker | Screen Locked / OFF | Remote Party Hangup | Verified Bidirectional | Wakelock held during call, released cleanly after hangup | PENDING PHYSICAL RUN |
| **TC-05** | Incoming Cellular Call | Bluetooth (HFP/SCO) | Screen ON | Remote Party Hangup | Verified Bidirectional | Bluetooth downlink & uplink captured cleanly | PENDING PHYSICAL RUN |
| **TC-06** | Outgoing Cellular Call | Bluetooth (HFP/SCO) | Screen OFF, Pocket Mode | Local User Hangup | Verified Bidirectional | Battery drain normal, zero audio truncation | PENDING PHYSICAL RUN |
| **TC-07** | Missed Incoming Call | N/A | Screen Locked | Caller Abandons ($15$s) | `MISSED` (Duration 0s, Unsupported) | Metadata record in Room DB with `MISSED` direction, NO dummy audio file | PENDING PHYSICAL RUN |
| **TC-08** | Rejected Incoming Call | N/A | Screen ON | Local Decline Button | `MISSED` / Unrecorded | Telephony tracker transitions `RINGING` $\rightarrow$ `IDLE`, no audio service launched | PENDING PHYSICAL RUN |
| **TC-09** | Rapid Back-to-Back Calls | Earpiece | Mixed Foreground/Background | Sequential Hangups ($<5$s gap) | 2x Independent Sessions | Idempotent service start/stop, distinct call UUIDs and files | PENDING PHYSICAL RUN |
| **TC-10** | Process Kill / Crash During Call | Earpiece | Process Killed Mid-Call (`kill -9`) | Emergency Reboot | Crash Recovery via Journal | Startup journal recovery extracts partial file, flags reason in DB | PENDING PHYSICAL RUN |

---

## 2. Capability Tier Truth Gates on Physical Target

```
1. PRIVILEGED_DIRECT:
   - Requires companion daemon running on UNIX socket (/dev/socket/mobiltool_companion).
   - If daemon responds to MAGIC_HEADER with PONG -> Streams ALSA raw hardware PCM/AAC.
   - Result: VERIFIED_BIDIRECTIONAL.

2. OEM_IMPORT:
   - Requires Samsung OneUI / Xiaomi native call recording enabled.
   - Harvests recording from /Recordings/Call or MediaStore with timestamp correlation.
   - Result: VERIFIED_BIDIRECTIONAL.

3. UNSUPPORTED_USERSPACE:
   - Active when neither Privileged Companion nor OEM Recording is present.
   - Strict Fail-Closed: Registers metadata session with diagnostic reason.
   - Zero ambient microphone recording during phone calls.
```
