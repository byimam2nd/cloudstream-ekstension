# 📚 Extractor Architecture Documentation

## 🎯 Overview

Dokumentasi ini menjelaskan arsitektur extractor yang digunakan di cloudstream-ekstension untuk memastikan konsistensi, maintainability, dan scalability.

---

## ✅ VERIFIKASI ARSITEKTUR (Last Updated: 2026-03-18)

### 1️⃣ SATU SITE = SATU FILE EKSTRAKTOR

**Status: ✅ VERIFIED**

Setiap site hanya memiliki **SATU** file `Extractors.kt` yang berisi semua extractor classes.

```
Pencurimovie/
└── src/main/kotlin/com/Pencurimovie/
    ├── Extractors.kt          ✅ (37 extractor classes)
    └── PencurimovieProvider.kt

Anichin/
└── src/main/kotlin/com/Anichin/
    ├── Extractors.kt          ✅ (37 extractor classes)
    └── AnichinProvider.kt
```

**Benefit:**
- Mudah maintenance
- Tidak ada duplikasi code
- Sinkronisasi otomatis via GitHub Actions

---

### 2️⃣ PACKAGE NAME KONSISTEN

**Status: ✅ VERIFIED**

Setiap file `Extractors.kt` menggunakan package name yang sesuai dengan nama site.

| Site | Package Name | Status |
|------|-------------|--------|
| Pencurimovie | `package com.Pencurimovie` | ✅ |
| LayarKaca21 | `package com.LayarKaca21` | ✅ |
| Donghuastream | `package com.Donghuastream` | ✅ |
| Funmovieslix | `package com.Funmovieslix` | ✅ |
| HiAnime | `package com.HiAnime` | ✅ |
| IdlixProvider | `package com.IdlixProvider` | ✅ |
| Anichin | `package com.Anichin` | ✅ |

---

### 3️⃣ DYNAMIC REGISTRATION (NO HARDCODE)

**Status: ✅ VERIFIED**

Semua Provider menggunakan **dynamic registration** untuk mendaftarkan extractors.

**TIDAK BOLEH:**
```kotlin
// ❌ BAD: Hardcode extractors satu-satu
registerExtractorAPI(Do7go())
registerExtractorAPI(Dhcplay())
registerExtractorAPI(Listeamed())
// ... 34 baris lagi
```

**HARUS:**
```kotlin
// ✅ GOOD: Dynamic registration
AllExtractors.list.forEach { extractor ->
    registerExtractorAPI(extractor)
}
```

**Benefit:**
- Tidak perlu update Provider setiap kali ada extractor baru
- Cukup update `AllExtractors.list` di `Extractors.kt`
- Code lebih clean dan maintainable

---

### 4️⃣ JUMLAH EXTRACTOR

**Status: ✅ VERIFIED**

Setiap site memiliki **37 extractor classes** yang terdaftar di `AllExtractors.list`.

**Breakdown:**
- **StreamWish based:** 10 extractors
- **VidStack based:** 7 extractors
- **Custom extractors:** 3 extractors (Voe, Veev, Dintezuvio)
- **OK.RU based:** 3 extractors (Odnoklassniki, OkRuSSL, OkRuHTTP)
- **Other extractors:** 14 extractors

**Total: 37 extractor classes**

---

## 🏗️ ARCHITECTURE DIAGRAM

```
┌─────────────────────────────────────────────────────────┐
│                  MASTER SOURCE                          │
│           docs/MasterExtractors.kt                      │
│              (37 extractor classes)                     │
└────────────────────┬────────────────────────────────────┘
                     │
                     │ GitHub Action (sync-extractors.yml)
                     │ OR bash scripts/sync-extractors.sh
                     │
        ┌────────────┼────────────┐
        │            │            │
        ▼            ▼            ▼
┌───────────────┬───────────────┬───────────────┐
│ Pencurimovie  │ LayarKaca21   │ Donghuastream │
│ Extractors.kt │ Extractors.kt │ Extractors.kt │
│ (37 classes)  │ (37 classes)  │ (37 classes)  │
└───────┬───────┴───────┬───────┴───────┬───────┘
        │               │               │
        ▼               ▼               ▼
┌───────────────┬───────────────┬───────────────┐
│ Provider.kt   │ Provider.kt   │ Provider.kt   │
│ AllExtractors │ AllExtractors │ AllExtractors │
│ .list.forEach │ .list.forEach │ .list.forEach │
└───────────────┴───────────────┴───────────────┘
```

---

## 📁 FILE STRUCTURE

### Standard Site Structure

```
SiteName/
├── build.gradle.kts
└── src/main/kotlin/com/SiteName/
    ├── Extractors.kt              # 37 extractor classes + AllExtractors
    ├── SiteName.kt                # Main API implementation
    ├── SiteNameProvider.kt        # Plugin provider (dynamic registration)
    └── Utils.kt                   # Helper functions (optional)
```

### Extractors.kt Structure

```kotlin
// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from docs/MasterExtractors.kt
// ========================================

package com.SiteName

import ...

// Helper functions
fun base64DecodeStr(str: String): String { ... }

// Extractor classes (37 classes)
class Do7go : StreamWishExtractor() { ... }
class Dhcplay : StreamWishExtractor() { ... }
class Voe : ExtractorApi() { ... }
// ... dan seterusnya

// Registry (WAJIB ADA)
object AllExtractors {
    val list = listOf(
        Do7go(),
        Dhcplay(),
        // ... semua 37 extractors
    )
}
```

### Provider.kt Structure

```kotlin
package com.SiteName

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class SiteNameProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(SiteName())

        // DYNAMIC REGISTER: Auto-register ALL extractors
        AllExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
```

---

## 🔄 SYNC WORKFLOW

### Automatic Sync (GitHub Actions)

**Trigger:** Push ke `docs/MasterExtractors.kt`

```yaml
# .github/workflows/sync-extractors.yml
on:
  push:
    paths:
      - 'docs/MasterExtractors.kt'

jobs:
  sync-extractors:
    runs-on: ubuntu-latest
    steps:
      - Copy MasterExtractors.kt to all sites
      - Update package names
      - Build all extensions
      - Upload artifacts
```

### Manual Sync (Local Development)

```bash
# Sync extractors ke semua sites
bash scripts/sync-extractors.sh

# Verify extractors
bash scripts/verify-extractors.sh

# Build all
./gradlew clean build

# Commit changes
git add -A
git commit -m "sync: update extractors to v2.0"
git push
```

---

## 📊 EXTRACTOR LIST (37 CLASSES)

### StreamWish Based (10)
1. Do7go
2. Dhcplay
3. Hglink
4. Ghbrisk
5. Movearnpre
6. Minochinos
7. Mivalyo
8. Ryderjet
9. Bingezove
10. Dingtezuni

### VidStack Based (7)
11. Listeamed
12. Streamcasthub
13. Dm21embed
14. Dm21upns
15. Pm21p2p
16. Dm21
17. Meplayer

### Custom Extractors (3)
18. Voe
19. Veev
20. Dintezuvio

### OK.RU Based (3)
21. Odnoklassniki
22. OkRuSSL
23. OkRuHTTP

### Other Extractors (14)
24. Dailymotion
25. Rumble
26. StreamRuby
27. Svanila
28. Svilla
29. Vidguardto
30. Vidguardto1
31. Vidguardto2
32. Vidguardto3
33. Archivd
34. Newuservideo
35. Vidhidepro
36. Dsvplay
37. ArchiveOrgExtractor

---

## 🛠️ MAINTENANCE GUIDE

### Menambahkan Extractor Baru

1. **Update MasterExtractors.kt:**
```kotlin
// docs/MasterExtractors.kt
class NewExtractor : ExtractorApi() {
    override val name = "NewExtractor"
    override val mainUrl = "https://newextractor.com"
    // ... implementation
}
```

2. **Add to AllExtractors.list:**
```kotlin
object AllExtractors {
    val list = listOf(
        // ... existing extractors
        NewExtractor(),  // ← Add here
    )
}
```

3. **Sync ke semua sites:**
```bash
bash scripts/sync-extractors.sh
```

4. **Verify:**
```bash
bash scripts/verify-extractors.sh
```

5. **Build & Test:**
```bash
./gradlew clean build
```

### Menghapus Extractor

1. Hapus dari `MasterExtractors.kt`
2. Hapus dari `AllExtractors.list`
3. Sync ke semua sites
4. Verify & Build

### Update Extractor

1. Update implementation di `MasterExtractors.kt`
2. Sync ke semua sites
3. Verify & Build

---

## 🧪 TESTING CHECKLIST

Setiap extractor harus ditest dengan:

- [ ] URL valid dan bisa diakses
- [ ] Video URL berhasil di-extract
- [ ] Quality terdeteksi dengan benar
- [ ] Subtitle terdeteksi (jika ada)
- [ ] Tidak ada error di log
- [ ] Video bisa diputar di CloudStream app

---

## 📈 METRICS

| Metric | Value | Status |
|--------|-------|--------|
| Total Sites | 7 | ✅ |
| Extractors per Site | 37 | ✅ |
| Total Extractor Classes | 37 | ✅ |
| Dynamic Registration | 100% | ✅ |
| Package Consistency | 100% | ✅ |
| Single Extractor File | 100% | ✅ |

---

## 🚨 COMMON ISSUES & SOLUTIONS

### Issue: "Extractor tidak terdaftar"

**Cause:** Provider tidak menggunakan dynamic registration

**Solution:**
```kotlin
// ❌ BAD
registerExtractorAPI(SpecificExtractor())

// ✅ GOOD
AllExtractors.list.forEach { extractor ->
    registerExtractorAPI(extractor)
}
```

### Issue: "Package name mismatch"

**Cause:** Extractors.kt menggunakan package name yang salah

**Solution:**
```kotlin
// ❌ BAD
package com.WrongPackage

// ✅ GOOD
package com.SiteName
```

### Issue: "Duplicate extractor files"

**Cause:** Ada lebih dari 1 file Extractors.kt di site yang sama

**Solution:**
```bash
# Find duplicate files
find SiteName/src/main/kotlin/com/SiteName -name "Extractors.kt"

# Delete old/duplicate files
rm path/to/old/Extractors.kt

# Re-sync
bash scripts/sync-extractors.sh
```

---

## 📚 RELATED DOCUMENTATION

- [MasterExtractors.kt](docs/MasterExtractors.kt) - Source code lengkap
- [sync-extractors.sh](scripts/sync-extractors.sh) - Script untuk sync
- [verify-extractors.sh](scripts/verify-extractors.sh) - Script untuk verify
- [sync-extractors.yml](.github/workflows/sync-extractors.yml) - GitHub Actions workflow

---

**Last Updated:** 2026-03-18  
**Maintainer:** Phisher98  
**Status:** ✅ VERIFIED & PRODUCTION READY
