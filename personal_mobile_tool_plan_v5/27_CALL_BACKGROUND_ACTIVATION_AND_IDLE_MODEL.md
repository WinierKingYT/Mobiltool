# 27 — Call Background Activation and Idle Model

## 1. Goal

The call recording feature should feel permanently available without permanently consuming recording resources.

---

# 2. State machine

```text
DISABLED
 |
 v
READY_IDLE
 |
 | call lifecycle event
 v
PREPARING
 |
 v
RECORDING
 |
 | call ended
 v
FINALIZING
 |
 +--> STORED
 |
 v
READY_IDLE
```

`READY_IDLE` is the normal all-day state.

---

# 3. READY_IDLE requirements

Must not:
- open microphone;
- allocate audio buffers;
- run encoder;
- poll call state;
- hold wake lock;
- hold constant foreground service solely for readiness.

May:
- retain tiny immutable configuration;
- rely on system/Telecom registration/privileged hooks;
- receive OS callback.

---

# 4. Trigger source

Preferred trigger order:

1. Android Telecom / privileged platform call lifecycle;
2. selected supported OEM/system integration;
3. documented event source appropriate to capture mode.

Polling is not an acceptable primary call trigger.

---

# 5. Capture start

On active/connected state:
- create immutable `CallSessionId`;
- verify capability;
- allocate capture buffers;
- create recording journal;
- activate capture;
- record start latency metric.

Do not delay call connection.

---

# 6. Call end

Immediately:
- stop capture;
- flush buffers;
- finalize;
- validate;
- commit;
- release audio resources;
- return READY_IDLE.

Finalization is bounded and must release resources even on exception.

---

# 7. Overlapping heavy jobs

When a call begins:
- active local transcription should pause/cancel at a safe chunk boundary if necessary;
- heavy transcode is deprioritized;
- remote desktop may reduce quality if CPU contention affects capture;
- download may continue if capture is unaffected.

Call reliability wins.

---

# 8. Reboot / app update

After boot:
- capability registration restored by normal platform mechanisms;
- do not start recorder;
- reconcile incomplete recording journal if any.

---

# 9. Health monitor

No continuous sampling.

Health is measured:
- during capture;
- after capture;
- in explicit diagnostics.

---

# 10. Battery validation

Test:
- 8h READY_IDLE with no calls;
- compare battery drain to app disabled baseline;
- inspect CPU wakeups;
- verify no recorder/microphone attribution while idle.
