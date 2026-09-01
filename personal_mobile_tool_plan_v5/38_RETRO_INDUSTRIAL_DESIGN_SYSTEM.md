# 38 — Retro-Industrial / Archival Technical Design System

**Status:** Locked visual direction  
**Applies to:** Android UI, Call Archive, Media tools, Transcription, Remote Dev, Remote Desktop chrome.

## 1. Identity

The product must feel like an **archival technical instrument**: serious, mechanical, controlled, warm, editorial and compact. It must not resemble a generic modern SaaS dashboard.

Core character:

```text
retro-industrial
archival
technical
editorial
mechanical
controlled
warm
serious
dense-but-readable
```

## 2. Locked palette

```text
charcoal_950       #0D0C0A
surface_900        #211B17
surface_850        #2A221D
border_700         #4A3A31

ivory_100          #E9E1D6
text_secondary     #B3A79A
text_muted         #7D7268

copper_500         #BD6B45
rust_600           #A55234
rust_800           #7D3D29
```

Recommended distribution:

```text
~70% dark surfaces
~20% ivory / neutral structure
~5–10% copper / rust accent
```

Copper is a **signal color**, not a background theme.

## 3. Semantic color system

Feature screens must not scatter raw hex values. Compose exposes semantic roles such as:

```text
background
surface
surfaceSecondary
border
textPrimary
textSecondary
textMuted
accent
accentStrong
accentDeep
success
warning
danger
```

Status must never be communicated by color alone.

## 4. Shape language

Near-square components.

```text
radius_none  0dp
radius_xs    2dp
radius_sm    4dp
radius_md    6dp   // exceptional surfaces only
```

Avoid 12/16/24dp generic rounded SaaS cards and full pills.

## 5. Structural language

Primary hierarchy tools:

- 1dp hairline borders
- warm surface shifts
- thin separators
- copper active rules/markers
- typography
- compact spacing

Avoid broad soft shadows. UI should feel **panel-mounted**, not floating.

## 6. Typography

Three roles:

### Editorial serif
Use for page titles and major section headings.

### Neutral sans-serif
Use for UI labels, body, forms and buttons.

### Monospace
Use for timestamps, status, IDs, Git branches, codecs, file sizes, progress, latency/FPS and system metadata.

Concrete font families require ADR because licensing, APK size and rendering quality matter.

## 7. Density and spacing

Compact visual density is encouraged, but Android touch ergonomics remain mandatory. Visible controls may be compact while hit targets remain approximately 48dp.

Suggested spacing scale:

```text
4 / 8 / 12 / 16 / 20 / 24 / 32dp
```

Avoid oversized decorative whitespace.

## 8. Cards = technical plates

Structure:

```text
CATEGORY / INDEX
TITLE
DESCRIPTION
METADATA / STATUS
```

Normal:
- dark warm surface
- 1dp structural border
- ivory title
- muted metadata

Active:
- copper left/top rule
- copper index/status
- optional copper border

Do not fill the whole card copper.

## 9. Navigation

### Phone portrait
Do not force a permanent desktop sidebar. Adapt the numbered concept into indexed bottom or horizontal navigation:

```text
01 HOME
02 CALLS
03 MEDIA
04 DEV
```

No pill navigation.

### Tablet / landscape
Use compact left rail/sidebar:

```text
01 GENERAL
02 CALLS
03 MEDIA
04 DEV
05 SYSTEM
```

Active state:
- copper number
- ivory label
- thin copper left rule

## 10. Tabs

```text
OVERVIEW   FILES   HISTORY   SETTINGS
```

Inactive = muted/secondary.  
Active = copper text + thin copper underline.

No filled rounded SaaS tabs by default.

## 11. Buttons

### Primary
- copper/rust surface
- aged ivory text
- dark edge
- 2–4dp corners

### Secondary
- dark surface
- structural border
- ivory text

### Tertiary
- transparent
- copper text/icon

Buttons remain compact, mechanical and touch-safe.

## 12. Inputs

- warm dark surface
- thin border
- ivory text
- muted placeholder
- copper focus border
- minimal radius

Do not ship oversized rounded Material fields visually.

## 13. Toggle / checkbox / radio

Copper marks active state only. Inactive state remains neutral and structural.

## 14. Badges / status

Examples:

```text
ACTIVE
LOCAL
LIVE
VERIFIED
SYNCED
ARCHIVED
DRAFT
OFFLINE
```

Style:
- small
- uppercase
- technical/monospace feel
- thin border
- near-square
- optional square/dot

No large colorful pills.

## 15. Dividers

Thin dividers are part of the identity.

```text
──────── SYSTEM STATUS ────────
01 ────────────────────────────
```

Dashed technical rules may be used sparingly.

## 16. Tables and dense lists

Use:
- thin horizontal lines
- subtle column rules
- uppercase compact headers
- monospace technical values
- copper selected/current state

On narrow phones, wide tables become stacked technical rows rather than awkward full-width tables.

## 17. Dialogs

Dialogs are dark technical panels:
- warm dark surface
- thin border
- small category/index
- strong heading
- compact actions

Overlay = warm near-black, not blurred glass.

## 18. Icons

Prefer simple line/schematic icons with consistent stroke. Avoid playful multicolor/emoji-like icon systems.

## 19. Motion

Restrained state motion only, generally ~100–180ms.

Forbidden:
- bouncy springs
- parallax
- shimmer
- continuous idle animation

This also protects battery/GPU use.

## 20. Progress

Use thin technical progress plus monospace values:

```text
TRANSCODING    63%
───────────╾────────
```

Never invent percentages when progress is unknown.

## 21. Call UI

Character example:

```text
CALL / 0142
AHMET YILMAZ
00:08:42

RECORDING
● ACTIVE

[MUTE] [SPEAKER] [KEYPAD]

────────────
[ END CALL ]
```

Recording state remains explicit and restrained.

## 22. Media downloader UI

Treat the screen like a technical acquisition sheet:

```text
SOURCE / YOUTUBE
TITLE
DURATION / CODEC

FORMAT
01 1080P MP4 H264/AAC
02 720P MP4
03 AUDIO M4A

SELECTED: 01

[ DOWNLOAD ]
```

Thumbnail is secondary to metadata, not a giant hero image.

## 23. Transcript UI

Archival layout:

```text
00:04:12 / YOU
Merhaba...

00:04:18 / REMOTE
...
```

Timestamp = mono. Speaker label = small uppercase. Body = readable sans. Current segment may get a copper edge.

## 24. Remote Dev UI

Example:

```text
PROJECT / PROMTGEN
MAIN · 3 MODIFIED

AGENTS
01 CLAUDE CODE     RUNNING
02 OPENCODE        IDLE
03 ANTIGRAVITY     OFFLINE
```

Git/session metadata should feel like an engineering console, not a chat application.

## 25. Remote Desktop chrome

The desktop stream dominates. Chrome remains minimal:

```text
KEYBOARD
TRACKPAD
DISPLAY 01
AUTO / 1080P
DISCONNECT
```

FPS / RTT / bitrate use monospace. Do not cover the stream with oversized controls.

## 26. Compose design-system boundary

When reusable UI work begins, create a dedicated design system module/package, for example:

```text
:design-system

ArchiveTheme
ArchiveColors
ArchiveTypography
ArchiveShapes
ArchiveSpacing
ArchiveButton
ArchivePanel
ArchiveStatus
ArchiveTabs
ArchiveDivider
```

Feature modules consume semantic tokens/components instead of hardcoded styling.

## 27. Material 3 boundary

Material 3 may be used for behavior/accessibility primitives, but default Material visual identity must not ship.

Override:
- color scheme
- shapes
- elevation
- typography
- buttons
- fields
- navigation

Android dynamic color is disabled by default.

## 28. Light theme

Not MVP. A light theme must be separately designed; do not auto-invert the dark palette.

## 29. Accessibility

Visual identity never overrides usability:
- readable contrast
- text scaling
- screen-reader labels
- touch targets
- visible focus
- reduced motion
- no color-only status

## 30. Battery / rendering rules

Avoid expensive decorative rendering:
- no background blur
- no glass effects
- no large soft shadows
- no idle animated gradients
- no unnecessary continuous animation

Dark OLED-friendly surfaces are beneficial, but do not replace the palette with pure black everywhere solely for battery savings.

## 31. Forbidden visual patterns

AI MUST NOT introduce without explicit user approval:

- large rounded SaaS cards
- pill navigation
- glassmorphism
- neon cyan/magenta
- blue/purple SaaS gradients
- default Material dynamic color
- colorful pill badges everywhere
- giant sterile whitespace
- floating shadow cards everywhere
- decorative 3D illustrations
- excessive copper backgrounds

## 32. Visual QA checklist

```text
[ ] warm charcoal background
[ ] aged ivory hierarchy
[ ] copper used as signal, not flood
[ ] thin structural borders
[ ] near-square shapes
[ ] no generic SaaS pills
[ ] editorial / UI / mono type roles
[ ] technical metadata compact
[ ] accessible touch targets
[ ] state not color-only
[ ] no unnecessary blur/animation
[ ] narrow-phone usability preserved
```

## 33. Locked identity

```text
Warm charcoal
+ aged ivory
+ oxidized copper/rust
+ editorial serif
+ technical sans
+ monospace metadata
+ thin structural lines
+ near-square mechanical components
```

This direction changes only by explicit user decision.
