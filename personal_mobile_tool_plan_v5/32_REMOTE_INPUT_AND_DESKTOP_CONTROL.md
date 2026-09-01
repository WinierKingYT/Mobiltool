# 32 — Remote Input and Desktop Control

## 1. Goal

Translate Android touch/keyboard actions into controlled Windows user-input events.

---

# 2. Input event model

No arbitrary executable command.

Typed events:

```text
PointerMove
PointerButtonDown
PointerButtonUp
Wheel
KeyDown
KeyUp
TextInput
SpecialKeyChord
```

Each event includes:
- session ID;
- sequence number;
- timestamp;
- display transform/version.

---

# 3. Mouse modes

## Direct touch
Tap coordinate maps directly to desktop coordinate.

Good for:
- large buttons;
- touch-like use.

## Trackpad
Phone surface acts as relative mouse pad.

Good for:
- precise desktop UI.

Both modes are useful.

---

# 4. Gestures

Suggested:

```text
Tap               -> left click
Double tap        -> double click
Long press        -> right click
Drag               -> left-button drag
Two-finger drag   -> wheel scroll
Pinch              -> client viewport zoom, not Ctrl+wheel by default
```

Gestures must be configurable after usability testing.

---

# 5. Keyboard

Android keyboard input:
- text;
- Enter;
- Backspace;
- arrows.

Special panel:
- Ctrl
- Alt
- Shift
- Esc
- Tab
- function keys
- Win key where appropriate.

Secure Attention Sequence such as Ctrl+Alt+Delete is not emulated through unsafe tricks.

---

# 6. Windows injection

Initial candidate:
- Windows `SendInput` for ordinary interactive desktop input.

Limitations and integrity-level behavior must be tested.

Input injection must not be elevated solely to control higher-integrity windows.

---

# 7. Coordinate mapping

Handle:
- DPI scaling;
- monitor resolution;
- orientation;
- client crop/zoom;
- desktop resize.

Mapping version accompanies input events to avoid clicking wrong coordinates after resize.

---

# 8. Input safety

On transport reconnect:
- release any logically-held keys/buttons;
- reset modifiers;
- discard stale queued events.

Never replay seconds of old mouse clicks after network recovery.

---

# 9. Application launch

Semantic launcher optional:

```text
LaunchRegisteredApp(appId)
```

Desktop controls allowed app registry.

Phone cannot send:
```text
C:\whatever\malware.exe
```

as arbitrary path.

---

# 10. Application close

Prefer:
- normal remote UI;
- graceful registered-app close action.

No `taskkill /F` generic endpoint in MVP.

---

# 11. Reference

https://learn.microsoft.com/windows/win32/api/winuser/nf-winuser-sendinput
