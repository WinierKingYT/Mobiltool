# 01 — Mobiltool Execution Protocol

**Project:** Mobiltool  
**Purpose:** Her parçayı kontrollü, doğrulanabilir ve geri alınabilir şekilde geliştirmek.

---

## 1. Execution Header Format

Her kod değişikliğinden önce zorunlu başlık:

```text
ACTIVE_PART:
EXECUTION_PACKAGE:
GOAL:
FILES EXPECTED TO CHANGE:
FILES FORBIDDEN TO CHANGE:
NEW PERMISSIONS:
NEW DEPENDENCIES:
NETWORK BEHAVIOR CHANGE:
STORAGE BEHAVIOR CHANGE:
BACKGROUND BEHAVIOR CHANGE:
POWER IMPACT:
SECURITY IMPACT:
ADR REQUIRED:
PHYSICAL DEVICE TEST REQUIRED:
```

---

## 2. Package Report Format

Her paket tamamlandığında:

```text
EXECUTION_PACKAGE:
GOAL:
IMPLEMENTED:
FILES CHANGED:
TESTS:
REAL EVIDENCE:
KNOWN LIMITATIONS:
FOLLOW-UP:
STATUS: PASS | FAIL | BLOCKED
```
