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

```
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

```
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

P0 gerçekten tamamlandıktan sonra **yalnızca kullanıcı** `ACTIVE_PART = P1` kararını verir. AI kendi başına part geçişi yapamaz.

---

## 5. P0 — Truth Pass & Baseline

### Amaç
Mevcut repository'nin gerçekte ne durumda olduğunu kesinleştirmek. P0 yeni özellik geliştirme aşaması değildir.

P0'ın görevi:
* `gerçek kod` vs `placeholder` vs `simulation` vs `experimental` vs `production-capable` ayrımını yapmaktır.

### Kontrol Edilen Sistemler ve Sınıflandırma:
1. **Call Capture:** `UNVERIFIED / FEASIBILITY` (Android 9+ userspace bloklu, hard capability gate aktif)
2. **Ambient MIC:** `KEEP / HARDENED` (Gerçek 44.1kHz AAC MediaRecorder, ONE_SIDED memo)
3. **Media Extractor & Downloader:** `KEEP / PARTIAL` (Doğrudan HTTP streamler aktif, platform scraping doğrulanacak)
4. **Library & Playback:** `KEEP / HARDENED` (Gerçek Media3 ExoPlayer & MediaPlayer, Room DB)
5. **Transcription:** `LAB_ONLY / UNLINKED` (Native C++ inference motoru bağlanana kadar dürüstçe ENGINE_UNAVAILABLE)
6. **Power Manager:** `KEEP / FEASIBILITY` (BroadcastReceiver tabanlı termal/pil dinleyici)
7. **Security / Vault:** `KEEP / HARDENED` (Android Keystore AES-256-GCM donanım anahtarı)
8. **Desktop Bridge & Remote Dev:** `LAB_ONLY / EXPERIMENTAL` (LAN daemon bağlantısı bekleyen deneysel istemci)
9. **Remote Desktop LAN / Internet:** `LAB_ONLY / EXPERIMENTAL` (Deneysel viewport prototipi)

---

## 6. Exit Gate Formatı

Her Part sonunda şu rapor zorunludur:

```
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
