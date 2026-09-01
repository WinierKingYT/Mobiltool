# 25 — Remote Access Beyond LAN

## 1. Goal

Eventually control the workstation while the phone is away from the same Wi-Fi, without requiring a separate remote-control mobile app.

This is **not** initial Remote Dev MVP.

---

# 2. Forbidden default

Do not tell users to expose:

```text
0.0.0.0:<bridge-port>
```

through router port forwarding.

---

# 3. Architecture options

## Option A — User-managed private VPN
Technically simple but may require another mobile app.
Useful for internal testing, not ideal final UX.

## Option B — Embedded tunnel
Implement a WireGuard-like/private-tunnel client inside our app.
High complexity and security review burden.

## Option C — Outbound E2EE relay
Recommended product candidate.

```text
Desktop Bridge ----outbound----\
                                Relay
Android -----------outbound----/
```

Relay performs:
- rendezvous;
- connection routing;
- push wake/signal.

Payload is end-to-end encrypted between paired devices.

Relay must not possess plaintext project/session data.

## Option D — Peer-to-peer with signaling
Potential later optimization; NAT traversal complexity.

---

# 4. Relay boundary

If built:
- separate repository/service;
- no source-code storage;
- no prompt archive;
- rate limiting;
- device authentication;
- encrypted payload opaque to relay;
- minimal metadata retention;
- breach model documented.

---

# 5. Offline behavior

Desktop can keep agent jobs running while Android disconnects.

On reconnect:
- Android gets session state/event catch-up;
- no action is repeated unless idempotency rules permit it.

---

# 6. Gate

Remote internet control is enabled only after:
- LAN security audit passes;
- device revocation works;
- protocol is versioned;
- end-to-end encryption proven;
- relay threat model approved.
