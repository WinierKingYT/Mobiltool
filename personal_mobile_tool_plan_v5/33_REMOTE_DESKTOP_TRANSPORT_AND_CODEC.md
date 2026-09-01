# 33 — Remote Desktop Transport and Codec

## 1. Objectives

- low latency;
- encryption;
- NAT-ready future;
- adaptive bitrate;
- packet-loss resilience;
- hardware codec compatibility.

---

# 2. Preferred architecture candidate

WebRTC is a strong candidate because it provides:
- encrypted real-time media transport;
- congestion control;
- NAT traversal primitives;
- data channels;
- video codec negotiation.

Final choice requires ADR.

Alternative custom QUIC/RTP designs require significantly more protocol/security work.

---

# 3. Channels

Conceptual:

```text
Video channel
Input data channel
Control/telemetry channel
Optional future audio channel
```

Input/control messages must be reliable/ordered as appropriate.
Pointer motion may use a latency-optimized bounded strategy.

---

# 4. LAN-first

M9 first target:

```text
Android <---- local network ----> Desktop Bridge
```

No relay necessary.

---

# 5. Internet later

M10 adds signaling/relay/TURN-like infrastructure as required.

End-to-end session authentication is preserved.

---

# 6. Codec priority

MVP:
- H.264 hardware encode/decode where possible.

Optional:
- HEVC;
- AV1.

Codec selection considers:
- device support;
- battery;
- licensing;
- latency;
- bandwidth.

---

# 7. Quality profiles

## Data Saver
- 720p max
- 15–20 FPS
- low bitrate

## Balanced
- 1080p max
- ~30 FPS

## Quality
- higher resolution/bitrate if thermal/network permit

## Auto
Adaptive controller chooses.

---

# 8. Latency policy

Drop stale frames.

Never allow:
```text
capture queue -> 2 seconds -> encode -> network
```

under congestion.

The remote pointer should reflect newest state, not perfect historical video.

---

# 9. Telemetry

Session-local:
- RTT;
- bitrate;
- FPS;
- decoder drops;
- packet loss;
- capture latency;
- encode latency;
- decode latency.

No desktop content recorded.

---

# 10. Disconnect

On disconnect:
- input disabled immediately;
- held keys released;
- encoder stopped after short reconnect grace;
- mobile decoder released.
