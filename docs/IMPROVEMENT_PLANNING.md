# 🏗️ Improvement Planning — Deep Analysis

**Tanggal:** 2026-04-13
**Status:** Planning (belum implementasi)

---

## POINT 3: Standardisasi Error Handling

### Analisa Saat Ini

| Provider | Pattern | Status |
|----------|---------|--------|
| Anichin | `runCatching { }.getOrElse { null }` | ✅ Bagus |
| Animasu | `runCatching { }.getOrElse { null }` | ✅ Bagus |
| Donghuastream | `try/catch` di beberapa tempat | ⚠️ Inconsistent |
| Funmovieslix | `try/catch` di loadLinks | ⚠️ Inconsistent |
| Idlix | `try/catch` di loadLinks | ⚠️ Inconsistent |
| LayarKaca21 | Campur `runCatching` + `try/catch` | ⚠️ Inconsistent |
| Pencurimovie | `try/catch` di loadLinks | ⚠️ Inconsistent |
| Samehadaku | `runCatching { }.getOrElse { null }` | ✅ Bagus |

### File yang Perlu Diubah

```
Donghuastream/Donghuastream.kt     — 3 tempat (loadLinks episode parsing)
Funmovieslix/Funmovieslix.kt        — 2 tempat (loadLinks, search)
Idlix/Idlix.kt                      — 2 tempat (loadLinks, getMainPage fallback)
LayarKaca21/LayarKaca21.kt          — 2 tempat (loadLinks, getIframe)
Pencurimovie/Pencurimovie.kt        — 3 tempat (loadLinks, deepResolve, universalExtract)
```

### Pattern Baru yang Akan Digunakan

```kotlin
// SEBELUM (berpotensi crash)
try {
    val data = someOperation()
    // process data
} catch (e: Exception) {
    logError("Provider", "Failed: ${e.message}", e)
}

// SESUDAH (aman, tidak crash)
runCatching {
    someOperation()
}.onSuccess { data ->
    // process data
}.onFailure { e ->
    logError("Provider", "Failed: ${e.message}", e)
}
```

### Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
|--------|--------|----------|
| `runCatching` tidak capture semua exception | Sedang | Tetap pakai `try/catch` untuk operasi yang butuh cleanup |
| Logging terlalu verbose | Rendah | Hanya log error, bukan setiap retry |
| Break existing logic | Rendah | Test setiap provider setelah perubahan |

### Urutan Implementasi (paling aman dulu)

1. Donghuastream (paling sedikit perubahan — 3 tempat)
2. Funmovieslix (2 tempat)
3. Pencurimovie (3 tempat)
4. LayarKaca21 (2 tempat)
5. Idlix (2 tempat)

---

## POINT 4: Code Duplication → Shared Helpers

### Analisa Duplikasi

**getImageAttr()** — identik di 3 file:
```
Anichin.kt line ~190
Donghuastream.kt line ~105
LayarKaca21.kt line ~473
```

Kode identik:
```kotlin
private fun Element.getImageAttr(): String {
    return when {
        this.hasAttr("data-src") -> this.attr("data-src")
        this.hasAttr("src") -> this.attr("src")
        else -> this.attr("src")
    }
}
```

**optimizeImageUrl()** — identik di 2 file:
```
Anichin.kt line ~320
Donghuastream.kt line ~256
```

Kode identik:
```kotlin
private fun optimizeImageUrl(url: String, maxWidth: Int = 500): String {
    return when {
        url.contains("domain") -> url  // no-op
        else -> url
    }
}
```

### Solusi: Pindahkan ke MasterUtils.kt

```kotlin
// Di master/MasterUtils.kt (atau MasterSharedHelpers.kt baru)

/**
 * Extract image URL dari element dengan fallback strategy.
 * Cek data-src → src → default src.
 */
fun Element.extractImageAttr(): String {
    return when {
        this.hasAttr("data-src") -> this.attr("data-src")
        this.hasAttr("src") -> this.attr("src")
        else -> this.attr("src")
    }
}

/**
 * Optimize image URL untuk mobile (placeholder untuk future resize logic).
 * Saat ini return URL apa adanya.
 */
fun optimizeImageUrl(url: String, maxWidth: Int = 500): String {
    return url
}
```

### File yang Perlu Diubah

```
master/MasterUtils.kt              — Tambah 2 shared functions
Anichin/Anichin.kt                 — Hapus getImageAttr(), panggil extractImageAttr()
Donghuastream/Donghuastream.kt     — Hapus getImageAttr() + optimizeImageUrl()
LayarKaca21/LayarKaca21.kt         — Hapus getImageAttr()
```

### Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
|--------|--------|----------|
| Nama function berbeda | Rendah | Gunakan `extractImageAttr()` untuk bedakan dari method bawaan |
| Break provider jika import lupa | Sedang | Sync workflow akan auto-copy ke semua providers |
| Provider punya custom logic yang berbeda | Rendah | Cek dulu apakah ada perbedaan |

### Checklist Sebelum Implementasi

- [ ] Verifikasi semua getImageAttr() identik
- [ ] Verifikasi semua optimizeImageUrl() identik
- [ ] Test compile setelah perubahan di 1 provider
- [ ] Test di app setelah sync ke semua providers

---

## POINT 5: URL Test Database Expansion

### Status Saat Ini

```
Total URLs: 22
Groups: 6 (okru, dailymotion, doodstream, streamruby, rumble, krakenfiles)
Series source: 21 Anichin series
```

### Target

```
Total URLs: 50+
Groups: 8-10
Series source: 50+ dari berbagai providers
```

### Rencana

1. **Tambah series dari Anichin** — scrape 30 series tambahan
2. **Tambah URL manual** — untuk extractors yang belum ada URL test:
   - Filemoon
   - MixDrop
   - Gofile
   - HubCDN
3. **Auto-collector update** — tambah lebih banyak series ke PROVIDERS array

### File yang Perlu Diubah

```
scripts/collect-extractor-urls.sh    — Tambah 30+ series URLs
tests/extractors/test-data/extractor-urls.json  — Tambah URLs baru
```

### Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
|--------|--------|----------|
| Series URL berubah/deleted | Rendah | Gunakan series populer yang stabil |
| Rate-limit dari server | Sedang | Delay 1-2 detik antar request |
| URL expired/time-limited | Sedang | Refresh URL secara berkala |

### Checklist

- [ ] Update collect-extractor-urls.sh dengan 30+ series baru
- [ ] Jalankan collector untuk scrape URLs
- [ ] Filter URLs yang masih valid
- [ ] Update extractor-urls.json
- [ ] Jalankan test suite untuk verifikasi

---

## POINT 6: Missing Extractors dari Referensi

### Extractors yang Potentially Useful

| Extractor | Source | Kegunaan |
|-----------|--------|----------|
| FilemoonNl | Phisher/StreamPlay | Filemoon variant |
| MixDropBz | ExtCloud/AnimeSail | MixDrop video host |
| Gofile | Phisher/DudeFilms | Gofile CDN API |
| HUBCDN | Phisher/StreamPlay | HubCDN redirect |
| CdnwishCom | Phisher | CDN variant |
| Dropload | Phisher | Cloud drop host |
| Krakenfiles | Phisher | Kraken video host |
| Embedrise | Phisher | Embed host |
| Akamaicdn | Phisher | Akamai CDN |
| AWSStream | Phisher | AWS video stream |

### Status: Sudah Ada 5 Extractor Baru

Dari commit sebelumnya, sudah ditambahkan:
- ✅ EmturbovidExtractorM3U8
- ✅ MixDropBz
- ✅ FilemoonNlExtractor
- ✅ Gofile
- ✅ HUBCDN

### Extractors Tambahan yang Masih Bisa Ditambah

| Extractor | Priority | Alasan |
|-----------|----------|--------|
| Dropload | Medium | Dipakai beberapa provider Indo |
| CdnwishCom | Medium | CDN variant populer |
| Embedrise | Low | Backup embed host |
| AWSStream | Low | AWS-based streaming |

### File yang Perlu Diubah

```
master/MasterExtractors.kt         — Tambah 4 extractor baru
```

### Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
|--------|--------|----------|
| Extractor tidak pernah dipakai | Rendah | Hanya tambahkan yang terbukti dipakai |
| Build error karena API changes | Sedang | Test compile sebelum commit |
| Name collision dengan extractor existing | Rendah | Cek dulu nama tidak duplikat |

### Checklist

- [ ] Baca source code dari referensi
- [ ] Adaptasi ke pattern MasterExtractors.kt
- [ ] Test compile
- [ ] Tambah ke SyncExtractors.list
- [ ] Sync ke semua providers
- [ ] Build test

---

## POINT 7: Connection Pre-warming Measurement

### Status Saat Ini

Pre-warming sudah diimplementasikan di semua 8 providers:
- Anichin ✅
- Animasu ✅
- Donghuastream ✅
- Funmovieslix ✅
- Idlix ✅
- LayarKaca21 ✅
- Pencurimovie ✅
- Samehadaku ✅

### Yang Kurang: Logging & Timing

Saat ini pre-warming jalan tanpa logging impact. Tidak ada cara tahu apakah:
- Berhasil atau gagal
- Berapa ms penghematannya
- URL mana yang gagal di-prewarm

### Rencana

Tambah logging ke `MasterHttpClientFactory.kt`:

```kotlin
suspend fun preWarmConnection(url: String) {
    val start = System.currentTimeMillis()
    // ... existing code ...
    val duration = System.currentTimeMillis() - start
    if (duration > 500) {
        Log.d("HttpClientFactory", "🔥 Pre-warm slow: $url (${duration}ms)")
    } else if (DEBUG_MODE) {
        Log.d("HttpClientFactory", "🔥 Pre-warmed: $url (${duration}ms)")
    }
}
```

Dan di provider `getMainPage()`:

```kotlin
val start = System.currentTimeMillis()
// ... preWarmConnection calls ...
val duration = System.currentTimeMillis() - start
logDebug("Provider", "Pre-warm completed in ${duration}ms for ${urls.size} URLs")
```

### File yang Perlu Diubah

```
master/MasterHttpClientFactory.kt  — Tambah timing logging
Anichin/Anichin.kt                 — Tambah timing di loadLinks
Donghuastream/Donghuastream.kt     — Tambah timing di loadLinks
Idlix/Idlix.kt                     — Tambah timing di getMainPage
```

### Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
|--------|--------|----------|
| Logging terlalu verbose | Rendah | Hanya log jika >500ms atau DEBUG_MODE |
| Overhead timing calculation | Sangat rendah | System.currentTimeMillis() sangat ringan |
| Break existing logic | Sangat rendah | Hanya tambah logging, tidak ubah logic |

### Checklist

- [ ] Tambah timing ke MasterHttpClientFactory.preWarmConnection()
- [ ] Tambah timing logging di 2-3 providers
- [ ] Test compile
- [ ] Test di app (cek logcat untuk timing logs)

---

## POINT 8: Dokumentasi Extractor Patterns

### Status Saat Ini

```
docs/EXTRACTOR_KNOWLEDGE_BASE.md — 5 extractors documented
Total extractors — 41
Coverage — 12%
```

### Target

```
Coverage — 50%+ (20+ extractors)
Format — Tabel: Extractor | Pattern | URL Test | Status
```

### Format Dokumentasi

```markdown
## Extractor: OK.ru (Odnoklassniki)

**Pattern:** JSON `"videos":[...]` setelah unicode decode
**Base Class:** RegexExtractor
**Requires:** Origin header, unicode decode (`\uXXXX`)
**Test URLs:** 5/5 working
**Notes:** 6 quality variants (144p-1080p)
```

### File yang Perlu Diubah

```
docs/EXTRACTOR_KNOWLEDGE_BASE.md   — Tambah 15+ extractor entries
```

### Extractors yang Harus Didokumentasikan (Priority)

1. Odnoklassniki / OK.ru
2. Dailymotion
3. Rumble
4. StreamRuby
5. Veev
6. Voe
7. MixDropBz
8. FilemoonNlExtractor
9. Gofile
10. HUBCDN
11. EmturbovidExtractorM3U8
12. DoodStream (Dsvplay)
13. Hownetwork
14. Jeniusplay
15. ArchiveOrg
16. Blogger
17. PixelDrain
18. Megacloud
19. Vidguard
20. Dingtezuni / Dintezuvio

### Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
|--------|--------|----------|
| Dokumentasi tidak akurat | Rendah | Test URL sebelum dokumentasi |
| Dokumentasi cepat outdated | Rendah | Update saat ada perubahan |
| Terlalu banyak tulisan | Rendah | Gunakan format tabel yang ringkas |

### Checklist

- [ ] Buat template entry per extractor
- [ ] Dokumentasikan 10 extractor utama
- [ ] Dokumentasikan 10 extractor tambahan
- [ ] Tambah test URL per entry
- [ ] Commit dokumentasi

---

## POINT 9: Logging Standardization

### Analisa Saat Ini

| Provider | logDebug() | Log.d() | Log.e() | logError() | Status |
|----------|------------|---------|---------|------------|--------|
| Anichin | 12 | 0 | 4 | 5 | ⚠️ Campur Log.e |
| Animasu | 3 | 3 | 0 | 2 | ⚠️ Campur Log.d |
| Donghuastream | 2 | 0 | 1 | 2 | ⚠️ Campur Log.e |
| Funmovieslix | 3 | 0 | 2 | 1 | ⚠️ Campur Log.e |
| Idlix | 3 | 0 | 1 | 3 | ⚠️ Campur Log.e |
| LayarKaca21 | 2 | 2 | 2 | 1 | ⚠️ Campur Log.d/Log.e |
| Pencurimovie | 1 | 0 | 3 | 1 | ⚠️ Campur Log.e |
| Samehadaku | 4 | 0 | 0 | 2 | ✅ Bagus |

### Standardisasi

**Ganti semua:**
- `Log.d("Tag", msg)` → `logDebug("Tag", msg)`
- `Log.e("Tag", msg)` → `logError("Tag", msg)`
- `Log.w("Tag", msg)` → `logWarning("Tag", msg)`

### File yang Perlu Diubah

```
Anichin/Anichin.kt              — 4 Log.e → logError
Animasu/Animasu.kt              — 3 Log.d → logDebug
Donghuastream/Donghuastream.kt  — 1 Log.e → logError
Funmovieslix/Funmovieslix.kt    — 2 Log.e → logError
Idlix/Idlix.kt                  — 1 Log.e → logError
LayarKaca21/LayarKaca21.kt      — 2 Log.d + 2 Log.e → logDebug + logError
Pencurimovie/Pencurimovie.kt    — 3 Log.e → logError
```

### Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
|--------|--------|----------|
| Signature berbeda | Sedang | Cek signature logDebug/logError di MasterUtils |
| Import tidak ada | Rendah | Sudah ada di generated_sync |
| Break logging output | Sangat rendah | Output sama, hanya beda nama function |

### Checklist

- [ ] Cek signature logDebug/logError di MasterUtils.kt
- [ ] Ganti semua Log.d → logDebug
- [ ] Ganti semua Log.e → logError
- [ ] Hapus import `android.util.Log` jika tidak dipakai lagi
- [ ] Test compile

---

## POINT 10: Cache TTL Standardization

### Analisa Saat Ini

| Provider | searchCache TTL | loadCache TTL | mainPageCache TTL | Status |
|----------|----------------|---------------|-------------------|--------|
| Anichin | Default (none) | - | Default (none) | ⚠️ No TTL |
| Animasu | 5 min | 10 min | 3 min | ✅ Explicit |
| Donghuastream | Default (none) | - | Default (none) | ⚠️ No TTL |
| Funmovieslix | Default (none) | - | Default (none) | ⚠️ No TTL |
| Idlix | Default (none) | - | Default (none) | ⚠️ No TTL |
| LayarKaca21 | Default (none) | - | Default (none) | ⚠️ No TTL |
| Pencurimovie | - | - | Default (none) | ⚠️ No TTL |
| Samehadaku | 5 min | 10 min | 3 min | ✅ Explicit |

### Standardisasi TTL

| Cache Type | TTL | Alasan |
|------------|-----|--------|
| searchCache | 30 min | Search result jarang berubah |
| mainPageCache | 5 min | Main page sering update (new episodes) |
| loadCache | 10 min | Detail page stabil tapi bisa ada update |

### File yang Perlu Diubah

```
Anichin/Anichin.kt                — Tambah TTL ke cache declarations
Donghuastream/Donghuastream.kt    — Tambah TTL ke cache declarations
Funmovieslix/Funmovieslix.kt      — Tambah TTL ke cache declarations
Idlix/Idlix.kt                    — Tambah TTL ke cache declarations
LayarKaca21/LayarKaca21.kt        — Tambah TTL ke cache declarations
Pencurimovie/Pencurimovie.kt      — Tambah TTL ke cache declarations
```

### Perubahan yang Akan Dilakukan

```kotlin
// SEBELUM
private val searchCache = CacheManager<List<SearchResponse>>()
private val mainPageCache = CacheManager<HomePageResponse>()

// SESUDAH
private val searchCache = CacheManager<List<SearchResponse>>(defaultTtl = 30 * 60 * 1000L)
private val mainPageCache = CacheManager<HomePageResponse>(defaultTtl = 5 * 60 * 1000L)
```

### Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
|--------|--------|----------|
| TTL terlalu pendek → banyak request | Sedang | Mulai dengan TTL konservatif (5-30 min) |
| TTL terlalu panjang → stale data | Sedang | Monitor cache hit rate |
| API tidak support TTL param | Rendah | CacheManager di repo sudah support TTL |
| Break existing behavior | Rendah | Default cache behavior tetap sama |

### Checklist

- [ ] Verifikasi CacheManager support `defaultTtl` parameter
- [ ] Tambah TTL ke 6 providers
- [ ] Test compile
- [ ] Test di app (cek cache behavior)

---

## 📋 URUTAN IMPLEMENTASI YANG AMAN

Urutan ini dirancang agar setiap perubahan **independen** dan **tidak saling bergantung**:

### Phase 1: Dokumentasi (Tidak ada risiko)
1. Point 8 — Dokumentasi extractor patterns

### Phase 2: Logging & Observability (Risiko sangat rendah)
2. Point 9 — Logging standardization
3. Point 7 — Pre-warming measurement

### Phase 3: Code Quality (Risiko rendah)
4. Point 4 — Shared helpers (getImageAttr, optimizeImageUrl)
5. Point 3 — Error handling standardization

### Phase 4: Caching & Testing (Risiko sedang)
6. Point 10 — Cache TTL standardization
7. Point 5 — URL test database expansion

### Phase 5: New Features (Risiko sedang)
8. Point 6 — Missing extractors (jika masih diperlukan)

---

## ⚠️ HAL YANG HARUS DIPERHATIKAN

### Yang TIDAK BOLEH diubah:
- URL patterns / selectors (bisa break scraping)
- Core extractor logic (sudah tested)
- `app.get()` / `app.post()` call patterns
- Response parsing logic
- Any working code paths

### Yang AMAN diubah:
- Logging function names (Log.d → logDebug)
- Cache TTL values
- Extractor registration
- Documentation
- Shared helper extraction (refactor only)
- Timing/performance logging

### Testing Strategy:
Setiap perubahan harus:
1. Compile tanpa error
2. Tidak mengubah behavior (hanya improve code quality)
3. Build CI/CD sukses sebelum push

---

**Apakah planning ini sudah sesuai? Mau langsung mulai implementasi Phase 1?**
