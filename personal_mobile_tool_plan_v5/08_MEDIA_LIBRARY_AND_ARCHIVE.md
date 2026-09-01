# 08 — Media Library and Archive

## 1. Library purpose

One place for:

- call recordings;
- downloaded videos;
- downloaded audio;
- transcripts.

Not a full file manager.

---

# 2. Main views

```text
ALL
CALLS
VIDEOS
AUDIO
TRANSCRIPTS
```

Filters are views over one archive, not separate storage silos.

---

# 3. Item card

Example video:

```text
[thumbnail]
Title
YouTube · 1080p · 12:43
Downloaded 1 Sep
Transcript available
```

Example call:

```text
Ahmet
Incoming call · 08:12
Recording verified
Transcript not created
```

---

# 4. Item detail

Shared sections:

- source;
- media info;
- playback;
- transcript;
- storage info;
- delete/export.

URL item additionally:
- Open original source
- original URL
- platform
- selected download format

Call item:
- call direction/date
- recording health

---

# 5. Search

MVP:

- title;
- uploader;
- phone/contact label;
- source platform;
- transcript text;
- date.

No vector search/RAG in MVP.

---

# 6. Duplicate handling

URL media:
- prefer source platform + remote media ID when available;
- also hash canonical downloaded file.

If same source is downloaded in different formats:
- may create separate variants under same logical source item if product UX supports it.

Do not silently overwrite.

---

# 7. Variant model

A media source may have:

```text
Source item
  |- 1080p MP4
  |- audio M4A
  |- transcript
```

MVP may simplify to one primary downloaded variant per download action, but schema must not prevent multiple variants.

---

# 8. Export

Explicit action:

- save/share media;
- export transcript TXT/MD/SRT later;
- no automatic cloud share.

---

# 9. Delete semantics

User chooses:

```text
Delete archive item
```

Removes:
- canonical local assets;
- derived audio;
- transcript;
- thumbnail cache;
- job remnants.

It does not delete the original online post/content.

---

# 10. Storage dashboard

Show:
- calls size;
- video size;
- audio size;
- transcript size;
- temp/cache size.

Never auto-delete canonical media merely because cache pressure is high.
