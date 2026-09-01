# 35 — Remote Desktop Security and Session Policy

## 1. Security model

Remote Desktop is the highest-risk feature in the product because it grants interactive control of a PC.

It therefore has a separate release gate.

---

# 2. Preconditions

A session requires:
- paired device identity;
- valid encrypted transport;
- non-revoked device;
- Remote Desktop capability enabled for that device;
- active Windows user session.

---

# 3. Desktop indication

PC should expose a visible local state:
- tray icon/session indicator;
- active connected device name;
- disconnect control.

No covert mode.

---

# 4. First-control policy

Possible setting:

```text
Remote Desktop:
[ ] View allowed
[ ] Control allowed
```

For new phone pairing:
- view/control are explicit capabilities.

---

# 5. Local revoke

PC user can immediately:
- disconnect current session;
- revoke phone;
- disable remote desktop module.

---

# 6. Input replay

All control events:
- session-bound;
- sequence-checked;
- expire quickly;
- rejected after reconnect if stale.

---

# 7. Lock / UAC

If Windows switches to a protected/secure desktop:
- control may stop/degrade;
- do not bypass it.

Document exact platform behavior.

---

# 8. Privilege

Desktop Bridge runs at the minimum required user privilege.

Do not run permanently as administrator merely to make all windows controllable.

If a future Windows service is required:
- UI/control worker remains separated;
- privileged IPC is narrow;
- separate security ADR.

---

# 9. Session recording

Off by default and absent in MVP.

No hidden screenshots or key logs.

---

# 10. Credentials

Remote keyboard can technically type credentials the user chooses to type.

Bridge must not:
- inspect password manager databases;
- save key events as a keylogger;
- persist input history.

---

# 11. Rate limiting

Pairing/auth endpoints are rate-limited.

Invalid sessions cannot cause expensive screen encoder allocation.

---

# 12. Internet exposure

No raw public Bridge port.

Remote Internet support follows `25_REMOTE_ACCESS_BEYOND_LAN.md`.
