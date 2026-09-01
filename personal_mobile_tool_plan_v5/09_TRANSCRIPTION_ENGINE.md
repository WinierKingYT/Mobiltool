# 09 — Unified Transcription Engine

## 1. Sources

One transcription engine serves:

```text
Call recording
Downloaded video
Downloaded audio
Local imported media (future)
```

No separate “YouTube transcriber” and “call transcriber” pipelines.

---

# 2. Behavior

MVP:

```text
Open item
 -> Transcribe
 -> prepare audio
 -> local model
 -> timestamped transcript
```

No automatic transcription.

---

# 3. Privacy

Default:
- completely local;
- no remote STT;
- no API upload.

A future remote provider requires user approval + ADR.

---

# 4. Model decision

Do not lock Whisper blindly.

Benchmark candidates on Turkish:

- speech recognition quality;
- telephony audio;
- social-video audio;
- numbers/prices/names;
- RAM;
- speed;
- thermals;
- binary/model size.

Likely candidates may include whisper.cpp-compatible models or equivalent local engines.

---

# 5. Audio preparation

```text
MediaAsset
 -> decoder
 -> source audio track
 -> normalization/resample only as required
 -> bounded chunks
 -> STT
```

No source mutation.

---

# 6. Long video strategy

Do not decode a two-hour video entirely into RAM.

Use:
- streaming/chunked decode;
- chunk boundaries with overlap if model requires;
- stable timestamp reconstruction.

---

# 7. Speaker handling

Call recording:
- deterministic YOU/REMOTE only if capture tracks support it.

Videos:
- no speaker labels by default.
- diarization is a separate future feature.

Never invent names from faces/uploader.

---

# 8. Transcript formats

Internal:
- segment model.

MVP display:
- readable text + timestamps.

Phase 2 export:
- TXT
- Markdown
- SRT
- VTT

---

# 9. Existing subtitles

If source provides subtitles:
- store as source captions;
- user may choose “Use captions” versus “Generate transcript” in future.

Do not merge source captions and local transcript invisibly.

---

# 10. Search indexing

Transcript can be locally indexed for keyword search.

If sensitive call transcripts are indexed, security model must cover the index.

---

# 11. Acceptance

- works offline once local model is installed;
- no source destruction;
- correct seek timestamps;
- cancel/retry;
- long file bounded-memory behavior;
- no speaker hallucination;
- model/version recorded.
