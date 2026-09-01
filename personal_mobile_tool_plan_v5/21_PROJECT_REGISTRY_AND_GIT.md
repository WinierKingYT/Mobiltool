# 21 — Project Registry and Git

## 1. Goal

Let the phone answer:

> What projects are on my PC and what state are they in?

without exposing the entire filesystem.

---

# 2. Registration

Projects are added on desktop initially.

A project root becomes a security boundary.

The Android app may later request adding a directory, but desktop-side approval is required.

---

# 3. Read model

```text
ProjectSummary
- id
- name
- rootAlias
- vcsType
- branch
- dirty
- stagedCount
- unstagedCount
- untrackedCount
- ahead
- behind
- lastCommit
- activeSessions
```

Do not expose absolute paths unless user enables them.

---

# 4. Git operations — MVP read

Allowed:
- status;
- branch;
- log summary;
- changed file list;
- diff stats;
- bounded diff text on explicit request.

No background full-repo diff streaming.

---

# 5. Git operations — later write

Potential gated actions:
- stage selected file;
- unstage;
- commit;
- push;
- create branch.

Explicitly out until approved:
- force push;
- hard reset;
- clean -fd;
- rebase;
- delete branch;
- discard changes.

---

# 6. Diff safety

Large diffs:
- size limits;
- pagination;
- binary files summarized only;
- secrets filtering where feasible.

---

# 7. Watcher

Bridge may watch registered repositories for changes.

Use debounce.
Do not rescan the full filesystem continuously.

---

# 8. Project lifecycle

States:

```text
AVAILABLE
MISSING
PERMISSION_DENIED
NOT_GIT
OFFLINE
```

A deleted/moved project does not silently point to another folder.
