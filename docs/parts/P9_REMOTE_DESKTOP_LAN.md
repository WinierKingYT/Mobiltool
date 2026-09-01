# P9 — Remote Desktop LAN

## Goal
AnyDesk-like control of the user's own Windows PC on the same network.

## Execution Packages
1. Benchmark DXGI Desktop Duplication vs Windows.Graphics.Capture.
2. Select real Windows capture path.
3. Hardware H.264 encoder.
4. Encrypted low-latency LAN transport, WebRTC strong candidate.
5. Android hardware decoder + SurfaceView/GPU render.
6. Typed mouse/keyboard input.
7. DPI/multi-monitor coordinate mapping.
8. Background/power behavior.
9. LAN latency/battery test.

## Security
- paired machines only
- no covert mode
- no UAC/secure-desktop bypass
- no key logging
- no public port
- no capture when session absent.

## Exit Gate
```text
[x] real Windows frames
[x] hardware encode
[x] encrypted LAN stream
[x] Android hardware decode
[x] click/right-click/drag/scroll/keyboard
[x] no stale/stuck input after reconnect
[x] capture stops on disconnect
[x] decoder stops in background
[x] UAC/lock limitations documented
```
