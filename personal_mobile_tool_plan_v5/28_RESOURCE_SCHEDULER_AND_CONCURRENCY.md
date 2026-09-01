# 28 — Resource Scheduler and Concurrency

## 1. Purpose

Prevent independent modules from each assuming they own CPU, memory, storage and network.

---

# 2. ResourceCoordinator

Conceptual service:

```text
requestWork(
  type,
  priority,
  expectedCpu,
  expectedMemory,
  expectedNetwork,
  userInitiated
)
```

It coordinates policy but does not become a giant business-logic class.

---

# 3. Workload types

```text
CALL_CAPTURE
CALL_FINALIZE
DOWNLOAD
MEDIA_MERGE
MEDIA_TRANSCODE
TRANSCRIPTION
REMOTE_DESKTOP
REMOTE_DEV_STREAM
MAINTENANCE
```

---

# 4. Default conflict table

| Active | Requested | Default |
|---|---|---|
| CALL_CAPTURE | TRANSCRIPTION | defer/pause STT |
| CALL_CAPTURE | TRANSCODE | defer transcode |
| CALL_CAPTURE | REMOTE_DESKTOP | allow, lower quality if needed |
| REMOTE_DESKTOP | TRANSCRIPTION | defer STT |
| REMOTE_DESKTOP | TRANSCODE | defer |
| DOWNLOAD | TRANSCRIPTION | allow if device healthy |
| TRANSCRIPTION | TRANSCODE | serialize |
| MAINTENANCE | anything user-visible | defer maintenance |

---

# 5. Memory budget

Avoid simultaneous:
- large STT model;
- software video encoder/decoder;
- huge FFmpeg buffers.

Debug build samples PSS/RSS and native heap.

On memory pressure:
- release caches;
- stop optional prefetch;
- unload STT model after job;
- reduce remote desktop buffers.

---

# 6. Storage I/O

Avoid:
- transcription source decode + massive download finalization + media merge writing to same storage simultaneously when not necessary.

Call finalization gets storage I/O priority.

---

# 7. Network budget

Remote Desktop gets low-latency priority.
Download is throughput-oriented and may throttle during active Remote Desktop.

No reason for background media download to saturate link and ruin remote-control latency.

---

# 8. User override

Advanced setting may allow:
- “continue transcription during Remote Desktop”
- “maximum performance”

Default remains safe/balanced.

---

# 9. Failure

Scheduler must never delete/corrupt work to resolve contention.

It delays, pauses or cancels only according to workload contracts.
