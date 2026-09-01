# 37 — Technical References

This file records external platform references used to shape V4.

## Android calls

### InCallService
https://developer.android.com/reference/android/telecom/InCallService

Used for:
- default dialer/call management lifecycle assumptions.

## Android background execution

### Background optimization
https://developer.android.com/topic/performance/background-optimization

### Foreground service background restrictions
https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

### Foreground service troubleshooting
https://developer.android.com/develop/background-work/services/fgs/troubleshooting

### WorkManager constraints
https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work

### Network access optimization
https://developer.android.com/develop/connectivity/network-ops/network-access-optimization

Used for:
- no always-running service principle;
- deferred work;
- charging/Wi-Fi/battery constraints;
- connection reuse.

## Android real-time decode

### MediaCodec low latency
https://developer.android.com/reference/android/media/MediaFormat

Used for:
- optional Remote Desktop low-latency hardware decoding.
- low-latency mode is not enabled when Remote Desktop is inactive.

## Windows screen capture

### Desktop Duplication
https://learn.microsoft.com/windows-hardware/drivers/display/desktop-duplication-api

### Desktop Duplication API
https://learn.microsoft.com/windows/win32/direct3ddxgi/desktop-dup-api

### Windows.Graphics.Capture
https://learn.microsoft.com/windows/apps/develop/media-authoring-processing/screen-capture

Used for:
- remote desktop frame acquisition;
- dirty/move/cursor metadata;
- GPU-friendly pipeline.

## Windows input

### SendInput
https://learn.microsoft.com/windows/win32/api/winuser/nf-winuser-sendinput

Used as:
- candidate ordinary interactive-user mouse/keyboard injection API.

These references are implementation inputs, not permission to violate higher-level product/security invariants.
