# 📚 Skills & Best Practices - CloudStream Extensions Development

Dokumentasi ini berisi kumpulan ilmu, pola, dan best practices yang diperoleh selama pengembangan CloudStream Extensions.

---

## 📋 Daftar Isi

1. [Arsitektur Proyek](#arsitektur-proyek)
2. [Struktur Folder](#struktur-folder)
3. [Cache Optimization](#cache-optimization)
4. [Extractor Development](#extractor-development)
5. [Error Handling](#error-handling)
6. [Workflow Automation](#workflow-automation)
7. [Common Issues & Solutions](#common-issues--solutions)
8. [CloudStream Guidelines](#cloudstream-guidelines)

---

## 🏗️ Arsitektur Proyek

### **Module-Based Architecture**

Setiap provider adalah module terpisah yang standalone:

```
cloudstream-ekstension/
├── Anichin/           # Module 1
├── LayarKaca21/       # Module 2
├── Pencurimovie/      # Module 3
└── ...
```

**Prinsip:**
- ✅ Setiap module independen (tidak ada code sharing)
- ✅ Setiap module punya package sendiri
- ✅ Build terpisah untuk setiap module
- ✅ Extractors di-sync dari MasterExtractors.kt

---

## 📁 Struktur Folder

### **Struktur Package yang Benar**

```
ProviderName/
└── src/main/kotlin/com/
    └── <package_name>/
        ├── ProviderName.kt          # Main API class
        ├── ProviderNameProvider.kt  # Plugin registration
        ├── Extractors.kt            # Auto-synced from docs/
        ├── CacheManager.kt          # Cache system
        ├── SmartCacheMonitor.kt     # Fingerprint cache
        ├── ImageCache.kt            # Image caching
        ├── SuperSmartPrefetchManager.kt  # Prefetching
        └── Utils.kt                 # Utilities
```

### **Package Naming Convention**

| Provider | Package Name | Folder |
|----------|-------------|--------|
| Anichin | `com.anichin` | `com/Anichin/` |
| LayarKaca21 | `com.layarKacaProvider` | `com/LayarKacaProvider/` |
| IdlixProvider | `com.hexated` | `com/hexated/` |
| Pencurimovie | `com.pencurimovie` | `com/Pencurimovie/` |
| Donghuastream | `com.donghuastream` | `com/Donghuastream/` |
| Funmovieslix | `com.funmovieslix` | `com/Funmovieslix/` |

**⚠️ PENTING:** Package name HARUS konsisten di semua file dalam satu provider!

---

## 🚀 Cache Optimization

### **4 Komponen Cache System**

#### **1. CacheManager.kt** - Thread-safe Cache dengan TTL

```kotlin
// Simple usage
private val searchCache = CacheManager<List<SearchResponse>>()
private val mainPageCache = CacheManager<HomePageResponse>()

// Get from cache
val cached = searchCache.get(cacheKey)
if (cached != null) return cached

// Put to cache
searchCache.put(cacheKey, results)
```

**Fitur:**
- ✅ TTL (Time To Live) otomatis
- ✅ Thread-safe dengan coroutines
- ✅ Auto cleanup expired entries
- ✅ Max size limiting

#### **2. SmartCacheMonitor.kt** - Fingerprint-based Invalidation

```kotlin
private val monitor = AnichinMonitor()

// Monitor will auto-invalidate cache when content changes
```

**Cara Kerja:**
1. Fetch titles dari homepage
2. Generate fingerprint (hash)
3. Compare dengan cached fingerprint
4. Invalidate jika berbeda

#### **3. ImageCache.kt** - Disk-based Image Caching

```kotlin
private val imageCache = ImageCache()

// Fetch and cache image
val bitmap = imageCache.fetchAndCache(imageUrl)
```

**Fitur:**
- ✅ 200MB disk limit
- ✅ Site-specific cache folders
- ✅ Auto global cleanup
- ✅ WEBP compression

#### **4. SuperSmartPrefetchManager.kt** - AI-powered Prefetching

```kotlin
private val prefetchManager = SuperSmartPrefetchManager()

// Prefetch next episode
prefetchManager.predictAndPrefetch(currentEpisode)
```

**Fitur:**
- ✅ Predictive prefetching
- ✅ User behavior analysis
- ✅ Priority-based queuing
- ✅ Memory-efficient

---

## 🎯 Extractor Development

### **MasterExtractors.kt Pattern**

Semua extractor disimpan di `docs/MasterExtractors.kt` dan auto-sync ke semua provider.

**Workflow Sync:**
```yaml
# .github/workflows/sync-extractors.yml
on:
  push:
    paths:
      - 'docs/MasterExtractors.kt'
```

### **Base Extractor Classes**

```kotlin
// StreamWish based
class MyExtractor : StreamWishExtractor() {
    override val name = "MyExtractor"
    override val mainUrl = "https://example.com"
}

// DoodLa based
class MyDoodExtractor : DoodLaExtractor() {
    override val name = "MyDood"
    override val mainUrl = "https://dood.example.com"
}

// Custom Extractor
open class MyCustomExtractor : ExtractorApi() {
    override val name = "Custom"
    override val mainUrl = "https://custom.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Extraction logic
    }
}
```

### **ExtractorLink Best Practices**

```kotlin
// ✅ GOOD: Using newExtractorLink builder
callback(
    newExtractorLink(
        source = "Provider Direct",
        name = "Provider Direct",
        url = videoUrl,
        type = INFER_TYPE
    ) {
        this.referer = baseUrl
        this.quality = 720
    }
)

// ❌ BAD: Deprecated constructor
callback(
    ExtractorLink(
        source = "Provider Direct",
        name = "Provider Direct",
        url = videoUrl,
        referer = baseUrl,
        quality = Qualities.UNKNOWN.value,  // Don't exist!
        type = ExtractorLinkType.VIDEO
    )
)
```

---

## 🛡️ Error Handling

### **Fallback Pattern**

```kotlin
override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    var extractorSuccess = false

    playerLinks.amap { url ->
        try {
            // Try extractor first
            val result = loadExtractor(iframeUrl, referer, subtitleCallback, callback)
            if (result) extractorSuccess = true
        } catch (e: Exception) {
            logError("Provider", "Extractor failed: ${e.message}", e)
            
            // FALLBACK: Direct video URL
            try {
                if (directUrl.contains(".mp4") || directUrl.contains(".m3u8")) {
                    callback(
                        newExtractorLink(
                            source = "Direct",
                            name = "Direct",
                            url = directUrl,
                            type = if (directUrl.contains(".m3u8")) INFER_TYPE else VIDEO
                        ) {
                            this.referer = baseUrl
                            this.quality = 720
                        }
                    )
                    extractorSuccess = true
                }
            } catch (e2: Exception) {
                logError("Provider", "Fallback failed: ${e2.message}", e2)
            }
        }
    }

    return extractorSuccess  // Return success only if at least one link works
}
```

### **Logging Best Practices**

```kotlin
// ✅ GOOD: 2-parameter Log.e
Log.e(TAG, "Error: ${e.message}")

// ❌ BAD: 3-parameter (not supported)
Log.e(TAG, "Error", e)

// Custom logError function
internal fun logError(tag: String, message: String, error: Throwable? = null) {
    Log.e(tag, message)
    error?.let { Log.e(tag, "Cause: ${it.message}") }
}
```

---

## 🔄 Workflow Automation

### **Sync Extractors Workflow**

```yaml
# .github/workflows/sync-extractors.yml

# Site to folder mapping
declare -A SITE_FOLDERS=(
  ["Pencurimovie"]="Pencurimovie"
  ["LayarKaca21"]="LayarKacaProvider"
  ["IdlixProvider"]="hexated"
  ["Donghuastream"]="Donghuastream"
  ["Funmovieslix"]="Funmovieslix"
  ["HiAnime"]="HiAnime"
  ["Anichin"]="Anichin"
)

# Copy with correct package
awk -v folder="$FOLDER" '
  /^package / { print "package com." folder; next }
  { print }
' "$MASTER_FILE" > "$DEST_FILE"
```

### **Build Workflow**

```yaml
# .github/workflows/build.yml

# Auto-build on push
on:
  push:
    branches: [master]
  workflow_run:
    workflows: ["Sync Master Extractors"]
    types: [completed]
```

---

## 🐛 Common Issues & Solutions

### **1. Episode dengan "END" tidak terdeteksi**

**Problem:**
```kotlin
// Episode text: "52 END"
val episodeNumber = info.selectFirst(".epl-num")?.text()?.toIntOrNull()
// Returns null!
```

**Solution:**
```kotlin
val episodeText = info.selectFirst(".epl-num")?.text()?.trim().orEmpty()
val episodeNumber = episodeText.replace(Regex("[^0-9]"), "").toIntOrNull()
// "52 END" → "52" → 52
```

### **2. Redeclaration Errors**

**Problem:**
```
e: file:///.../Utils.kt:29:21 Redeclaration: data class CachedResult
e: file:///.../CacheManager.kt:34:12 Redeclaration: data class CachedResult
```

**Solution:**
- Hapus `CachedResult` dan `CacheManager` dari Utils.kt
- Gunakan file terpisah (CacheManager.kt)

### **3. PARSING_CONTAINER_MALFORMED**

**Problem:**
```
ERROR_CODE_PARSING_CONTAINER_MALFORMED (3001)
```

**Solution:**
- Tambahkan fallback ke direct URL
- Multiple regex patterns untuk extraction
- Better error handling

### **4. isCrossPlatform Error**

**Problem:**
```
The cross-platform jar file contains Android imports!
```

**Solution:**
```kotlin
// build.gradle.kts
isCrossPlatform = false  // Untuk provider dengan Android code
```

### **5. Workflow Sync ke Folder Salah**

**Problem:**
- Workflow sync ke `com/LayarKaca21/`
- Tapi folder sebenarnya `com/LayarKacaProvider/`

**Solution:**
```yaml
# Use mapping
SITE_FOLDERS=(
  ["LayarKaca21"]="LayarKacaProvider"
  ["IdlixProvider"]="hexated"
)
```

---

## 📖 CloudStream Guidelines

### **MainAPI Class Structure**

```kotlin
class MyProvider : MainAPI() {
    override var mainUrl = "https://example.com"
    override var name = "MyProvider"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )

    override val mainPage = mainPageOf(
        "movies/page/" to "Movies",
        "series/page/" to "TV Series"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Implementation
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Implementation
    }

    override suspend fun load(url: String): LoadResponse {
        // Implementation
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Implementation
    }
}
```

### **Plugin Registration**

```kotlin
@CloudstreamPlugin
class MyProviderPlugin: BasePlugin() {
    override fun load() {
        // Register main API
        registerMainAPI(MyProvider())
        
        // Register extractors (dynamic)
        AllExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
```

### **Response Builders**

```kotlin
// Movie
newMovieLoadResponse(title, url, TvType.Movie, videoUrl) {
    this.posterUrl = poster
    this.plot = description
    this.year = year
    addTrailer(trailerUrl)
}

// TV Series
newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
    this.posterUrl = poster
    this.plot = description
    this.showStatus = ShowStatus.Ongoing
}

// Episode
newEpisode(url) {
    this.name = episodeTitle
    this.episode = episodeNumber
    this.posterUrl = thumbnail
}

// Search Result
newMovieSearchResponse(title, url, TvType.Movie) {
    this.posterUrl = poster
    this.quality = SearchQuality.HD
}
```

---

## 🎓 Lessons Learned

### **DO:**
- ✅ Gunakan package name konsisten (lowercase)
- ✅ Separate concerns (CacheManager, Utils, Extractors terpisah)
- ✅ Error handling dengan fallback
- ✅ Logging yang informatif
- ✅ Test build sebelum push besar
- ✅ Gunakan workflow sync untuk extractors

### **DON'T:**
- ❌ Hardcode extractor di provider (gunakan dynamic register)
- ❌ Duplicate code antar file
- ❌ Gunakan 3-parameter Log.e
- ❌ Assume folder name = provider name
- ❌ Ignore build errors
- ❌ Mix package declarations

---

## 📈 Performance Tips

### **1. Rate Limiting**
```kotlin
internal suspend fun rateLimitDelay() = mutex.withLock {
    val now = System.currentTimeMillis()
    val elapsed = now - lastRequestTime
    if (elapsed < MIN_REQUEST_DELAY) {
        delay(MIN_REQUEST_DELAY - elapsed)
    }
    lastRequestTime = System.currentTimeMillis()
}
```

### **2. Parallel Requests**
```kotlin
val results = coroutineScope {
    (1..3).map { page ->
        async {
            fetchPage(page)
        }
    }.awaitAll().flatten()
}
```

### **3. Retry Logic**
```kotlin
suspend fun <T> executeWithRetry(
    maxRetries: Int = 3,
    block: suspend () -> T
): T {
    var lastException: Exception? = null
    repeat(maxRetries) { attempt ->
        try { return block() }
        catch (e: Exception) { lastException = e }
        delay(1000L * (attempt + 1))
    }
    throw lastException!!
}
```

---

## 🔗 References

- [CloudStream3 Documentation](https://recloudstream.github.io/cloudstream/)
- [CloudStream3 GitHub](https://github.com/recloudstream/cloudstream)
- [ExtCloud Repository](https://github.com/Phisher98/ExtCloud)
- [CloudStream Extensions](https://github.com/recloudstream/cloudstream-extensions)

---

**Last Updated:** 2026-03-19  
**Maintained By:** Development Team
