# 10 — Storage, Security and Privacy

## 1. Storage classes

### Canonical private archive
- call recordings;
- downloaded media;
- transcripts.

### Derived
- transcription audio;
- thumbnails;
- waveforms;
- converted variants.

### Staging
- partial downloads;
- merge temp;
- unfinished recording.

---

# 2. Storage strategy

Default:
- app-private storage for archive.

Optional export:
- SAF/MediaStore user-selected destination.

Do not require `MANAGE_EXTERNAL_STORAGE`.

---

# 3. Encryption

Call recordings and transcripts are highly sensitive.

Preferred architecture:
- Android Keystore protected key hierarchy;
- authenticated encryption;
- per-file data keys where appropriate.

Downloaded public videos may use same archive protection for consistency, but decision belongs in crypto ADR.

---

# 4. Download staging and encryption

If downloader/postprocessor requires plaintext staging:
- app-private only;
- random names;
- startup cleanup;
- exclude from backups;
- no other app access.

After final validation:
- commit according to archive encryption policy.

---

# 5. Secrets

MVP should not need social-platform account credentials.

Never store:
- browser passwords;
- app passwords;
- raw cookies from other apps.

If authenticated downloading is later approved:
- explicit user authorization;
- encrypted credential store;
- least scope;
- revocation UI;
- platform-specific ADR.

---

# 6. Backup

Default:
- recordings/media/transcripts excluded from automatic cloud backup unless explicitly designed.

Reason:
- large size;
- sensitive calls;
- user expectations.

---

# 7. Logs

No:
- transcript body;
- contact names;
- full phone numbers;
- cookies;
- signed CDN URLs;
- authorization headers.

Use job/item UUIDs.

---

# 8. Network privacy

Downloader may contact:
- source platform domains;
- domains resolved by the approved extractor for media delivery.

It must not send user archive metadata to an analytics backend.

No analytics/ad SDK in MVP.

---

# 9. Delete

Deletion cascade includes:
- canonical local media;
- transcript;
- derivatives;
- temp/staging;
- local metadata.

Do not delete remote platform content.

---

# 10. Database

Suggested tables:

```text
archive_items
source_refs
media_assets
download_probes
download_jobs
call_sessions
capture_attempts
transcripts
transcript_segments
engine_versions
```

No audio/video blobs in SQLite.
