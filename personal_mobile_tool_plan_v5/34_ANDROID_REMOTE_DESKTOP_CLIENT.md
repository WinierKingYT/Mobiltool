# 34 — Android Remote Desktop Client

## 1. Objective

Display and control a PC with minimal Android battery/thermal cost.

---

# 2. Decoder

Prefer hardware decoder through Android codec/WebRTC stack.

Do not decode H.264/HEVC/AV1 in pure CPU by default.

Low-latency decoder mode may be enabled only while Remote Desktop is active and supported, because low-latency operation can increase resource use.

---

# 3. Rendering

Prefer:
- SurfaceView or efficient GPU-backed rendering path;
- no unnecessary bitmap copies;
- no per-frame Compose bitmap conversion.

Compose surrounds the video surface but is not the video pixel pipeline.

---

# 4. Client state

```text
DISCONNECTED
CONNECTING
CONNECTED_VIEW
CONNECTED_CONTROL
BACKGROUND_GRACE
RECONNECTING
FAILED
```

---

# 5. App background

Default:
- stop rendering/decoding quickly;
- notify Bridge to pause video;
- input disabled;
- bounded reconnect grace;
- disconnect.

Optional later:
- background audio only if remote-audio feature exists.

No background 30 FPS screen decode.

---

# 6. Orientation

Support:
- portrait control with viewport;
- landscape full desktop;
- orientation change without duplicate session.

---

# 7. Mobile data

Show warning/setting for metered networks.

Remote desktop can use significant data.

Auto profile reduces bitrate on cellular.

---

# 8. Battery saver

When Android Battery Saver active:
- Auto mode lowers quality;
- do not force max FPS;
- keep touch/control responsive.

---

# 9. Thermal

On thermal escalation:
- request lower stream profile;
- reduce render FPS;
- disable optional visual extras;
- if critical, disconnect gracefully.

---

# 10. Input UX

Trackpad overlay/input controls should not force video re-layout per pointer event.

Use pointer event pipeline with bounded rate/coalescing.

---

# 11. Keyboard

Opening soft keyboard must preserve enough desktop view to understand focus.

Provide configurable modifier toolbar.

---

# 12. Accessibility

UI controls are accessible.

Remote desktop canvas itself may need explicit semantic labels, but do not pretend arbitrary desktop pixels are fully accessible Android UI.

---

# 13. Screenshot

No automatic screenshots/cache.

A future explicit “capture screenshot” action is a separate user command.
