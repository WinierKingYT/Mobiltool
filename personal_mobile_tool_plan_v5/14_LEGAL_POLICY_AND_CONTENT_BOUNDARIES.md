# 14 — Legal, Policy and Content Boundaries

This is an engineering boundary document, not legal advice.

## 1. Downloader purpose

The product is intended for content where the user:

- owns the content;
- has permission from the rights holder;
- is given a download right by the service;
- or is otherwise permitted by applicable law.

Technical accessibility does not establish rights.

---

# 2. Platform terms

Different services impose different contractual restrictions.

Engineering therefore must:
- avoid promising that every technically retrievable item is permitted to download;
- provide a concise rights reminder in onboarding/settings;
- avoid DRM/access-control circumvention;
- maintain a source-policy review before public distribution.

---

# 3. YouTube boundary

YouTube's terms restrict downloading/reproducing content except where the service permits it, rights holders permit it, or applicable law permits it.

Product implication:
- no “bypass YouTube restrictions” positioning;
- no DRM;
- no account theft;
- users remain responsible for rights to save content.

---

# 4. Instagram/X

Their public-content and API policies can change.

Product implication:
- use public/authorized content only;
- do not access private data without approved authorization;
- do not store stolen session credentials;
- perform terms/policy review before store release.

---

# 5. Call recording

Call recording consent laws vary.

Product implication:
- no stealth-recording mode;
- legal review per target jurisdiction before enabling automatic recording defaults;
- support disclosure/consent UX if required.

---

# 6. App-store review

Before Google Play release:
- review permissions;
- review Accessibility absence;
- review downloadable-content functionality against current Play policies;
- document lawful-use positioning;
- ensure no copyright-infringement marketing language;
- ensure no prohibited code download/self-update behavior.

A private/internal distribution build may have different constraints, but security invariants remain.

---

# 7. User-facing wording

Good:

> Download media you own or have permission to save.

Avoid:

> Download anything from anywhere.

Avoid:

> Bypass restrictions.

---

# 8. Takedown/abuse handling

If the app becomes publicly distributed:
- publish contact/policy page;
- describe what the app does and does not host;
- because MVP has no server, the app itself does not host user media;
- review obligations if future cloud features are added.
