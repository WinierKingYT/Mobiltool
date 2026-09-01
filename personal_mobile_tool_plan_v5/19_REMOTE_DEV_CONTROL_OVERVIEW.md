# 19 — Remote Dev Control Overview

## 1. User goal

From the Android app, see and control work happening on the user's own computer without installing separate mobile clients for every coding tool.

Example:

```text
DEV

WORKSTATION
● Main-PC online

PROJECTS

PromtGen
main · 3 modified
Claude Code: running
OpenCode: idle

Eleven
feature/capture · clean
Antigravity: waiting for approval
```

Open project:

```text
PromtGen

Git
main
M src/...
M tests/...

AGENTS
Claude Code
Running · 08:43
"Implementing auth migration..."

OpenCode
Completed · 12m ago

[ New task ]
```

---

# 2. Control surface

Initial operations:

- list machines;
- machine online/offline;
- list projects;
- Git branch/status;
- changed files;
- summarized diff;
- list sessions by adapter;
- view streamed events/output;
- start session;
- send prompt;
- resume where adapter supports it;
- cancel;
- respond to approval.

---

# 3. Not remote desktop

This feature does not stream the desktop UI.

We control semantic operations through APIs/SDKs.

Advantages:
- lower bandwidth;
- better mobile UX;
- auditable permissions;
- no screen-coordinate automation;
- works headless.

---

# 4. Tool-neutral UI

The app should not become four separate mini-apps.

Normalized session:

```text
AgentSession
- adapter
- project
- id
- title?
- status
- startedAt
- lastEventAt
- capabilities
```

Tool-specific detail may be shown secondarily.

---

# 5. Capability examples

```text
Claude Code:
list/resume via supported CLI/SDK behavior
send prompt
stream structured output where supported

OpenCode:
HTTP/OpenAPI server
project list
sessions/events
TUI/API operations where exposed

Antigravity:
SDK-owned agents and sessions
or official Remote Control surface for desktop-native sessions
(no UI scraping)

Codex/OpenAI:
Codex SDK/CLI sessions where selected
OpenAI API for app-owned model conversations
```

---

# 6. Project dashboard

Each project can show:

- local path alias;
- repository;
- branch;
- dirty state;
- ahead/behind;
- changed files;
- recent commits;
- active agents;
- last task;
- test/build status only when produced by an approved action.

Do not run expensive commands merely to populate a dashboard without policy.

---

# 7. Read-only first

Remote Dev development sequence:

```text
READ
 -> project list
 -> Git status
 -> session monitoring

then CONTROL
 -> prompt
 -> cancel

then APPROVALS
 -> explicit gated actions

then OPTIONAL WRITE/GIT ACTIONS
```

This ordering is mandatory.
