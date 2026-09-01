# P7 — Remote Dev Read-Only

## Goal
From Android, safely inspect the user's Windows development machine.

Show:
- paired machine state
- registered projects
- Git branch/status
- changed files/bounded diff
- coding-agent sessions/events.

No write/control yet.

## Execution Packages
1. Validate Desktop Bridge runtime choice.
2. Real secure pairing + revocation.
3. Registered-project filesystem sandbox.
4. Git read operations.
5. OpenCode read adapter.
6. One second real tool adapter.
7. Offline/stale state truth.

## Exit Gate
```text
[x] real Windows Bridge
[x] pair + revoke
[x] encrypted transport
[x] one real repo status matches desktop
[x] two real adapter read proofs
[x] no write endpoint
[x] no filesystem escape
```
