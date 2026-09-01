# 07 — Format Selection and Postprocessing

## 1. User concept

The user chooses outcomes, not raw extractor syntax.

Example choices:

```text
VIDEO
- Best available
- 2160p
- 1440p
- 1080p
- 720p
- 480p
- Original / no conversion

AUDIO
- Original audio
- M4A/AAC
- Opus
- MP3
```

Only choices actually possible from the current probe are enabled.

---

# 2. Format normalization

Extractor-specific formats normalize to `MediaFormatOption`.

Example:

```text
1080p • MP4 • H.264/AAC • 62 MB
1080p • WebM • VP9/Opus • 45 MB
Audio • M4A • 5 MB
Audio • Opus • 4 MB
```

If video and audio are separate:

```text
requiresMerge = true
```

---

# 3. Original vs converted

UI must distinguish:

- **Source/Original**: no unnecessary transcode;
- **Merged**: tracks combined without re-encoding when possible;
- **Converted**: codec/container changed.

This matters for:
- quality;
- time;
- battery;
- file size.

---

# 4. Preferred outcome presets

MVP presets:

## Video Best
Best compatible video+audio within user-selected max resolution.

## Video MP4
Prefer MP4-compatible output when available without destructive re-encoding.

## Audio Original
Best source audio, preserved container when practical.

## Audio M4A
Use source M4A/AAC when available; transcode only if user explicitly requests.

## Audio MP3
Transcode; show that conversion will occur.

---

# 5. FFmpeg/postprocessor abstraction

```kotlin
interface MediaPostProcessor {
    suspend fun inspect(...)
    fun merge(...)
    fun extractAudio(...)
    fun transcode(...)
}
```

No raw command construction outside adapter.

---

# 6. Avoid needless transcoding

Priority:

```text
remux
>
copy streams
>
transcode only when requested/required
```

Do not convert everything to MP4 automatically.

---

# 7. Transcription derivative

For speech-to-text, create a temporary/derived audio representation suitable for the chosen model.

Example:

```text
video source
 -> decode audio
 -> model-required PCM/sample rate
 -> transcription
```

This derivative:
- is not canonical;
- may be deleted after transcription;
- is tracked for cleanup.

---

# 8. Metadata

Preserve where available:

- title;
- source URL;
- source platform;
- uploader;
- publish date;
- duration;
- resolution;
- codecs;
- source extractor ID.

Do not embed metadata that leaks secrets/tokens.

---

# 9. Subtitles

Phase 2.

Potential:
- download creator-provided subtitles;
- download auto captions where platform permits;
- store separately;
- do not mislabel captions as speech-to-text transcript.

---

# 10. Acceptance

Format selection passes when:

- only valid format options shown;
- selected resolution respected;
- audio-only has no video track;
- merged output playable;
- original source not destroyed;
- cancellation cleans temp files;
- conversion is visibly labeled.
