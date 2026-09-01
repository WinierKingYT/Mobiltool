# 31 — Windows Screen Capture and Encoder

## 1. Goal

Capture the interactive Windows desktop efficiently enough for low-latency remote control.

---

# 2. Capture APIs

Primary candidates:

## DXGI Desktop Duplication
Designed for desktop collaboration/remote access.
Provides:
- GPU-backed desktop frames;
- dirty-region metadata;
- move metadata;
- cursor information.

## Windows.Graphics.Capture
Modern Windows screen/window capture API.

Decision is ADR/device/OS benchmark controlled.

---

# 3. Selection criteria

Measure:
- latency;
- CPU;
- GPU;
- multi-monitor;
- cursor;
- protected content behavior;
- RDP/session behavior;
- Windows versions;
- ease of hardware encoder interop.

---

# 4. Encoder

Preferred:
- hardware H.264 first for compatibility;
- hardware HEVC/AV1 may be optional if both ends support and licensing/distribution is resolved.

Do not require AV1 for MVP.

---

# 5. Hardware paths

Candidate Windows encoder technologies may include:
- Media Foundation hardware transforms;
- vendor-independent platform paths;
- WebRTC native encoder integration.

Do not invoke external `ffmpeg.exe` for every frame as the production remote-desktop architecture.

FFmpeg can remain a test/diagnostic option.

---

# 6. Frame behavior

Target:
- capture newest frame;
- drop stale frames under congestion;
- never build seconds-long frame queues.

Remote desktop values latency over perfect frame delivery.

---

# 7. Static desktop optimization

Use:
- dirty-region metadata where capture path exposes it;
- adaptive bitrate/FPS;
- encoder rate control.

A static editor should consume much less bandwidth than a moving video.

---

# 8. Cursor

Prefer sending:
- cursor position/shape metadata;
or
- cursor rendered into frame depending protocol/implementation.

No duplicate cursor.

---

# 9. Resolution

Do not capture at a resolution higher than useful client/display policy without reason.

Examples:
- 4K PC monitor -> Android may request 1440p/1080p stream.
- user can zoom/pan.

---

# 10. Protected content

Protected Windows/video surfaces may be black/unavailable.

Do not bypass content protection.

---

# 11. Capture lifecycle

```text
no remote session
 -> capture object absent
 -> encoder absent

authenticated session begins
 -> allocate capture
 -> allocate encoder

session ends
 -> drain/drop
 -> stop encoder
 -> release GPU surfaces
```

---

# 12. References

- https://learn.microsoft.com/windows-hardware/drivers/display/desktop-duplication-api
- https://learn.microsoft.com/windows/win32/direct3ddxgi/desktop-dup-api
- https://learn.microsoft.com/windows/apps/develop/media-authoring-processing/screen-capture
