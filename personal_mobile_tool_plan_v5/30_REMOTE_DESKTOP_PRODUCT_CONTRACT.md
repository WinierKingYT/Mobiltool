# 30 — Remote Desktop Product Contract

## 1. Goal

Control the user's own Windows computer from the Android application similarly to a lightweight AnyDesk-style experience.

---

# 2. Core UX

```text
DEV / COMPUTERS

Main-PC
● Online

[ Projects ]
[ Remote Desktop ]
```

Open Remote Desktop:

```text
+--------------------------------+
| Main-PC · Display 1             |
|                                |
|       live desktop             |
|                                |
+--------------------------------+

[Keyboard] [Mouse mode] [Quality]
```

User can:
- tap to click;
- long press/right click;
- drag;
- two-finger scroll;
- pinch zoom viewport;
- use Android keyboard;
- switch monitors;
- invoke approved Ctrl/Alt/function-key helpers.

---

# 3. PC architecture

```text
Windows session
   |
Screen Capture
   |
GPU surface
   |
Hardware Encoder
   |
Encrypted real-time transport
   |
Android
   |
Hardware Decoder
   |
SurfaceView/TextureView
```

Input reverse path:

```text
Android gesture/key
   |
typed input event
   |
encrypted transport
   |
Windows RemoteInputAdapter
   |
SendInput / approved Windows APIs
```

---

# 4. Not VNC-by-pixel polling

Do not repeatedly screenshot the full desktop into JPEG.

Use a real streaming pipeline with:
- frame metadata;
- dirty region awareness where useful;
- video compression;
- hardware encode/decode.

---

# 5. Session types

## VIEW_ONLY
No input accepted.

## CONTROL
Mouse/keyboard accepted.

Default after initial setup can be user-defined, but each paired device has capability policy.

---

# 6. Windows user-session boundary

Initial release controls the currently logged-in interactive user desktop.

No promise to:
- control login screen;
- bypass lock;
- bypass UAC secure desktop;
- create hidden Windows sessions.

---

# 7. Multi-monitor

MVP:
- select one display.

Future:
- switch displays;
- composite displays;
- multi-view.

Do not stream all monitors simultaneously by default.

---

# 8. Remote audio

Not required for initial Remote Desktop.

Phase 2 candidate:
- desktop audio capture;
- separate audio stream.

Do not block initial control on audio support.

---

# 9. Clipboard/file transfer

Not initial Remote Desktop requirement.

Reason:
- expands security surface;
- duplicates media/project systems.

Can be a separate approved subsystem later.

---

# 10. Application start/close

Two ways:

### Natural UI
Use remote mouse/keyboard.

### Approved semantic action
Bridge may expose registered app actions:
- launch VS Code;
- launch terminal;
- launch browser;
- close a known app gracefully.

No arbitrary executable path from mobile in MVP.
