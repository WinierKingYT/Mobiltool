# 23 — Secure Pairing and Transport

## 1. Threat model

Protect against:
- random LAN client;
- stolen pairing code;
- MITM;
- replay;
- lost phone;
- malicious second phone;
- accidental public exposure of Bridge port.

---

# 2. Device identity

Each Android installation and Bridge has a cryptographic device identity.

Pairing creates a durable trust record.

Passwords alone are not the long-term device identity.

---

# 3. Pairing flow

Preferred UX:

```text
Desktop Bridge
 -> Pair new phone
 -> one-time QR

Android
 -> scan QR
 -> verify short code/fingerprint
 -> encrypted handshake
 -> both store peer identity
 -> desktop confirms
```

QR must expire quickly and be single-use.

---

# 4. Transport

MVP LAN:

- Bridge binds conservatively;
- encrypted authenticated channel;
- certificate/public-key pinning;
- protocol version negotiation;
- reconnect support.

Implementation protocol is ADR-controlled:
- TLS with mutual client auth;
- Noise-like authenticated handshake;
- another audited standard.

No custom cryptography.

---

# 5. Authorization

Authentication answers:

> Which phone is this?

Authorization answers:

> What may this phone do?

Per-device capability policy:

```text
READ_PROJECTS
READ_GIT
READ_SESSIONS
SEND_AGENT_PROMPT
CANCEL_AGENT
RESPOND_APPROVAL
GIT_WRITE (later)
```

---

# 6. Revocation

Desktop UI/CLI can:
- list paired phones;
- revoke immediately;
- rotate Bridge identity if compromised.

Revoked device cannot reconnect with cached tokens.

---

# 7. Session security

- short-lived session tokens;
- replay protection;
- monotonic/request IDs;
- bounded idle timeout;
- device lock can require re-auth before destructive approvals.

---

# 8. Discovery

LAN discovery may use mDNS.

Discovery advertises only:
- Bridge presence;
- non-sensitive instance name;
- protocol version.

No project names in broadcast.

---

# 9. Public internet

Never port-forward Bridge directly as the recommended remote-access design.

See `25_REMOTE_ACCESS_BEYOND_LAN.md`.
