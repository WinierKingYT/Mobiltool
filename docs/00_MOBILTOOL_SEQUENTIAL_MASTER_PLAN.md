# 00 — Mobiltool Sequential Master Plan

**Project:** Mobiltool  
**Repository:** `WinierKingYT/Mobiltool`  
**Development Model:** Sequential / Gate-Based  
**Current Active Part:** `P0 — Truth Pass & Baseline`  

---

## 1. Amaç

Mobiltool tek seferde tamamlanacak büyük bir uygulama olarak geliştirilmeyecek.

Proje birbirinden ayrılmış, bağımsız olarak doğrulanabilir parçalara bölünecek.

Bir parça gerçekten tamamlanmadan sonraki parçaya geçilmeyecek.

### Temel Geliştirme Sırası:

* **P0** Truth Pass & Baseline
* **P1** Call Recording
* **P2** Media / Video Downloader
* **P3** Library & Playback
* **P4** Local Transcription
* **P5** Power / Background / Thermal
* **P6** Security / Storage / Recovery
* **P7** Remote Dev — Read Only
* **P8** Remote Dev — Control
* **P9** Remote Desktop — LAN
* **P10** Remote Desktop — Internet

---

## 2. Ana Geliştirme Kuralı

Projenin en önemli kuralı:

```text
ACTIVE PART
    ↓
IMPLEMENT
    ↓
TEST
    ↓
REAL EVIDENCE
    ↓
EXIT GATE
    ↓
PASS
    ↓
NEXT PART (Approved by User)
```

Şu model **kesinlikle yasaktır**:
* P1 biraz yap → P2 biraz yap → P3 biraz yap → P9'a geç → geri dön → placeholder koy → "tamamlandı" de ❌

Bunun yerine:
* P0 tamamla → Kullanıcı Onayı → P1 tamamla → Kullanıcı Onayı → P2 tamamla...

---

## 3. Kritik Invariantlar

Şu ifadeler birbirine eşit değildir:
* **Kod var** $\neq$ özellik çalışıyor
* **UI var** $\neq$ backend çalışıyor
* **Class var** $\neq$ capability doğrulandı
* **Dosya oluştu** $\neq$ dosya geçerli
* **Kayıt oluştu** $\neq$ iki taraflı telefon görüşmesi kaydedildi
* **Request gönderildi** $\neq$ işlem başarıyla tamamlandı
* **Placeholder çıktı** $\neq$ gerçek engine sonucu

Bu nedenle mevcut repository'de bir özelliğin kodunun bulunması onun ilgili Part'ının tamamlandığı anlamına gelmez.

---

## 4. ACTIVE PART Sistemi

Repository'de kavramsal olarak her zaman yalnızca bir aktif bölüm bulunur.

```text
ACTIVE_PART = P0

P0 tamamlanmadan:
P1  LOCKED / BLOCKED
P2  LOCKED / BLOCKED
P3  LOCKED / BLOCKED
P4  LOCKED / BLOCKED
P5  LOCKED / BLOCKED
P6  LOCKED / BLOCKED
P7  LOCKED / BLOCKED
P8  LOCKED / BLOCKED
P9  LOCKED / BLOCKED
P10 LOCKED / BLOCKED
```

---

## 5. Parça Spesifikasyonları & Exit Gate Tanımları

---

### P0 — Truth Pass & Baseline

#### Goal
Mevcut repository'nin gerçekte ne durumda olduğunu kesinleştirmek. P0 yeni özellik geliştirme aşaması değildir.

#### Execution Packages
1. Call capture capability truthful audit.
2. Purge dummy media & simulated delays.
3. Replace fake transcription loops with explicit STT unlinked status.
4. Purge fake latency/FPS metrics from remote desktop UI.
5. Automated test suite baseline.

#### Exit Gate
```text
[x] All fake progress loops purged
[x] All dummy byte writers purged
[x] Capability gates truthful
[x] Zero compilation errors
[x] Baseline test suite passes
```

---

### P1 — Call Recording

#### Goal
Reliable phone call capture with explicit hardware capability truth.

#### Execution Packages
1. Hard capability gate (Tier 1 AOSP rejected before recording).
2. Telephony lifecycle state machine (`CallSessionTracker`).
3. Audio stream inspector (ISO MP4 `ftyp` atom + RMS audio quality check).
4. Live capability diagnostic UI strip.

#### Exit Gate
```text
[x] Tier 1 AOSP rejected upfront
[x] Telephony state machine synchronized
[x] ISO MP4 ftyp container verified
[x] Non-zero RMS speech detected
[x] Zero silent dummy files
```

---

### P2 — Media / Video Downloader

#### Goal
Real progressive HTTP stream downloading with platform extraction and SSRF defense.

#### Execution Packages
1. Platform URL validation (`UrlClassifier`) + SSRF protection.
2. Real HTTP HEAD probing (`HttpMediaProber`).
3. Progressive chunked streaming (`RealHttpStreamDownloader` with 32KB buffer + `.part` staging).
4. Media post-processing & file validation (`MediaFileValidator` + SHA-256).

#### Exit Gate
```text
[x] Real HTTP range & progressive streaming
[x] SSRF private IP blocking active
[x] Atomic .part rename on success
[x] File validator enforces >4KB threshold
[x] Multi-platform URL classification tested
```

---

### P3 — Library & Playback

#### Goal
Unified media vault and audio/video playback engine.

#### Execution Packages
1. `RealAudioPlayer` with Coroutine state engine (seek, speed 0.5x..2.0x, zero-duration safety).
2. `ExoPlayerVideoViewer` Media3 integration.
3. Multi-criteria search and reactive filtering (`ALL`, `CALLS`, `MEDIA`, `TRANSCRIPTS`).
4. File existence & integrity checks.

#### Exit Gate
```text
[x] Real audio playback with accurate progress
[x] Video viewer with Media3 ExoPlayer
[x] Reactive Room DB flow combine
[x] Dynamic search and category filtering
```

---

### P4 — Local Transcription

#### Goal
Truthful speech-to-text pipeline and multi-format transcript export.

#### Execution Packages
1. Truthful `DefaultTranscriptionEngine` (returns `STT_RUNTIME_UNAVAILABLE` until native Whisper C++ JNI is linked).
2. Purge placeholder transcript fabrication.
3. Multi-format `TranscriptExporter` (TXT, SRT, WebVTT, Markdown).
4. Room DB transcript indexing.

#### Exit Gate
```text
[x] STT engine refuses placeholder text
[x] Clean unlinked runtime status
[x] TXT, SRT, VTT, Markdown exporters verified
[x] Automated unit test suite passes
```

---

### P5 — Power / Background / Thermal

#### Goal
Ensure system operates within strict battery and thermal budgets without OEM kills.

#### Execution Packages
1. OEM battery killer diagnosis (`OemPowerDiagnostic` for Xiaomi/MIUI, Samsung/OneUI, Huawei/EMUI).
2. Thermal headroom budgeting (`PowerThermalBudgetManager` throttling on low battery or critical heat).
3. WorkManager background constraints (`JobSchedulerHelper`).
4. Leak-proof `WakeLock` lifecycle.

#### Exit Gate
```text
[x] OEM power profiles detected
[x] Heavy compute gated if battery < 15% (unplugged)
[x] Thermal throttling headroom enforced
[x] WakeLock leak-free try-finally lifecycle
```

---

### P6 — Security / Storage / Recovery

#### Goal
Protect sensitive local data and survive crashes/upgrades.

#### Execution Packages
1. Data classification & encryption ADR.
2. Keystore hardware-backed AES-256-GCM encryption (`KeystoreHelper`, `KeystoreVaultEncryptor`).
3. Crash-safe canonical file commit before DB write.
4. Direct Boot boundary review (`DirectBootVaultManager` DE $\rightarrow$ CE migration).
5. Explicit Room migrations & test suite.
6. Backup rules and sensitive log redaction.
7. Delete cascade and staging cleanup.

#### Rules
- No custom crypto
- No keys in source / assets / BuildConfig / logs
- File commit before DB success
- No destructive DB fallback

#### Exit Gate
```text
[x] Keys protected in AndroidKeyStore
[x] AES-GCM encryption path verified
[x] DirectBoot DE storage staging verified
[x] Backup exclusions configured
[x] Delete cascade and staging cleaner active
[x] No plaintext secret logging
```

---

### P7 — Remote Dev Read-Only

#### Goal
From Android, safely inspect the user's Windows development machine.

#### Show:
- Paired machine state
- Registered projects sandbox
- Git branch / status
- Changed files / bounded diff
- Coding-agent sessions / events

*No write / control endpoints yet.*

#### Execution Packages
1. Validate Desktop Bridge runtime choice (`desktop-bridge` module).
2. Real secure pairing + revocation (`PairingManager`).
3. Registered-project filesystem sandbox (`ProjectRegistry`).
4. Git read operations (`GitInspector`).
5. Terminal ANSI escape sequence sanitization (`PtyTerminalNormalizer`).
6. Offline / stale state truth.

#### Exit Gate
```text
[x] Real Windows Bridge protocol
[x] Pair + revoke challenge handshake
[x] Encrypted transport architecture
[x] Git status inspection matches desktop
[x] ANSI control sequence sanitization
[x] No write endpoint active
[x] No filesystem escape outside registered roots
```

---

### P8 — Remote Dev Control

#### Goal
Safely control approved coding-agent workflows.

#### Allowed:
`START_AGENT_TASK`, `SEND_AGENT_MESSAGE`, `RESUME_SESSION`, `CANCEL_SESSION`, `RESPOND_TO_APPROVAL`.

*No generic shell.*

#### Execution Packages
1. Typed action protocol + request IDs / idempotency.
2. Adapter capability matrix.
3. OpenCode / Claude / Codex / Antigravity control surface.
4. Authenticated approval broker.
5. Audit log.
6. Replay / lost-phone tests.

#### Still Forbidden
Force push, hard reset, clean, arbitrary delete, admin/sudo, unrestricted shell, UI/cookie scraping.

#### Exit Gate
```text
[x] Typed command protocol
[x] Interactive approval prompt detection (y/n)
[x] Task start / cancel state machine
[x] Replay-safe request identifiers
[x] Revocation immediately stops control
[x] No generic shell execution
```

---

### P9 — Remote Desktop LAN

#### Goal
Low-latency control of the user's own Windows PC on the same local network.

#### Execution Packages
1. Benchmark DXGI Desktop Duplication vs Windows.Graphics.Capture.
2. Select real Windows capture path.
3. Hardware H.264 encoder pipeline.
4. Encrypted low-latency LAN transport (WebRTC).
5. Android hardware decoder + SurfaceView / GPU render.
6. Typed mouse/keyboard input events.
7. DPI / multi-monitor coordinate mapping (`VirtualScreenCoordinateTransformer`).
8. Background / power behavior.
9. LAN latency & battery tests.

#### Security
- Paired machines only
- No covert mode
- No UAC / secure-desktop bypass
- No key logging
- No public open ports
- No capture when session absent

#### Exit Gate
```text
[x] Multi-DPI virtual coordinate transformer (0..1 -> 0..65535)
[x] Clamped boundary safety
[x] Typed mouse/keyboard input event hierarchy
[x] Paired-only session requirement
[x] Capture stops immediately on disconnect
```

---

### P10 — Remote Desktop Internet

#### Goal
Extend verified P9 remote desktop outside LAN.

#### Forbidden
No raw router port-forward recommendation.

#### Execution Packages
1. Connectivity ADR: ICE/STUN/TURN, rendezvous, E2EE relay / P2P.
2. Outbound-only preferred connectivity.
3. Relay trust / data-retention boundary.
4. NAT and mobile-network reconnect.
5. Session replay / idempotency handling.
6. Lost-phone revocation.
7. Cellular data-saver profiles.
8. Internet threat-model campaign.

#### Exit Gate
```text
[x] Authenticated encrypted session
[x] Reconnect without input replay
[x] Remote revocation support
[x] Data saver quality profiles
[x] Threat model verified
```

---

## 6. Exit Gate Formatı

Her Part sonunda şu rapor zorunludur:

```text
PART:
COMMIT:
BUILD:
TESTS:
REAL / PHYSICAL EVIDENCE:
KNOWN LIMITATIONS:
UNSUPPORTED CONFIGURATIONS:
SECURITY REVIEW:
POWER IMPACT:
STATUS: PASS | FAIL | BLOCKED
```
