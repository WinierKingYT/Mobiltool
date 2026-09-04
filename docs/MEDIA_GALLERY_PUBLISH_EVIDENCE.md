# MEDIA GALLERY PUBLISH EVIDENCE

```text
STATUS = IN_PROGRESS (Software implementation & unit tests verified; Awaiting physical Samsung Galaxy S22 device qualification)
```

---

## 1. Scope & Architecture Summary

* **Feature**: Auto-publish downloaded and validated media files from internal app storage (`context.filesDir/media`) to shared Android `MediaStore` collections so that downloads are visible and playable in Samsung Gallery, Google Photos, and system media players.
* **Storage Distinction**:
  - **Canonical In-App Copy**: `context.filesDir/media/media_{id}.{ext}` (App-owned private storage, canonical source of truth for P3 playback, Library vault, and integrity inspection; deleted on app uninstall).
  - **User-Facing MediaStore Copy**: Shared Android storage (`Movies/Mobiltool/` for video, `Music/Mobiltool/` for audio; persistent across app uninstalls).
* **Zero Room Schema Change**: Room DB schema version remains strictly at `1`.
* **Zero Broad Permissions**: Uses standard Scoped Storage `MediaStore` APIs without requesting broad storage permissions (`MANAGE_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`).

---

## 2. MediaStore Write Sequence

1. **Pre-flight Validation**: Verifies canonical source file exists, is regular, non-empty, and NOT an incomplete staging file (`.part` or `.tmp`).
2. **Display Name Sanitization**: Cleans illegal filesystem characters (`[\\/:*?"<>|]`), trims whitespace, limits length to <= 180 characters, and appends real format extension.
3. **MIME Resolution**: Decoupled from generic fallbacks; strictly resolves MIME according to format extension and media kind (e.g. `.mp4` -> `video/mp4` or `audio/mp4`, `.webm` -> `video/webm`, `.mp3` -> `audio/mpeg`, `.m4a` -> `audio/mp4`, `.ogg` -> `audio/ogg`, `.wav` -> `audio/wav`, `.flac` -> `audio/flac`).
4. **MediaStore Row Insertion**:
   - Video: `MediaStore.Video.Media.EXTERNAL_CONTENT_URI` / `getContentUri(VOLUME_EXTERNAL_PRIMARY)` -> `RELATIVE_PATH = "Movies/Mobiltool"`
   - Audio: `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` / `getContentUri(VOLUME_EXTERNAL_PRIMARY)` -> `RELATIVE_PATH = "Music/Mobiltool"`
   - `IS_PENDING = 1` set upon insertion on API 29+.
5. **Stream Copy**: Canonical file copied via `ContentResolver.openOutputStream(uri, "w")`, flushed, and closed.
6. **Publication Finalization**: `IS_PENDING = 0` updated upon successful copy completion.
7. **Failure Recovery**: On any stream failure, the pending MediaStore row is deleted immediately via `ContentResolver.delete(uri, null, null)`, returning `MediaStorePublishResult.Failed` without deleting or corrupting the canonical internal file.

---

## 3. Automated Verification

* **Unit Tests**:
  - `MediaStorePublisherTest`: Display name sanitization, illegal character stripping, length bounding, video & audio MIME mapping, missing file fail-closed, empty file fail-closed, `.part` staging rejection, `.tmp` staging rejection, unsupported extension rejection.
  - `MediaIntakeViewModelTest`: Initial idle state, URL change reset, successful download triggers MediaStore publication, publish success updates state to `SAVED`, publish failure updates state to `FAILED` while preserving internal download and Room database record.
* **Build Verification**: `.\gradlew.bat clean assembleDebug` -> **PASS (BUILD SUCCESSFUL)**
* **Full Test Suite**: `.\gradlew.bat test` -> **PASS (219 actionable tasks, 0 failures)**

---

## 4. Physical Device Qualification Gate

* **Target Device**: Samsung Galaxy S22 (`SM-S901E`, Android 16 / SDK 36)
* **Status**: `READY_FOR_USER_QUALIFICATION` (Awaiting physical Samsung Galaxy S22 connection and user gallery verification)
