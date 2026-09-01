# 02 — System Architecture V3

## 1. Architectural style

Two cooperating modular monoliths:

```text
Android Application
+
Desktop Bridge
```

Optional relay may be added later only for remote-outside-LAN transport.

No microservice farm.

---

# 2. Android modules

```text
:app

:core-common
:core-model
:core-storage
:core-security
:core-jobs
:core-media
:core-network

:feature-calls
:feature-download
:feature-library
:feature-transcript
:feature-remote-dev
:feature-settings

:remote-protocol
:remote-client

...existing capture/extractor/transcription adapters
```

---

# 3. Desktop Bridge modules

Language is ADR-controlled.
Strong candidates:
- Rust for small secure daemon;
- TypeScript/Node where SDK compatibility materially helps;
- split process with a narrow Rust daemon and tool-specific worker only if proven necessary.

Conceptual modules:

```text
bridge-core
bridge-auth
bridge-transport
bridge-projects
bridge-git
bridge-events
bridge-approvals
bridge-process
bridge-adapter-api

adapter-claude-code
adapter-opencode
adapter-antigravity
adapter-codex-openai
```

Do not create all adapters before their milestone.

---

# 4. Remote protocol

Use typed request/response/event schemas.

Example concepts:

```text
MachineInfo
ProjectSummary
GitStatus
GitDiffSummary
AgentSession
AgentEvent
ApprovalRequest
ActionRequest
ActionResult
CapabilitySet
```

Protocol has explicit versioning.

No raw shell-string protocol in MVP.

---

# 5. Data direction

```text
Desktop canonical state
      |
      v
Bridge normalized snapshot/events
      |
 encrypted transport
      |
      v
Android cache/UI

Android action
      |
 capability + approval policy
      |
      v
Bridge
      |
      v
Tool adapter / Git / safe action
```

---

# 6. Event model

Prefer streaming events for agent output:

```text
SESSION_STARTED
MESSAGE
TOOL_REQUESTED
APPROVAL_REQUIRED
TOOL_STARTED
TOOL_COMPLETED
SESSION_COMPLETED
SESSION_FAILED
SESSION_CANCELLED
GIT_CHANGED
```

Transport may use WebSocket/SSE-compatible semantics behind abstraction.

---

# 7. Adapter contract

Conceptual:

```kotlin
interface DevAgentAdapter {
    suspend fun capability(): AgentCapability
    suspend fun listSessions(projectId: ProjectId): List<AgentSession>
    fun observeSession(sessionId: SessionId): Flow<AgentEvent>
    suspend fun start(request: StartAgentRequest): AgentSession
    suspend fun send(sessionId: SessionId, message: String): SendResult
    suspend fun cancel(sessionId: SessionId): CancelResult
    suspend fun respondToApproval(
        approvalId: ApprovalId,
        decision: ApprovalDecision
    ): ApprovalResult
}
```

Desktop implementation language may differ; schema semantics stay stable.

---

# 8. Project boundary

Bridge registers explicit roots:

```text
D:\Projects\promtgen
D:\Projects\eleven
...
```

Remote Dev sees only registered projects.

No filesystem root browser.

---

# 9. Shared app shell

The Android app can expose:

```text
HOME
TOOLS
DEV
LIBRARY
SETTINGS
```

Exact navigation is UX-controlled.

The architecture must not force local media and remote development into the same domain models merely because they live in one app.

---

# 10. Failure isolation

If Desktop Bridge is offline:
- downloader still works;
- local library still works;
- call archive still works;
- transcription still works.

If yt-dlp breaks:
- remote dev still works.

If one agent adapter breaks:
- other adapters continue working.

This isolation is mandatory.
