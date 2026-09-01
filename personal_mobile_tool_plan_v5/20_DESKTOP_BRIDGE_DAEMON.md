# 20 — Desktop Bridge / Daemon

## 1. Purpose

A small trusted process running on the development computer.

It translates local tool APIs/CLIs into the stable mobile protocol.

---

# 2. Responsibilities

- device pairing;
- transport encryption;
- project registry;
- Git inspection;
- adapter lifecycle;
- process supervision;
- session event normalization;
- approval broker;
- audit journal;
- revocation.

---

# 3. Non-responsibilities

The Bridge is not:
- a cloud server;
- a full IDE;
- a package manager UI;
- an unrestricted SSH replacement;
- a credential sync service;
- a source-code backup service.

---

# 4. Startup

Preferred:

```text
OS boot/login
 -> bridge starts
 -> loads local config
 -> unlocks local identity
 -> starts loopback listener
 -> optional LAN listener according to settings
 -> probes adapters
 -> announces discovery if enabled
```

---

# 5. Project registry

Config example concept:

```text
projects:
  - id: ...
    name: PromtGen
    root: D:\Projects\promtgen
    adapters:
      - claude-code
      - opencode
```

Paths are never supplied arbitrarily by mobile requests.

---

# 6. Adapter process isolation

Where practical, adapter failures must not crash Bridge.

For tools that require subprocesses:
- bounded stdout/stderr;
- timeout/cancellation;
- process tree cleanup;
- no inherited unnecessary environment variables.

---

# 7. Secrets

Bridge stores/uses tool credentials in their normal supported environment.

Mobile receives:
- adapter name;
- auth state;
- capability state.

Mobile does not receive raw API keys.

---

# 8. Audit journal

Local audit events:

```text
paired device
revoked device
session started
prompt sent
approval granted/denied
session cancelled
Git action executed
```

Do not log full secret values.
Prompt logging is configurable because prompts may contain sensitive source/business content.

---

# 9. Updates

Bridge update mechanism requires signed releases.

No arbitrary self-updating code from the Android app.

---

# 10. Windows-first

Initial reference:
- Windows 10/11.

Linux/macOS support can follow via adapter/platform milestone.

Do not claim cross-platform until physical testing.
