# 🏗️ Rancangan Detail: Base Extractor Architecture

**Tujuan:** Satu base class untuk logika umum. Setiap extractor hanya set regex + konfigurasi.
**Tanggal:** 2026-04-05
**Status:** Rancangan Detail (belum implementasi)

---

## 📋 1. Analisis 36 Extractor Saat Ini

### **1.1 Pemetaan Semua Extractor**

| No | Extractor | Baris | Pola | Butuh Unpack? | Multi URL? | Custom Logic? |
|----|-----------|-------|------|---------------|------------|---------------|
| 1 | Odnoklassniki | 50 | JSON `"videos":[]` | ❌ | ✅ (6 URLs) | Unicode decode `\\uXXXX` |
| 2 | OkRuSSL | 3 | extends Odnoklassniki | ❌ | ✅ | - |
| 3 | OkRuHTTP | 3 | extends Odnoklassniki | ❌ | ✅ | - |
| 4 | StreamRuby | 40 | POST → packed JS → `file:` | ✅ | ❌ | - |
| 5 | svanila | 5 | extends StreamRuby | ✅ | ❌ | - |
| 6 | svilla | 5 | extends StreamRuby | ✅ | ❌ | - |
| 7 | Rumble | 15 | `"hls":{"url":"..."}` | ❌ | ❌ | - |
| 8 | Dailymotion | 20 | `cdndirector` URL | ❌ | ❌ | Geo player vs embed |
| 9 | Voe | 35 | packed JS → `hls:` | ✅ | ❌ | - |
| 10 | Dsvplay | 5 | extends DoodLaExtractor | ❌ | ❌ | (CloudStream bawaan) |
| 11 | Do7go | 5 | extends StreamWishExtractor | ❌ | ❌ | (CloudStream bawaan) |
| 12 | Dingtezuni | 50 | unpack → M3U8 parse | ✅ | ❌ | Shared base class |
| 13 | Hglink | 3 | extends StreamWishExtractor | ❌ | ❌ | - |
| 14 | Ghbrisk | 3 | extends StreamWishExtractor | ❌ | ❌ | - |
| 15 | Dhcplay | 5 | extends StreamWishExtractor | ❌ | ❌ | - |
| 16 | Dintezuvio | 50 | unpack → M3U8 parse | ✅ | ❌ | Sama seperti Dingtezuni |
| 17 | Listeamed | 5 | extends VidStack | ❌ | ❌ | (CloudStream bawaan) |
| 18 | Veev | 120 | LZW decode + Caesar cipher | ❌ | ❌ | ✅ Kompleks |
| 19 | Hownetwork | 5 | extends StreamWishExtractor | ❌ | ❌ | - |
| 20 | Jeniusplay | 5 | extends StreamWishExtractor | ❌ | ❌ | - |
| 21 | Playerngefilm21 | 5 | extends VidStack | ❌ | ❌ | - |
| 22 | Pm21p2p | 5 | extends VidStack | ❌ | ✅ | - |
| 23 | P2pplay | 5 | extends VidStack | ❌ | ✅ | - |
| 24 | Streamcasthub | 5 | extends VidStack | ❌ | ❌ | - |
| 25 | Meplayer | 5 | extends VidStack | ❌ | ❌ | - |
| 26 | Fufaupns | 5 | extends VidStack | ❌ | ❌ | - |
| 27 | Dm21 | 5 | extends VidStack | ❌ | ❌ | - |
| 28 | Dm21embed | 5 | extends VidStack | ❌ | ❌ | - |
| 29 | Dm21upns | 5 | extends VidStack | ❌ | ❌ | - |
| 30 | Archivd | 10 | direct URL passthrough | ❌ | ❌ | - |
| 31 | ArchiveOrgExtractor | 10 | direct URL passthrough | ❌ | ❌ | - |
| 32 | BloggerExtractor | 10 | direct URL passthrough | ❌ | ❌ | - |
| 33 | PixelDrainDev | 10 | extends PixelDrain | ❌ | ❌ | - |
| 34 | Newuservideo | 10 | direct URL passthrough | ❌ | ❌ | - |
| 35 | Upload18com | 10 | direct URL passthrough | ❌ | ❌ | - |
| 36 | Vidguardto | 5 | extends VidHidePro | ❌ | ❌ | - |
| **BARU** | EmturbovidExtractorM3U8 | 40 | XPath → var urlPlay → M3U8 | ❌ | ✅ (multi quality) | ✅ |
| **BARU** | MixDropBz | 25 | packed JS → `video_src=` | ✅ | ❌ | - |
| **BARU** | FilemoonNlExtractor | 25 | packed JS → `file:` | ✅ | ❌ | - |
| **BARU** | Gofile | 40 | API POST → JSON parse | ❌ | ✅ (multiple files) | ✅ |
| **BARU** | HUBCDN | 20 | `var reurl` → base64 decode | ❌ | ❌ | ✅ |

### **1.2 Kesimpulan Pemetaan**

| Kelompok | Extractor | Total | Pattern yang Sama |
|----------|-----------|-------|-------------------|
| **Simple Regex** (URL langsung di HTML) | Rumble, Archivd, ArchiveOrg, Blogger, PixelDrain, Newuservideo, Upload18com | 7 | `app.get()` → regex → callback |
| **Packed JS** | Voe, MixDropBz, FilemoonNlExtractor | 3 | `app.get()` → unpack → regex → callback |
| **StreamWish Family** | Do7go, Hglink, Ghbrisk, Dhcplay, Hownetwork, Jeniusplay | 6 | extends CloudStream `StreamWishExtractor` |
| **VidStack Family** | Listeamed, Playerngefilm21, Pm21p2p, P2pplay, Streamcasthub, Meplayer, Fufaupns, Dm21, Dm21embed, Dm21upns | 10 | extends CloudStream `VidStack` |
| **Dingtezuni Family** | Dingtezuni, Dintezuvio | 2 | `app.get()` → unpack → M3U8 parse |
| **OK.ru Family** | Odnoklassniki, OkRuSSL, OkRuHTTP | 3 | `app.get()` → JSON `"videos":[]` → loop |
| **Custom Complex** | Veev, Gofile, EmturbovidExtractorM3U8, HUBCDN, Vidguardto, Dailymotion | 6 | Override `getUrl()` manual |
| **DoodStream** | Dsvplay | 1 | extends CloudStream `DoodLaExtractor` |

---

## 🎯 2. Arsitektur Base Extractor

### **2.1 Class Hierarchy**

```
ExtractorApi (CloudStream)
│
├── BaseExtractor (abstract) — Template pattern
│   │
│   ├── SimpleRegexExtractor — URL langsung di HTML text
│   │   └── Rumble, Archivd, Blogger, Newuservideo, Upload18com
│   │
│   ├── PackedJsExtractor — URL di eval(function(p,a,c,k,e,d)
│   │   └── Voe, MixDropBz, FilemoonNlExtractor
│   │
│   └── M3U8Extractor — Khusus M3U8 + auto quality detection
│       └── (akan dipakai untuk Dingtezuni, Dintezuvio)
│
└── [TETAP MANUAL — terlalu unik]
    ├── Odnoklassniki — JSON "videos":[] parsing + unicode decode
    ├── Dailymotion — cdndirector + geo player detection
    ├── Veev — LZW + Caesar cipher decryption
    ├── Gofile — API POST + JSON tree traversal
    ├── EmturbovidExtractorM3U8 — XPath + M3U8 playlist parsing
    ├── HUBCDN — var reurl + base64 decode
    └── Dingtezuni/Dintezuvio — extractScriptFromHtml + M3U8 parse
```

### **2.2 Property BaseExtractor**

```kotlin
abstract class BaseExtractor : ExtractorApi() {

    // ===== WAJIB OVERRIDE =====
    override val name: String                          // Nama extractor
    override val mainUrl: String                       // URL utama
    val extractUrlRegex: Regex                         // Regex cari video URL

    // ===== OPSIONAL (ada default) =====
    val urlType: ExtractorLinkType = INFER_TYPE        // VIDEO / M3U8
    val useUnpack: Boolean = false                     // Butuh getAndUnpack()?
    val defaultQuality: Int = Qualities.Unknown.value  // Quality default
    val customHeaders: Map<String, String> = emptyMap() // Custom headers
    val requiresCaptcha: Boolean = false               // Butuh bypass CF?
    val scriptSelector: String = ""                    // CSS selector script tag

    // ===== META (untuk logging & debugging) =====
    val debugTag: String get() = "Extractor:[$name]"
}
```

### **2.3 Flow `getUrl()` di BaseExtractor**

```
Step 1: FETCH
    app.get(url, referer, headers)
    ↓
    Response (HTML text)

Step 2: EXTRACT SCRIPT
    ┌─────────────────────────────────────────────────┐
    │ IF requiresCaptcha = true:                      │
    │   → WebViewResolver (bypass Cloudflare)          │
    │ ELSE IF useUnpack = true:                        │
    │   → getPacked(response.text)                    │
    │   → if packed exists → getAndUnpack(response)   │
    │   → else → response.document.select(scriptSelector)│
    │ ELSE:                                            │
    │   → response.document.select(scriptSelector)    │
    │   → if null → response.text                     │
    └─────────────────────────────────────────────────┘
    ↓
    Script Content (String)

Step 3: APPLY REGEX
    extractUrlRegex.find(scriptContent)
    ↓
    groupValues[1] → video URL (String?)

Step 4: CLEAN & BUILD
    url.replace("\\/", "/")
    ↓
    newExtractorLink(source, name, url, urlType) {
        this.referer = mainUrl
        this.quality = defaultQuality
        this.headers = customHeaders
    }
    ↓
    callback.invoke(link)
```

### **2.4 Method yang Bisa Di-Override**

```kotlin
// Override jika perlu custom logic setelah fetch
protected open fun afterFetch(response: Response): Response = response

// Override jika perlu custom extraction dari HTML
protected open fun extractScript(html: String): String? = null

// Override jika perlu post-processing URL
protected open fun processUrl(url: String): String = url.replace("\\/", "/")

// Override jika perlu custom quality detection
protected open fun detectQuality(content: String): Int = defaultQuality
```

---

## 📦 3. Sub-kelas Detail

### **3.1 SimpleRegexExtractor**

**Untuk:** Extractor yang URL-nya langsung ada di HTML text (tanpa unpack).

```kotlin
abstract class SimpleRegexExtractor : BaseExtractor() {
    override val useUnpack = false
}
```

**Contoh penggunaan:**
```kotlin
class Rumble : SimpleRegexExtractor() {
    override val name = "Rumble"
    override val mainUrl = "https://rumble.com"
    override val extractUrlRegex = Regex(""""hls":\{"url":"(https?:[^"]+playlist\.m3u8)"""")
}
```

**Extractor yang cocok (7):**
| Extractor | Regex |
|-----------|-------|
| Rumble | `"hls":{"url":"(https?:[^"]+playlist\.m3u8)"` |
| Archivd | `(https?://archive\.org/[^"']+)` |
| BloggerExtractor | `(https?://www\.blogger\.com/video\.g\?token=[^"']+)` |
| Newuservideo | `(https?://new\.uservideo\.xyz/[^"']+)` |
| Upload18com | `(https?://www\.upload18\.com/[^"']+)` |
| PixelDrainDev | `(https?://pixeldrain\.com/api/file/[^"']+)` |
| HUBCDN (baru) | `reurl\s*=\s*"([^"]+)"` + base64 decode |

### **3.2 PackedJsExtractor**

**Untuk:** Extractor yang URL-nya disembunyikan di packed JavaScript.

```kotlin
abstract class PackedJsExtractor : BaseExtractor() {
    override val useUnpack = true
    override val scriptSelector = "script:containsData(sources:)"
}
```

**Contoh penggunaan:**
```kotlin
class FilemoonNlExtractor : PackedJsExtractor() {
    override val name = "FilemoonNl"
    override val mainUrl = "https://filemoon.nl"
    override val extractUrlRegex = Regex("file:\\s*\"(.*?m3u8.*?)\"")
    override val urlType = ExtractorLinkType.M3U8
}
```

**Extractor yang cocok (3):**
| Extractor | Regex |
|-----------|-------|
| Voe | `['"]hls['"]\s*:\s*['"]([^'"]+)['"]` |
| MixDropBz | `video_src\s*=\s*["'](.*?)["']` |
| FilemoonNlExtractor | `file:\s*"(.*?m3u8.*?)"` |

### **3.3 M3U8Extractor**

**Untuk:** Extractor khusus M3U8 playlist dengan auto quality detection.

```kotlin
abstract class M3U8Extractor : BaseExtractor() {
    override val urlType = ExtractorLinkType.M3U8
    override val extractUrlRegex = Regex("file:\\s*\"(.*?m3u8.*?)\"")

    // Override untuk custom M3U8 parsing
    protected open fun parseM3U8Playlist(playlistUrl: String): List<ExtractorLink> {
        // Default: ambil semua variant dari M3U8
        val playlist = app.get(playlistUrl).text
        return parseM3U8Variants(playlist, playlistUrl)
    }
}
```

**Extractor yang cocok (2):**
| Extractor | Regex |
|-----------|-------|
| Dingtezuni | (perlu custom M3U8 parse) |
| Dintezuvio | (sama seperti Dingtezuni) |

### **3.4 MultiUrlExtractor**

**Untuk:** Extractor yang menghasilkan multiple video URLs sekaligus.

```kotlin
abstract class MultiUrlExtractor : BaseExtractor() {
    // Regex yang match multiple URLs
    abstract val extractAllUrlsRegex: Regex

    override suspend fun getUrl(
        url: String, referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val content = fetchContent(url, referer)
        for (match in extractAllUrlsRegex.findAll(content)) {
            val videoUrl = match.groupValues[1].replace("\\/", "/")
            callback.invoke(buildLink(videoUrl))
        }
    }
}
```

---

## 🔧 4. Extractor yang TETAP MANUAL (Custom Logic)

**Alasan:** Terlalu unik, tidak cocok pattern standar.

| Extractor | Alasan | Baris |
|-----------|--------|-------|
| **Odnoklassniki** | JSON `"videos":[]` parsing + unicode `\\uXXXX` decode | 50 |
| **Dailymotion** | `cdndirector` vs embed player detection | 20 |
| **Veev** | LZW decompression + Caesar cipher + hex decode | 120 |
| **Gofile** | API POST → JSON tree traversal → multiple files | 40 |
| **EmturbovidExtractorM3U8** | XPath → `var urlPlay` → M3U8 playlist parsing | 40 |
| **HUBCDN** | `var reurl` → base64 decode → link extract | 20 |
| **Vidguardto** | Multi-step: POST → decrypt → M3U8 parse | (extends VidHidePro) |

**Total:** 7 extractor tetap manual = ~300 baris (dari 2400 total → masih 87% reduction)

---

## 📊 5. Estimasi Setelah Refactor

| Kelompok | Sebelum (baris) | Sesudah (baris) | Reduction |
|----------|----------------|----------------|-----------|
| **BaseExtractor** | 0 | ~200 | - |
| **SimpleRegexExtractor** (7) | ~100 | ~40 | 60% |
| **PackedJsExtractor** (3) | ~100 | ~25 | 75% |
| **M3U8Extractor** (2) | ~100 | ~30 | 70% |
| **MultiUrlExtractor** (3) | ~50 | ~35 | 30% |
| **VidStack Family** (10) | ~50 | ~50 | 0% (extends CloudStream) |
| **StreamWish Family** (6) | ~30 | ~30 | 0% (extends CloudStream) |
| **Custom Manual** (7) | ~350 | ~350 | 0% (tidak refactor) |
| **CloudStream extends** (11) | ~55 | ~55 | 0% (sudah efisien) |
| **TOTAL** | **~2400** | **~815** | **66% reduction** |

---

## 📋 6. Tahapan Implementasi (Step-by-Step)

### **Phase 1: Buat Base Class (1 jam)**

1. Buat `MasterBaseExtractor.kt`
2. Implement `BaseExtractor` abstract class
3. Implement `SimpleRegexExtractor`, `PackedJsExtractor`, `M3U8Extractor`
4. Tambah import di sync script

### **Phase 2: Migrate Simple Regex Extractors (30 menit)**

| Extractor | Regex | Test URL |
|-----------|-------|----------|
| Rumble | `"hls":{"url":"(https?:[^"]+playlist\.m3u8)"` | `https://rumble.com/embed/v75rli2/` |
| Archivd | `(https?://archive\.org/[^"']+)` | `https://archive.org/embed/...` |
| HUBCDN | `reurl\s*=\s*"([^"]+)"` + base64 decode | `https://hubcdn...` |

### **Phase 3: Migrate Packed JS Extractors (30 menit)**

| Extractor | Regex | Test URL |
|-----------|-------|----------|
| Voe | `['"]hls['"]\s*:\s*['"]([^'"]+)['"]` | `https://voe.sx/e/...` |
| MixDropBz | `video_src\s*=\s*["'](.*?)["']` | `https://m1xdrop.bz/e/...` |
| FilemoonNlExtractor | `file:\s*"(.*?m3u8.*?)"` | `https://filemoon.nl/e/...` |

### **Phase 4: Migrate M3U8 Extractors (20 menit)**

| Extractor | Test URL |
|-----------|----------|
| Dingtezuni | `https://dingtezuni.com/...` |
| Dintezuvio | `https://dintezuvio.com/...` |

### **Phase 5: Test & Verify (1 jam)**

1. Jalankan `node tests/extractors/test-comprehensive.js`
2. Bandingkan hasil test sebelum & sesudah refactor
3. Fix jika ada extractor yang broken

### **Phase 6: Update SyncExtractors List (10 menit)**

1. Register semua extractor baru di `SyncExtractors.list`
2. Jalankan sync script
3. Commit & push

---

## ⚠️ 7. Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
|--------|--------|----------|
| Regex tidak match di beberapa URL | Extractor gagal | Test dengan 2+ URL per extractor |
| `getAndUnpack()` behavior beda | Packed JS tidak ter-unpack | Tetap override manual jika perlu |
| Sync script tidak include BaseExtractor | Extractor not found | Tambah `MasterBaseExtractor.kt` ke sync list |
| Breaking change di CloudStream API | Semua extractor broken | Tetap gunakan CloudStream function (`app.get()`, `getPacked()`, dll) |

---

## ✅ 8. Checklist Sebelum Commit

- [ ] BaseExtractor compile tanpa error
- [ ] Semua 7 SimpleRegexExtractor migrate & test
- [ ] Semua 3 PackedJsExtractor migrate & test
- [ ] Semua 2 M3U8Extractor migrate & test
- [ ] Custom extractors tetap jalan (tidak diubah)
- [ ] SyncExtractors list updated
- [ ] Sync script include MasterBaseExtractor.kt
- [ ] Test suite pass (10+ URLs working)
- [ ] Build GitHub Actions sukses
- [ ] Documentasi updated

---

## 📝 9. Referensi Code dari ExtCloud/Phisher

| Pattern | Source File | Line |
|---------|------------|------|
| Packed JS + `file:` regex | Phisher/StreamPlay/Extractors.kt | Ridoo class |
| `video_src=` regex | ExtCloud/AnimeSail/Extractors.kt | MixDropBz |
| `var urlPlay` XPath | ExtCloud/Layarasia/Extractors.kt | EmturbovidExtractor |
| `"hls":{"url":"..."}" | Manual test (verified via curl) | Rumble |
| API POST + JSON | Phisher/DudeFilms/Extractors.kt | Gofile |
| LZW + Caesar cipher | MasterExtractors.kt (existing) | Veev |

---

**Apakah rancangan detail ini sudah sesuai? Jika ya, saya mulai implementasi Phase 1.**
