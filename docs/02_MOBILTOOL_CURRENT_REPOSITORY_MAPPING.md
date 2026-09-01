# 02 — Mobiltool Current Repository Mapping

**Project:** Mobiltool  
**Repository:** `WinierKingYT/Mobiltool`  
**Purpose:** Mevcut repository’de hangi kodun hangi geliştirme parçasına ait olduğunu eşlemek ve “kod var = özellik tamam” yanılgısını engellemek.

---

# 1. Bu dosyanın amacı

Mobiltool repository’sinde şu anda yalnızca başlangıç iskeleti yok.

Birçok ileri seviye özellik için:

- class’lar,
- modüller,
- ViewModel’ler,
- UI ekranları,
- interface’ler,
- helper’lar,
- storage katmanları

zaten oluşturulmuş durumda.

Ancak temel kural:

```text
EXISTING CODE
≠
VERIFIED FEATURE
```

Bu nedenle bu belge mevcut kodu ilgili `P0 → P10` parçalarına eşler.

Her ilgili Part başladığında kod yeniden incelenip şu kategorilerden birine konacak:

```text
KEEP
HARDEN
REPLACE
LAB_ONLY
REMOVE
UNVERIFIED
```

---

# 2. Repository'nin genel yapısı

Mobiltool şu ana mimari alanlara ayrılmış durumda:

```text
app/

core-common/

core-model/

core-designsystem/

core-storage/

core-security/

core-jobs/

call-capture-api/

media-extractor-api/

transcription-api/

desktop-bridge/
```

Bu modüler ayrım genel olarak korunabilir.

Ancak bir modülün varlığı:

```text
feature production-ready
```

anlamına gelmez.

---

# 3. P0 — Truth Pass ile ilişkili mevcut kod

P0 bütün repository’yi etkiler.

Özellikle şu tür davranışlar aranacaktır:

```text
hardcoded success

fake transcript

dummy media

fake progress

fake latency

fake call

unconditional isSupported=true

simulation presented as production

experimental feature presented as complete
```

P0’ın amacı yeni feature eklemek değil:

```text
repository truthfulness
```

sağlamaktır.

---

# 4. P1 — Call Recording mevcut yapı

İlgili ana modül:

```text
call-capture-api/
```

Mevcut önemli parçalar arasında:

```text
CaptureEngine.kt

DefaultCaptureEngine.kt

AudioQualityValidator.kt
```

bulunuyor.

---

# 5. Ambient microphone yolu

App tarafında mevcut:

```text
AmbientMicRecorder.kt
```

Bu önemli bir ayrımdır.

Ambient MIC:

```text
phone microphone
```

kullanır.

Dolayısıyla:

```text
AmbientMicRecorder
≠
verified bidirectional call recorder
```

Bu component:

```text
KEEP / LAB / DIAGNOSTIC
```

olarak yararlı olabilir.

Ama production GSM call capture engine yerine kullanılamaz.

---

# 6. Call playback

Mevcut:

```text
RealAudioPlayer.kt
```

gibi playback component’leri bulunuyor.

Bunlar P1 ve P3 kapsamında doğrulanacaktır.

Kontrol edilmesi gerekenler:

```text
real file playback

seek

duration

missing file

corrupt file

app restart

resource release
```

---

# 7. Call domain modeli

`core-model` tarafında call ile ilgili domain yapıları bulunuyor.

Örneğin kavramsal olarak:

```text
CallSession

CallDirection

RecordingQuality

CaptureMode
```

gibi modeller.

P1 başladığında bunların truth model ile tam uyumu kontrol edilecek.

Özellikle:

```text
VERIFIED_BIDIRECTIONAL
```

değerinin kolayca üretilememesi gerekir.

---

# 8. Call database

`core-storage` içerisinde call tarafında:

```text
CallEntity

CallDao
```

gibi Room yapıları bulunuyor.

P1 ve P3 sırasında kontrol edilecek:

```text
schema

foreign keys

indexes

migration

file path

source identity

delete behavior
```

---

# 9. P1 mevcut durum yorumu

Şu anki sınıflandırma:

```text
Call architecture:
KEEP / HARDEN

Ambient MIC:
KEEP AS DIAGNOSTIC

Real bidirectional capture:
UNVERIFIED

Physical call evidence:
REQUIRED
```

Yani:

```text
P1 != COMPLETE
```

---

# 10. P2 — Media / Video Downloader mevcut yapı

İlgili ana modül:

```text
media-extractor-api/
```

Mevcut önemli parçalar:

```text
MediaExtractor.kt

DefaultMediaExtractor.kt

MediaFileValidator.kt

UrlClassifier.kt
```

---

# 11. Media extractor

`MediaExtractor` doğru bir abstraction yönüdür.

UI’ın:

```text
yt-dlp raw command
```

veya platform-specific extraction detaylarını bilmemesi gerekir.

İdeal sınır:

```text
UI
↓
Media domain
↓
MediaExtractor
↓
platform/extractor implementation
```

Bu mimari korunmalıdır.

---

# 12. DefaultMediaExtractor

`DefaultMediaExtractor` mevcut olabilir.

Ancak kontrol edilmesi gereken esas soru:

```text
Gerçek YouTube URL'si çalışıyor mu?

Gerçek Instagram URL'si çalışıyor mu?

Gerçek X URL'si çalışıyor mu?
```

Class’ın bulunması yeterli değildir.

Şimdiki sınıflandırma:

```text
UNVERIFIED
```

Gerçek fixture testleri P2’de yapılacaktır.

---

# 13. URL Classifier

Mevcut:

```text
UrlClassifier.kt
```

P2’de şu ayrımlar doğrulanacaktır:

```text
YOUTUBE

INSTAGRAM

X

GENERIC

UNSUPPORTED
```

Burada kritik invariant:

```text
URL recognized
≠
media downloadable
```

---

# 14. MediaFileValidator

Mevcut:

```text
MediaFileValidator.kt
```

Bu component P2 için önemlidir.

Çünkü:

```text
HTTP download finished
```

demek:

```text
valid media
```

demek değildir.

Validator en azından gelecekte şunları doğrulamalıdır:

```text
file exists

size sane

parser opens

expected tracks exist

duration sane

resolution sane

hash generated
```

---

# 15. HTTP downloader

App tarafında mevcut:

```text
RealHttpMediaDownloader.kt
```

Bu component’in gerçekten production download engine olarak yeterli olup olmadığı P2’de incelenecek.

Kontrol:

```text
progress

cancel

partial files

retry

redirect

timeouts

resume

staging

atomic commit
```

---

# 16. Media intake UI

Mevcut:

```text
MediaIntakeViewModel

media intake screens
```

gibi UI parçaları bulunuyor.

Bunlar korunabilir.

Ancak UI’ın gösterdiği:

```text
SUPPORTED
READY
DOWNLOAD COMPLETE
```

durumları gerçek backend state’lerinden gelmelidir.

---

# 17. P2 mevcut durum yorumu

```text
Extractor abstraction:
KEEP

URL classifier:
KEEP / HARDEN

Media validator:
KEEP / HARDEN

HTTP downloader:
UNVERIFIED / HARDEN

YouTube:
UNVERIFIED

Instagram:
UNVERIFIED

X:
UNVERIFIED
```

P2 gerçek fixture testlerinden önce:

```text
"supports YouTube, Instagram, X"
```

claim’i yapılmaz.

---

# 18. P3 — Library & Playback mevcut yapı

Repository’de library sistemi için temel yapılar bulunuyor.

İlgili alanlar:

```text
core-model/media/

core-storage/

app/ui/library/
```

---

# 19. Media model

Mevcut media modelleri P3’te şu canonical yapıya yaklaştırılacaktır:

```text
ArchiveItem

SourceRef

MediaAsset
```

Ama mevcut model gereksiz yere tamamen rewrite edilmemelidir.

Önce mapping yapılmalıdır.

---

# 20. Media database

Mevcut:

```text
MediaEntity

MediaDao
```

gibi Room yapıları bulunuyor.

P3 sırasında kontrol edilecek:

```text
call + media coexistence

variant model

source URL

local file

hash

duration

mime/container

delete cascade
```

---

# 21. Library UI

Mevcut:

```text
Library screen

LibraryViewModel
```

bulunuyor.

Hedef:

```text
ALL

CALLS

VIDEOS

AUDIO
```

gibi tek unified archive.

UI zaten var diye P3 tamamlanmış kabul edilmez.

---

# 22. P3 mevcut durum yorumu

```text
Library architecture:
KEEP / HARDEN

Database:
HARDEN

Playback:
UNVERIFIED

Restart reconciliation:
UNVERIFIED

Delete cascade:
UNVERIFIED

Duplicate policy:
UNVERIFIED
```

---

# 23. P4 — Transcription mevcut yapı

Ana modül:

```text
transcription-api/
```

Mevcut:

```text
TranscriptionEngine.kt

DefaultTranscriptionEngine.kt

TranscriptExporter.kt
```

Ayrıca storage/UI tarafında:

```text
TranscriptEntity

TranscriptDao

TranscriptViewModel

Transcript UI
```

gibi yapılar mevcut.

---

# 24. TranscriptionEngine

Interface bulunması iyi.

Hedef mimari:

```text
Call
Video
Audio
   ↓
TranscriptionEngine
```

olmalıdır.

Bu abstraction korunabilir.

---

# 25. DefaultTranscriptionEngine riski

Bu alan özellikle truth-pass gerektiriyor.

Kontrol edilmesi gereken:

```text
Gerçek model inference çalışıyor mu?
```

Eğer engine yalnızca:

```text
model file exists
audio exists
```

kontrol edip sonra sabit transcript üretiyorsa:

```text
PLACEHOLDER
```

sayılır.

Gerçek inference yoksa:

```text
ENGINE_UNAVAILABLE
```

dönmelidir.

---

# 26. Transcript exporter

Mevcut:

```text
TranscriptExporter.kt
```

ileride:

```text
TXT

MD

SRT

VTT
```

gibi formatlar için kullanılabilir.

Ama export P4’ün ana risk alanı değildir.

Öncelik:

```text
real STT
```

olmalıdır.

---

# 27. P4 mevcut durum yorumu

```text
Transcription API:
KEEP

Transcript storage:
KEEP / HARDEN

Transcript UI:
KEEP / HARDEN

Real local STT:
UNVERIFIED

Current engine:
TRUTH PASS REQUIRED
```

---

# 28. P5 — Power / Background / Thermal mevcut yapı

Ana modül:

```text
core-jobs/
```

Mevcut önemli parçalar:

```text
JobSchedulerHelper.kt

power/PowerThermalBudgetManager.kt

power/OemPowerDiagnostic.kt
```

---

# 29. PowerThermalBudgetManager

Bu iyi bir architectural direction’dır.

Ama sınıfın bulunması yeterli değildir.

P5’te gerçek davranış doğrulanacaktır:

```text
Does STT actually pause?

Does transcode actually defer?

Does call recording get priority?

Does Remote Desktop quality decrease?

Does thermal state really affect jobs?
```

---

# 30. Job scheduler

`JobSchedulerHelper` veya WorkManager wrapper mantığı varsa P5 sırasında şu ayrım kontrol edilmeli:

```text
event-critical work

user-active work

deferred maintenance
```

Özellikle:

```text
active call recording
```

WorkManager job’ı olmamalıdır.

---

# 31. P5 mevcut durum yorumu

```text
Power architecture:
KEEP

Resource policy:
HARDEN

Real battery evidence:
UNVERIFIED

Thermal evidence:
UNVERIFIED

8-hour idle test:
NOT VERIFIED
```

---

# 32. P6 — Security / Storage / Recovery mevcut yapı

Ana modüller:

```text
core-security/

core-storage/
```

Mevcut önemli security parçaları:

```text
KeystoreHelper.kt

KeystoreVaultEncryptor.kt

DirectBootVaultManager.kt
```

Storage/recovery tarafında:

```text
StagingCleaner.kt
```

gibi yardımcı yapılar bulunuyor.

---

# 33. Keystore

Android Keystore kullanılması doğru architectural direction’dır.

Ancak P6’da şu sorular cevaplanmalıdır:

```text
Key gerçekten non-exportable mı?

Key rotation var mı?

Hangi dosyalar encrypted?

Canonical file encryption nasıl?

DB encrypted mı / gerekli mi?

Device reset sonrası davranış ne?

Backup ile key ilişkisi ne?
```

---

# 34. Direct Boot

`DirectBootVaultManager` önemli ve riskli bir alan.

Kontrol edilmesi gereken:

```text
Telefon unlock olmadan
hangi veri erişilebilir?
```

Call recording için Direct Boot gerekebilir.

Ama convenience uğruna:

```text
sensitive data plaintext
```

yapılamaz.

---

# 35. Staging cleanup

Mevcut:

```text
StagingCleaner
```

yararlı.

Ama şunlar test edilmelidir:

```text
crash after download

crash during recording

orphan temp

old partial files

active job accidentally deleted?
```

---

# 36. P6 mevcut durum yorumu

```text
Keystore foundation:
KEEP

Encryption:
HARDEN / VERIFY

Direct Boot:
SECURITY REVIEW REQUIRED

Staging cleanup:
KEEP / HARDEN

Crash recovery:
UNVERIFIED

Migration safety:
UNVERIFIED
```

---

# 37. P7 — Remote Dev Read-Only mevcut yapı

Ana modül:

```text
desktop-bridge/
```

Mevcut önemli parçalar arasında:

```text
BridgeDaemon

PairingManager

ProjectRegistry

GitInspector

SessionMonitor

RemoteDevClient
```

bulunuyor.

---

# 38. Desktop Bridge

`BridgeDaemon` bulunması gelecekteki Remote Dev için iyi temel olabilir.

Ama şu anda cevaplanması gereken:

```text
Gerçek Windows process olarak çalışıyor mu?

Secure socket var mı?

Pairing gerçek mi?

Auth gerçek mi?

Encryption gerçek mi?
```

Bunlar P7’nin konusudur.

---

# 39. ProjectRegistry

Bu component P7 güvenliği açısından önemlidir.

Hedef:

```text
C:\Projects\PromtGen
```

gibi explicitly registered roots.

Mobil cihaz:

```text
C:\
```

üzerinde keyfi browse yapamamalı.

---

# 40. GitInspector

Mevcut olması yararlı.

P7’de gerçek repo ile doğrulanacaktır:

```text
branch

clean/dirty

modified files

staged

unstaged

ahead/behind

recent commits
```

Telefon verisi desktop `git status` ile aynı olmalıdır.

---

# 41. SessionMonitor

Coding agents için session monitoring foundation olabilir.

Ama adapter bazında doğrulanmalıdır.

Örneğin:

```text
OpenCode

Claude Code

Codex

Antigravity
```

her biri aynı capability’ye sahip değildir.

---

# 42. P7 mevcut durum yorumu

```text
Bridge architecture:
KEEP / HARDEN

Real Windows daemon:
UNVERIFIED

Secure pairing:
UNVERIFIED

Git integration:
UNVERIFIED

Agent monitoring:
UNVERIFIED

Remote UI:
LAB_ONLY until P7
```

---

# 43. P8 — Remote Dev Control mevcut yapı

`RemoteDevClient` ve ilgili remote UI kontrol davranışları repository’de bulunabilir.

Ancak P8 başlamadan önce bunlar:

```text
LAB_ONLY
```

kabul edilmelidir.

---

# 44. P8 için mevcut risk

Eğer herhangi bir component şu modeli sunuyorsa:

```text
exec(command: String)
```

P8 başlamadan üretim yeteneği olarak kabul edilmemelidir.

Hedef:

```text
START_AGENT_TASK

SEND_AGENT_MESSAGE

RESUME_SESSION

CANCEL_SESSION

RESPOND_TO_APPROVAL
```

gibi typed operations.

---

# 45. P8 mevcut durum yorumu

```text
Remote control UI:
LAB_ONLY

Typed protocol:
UNVERIFIED

Approval broker:
UNVERIFIED

Agent adapter control:
UNVERIFIED

Replay protection:
UNVERIFIED
```

---

# 46. P9 — Remote Desktop LAN mevcut yapı

`desktop-bridge` ve Android UI tarafında Remote Desktop için şimdiden bazı yapılar bulunuyor.

Örneğin:

```text
DesktopStreamManager

VirtualScreenCoordinateTransformer
```

ve Remote Desktop UI/ViewModel.

---

# 47. DesktopStreamManager

Bu class’ın bulunması:

```text
real desktop streaming works
```

anlamına gelmez.

P9’da gerçek zincir doğrulanmalıdır:

```text
Windows Desktop

↓

real frame capture

↓

hardware encode

↓

encrypted transport

↓

Android hardware decode

↓

render
```

---

# 48. VirtualScreenCoordinateTransformer

Bu component faydalıdır.

P9’da özellikle:

```text
DPI

resolution

orientation

zoom

multi-monitor

viewport
```

ile test edilmelidir.

Yanlış coordinate mapping:

```text
kullanıcı başka yere tıklar
```

gibi ciddi UX/safety hatasına dönüşebilir.

---

# 49. Remote Desktop mevcut UI

UI’ın mevcut olması güzel bir prototyping kazanımıdır.

Ancak P9 öncesinde:

```text
LABS

EXPERIMENTAL

NOT CONNECTED TO REAL DESKTOP PIPELINE
```

gibi dürüst state’ler kullanılmalıdır.

---

# 50. P9 mevcut durum yorumu

```text
Remote Desktop UI:
LAB_ONLY

Coordinate model:
KEEP / HARDEN

Real Windows capture:
UNVERIFIED

Hardware encoder:
UNVERIFIED

Transport:
UNVERIFIED

Android hardware decoder:
UNVERIFIED

Real mouse/keyboard:
UNVERIFIED
```

Dolayısıyla:

```text
Remote Desktop != COMPLETE
```

---

# 51. P10 — Remote Desktop Internet mevcut yapı

P10 için bazı networking abstraction’ları repository’de bulunabilir.

Ancak P10:

```text
P9 VERIFIED
```

olmadan aktif hale gelmemelidir.

---

# 52. P10 başlamadan gerekli foundation

```text
P7 secure device identity

P7 revocation

P9 real remote desktop

P9 authenticated LAN control

P9 replay protection
```

tamamlanmış olmalıdır.

---

# 53. Internet remote mevcut durum

Şimdiki sınıflandırma:

```text
LAB_ONLY / FUTURE
```

Şunlar kesinlikle production claim değildir:

```text
remote relay

NAT traversal

E2EE Internet session

TURN

remote reconnect
```

gerçek integration testi yapılmadan desteklenmiş sayılamaz.

---

# 54. Design System mevcut yapı

Repository’de:

```text
core-designsystem/
```

bulunuyor.

Bu önemli çünkü Mobiltool’un görsel dili zaten belirlenmiş durumda.

Ana kimlik:

```text
Warm Charcoal
+
Aged Ivory
+
Oxidized Copper / Rust
```

---

# 55. Design System sınıflandırması

Genel direction:

```text
KEEP
```

Ancak her yeni ekran kontrol edilmeli.

Yasak:

```text
default Material 3 appearance

blue/purple SaaS

pill navigation

huge rounded cards

glassmorphism

neon

heavy blur
```

---

# 56. Mevcut repository hakkında ana değerlendirme

Repository’nin problemi:

```text
kod yok
```

değildir.

Tam tersine:

```text
çok fazla alan için erken kod var
```

durumudur.

Bu nedenle geliştirme stratejisi:

```text
MORE CODE
```

değil.

Şu olmalıdır:

```text
VERIFY
HARDEN
REMOVE FAKE STATE
TEST
LOCK
```

---

# 57. Şimdiki maturity haritası

Kabaca:

```text
DESIGN SYSTEM
████████░░
Foundation strong

CALL ARCHITECTURE
██████░░░░
Real capture unverified

MEDIA ARCHITECTURE
██████░░░░
Real platform fixtures required

LIBRARY
██████░░░░
Integration/recovery required

TRANSCRIPTION
███░░░░░░░
Real inference required

POWER
█████░░░░░
Benchmarks required

SECURITY
█████░░░░░
Hardening required

REMOTE DEV
███░░░░░░░
Lab/unverified

REMOTE DESKTOP
██░░░░░░░░
Prototype/lab
```

Bu grafik feature completion göstermiyor.

Sadece mevcut architectural groundwork seviyesini gösteriyor.

---

# 58. P0 için ilk çalışma listesi

Bu mapping sonrasında P0 özellikle şu alanlara bakacak:

```text
01 Call fake success?

02 Call capture capability truthful?

03 Fake media?

04 Downloader fake progress?

05 Transcript placeholder?

06 Fake confidence?

07 Fake remote latency/FPS?

08 Fake PC online state?

09 Remote services background'da çalışıyor mu?

10 Dangerous unused permissions var mı?

11 Future features production UI'da exposed mı?

12 Build/test baseline nedir?
```

---

# 59. P0 sonunda bu dosyanın güncellenmesi

P0 tamamlanınca bu mapping daha kesin hale gelir.

Örneğin:

```text
DefaultTranscriptionEngine
BEFORE:
UNVERIFIED

AFTER P0:
LAB_ONLY / ENGINE_UNAVAILABLE
```

veya:

```text
AmbientMicRecorder
BEFORE:
unclear

AFTER:
KEEP — diagnostics only
```

gibi.

---

# 60. Ana repository prensibi

Mobiltool repository’sinde bundan sonra:

> **Dosyanın veya sınıfın varlığı maturity kanıtı değildir.**

Her capability için üç ayrı soru vardır:

```text
1. Kod var mı?

2. Doğru mimariyle çalışıyor mu?

3. Gerçek ortamda doğrulandı mı?
```

Ancak üçünün cevabı da `YES` ise ilgili özellik:

```text
SUPPORTED / VERIFIED
```

olarak değerlendirilebilir.
