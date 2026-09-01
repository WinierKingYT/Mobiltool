# 05 — URL Intake and Media Probe

## 1. Entry points

MVP supports:

### A. Paste

```text
Download screen
 -> paste URL
 -> Inspect
```

### B. Android Share Sheet

Share a URL from browser/social app:

```text
ACTION_SEND text/plain
 -> validate URL
 -> open probe screen
```

### C. Manual text entry

No clipboard background monitoring.

The app must not read clipboard continuously.

---

# 2. URL normalization

Before probe:

- trim whitespace;
- reject non-http/https;
- preserve original URL;
- resolve known redirect only through controlled network handling;
- do not execute arbitrary JavaScript;
- do not load a hidden full browser as default extractor.

Store:
- original URL;
- normalized URL;
- final canonical URL if extractor provides it.

---

# 3. Probe pipeline

```text
URL
 |
 v
UrlClassifier
 |
 v
PolicyPrecheck
 |
 v
MediaExtractorEngine.probe()
 |
 +--> unsupported
 +--> auth required
 +--> DRM
 +--> success
 |
 v
Normalized DownloadProbe
```

---

# 4. Probe UI

Show:

- source/platform;
- title;
- thumbnail when safely available;
- duration;
- author/uploader when known;
- list of normalized format choices;
- estimated size when available;
- warnings.

No download begins until selection.

---

# 5. Probe cache

Site format URLs often expire.

Therefore:
- probe result has TTL;
- download may re-probe if stale;
- never persist a temporary media CDN URL as canonical source identity.

---

# 6. URL safety

Prevent:
- `file://`
- `content://`
- localhost/loopback misuse unless explicitly local-file feature;
- arbitrary internal-network scanning;
- URL schemes other than allowed set.

Extractor is not an SSRF toolkit.

---

# 7. Redirect limits

Bound redirect count.
Reject suspicious redirect loops.
Retain source domain history in diagnostics without storing secrets.

---

# 8. Unsupported URL UX

Differentiate:

```text
Unsupported URL
Platform temporarily broken
Login required
Content unavailable
DRM-protected
Region restricted
Network failure
```

Do not show all as “download failed.”
