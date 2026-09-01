# 03 — Shared Domain Model

## 1. ArchiveItem

The top-level user-visible entity.

```text
ArchiveItem
- id: UUID
- type:
    CALL
    DOWNLOADED_MEDIA
    LOCAL_MEDIA
- title
- createdAt
- sourceRef
- primaryAssetId
- transcriptId?
- status
```

Avoid creating an over-general “everything object.”

---

# 2. SourceRef

## CallSourceRef

```text
callSessionId
direction
endpoint
contact snapshot?
startedAt
endedAt
```

## UrlSourceRef

```text
originalUrl
canonicalUrl?
platform
extractorId?
remoteMediaId?
uploader?
publishedAt?
sourceTitle?
```

## LocalFileSourceRef

Future.

---

# 3. MediaAsset

```text
MediaAsset
- id
- archiveItemId
- role:
    CANONICAL
    AUDIO_DERIVATIVE
    PLAYBACK_DERIVATIVE
    THUMBNAIL
    SUBTITLE
- mediaType
- container
- videoCodec?
- audioCodec?
- width?
- height?
- fps?
- bitrate?
- durationMs?
- sizeBytes
- hash
- path
- validationStatus
```

---

# 4. DownloadProbe

A probe is ephemeral/cacheable information.

```text
DownloadProbe
- probeId
- inputUrl
- platform
- title
- duration
- thumbnailUrl?
- formats[]
- requiresAuth
- drmDetected
- extractorVersion
- expiresAt
```

Do not treat stale probe data as guaranteed downloadable.

---

# 5. MediaFormatOption

Normalized internal model:

```text
id
kind:
  VIDEO_AUDIO
  VIDEO_ONLY
  AUDIO_ONLY

container
resolution?
fps?
videoCodec?
audioCodec?
approxSize?
bitrate?
sourceFormatId
requiresMerge
isOriginal
```

UI never displays raw extractor format objects.

---

# 6. DownloadJob

```text
id
archiveDraftId
probeId
selectedFormat
status
bytesDownloaded
totalBytes?
progress?
createdAt
updatedAt
errorCode?
```

---

# 7. Transcript

Same shared model for calls and media.

```text
Transcript
- id
- archiveItemId
- sourceAssetId
- engine
- modelVersion
- language
- status
- createdAt
- completedAt

TranscriptSegment
- ordinal
- startMs
- endMs
- speakerRole?
- text
```

Speaker role is optional for ordinary videos.
