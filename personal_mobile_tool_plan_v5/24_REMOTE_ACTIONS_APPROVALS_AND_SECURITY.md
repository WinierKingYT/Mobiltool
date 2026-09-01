# 24 — Remote Actions, Approvals and Security

## 1. Core rule

Mobile convenience must not turn the phone into an unbounded RCE key.

---

# 2. Action classes

## Class R — Read
Examples:
- Git status;
- session list;
- logs.

Default allowed after trusted pairing.

## Class A — Agent control
Examples:
- send prompt;
- start adapter-owned task;
- cancel.

Allowed by per-device policy.

## Class W — Write
Examples:
- edit through agent;
- stage/commit;
- tool write approval.

Requires explicit policy and often per-action confirmation.

## Class D — Destructive / privileged
Examples:
- discard changes;
- force push;
- delete arbitrary files;
- admin/sudo;
- arbitrary shell.

Not MVP.

---

# 3. Approval request

Normalized model:

```text
ApprovalRequest
- id
- project
- session
- adapter
- requestedCapability
- humanSummary
- commandPreview? (if safe)
- affectedPaths?
- expiresAt
```

Phone shows enough context to make a decision.

---

# 4. Deny by default

Unknown action type:
`DENIED_UNSUPPORTED`

No fallback to raw shell.

---

# 5. Prompt injection boundary

Agent output is untrusted content.

A message from an agent saying:
> approve this command

does not itself create a trusted approval request.

Only Bridge/tool adapter can emit authenticated `ApprovalRequest`.

---

# 6. File boundaries

Agent/tool access is additionally constrained by its configured tool permissions.

Bridge does not weaken Claude/OpenCode/Antigravity/Codex native permission systems.

---

# 7. Dangerous Git

MVP has no:
- force push;
- hard reset;
- clean;
- checkout overwrite;
- destructive branch delete.

---

# 8. Notifications

Optional Android notifications:
- task complete;
- task failed;
- approval required.

Notification content should be privacy-minimized on lock screen.

---

# 9. Audit

Store security-relevant action metadata:
- device;
- action class;
- project;
- decision;
- timestamp;
- result.

Do not require full prompt/source logging.
