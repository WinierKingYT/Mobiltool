# 12 — Android UI/UX

## 1. Product navigation

Recommended MVP:

```text
LIBRARY
DOWNLOAD
CALLS
SETTINGS
```

Transcript is part of item detail/search, not necessarily a permanent bottom tab.

---

# 2. Download screen

```text
Paste a link
[ https://...                  ]
[ Inspect ]

Recent:
...
```

After probe:

```text
thumbnail
title
source

Download as

VIDEO
( ) Best
( ) 1080p MP4
( ) 720p MP4
...

AUDIO
( ) Original audio
( ) M4A
( ) MP3

[ Download ]
```

---

# 3. Share-sheet flow

User shares link from Instagram/X/YouTube/browser.

Open a lightweight intake screen:
- URL;
- probe progress;
- format selection;
- Download.

Do not automatically start before showing the selected outcome unless user has configured a safe default.

---

# 4. Download queue

```text
Downloading
Title
1080p MP4
63% · 14 MB / 22 MB
[Cancel]
```

Postprocess:

```text
Merging audio + video
```

Do not fake progress percentages when unknown.
Use indeterminate state.

---

# 5. Library

Cards visually distinguish:
- Call
- Video
- Audio

Show source platform icon/label where known.

---

# 6. Transcript UI

```text
[Play]

00:04
Merhaba...

00:11
...
```

Tap timestamp/segment -> seek.

For call speaker labels:
- only show YOU/REMOTE when reliable.

---

# 7. Errors

Examples:

```text
This media is DRM-protected and isn't downloadable in this app.

This post requires an authenticated/private account.
This version doesn't support authenticated downloads.

The source site changed and this extractor could not read the media.
Try updating the app later.

Download succeeded but the file could not be validated.
The file was not added to your library.
```

---

# 8. Calls

Call UI keeps the V1 truth model.

A device can show:
```text
Call recording not supported on this device configuration
```

while downloader/transcription remain fully usable.

---

# 9. Settings

MVP:

- download directory/export behavior;
- Wi-Fi-only;
- preferred video max resolution;
- preferred audio outcome;
- storage usage;
- transcription model;
- delete model;
- privacy/data;
- diagnostics;
- call capability.

No social account login settings in MVP.

# 13. Locked visual system

All Android screens must comply with `38_RETRO_INDUSTRIAL_DESIGN_SYSTEM.md`.

Mobile adaptation rules:
- sidebar concept becomes indexed bottom/horizontal navigation on narrow phones;
- true sidebar/rail is reserved for tablet/landscape;
- compact appearance must preserve Android touch targets;
- Material 3 may provide primitives but not the visible product identity.
