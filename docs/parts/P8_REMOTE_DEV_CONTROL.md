# P8 — Remote Dev Control

## Goal
Safely control approved coding-agent workflows.

Allowed:
`START_AGENT_TASK`, `SEND_AGENT_MESSAGE`, `RESUME_SESSION`, `CANCEL_SESSION`, `RESPOND_TO_APPROVAL`.

No generic shell.

## Execution Packages
1. Typed action protocol + request IDs/idempotency.
2. Adapter capability matrix.
3. OpenCode control.
4. Supported Claude/Codex/Antigravity surface only.
5. Authenticated approval broker.
6. Audit log.
7. Replay/lost-phone tests.

## Still Forbidden
force push, hard reset, clean, arbitrary delete, admin/sudo, unrestricted shell, UI/cookie scraping.

## Exit Gate
```text
[x] start real task
[x] stream real events
[x] send real prompt
[x] cancel
[x] approval round-trip
[x] replay-safe
[x] revocation stops control
[x] no generic shell
```
