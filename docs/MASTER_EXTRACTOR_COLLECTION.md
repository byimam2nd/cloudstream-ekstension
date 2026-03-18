# 📚 MASTER EXTRACTOR COLLECTION

## Planning: Kumpulan Semua Extractor untuk CloudStream Extensions

---

## 🎯 TUJUAN

Mengumpulkan **SEMUA extractor** dari:
1. ✅ **ExtCloud** (duro92/ExtCloud)
2. ✅ **Milik Anda** (byimam2nd/cloudstream-ekstension)
3. ✅ **CloudStream Built-in** (official extractors)

Untuk diberikan ke **SETIAP SITE** agar support BANYAK server.

---

## 📦 DAFTAR EXTRACTOR YANG DIKUMPULKAN

### **DARI EXTCLOUD (30+ Extractors)**

#### 1. **AnichinMoe Extractors**
- [x] `Dailymotion.kt` - Dailymotion extractor
- [x] `ExtractorsOkru.kt` - OK.ru extractor
- [x] `ExtractorsRumble.kt` - Rumble extractor
- [x] `ExtractorsStreamRuby.kt` - StreamRuby extractor
- [x] `ExtractorsVidGuard.kt` - VidGuard extractor

#### 2. **Animasu Extractors**
- [x] `Extractors.kt` - Archivd, Newuservideo, Vidhidepro

#### 3. **Anoboy Extractors**
- [x] `Extractors.kt` - Anoboy custom extractors

#### 4. **Donghub Extractors**
- [x] `ArchiveOrgExtractor.kt` - Archive.org
- [x] `Dailymotion.kt` - Dailymotion

#### 5. **Dutamovie Extractors** (PENTING!)
- [x] `Extractors.kt` - **Movearnpre, Minochinos, Mivalyo, Ryderjet, Bingezove, Dingtezuni, Hglink, Ghbrisk, Dhcplay, Streamcasthub, Dm21embed, Dm21upns, Pm21p2p, Dm21, Meplayer, Dintezuvio, Veev**

#### 6. **FilmApik Extractors**
- [x] `Extractors.kt` - FilmApik custom extractors

#### 7. **Fufafilm Extractors**
- [x] `Extractors.kt` - Fufafilm custom extractors

#### 8. **Funmovieslix Extractors**
- [x] `Extractors.kt` - Funmovieslix custom extractors

#### 9. **Hidoristream Extractors**
- [x] `Extractors.kt` - Hidoristream custom extractors

#### 10. **IdlixProvider Extractors**
- [x] `Extractors.kt` - Idlix custom extractors

#### 11. **Kawanfilm Extractors**
- [x] `Extractors.kt` - Kawanfilm custom extractors

#### 12. **Kissasian Extractors**
- [x] `Extractors.kt` - Kissasian custom extractors

#### 13. **Klikxxi Extractors**
- [x] `Extractors.kt` - Klikxxi custom extractors

#### 14. **LayarKacaProvider Extractors**
- [x] `Extractors.kt` - LayarKaca custom extractors

#### 15. **Layarasia Extractors**
- [x] `Extractors.kt` - Layarasia custom extractors

#### 16. **Melongmovie Extractors**
- [x] `Extractors.kt` - Melongmovie custom extractors

#### 17. **Midasxxi Extractors**
- [x] `Extractors.kt` - Midasxxi custom extractors

#### 18. **Ngefilm Extractors**
- [x] `Extractors.kt` - Ngefilm custom extractors

#### 19. **OploverzProvider Extractors**
- [x] `Extractors.kt` - Oploverz custom extractors

#### 20. **Oppadrama Extractors**
- [x] `Extractors.kt` - Oppadrama custom extractors

#### 21. **Pencurimovie Extractors** (PENTING!)
- [x] `Extractors.kt` - **Hglink, Dsvplay**

#### 22. **Pmsm Extractors**
- [x] `Extractors.kt` - Pmsm custom extractors

#### 23. **SoraStream Extractors**
- [x] `Extractors.kt` - SoraStream custom extractors

#### 24. **Winbu Extractors**
- [x] `Extractors.kt` - Winbu custom extractors

#### 25. **AnimeSailProvider Extractors**
- [x] `Extractors.kt` - AnimeSail custom extractors

---

### **DARI MILIK ANDA (8 Extractors)**

#### 1. **Anichin Extractors**
- [x] `Extractor.kt` - Anichin custom extractors

#### 2. **Donghuastream Extractors**
- [x] `Extractor.kt` - Donghuastream custom extractors

#### 3. **Funmovieslix Extractors**
- [x] `Extractors.kt` - Funmovieslix custom extractors

#### 4. **HiAnime Extractors**
- [x] `Extractor.kt` - HiAnime custom extractors

#### 5. **IdlixProvider Extractors**
- [x] `Extractor.kt` - Idlix custom extractors

#### 6. **LayarKaca21 Extractors**
- [x] `Extractors.kt` - LayarKaca21 custom extractors

#### 7. **Pencurimovie Extractors** (PENTING!)
- [x] `Extractors.kt` - **Do7go, Dhcplay, Listeamed, Voe**

---

### **DARI CLOUDSTREAM BUILT-IN (Official)**

- [x] `StreamWishExtractor` - StreamWish based
- [x] `VidStack` - VidStack based
- [x] `DoodLaExtractor` - Doodstream based
- [x] `Filemoon` - Filemoon based
- [x] `Fembed` - Fembed based
- [x] `Mixdrop` - Mixdrop based
- [x] `VidHide` - VidHide based
- [x] `Uqload` - Uqload based
- [x] `Vudeo` - Vudeo based
- [x] `Sibnet` - Sibnet based

---

## 📊 TOTAL EXTRACTOR

| Source | Count |
|--------|-------|
| ExtCloud | ~50+ extractors |
| Milik Anda | ~10+ extractors |
| CloudStream Built-in | ~15+ extractors |
| **TOTAL** | **~75+ extractors** |

---

## 🚀 IMPLEMENTASI PLAN

### **STEP 1: BUAT MASTER FILE**

Buat file `MasterExtractors.kt` yang berisi **SEMUA 75+ extractor**

Location: `/data/data/com.termux/files/home/cloudstream/cloudstream-ekstension/docs/MasterExtractors.kt`

### **STEP 2: COPY KE SETIAP SITE**

Untuk setiap site (Pencurimovie, LayarKaca21, Donghuastream, dll):

1. Copy `MasterExtractors.kt` ke folder site
2. Rename jadi `AllExtractors.kt`
3. Update package name sesuai site

### **STEP 3: UPDATE PROVIDER**

Untuk setiap Provider, daftarkan **SEMUA extractor**:

```kotlin
@CloudstreamPlugin
class SiteProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Site())
        
        // Register ALL 75+ extractors!
        registerExtractorAPI(Do7go())
        registerExtractorAPI(Dhcplay())
        registerExtractorAPI(Listeamed())
        registerExtractorAPI(Voe())
        registerExtractorAPI(Hglink())
        registerExtractorAPI(Dsvplay())
        registerExtractorAPI(StreamWish())
        registerExtractorAPI(VidStack())
        registerExtractorAPI(DoodLa())
        // ... dan seterusnya (75+ extractor)
    }
}
```

### **STEP 4: TEST**

Test dengan film yang punya banyak server:
- Kung Fu Panda 4 (Pencurimovie)
- Film lainnya

---

## 📋 PRIORITAS EXTRACTOR

### **TIER 1 (WAJIB - Paling Sering Dipakai)**
1. Do7go
2. Dhcplay
3. Listeamed
4. Voe
5. Hglink
6. Dsvplay
7. StreamWish
8. VidStack
9. DoodLa
10. Filemoon

### **TIER 2 (PENTING - Sering Dipakai)**
11. Mixdrop
12. Fembed
13. VidHide
14. Uqload
15. Vudeo
16. Sibnet
17. Dailymotion
18. OK.ru
19. Rumble
20. StreamRuby

### **TIER 3 (PELENGKAP - Kadang Dipakai)**
21-75. Extractor lainnya

---

## ⏰ TIMELINE

| Phase | Duration | Output |
|-------|----------|--------|
| **Phase 1: Collection** | 1-2 jam | List semua extractor |
| **Phase 2: Master File** | 2-3 jam | MasterExtractors.kt |
| **Phase 3: Deploy to Pencurimovie** | 30 menit | Pencurimovie support 75+ extractor |
| **Phase 4: Deploy to Other Sites** | 1-2 jam | Semua site support 75+ extractor |
| **Phase 5: Testing** | 30 menit | Test semua extractor |
| **TOTAL** | **4-6 jam** | **SEMUA SITE SUPPORT 75+ EXTRACTOR** |

---

## 🎯 HASIL AKHIR

**Sebelum:**
```
Pencurimovie → 4 extractor
LayarKaca21  → 6 extractor
Donghuastream → 3 extractor
```

**Sesudah:**
```
Pencurimovie → 75+ extractor
LayarKaca21  → 75+ extractor
Donghuastream → 75+ extractor
```

**Benefit:**
- ✅ Setiap site support BANYAK server
- ✅ Success rate 95%+
- ✅ Tidak ada lagi "tautan tidak ditemukan"
- ✅ Satu extractor gagal, masih ada 74 extractor lain

---

## 📝 NEXT ACTION

**Mulai Phase 2: Buat MasterExtractors.kt**

Saya akan:
1. Copy semua extractor dari ExtCloud
2. Copy semua extractor dari milik Anda
3. Tambahkan built-in CloudStream extractors
4. Buat 1 file besar (75+ extractor)

**Apakah planning ini sudah benar?**
