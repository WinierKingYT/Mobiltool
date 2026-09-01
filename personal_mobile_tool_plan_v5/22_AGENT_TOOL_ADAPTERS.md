# 22 — Agent Tool Adapters

## 1. Rule

Each external coding tool is integrated through its most stable supported programmatic surface.

No UI scraping.

---

# 2. Claude Code adapter

Preferred mechanisms:
- Claude Code programmatic/SDK interface where applicable;
- CLI print/stream JSON modes;
- supported session resume/continue identifiers.

Adapter responsibilities:
- start in registered project `cwd`;
- structured output parsing;
- capture session ID;
- resume;
- cancellation;
- permission/tool policy mapping.

Do not:
- scrape Claude desktop/web cookies;
- automate terminal keystrokes as primary protocol.

---

# 3. OpenCode adapter

OpenCode provides a headless HTTP server and OpenAPI specification.

Preferred integration:
- Bridge launches or connects to explicitly configured `opencode serve`;
- authenticated locally;
- use HTTP API;
- consume event stream;
- map projects/sessions/config/actions to normalized protocol.

Security:
- keep OpenCode server loopback-only where Bridge proxies it;
- never expose port 4096 directly to the public internet;
- Bridge owns auth boundary.

---

# 4. Antigravity adapter

There are two different integration targets.

## A — Bridge-owned Antigravity SDK sessions

Preferred for a truly unified native mobile UI.

Use official SDK to:
- start agent;
- maintain persistent conversation IDs;
- receive responses/events;
- configure approved tools/skills;
- manage subagents where desired later.

## B — Existing Antigravity 2.0 desktop sessions

Antigravity has official Remote Control through a browser and headless daemon.

If there is no stable public API for controlling those existing desktop-native sessions through our own UI:
- do not scrape its web UI;
- expose a safe “Open official Remote Control” action;
- or treat existing-session control as unsupported until an official interface is available.

---

# 5. Codex / OpenAI adapter

Two distinct concepts:

## Codex
If selected:
- use supported Codex CLI/SDK;
- sessions are Bridge-owned;
- normalize structured agent results/events.

## OpenAI model conversations
Use official OpenAI API for conversations owned by this application.

Important:
- API billing/auth is separate from ChatGPT subscriptions;
- do not claim access to existing ChatGPT consumer conversations.

---

# 6. Adapter capability model

Example:

```text
AgentCapability
- canListSessions
- canStart
- canResume
- canSend
- canStream
- canCancel
- canApproveTools
- canReportCost
- canExposeArtifacts
```

UI renders only supported controls.

---

# 7. Version qualification

Record:
- tool version;
- adapter version;
- last probe;
- capability snapshot.

After tool update:
- adapter re-probes;
- unsupported operations disappear rather than failing dangerously.

---

# 8. Tool selection

A project may have multiple adapters.

The Android app can choose:

```text
New task
[ Claude Code ]
[ OpenCode ]
[ Antigravity ]
[ Codex ]
```

Only installed/authorized adapters appear.
