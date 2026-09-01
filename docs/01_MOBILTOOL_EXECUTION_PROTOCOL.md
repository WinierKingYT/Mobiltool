# 01 — Mobiltool Execution Protocol

**Project:** Mobiltool  
**Purpose:** Her parçayı kontrollü, doğrulanabilir ve geri alınabilir şekilde geliştirmek  
**Applies To:** P0 → P10 arasındaki tüm geliştirme parçaları  

---

## 1. Temel Çalışma Modeli

Mobiltool üzerinde hiçbir parça tek seferde büyük bir görev olarak uygulanmayacak.

Her Part küçük **Execution Package**'lara ayrılır:
- Tek bir davranış hedefler.
- Sınırlı dosya değiştirir.
- Kendi testi/kanıtı bulunur.
- Tek başına revert edilebilir.
- Başka Part'a sıçramaz.

---

## 2. Her Görevden Önce Zorunlu Başlık Bloğu

AI herhangi bir kod değişikliğine başlamadan önce şu bloğu hazırlamak **zorundadır**:

```text
ACTIVE_PART:
EXECUTION_PACKAGE:
GOAL:
FILES EXPECTED TO CHANGE:
FILES FORBIDDEN TO CHANGE:
NEW PERMISSIONS:
NEW DEPENDENCIES:
NETWORK BEHAVIOR CHANGE:
STORAGE BEHAVIOR CHANGE:
BACKGROUND BEHAVIOR CHANGE:
POWER IMPACT:
SECURITY IMPACT:
ADR REQUIRED:
PHYSICAL DEVICE TEST REQUIRED:
```

---

## 3. Kod Sınıflandırma Disiplini

İlgili kod her Execution Package öncesinde sınıflandırılır:
* **KEEP:** Kod doğru ve sözleşmeye uygun.
* **HARDEN:** Mimari doğru ama production için eksikleri var (örn: threshold testleri, edge caseler).
* **REPLACE:** Temel yaklaşım yanlış veya yanıltıcı.
* **LAB_ONLY:** Deneysel tutulabilir, production capability değildir.
* **REMOVE:** Yanlış, tehlikeli veya dummy/sahte kod.
* **UNVERIFIED:** Kod mantıklı fakat henüz fiziksel cihaz/gerçek kaynak kanıtı yok.

---

## 4. Bounded Package & Scope Sınırı

* 1 Problem + 1 Bounded Implementation + 1 Validation Set.
* Beklenen dosya listesi dışına çıkılması gerekirse **SCOPE EXPANSION REQUIRED** raporu verilir, kendiliğinden diğer modüllere dokunulmaz.

---

## 5. Raporlama ve Çıkış Şablonları

### Execution Package Raporu:
```text
EXECUTION_PACKAGE:
GOAL:
IMPLEMENTED:
FILES CHANGED:
TESTS:
REAL EVIDENCE:
KNOWN LIMITATIONS:
FOLLOW-UP:
STATUS: PASS | FAIL | BLOCKED
```

### Part Exit Raporu:
```text
PART:
COMMIT:
BUILD:
UNIT TESTS:
INTEGRATION TESTS:
INSTRUMENTATION:
REAL SOURCE TEST:
PHYSICAL DEVICE TEST:
SECURITY REVIEW:
POWER IMPACT:
KNOWN LIMITATIONS:
UNSUPPORTED CONFIGURATIONS:
OPEN BLOCKERS:
STATUS: PASS | FAIL | BLOCKED
```

---

## 6. Ana Mühendislik Prensibi

```
UNDERSTAND → BOUND → IMPLEMENT → TEST → MEASURE → VERIFY → LOCK → MOVE ON
```
