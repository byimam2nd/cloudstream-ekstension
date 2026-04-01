# 📊 ANALISIS: PENGGUNAAN SYNC EXTRACTORS

**Objective**: Mengecek apakah semua provider sudah menggunakan `SyncExtractors` dari `generated_sync/`

**Date**: 2026-04-01

---

## ✅ **STATUS PENGGUNAAN SYNC EXTRACTORS**

### **SUMMARY:**

| Provider | Plugin Uses SyncExtractors? | loadLinks Uses SyncExtractors? | Status |
|----------|----------------------------|--------------------------------|--------|
| **Anichin** | ✅ Yes | ✅ Yes (manual implementation) | ✅ COMPLETE |
| **Animasu** | ✅ Yes | ✅ Yes (loadExtractorWithFallback) | ✅ COMPLETE |
| **Donghuastream** | ✅ Yes | ✅ Yes (loadExtractorWithFallback) | ✅ COMPLETE |
| **SeaTV** | ❌ No (extends Donghuastream) | ❌ No (uses old loadExtractor) | ⚠️ NEEDS UPDATE |
| **Funmovieslix** | ✅ Yes | ✅ Yes (loadExtractorWithFallback) | ✅ COMPLETE |
| **Idlix** | ✅ Yes | ✅ Yes (loadExtractorWithFallback) | ✅ COMPLETE |
| **LayarKaca21** | ✅ Yes | ✅ Yes (loadExtractorWithFallback) | ✅ COMPLETE |
| **Pencurimovie** | ✅ Yes | ✅ Yes (loadExtractorWithFallback) | ✅ COMPLETE |
| **Samehadaku** | ✅ Yes | ✅ Yes (loadExtractorWithFallback) | ✅ COMPLETE |

---

## 📊 **DETAILED ANALYSIS**

### **1. PLUGIN REGISTRATION - SEMUA SUDAH MENGGUNAKAN SYNC EXTRACTORS** ✅

**SEMUA 8 Provider Plugin sudah benar:**

```kotlin
// ✅ ANICHIN PLUGIN
package com.Anichin

import com.Anichin.generated_sync.SyncExtractors

@CloudstreamPlugin
class AnichinPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Anichin())
        
        // ✅ DYNAMIC REGISTER: Auto-register ALL extractors
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}

// ✅ ANIMASU PLUGIN
package com.Animasu

import com.Animasu.generated_sync.SyncExtractors

@CloudstreamPlugin
class AnimasuPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Animasu())
        
        // ✅ DYNAMIC REGISTER
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}

// ✅ DONGHUASTREAM PLUGIN
package com.Donghuastream

import com.Donghuastream.generated_sync.SyncExtractors

@CloudstreamPlugin
class DonghuastreamPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Donghuastream())
        
        // ✅ DYNAMIC REGISTER
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}

// ✅ FUNMOVIESLIX PLUGIN
package com.Funmovieslix

import com.Funmovieslix.generated_sync.SyncExtractors

@CloudstreamPlugin
class FunmovieslixPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Funmovieslix())
        
        // ✅ DYNAMIC REGISTER
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}

// ✅ IDLIX PLUGIN
package com.Idlix

import com.Idlix.generated_sync.SyncExtractors

@CloudstreamPlugin
class IdlixPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Idlix())
        
        // ✅ DYNAMIC REGISTER
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}

// ✅ LAYARKACA21 PLUGIN
package com.LayarKaca21

import com.LayarKaca21.generated_sync.SyncExtractors

@CloudstreamPlugin
class LayarKaca21Plugin: BasePlugin() {
    override fun load() {
        registerMainAPI(LayarKaca21())
        
        // ✅ DYNAMIC REGISTER
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}

// ✅ PENCURIMOVIE PLUGIN
package com.Pencurimovie

import com.Pencurimovie.generated_sync.SyncExtractors

@CloudstreamPlugin
class PencurimoviePlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Pencurimovie())
        
        // ✅ DYNAMIC REGISTER
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}

// ✅ SAMEHADAKU PLUGIN
package com.Samehadaku

import com.Samehadaku.generated_sync.SyncExtractors

@CloudstreamPlugin
class SamehadakuPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Samehadaku())
        
        // ✅ DYNAMIC REGISTER
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
```

**Status**: ✅ **SEMUA 8 PLUGIN SUDAH BENAR**

Tidak ada lagi hardcoding extractor satu-per-satu!

---

### **2. LOAD LINKS IMPLEMENTATION**

#### **✅ YANG SUDAH MENGGUNAKAN `loadExtractorWithFallback`** (7 providers):

**Animasu, Donghuastream, Funmovieslix, Idlix, LayarKaca21, Pencurimovie, Samehadaku**

```kotlin
// ✅ CONTOH: ANIMASU
override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    // Check cache first
    if (EpisodePreFetcher.loadCached(data, callback, subtitleCallback)) {
        return true
    }
    
    val document = executeWithRetry { ... }
    
    // Extract player links
    val playerLinks = document.select(".mobius > .mirror > option").mapNotNull { ... }
    
    // ✅ USE loadExtractorWithFallback
    playerLinks.amap { (iframe, quality) ->
        try {
            loadFixedExtractor(iframe, quality, "$mainUrl/", subtitleCallback, callback)
        } catch (e: Exception) {
            logError("Animasu", "Failed: ${e.message}")
        }
    }
    
    return true
}

private suspend fun loadFixedExtractor(
    url: String,
    quality: String?,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
) {
    // ✅ USE loadExtractorWithFallback dari generated_sync
    val loaded = com.Animasu.generated_sync.loadExtractorWithFallback(
        url = url,
        referer = referer,
        subtitleCallback = subtitleCallback
    ) { link ->
        runBlocking {
            MasterLinkGenerator.createLink(
                source = link.name,
                url = link.url,
                referer = link.referer,
                quality = ...,
                headers = link.headers
            )?.let { extractorLink ->
                extractorLink.extractorData = link.extractorData
                callback.invoke(extractorLink)
            }
        }
    }
    
    if (!loaded) {
        Log.e("Animasu", "loadFixedExtractor failed for $url")
    }
}
```

**Benefit**:
- ✅ Auto-fallback ke SyncExtractors jika loadExtractor gagal
- ✅ CircuitBreaker untuk failure isolation
- ✅ Parallel extraction

---

#### **⚠️ ANICHIN - IMPLEMENTASI MANUAL (TAPI BENAR)**

**Anichin TIDAK menggunakan `loadExtractorWithFallback`, tapi implementasi manual yang SAMA:**

```kotlin
// ANICHIN - Manual implementation (tapi logic sama dengan loadExtractorWithFallback)
override suspend fun loadLinks(...) {
    // ... extract iframe ...
    
    when {
        iframeUrl.endsWith(".mp4") -> {
            // Direct MP4
            MasterLinkGenerator.createLink(...)
        }
        else -> {
            // Step 1: Try loadExtractor (CloudStream API)
            var loaded = false
            try {
                loaded = loadExtractor(iframeUrl, referer = data, subtitleCallback, callback)
            } catch (e: Exception) {
                logError("Anichin", "loadExtractor exception: ${e.message}")
            }
            
            // Step 2: If failed, try direct extractor call from SyncExtractors
            if (!loaded) {
                logDebug("Anichin", "loadExtractor failed, trying direct extractor from SyncExtractors...")
                
                // Find ALL matching extractors from SyncExtractors list
                val iframeDomain = iframeUrl.removePrefix("http://").removePrefix("https://")
                    .split("/").first().lowercase()
                
                val matchingExtractors = com.Anichin.generated_sync.SyncExtractors.list
                    .filter { extractor ->
                        val extractorDomain = extractor.mainUrl
                            .removePrefix("http://").removePrefix("https://")
                            .split("/").first().lowercase()
                        val domainMatch = iframeDomain.contains(extractorDomain) || 
                                         extractorDomain.contains(iframeDomain)
                        val nameMatch = iframeUrl.contains(extractor.name, ignoreCase = true)
                        domainMatch || nameMatch
                    }
                
                logDebug("Anichin", "Found ${matchingExtractors.size} matching extractors")
                
                // Try ALL matching extractors
                matchingExtractors.forEach { extractor ->
                    try {
                        logDebug("Anichin", "Trying extractor: ${extractor.name}")
                        extractor.getUrl(iframeUrl, data, subtitleCallback, callback)
                        logDebug("Anichin", "SUCCESS: Extractor ${extractor.name} worked!")
                    } catch (e: Exception) {
                        logError("Anichin", "Extractor ${extractor.name} failed: ${e.message}")
                    }
                }
            }
        }
    }
}
```

**Status**: ✅ **BENAR** (tapi lebih baik gunakan `loadExtractorWithFallback` untuk konsistensi)

**Recommendation**: Refactor untuk gunakan `loadExtractorWithFallback`

---

#### **❌ SEATV - MASIH MENGGUNAKAN CARA LAMA**

**SeaTV TIDAK menggunakan `SyncExtractors` atau `loadExtractorWithFallback`:**

```kotlin
// ❌ SEATV - Old implementation (needs update!)
open class SeaTV : Donghuastream() {
    override var mainUrl = "https://seatv-24.xyz"
    override var name = "SeaTV"
    
    // ... search implementation ...
    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).documentLarge
        
        // ❌ TIDAK menggunakan loadExtractorWithFallback
        // ❌ TIDAK menggunakan SyncExtractors
        document.select(".mobius option").amap { server ->
            val base64 = server.attr("value").takeIf { it.isNotEmpty() }
            val doc = base64?.let { base64Decode(it).let(Jsoup::parse) }
            val iframeUrl = doc?.select("iframe")?.attr("src")?.let(::httpsify)
            val metaUrl = doc?.select("meta[itemprop=embedUrl]")?.attr("content")?.let(::httpsify)
            val url = iframeUrl?.takeIf { it.isNotEmpty() } ?: metaUrl.orEmpty()
            
            if (url.isNotEmpty()) {
                when {
                    url.contains("vidmoly") -> {
                        val newUrl = url.substringAfter("=\"").substringBefore("\"")
                        val link = "http:$newUrl"
                        
                        // ❌ OLD: Direct loadExtractor call (no fallback!)
                        loadExtractor(link, referer = url, subtitleCallback, callback)
                    }
                    url.endsWith("mp4") -> {
                        callback.invoke(
                            newExtractorLink("All Sub Player", "All Sub Player", url = url, INFER_TYPE) {
                                this.referer = ""
                                this.quality = getQualityFromName("")
                            }
                        )
                    }
                    else -> {
                        // ❌ OLD: Direct loadExtractor call (no fallback!)
                        loadExtractor(url, referer = url, subtitleCallback, callback)
                    }
                }
            }
        }
        return true
    }
}
```

**Masalah**:
- ❌ Tidak ada fallback ke SyncExtractors
- ❌ Jika loadExtractor gagal, tidak ada alternatif
- ❌ Tidak ada CircuitBreaker protection
- ❌ Tidak konsisten dengan provider lain

**Recommendation**: **HARUS UPDATE** untuk gunakan `loadExtractorWithFallback`

---

### **3. SYNC EXTRACTORS LOCATION**

**Setiap provider punya `SyncExtractors` di `generated_sync/`:**

```
Anichin/
└── src/main/kotlin/com/Anichin/generated_sync/
    └── SyncExtractors.kt  ← Auto-generated dari master/MasterExtractors.kt

Animasu/
└── src/main/kotlin/com/Animasu/generated_sync/
    └── SyncExtractors.kt  ← Auto-generated

... (semua provider punya yang sama)
```

**Isi `SyncExtractors.kt`:**

```kotlin
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from master/MasterExtractors.kt

package com.{Provider}.generated_sync

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
// ... imports ...

object SyncExtractors {
    val list = listOf(
        // Semua extractor APIs (200+ extractors)
        VidGuard(),
        Voe(),
        Filemoon(),
        Mixdrop(),
        Vidhide(),
        Streamwish(),
        Dood(),
        // ... 200+ extractors
    )
}
```

**Source of Truth**: `master/MasterExtractors.kt` (1,730 lines)

---

## 📊 **COMPARISON: OLD vs NEW**

### **OLD WAY (❌ TIDAK DISARANKAN):**

```kotlin
// ❌ Hardcode extractor registration (satu-per-satu)
@CloudstreamPlugin
class OldPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(OldProvider())
        
        // ❌ HARDCODE - harus update manual kalau ada extractor baru
        registerExtractorAPI(VidGuard())
        registerExtractorAPI(Voe())
        registerExtractorAPI(Filemoon())
        registerExtractorAPI(Mixdrop())
        // ... 200+ lines
    }
}

// ❌ Direct loadExtractor call (no fallback)
override suspend fun loadLinks(...) {
    val iframeUrl = extractIframe(document)
    
    // ❌ Jika loadExtractor gagal, tidak ada alternatif
    loadExtractor(iframeUrl, referer, subtitleCallback, callback)
}
```

**Problems**:
- ❌ Harus update manual kalau ada extractor baru
- ❌ Riskan lupa register extractor
- ❌ Tidak ada fallback jika loadExtractor gagal
- ❌ Inconsistent across providers

---

### **NEW WAY (✅ RECOMMENDED):**

```kotlin
// ✅ Dynamic extractor registration
@CloudstreamPlugin
class NewPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(NewProvider())
        
        // ✅ AUTO-REGISTER semua extractor dari SyncExtractors
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}

// ✅ loadExtractorWithFallback
override suspend fun loadLinks(...) {
    val iframeUrl = extractIframe(document)
    
    // ✅ Auto-fallback ke SyncExtractors jika loadExtractor gagal
    val loaded = loadExtractorWithFallback(
        url = iframeUrl,
        referer = referer,
        subtitleCallback = subtitleCallback
    ) { link ->
        MasterLinkGenerator.createLink(
            source = link.name,
            url = link.url,
            referer = link.referer,
            quality = link.quality
        )?.let { callback(it) }
    }
    
    if (!loaded) {
        logError("Provider", "All extractors failed")
    }
}
```

**Benefits**:
- ✅ Auto-register semua extractor (200+)
- ✅ Tidak perlu update manual
- ✅ Fallback jika loadExtractor gagal
- ✅ CircuitBreaker protection
- ✅ Consistent across providers

---

## ✅ **RECOMMENDATIONS**

### **1. UPDATE SEATV** ❌→✅

**File**: `Donghuastream/src/main/kotlin/com/Donghuastream/SeaTV.kt`

**Current**:
```kotlin
// ❌ Old implementation
override suspend fun loadLinks(...) {
    document.select(".mobius option").amap { server ->
        // ... extract iframe ...
        
        when {
            url.contains("vidmoly") -> {
                loadExtractor(link, referer = url, subtitleCallback, callback)  // ❌ No fallback
            }
            else -> {
                loadExtractor(url, referer = url, subtitleCallback, callback)  // ❌ No fallback
            }
        }
    }
}
```

**Recommended**:
```kotlin
// ✅ Update to use loadExtractorWithFallback
override suspend fun loadLinks(...) {
    val document = app.get(data).documentLarge
    
    document.select(".mobius option").amap { server ->
        val base64 = server.attr("value").takeIf { it.isNotEmpty() }
        val doc = base64?.let { base64Decode(it).let(Jsoup::parse) }
        val iframeUrl = doc?.select("iframe")?.attr("src")?.let(::httpsify)
        val metaUrl = doc?.select("meta[itemprop=embedUrl]")?.attr("content")?.let(::httpsify)
        val url = iframeUrl?.takeIf { it.isNotEmpty() } ?: metaUrl.orEmpty()
        
        if (url.isNotEmpty()) {
            // ✅ USE loadExtractorWithFallback
            val loaded = com.Donghuastream.generated_sync.loadExtractorWithFallback(
                url = url,
                referer = mainUrl,
                subtitleCallback = subtitleCallback,
                callback = callback
            )
            
            if (!loaded) {
                logError("SeaTV", "loadExtractorWithFallback failed for $url")
            }
        }
    }
    
    return true
}
```

**Benefit**: Konsisten dengan Donghuastream dan provider lain

---

### **2. REFACTOR ANICHIN** ⚠️→✅

**File**: `Anichin/src/main/kotlin/com/Anichin/Anichin.kt`

**Current**: Manual implementation (tapi logic benar)

**Recommended**: Gunakan `loadExtractorWithFallback` untuk konsistensi:

```kotlin
// ✅ Refactor to use loadExtractorWithFallback
override suspend fun loadLinks(...) {
    val html = executeWithRetry { ... }
    
    val options = html.select("option[data-index]")
    
    supervisorScope {
        options.map { option ->
            async {
                val base64 = option.attr("value").trim()
                if (base64.isBlank()) return@async
                
                val decodedHtml = base64Decode(base64)
                val iframeUrl = Jsoup.parse(decodedHtml)
                    .selectFirst("iframe")?.attr("src")
                    ?.let(::httpsify)
                
                if (iframeUrl.isNullOrEmpty()) return@async
                
                when {
                    iframeUrl.endsWith(".mp4") -> {
                        MasterLinkGenerator.createLink(...)
                    }
                    else -> {
                        // ✅ USE loadExtractorWithFallback
                        val loaded = com.Anichin.generated_sync.loadExtractorWithFallback(
                            url = iframeUrl,
                            referer = data,
                            subtitleCallback = subtitleCallback,
                            callback = callback
                        )
                        
                        if (!loaded) {
                            logError("Anichin", "loadExtractorWithFallback failed for $iframeUrl")
                        }
                    }
                }
            }
        }.awaitAll()
    }
    
    return true
}
```

**Benefit**: Lebih simple, konsisten, easier maintenance

---

## 📊 **FINAL STATUS**

| Provider | Plugin | loadLinks | Status | Action Needed |
|----------|--------|-----------|--------|---------------|
| **Anichin** | ✅ | ⚠️ Manual | ✅ Working | Refactor untuk konsistensi |
| **Animasu** | ✅ | ✅ | ✅ Perfect | None |
| **Donghuastream** | ✅ | ✅ | ✅ Perfect | None |
| **SeaTV** | ❌ Extends parent | ❌ Old way | ⚠️ **Needs Update** | **UPDATE REQUIRED** |
| **Funmovieslix** | ✅ | ✅ | ✅ Perfect | None |
| **Idlix** | ✅ | ✅ | ✅ Perfect | None |
| **LayarKaca21** | ✅ | ✅ | ✅ Perfect | None |
| **Pencurimovie** | ✅ | ✅ | ✅ Perfect | None |
| **Samehadaku** | ✅ | ✅ | ✅ Perfect | None |

---

## ✅ **KESIMPULAN**

### **Yang SUDAH BENAR (7 providers):**
- ✅ **Animasu, Donghuastream, Funmovieslix, Idlix, LayarKaca21, Pencurimovie, Samehadaku**
- Plugin: Menggunakan `SyncExtractors.list.forEach { ... }`
- loadLinks: Menggunakan `loadExtractorWithFallback`

### **Yang PERLU UPDATE (2 providers):**
- ⚠️ **SeaTV**: Harus update untuk gunakan `loadExtractorWithFallback`
- ⚠️ **Anichin**: Refactor untuk konsistensi (opsional, karena sudah benar)

### **Benefits Menggunakan SyncExtractors:**
1. ✅ Auto-register 200+ extractors
2. ✅ Tidak perlu update manual
3. ✅ Fallback jika loadExtractor gagal
4. ✅ CircuitBreaker protection
5. ✅ Consistent across providers
6. ✅ Easier maintenance

---

**Prepared by**: AI Code Analyst  
**Date**: 2026-04-01  
**Status**: Complete
