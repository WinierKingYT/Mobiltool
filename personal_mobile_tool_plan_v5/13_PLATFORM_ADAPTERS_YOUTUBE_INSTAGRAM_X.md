# 13 — Platform Adapters: YouTube, Instagram, X

## 1. Important rule

Platform support means:

> tested source classes currently work with the approved extraction path.

It does not mean “all URLs from this brand always work.”

---

# 2. Adapter abstraction

```text
PlatformAdapter
- classify(url)
- normalize(probe)
- mapErrors(raw)
- capabilityNotes
- testFixtures
```

Actual extraction may still be performed by a shared yt-dlp engine.

Platform adapter does not reimplement all extraction logic unless an ADR chooses an official API path.

---

# 3. YouTube

Initial desired cases:

- public standard video;
- public Shorts URL;
- audio-only extraction from permitted accessible media;
- format selection.

Not initially certified:
- paid content;
- members-only;
- private/unlisted requiring authorization;
- movies/DRM;
- account-only pages;
- full channels/playlists.

YouTube support must respect applicable service terms and rights.

Because YouTube extraction behavior changes, test fixtures are versioned.

---

# 4. Instagram

Initial desired cases:

- public Reel URL;
- public video post where extractor works;
- public carousel/multi-item support only after multi-asset model is implemented.

Not initially certified:
- private profiles;
- close friends;
- stories requiring login;
- disappearing content;
- account scraping.

If a public URL becomes login-gated, return `AUTH_REQUIRED`, not a bypass.

---

# 5. X / Twitter

Initial desired cases:

- public post containing video;
- public post containing animated media;
- format variants if exposed.

Not initially certified:
- private/protected accounts;
- DMs;
- account-only data;
- bulk user timeline scraping.

---

# 6. Other sites

The extractor may technically work with additional sites.

UI policy:

- generic “Other supported source” may be experimental;
- do not market/certify a platform until fixture tests exist.

---

# 7. Fixture set per platform

Each certified source class has:
- at least 10 public/test URLs where legally appropriate;
- short/long media;
- multiple resolutions;
- audio-only possibility;
- removed/unavailable fixture;
- auth-required fixture if safely testable;
- expected error class.

Avoid depending entirely on random third-party content that may disappear.
Use owned/authorized test content where possible.

---

# 8. Site-change response

When extraction breaks:

1. classify failure;
2. disable misleading one-click default if necessary;
3. update extractor dependency/app;
4. rerun fixture suite;
5. restore support.

Do not patch site HTML selectors directly in UI code.
