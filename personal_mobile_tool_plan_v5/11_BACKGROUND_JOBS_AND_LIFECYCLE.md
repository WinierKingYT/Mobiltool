# 11 — Background Jobs, Lifecycle and Battery Discipline

## 1. Rule

Background capability does not mean permanent execution.

Android's modern background model intentionally restricts foreground-service launches and long-running background work. The application must cooperate with the OS, not fight it.

---

# 2. Work categories

## Event-critical
- phone-call capture lifecycle;
- recording finalization.

## User-active
- media download;
- media conversion;
- transcription;
- Remote Desktop.

## Deferred maintenance
- cleanup;
- index maintenance;
- integrity scan;
- stale temp cleanup.

Different categories use different scheduling.

---

# 3. Call lifecycle

Preferred:

```text
Telecom/system call event
    |
    v
CallSessionCoordinator
    |
    +--> supported capture? yes
               |
               v
       start capture resources
               |
               v
          active call
               |
             end
               |
               v
         bounded finalize
               |
               v
              IDLE
```

Do not keep a normal microphone recorder open to detect calls.

---

# 4. WorkManager

Use for deferrable persistent work:
- cleanup;
- retry;
- maintenance;
- optional queued media/transcription.

Possible constraints:
- battery not low;
- storage not low;
- unmetered/Wi-Fi;
- charging.

WorkManager is not the active-call recording lifecycle.

---

# 5. Foreground services

Use only when:
- current Android platform rules allow;
- the work genuinely requires user-visible long-running execution;
- correct service type is declared;
- notification is meaningful.

Examples:
- active user-initiated download;
- long explicit transcription where required;
- active Remote Desktop connectivity if platform/lifecycle requires.

No always-running “umbrella service.”

---

# 6. Wake locks

Default: none.

If unavoidable:
- acquire immediately before critical bounded section;
- timeout;
- `try/finally` release;
- metrics around held duration.

Never hold across the whole day.

---

# 7. Job concurrency

Default global heavy-work budget:

```text
Remote Desktop active?
  YES:
    transcription = paused/not started
    heavy postprocess = delayed
    media download = allowed with lower priority

  NO:
    max transcription = 1
    max heavy postprocess = 1
    downloads = max 2
```

Call recording always has priority over optional CPU-heavy work.

---

# 8. Network

Do not poll remote PC continuously.

States:
- Remote tab closed -> no connection unless explicit persistent-notification feature enabled later.
- Remote tab open -> connect/on-demand refresh.
- Remote session active -> persistent socket.
- session ends -> close after grace window.

Downloads reuse connections where supported and respect metered/Wi-Fi policy.

---

# 9. Android process death

State belongs in durable jobs/domain journal.

Activity/process death must not:
- lose completed call recording;
- corrupt download;
- attach wrong partial file;
- repeat destructive remote input.

---

# 10. Reboot

On boot:
- no heavy job stampede;
- reconcile metadata;
- requeue eligible durable work under constraints;
- call capability remains event-driven;
- Remote Desktop does not auto-connect.

---

# 11. Battery Saver / restricted state

Application must tolerate:
- jobs delayed;
- background work suspended;
- network unavailable;
- foreground-service restrictions.

Do not ask user to disable battery optimization as the primary architecture.

Only request exceptional settings if a proven required feature cannot operate otherwise, and only after ADR/product approval.

---

# 12. References

- https://developer.android.com/topic/performance/background-optimization
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
