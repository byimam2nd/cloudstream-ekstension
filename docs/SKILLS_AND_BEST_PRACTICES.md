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
7. [Git & GitHub Management](#git--github-management)
8. [Build System](#build-system)
9. [Common Issues & Solutions](#common-issues--solutions)
10. [CloudStream Guidelines](#cloudstream-guidelines)
11. [Development Checklist](#development-checklist)
12. [Troubleshooting Flow](#troubleshooting-flow)

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

| Provider | Package Name | Folder | Status |
|----------|-------------|--------|--------|
| Anichin | `com.anichin` | `com/Anichin/` | ✅ |
| LayarKaca21 | `com.layarKacaProvider` | `com/LayarKacaProvider/` | ✅ |
| IdlixProvider | `com.hexated` | `com/hexated/` | ✅ |
| Pencurimovie | `com.pencurimovie` | `com/Pencurimovie/` | ✅ |
| Donghuastream | `com.donghuastream` | `com/Donghuastream/` | ✅ |
| Funmovieslix | `com.funmovieslix` | `com/Funmovieslix/` | ✅ |
| Animasu | `com.animasu` | `com/Animasu/` | ✅ 🆕 |

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

## 🔧 Git & GitHub Management

### **Commit Message Convention**

```
type(scope): description

[type]: fix, feat, chore, docs, refactor, test
[scope]: Provider name or component
[description]: Short, imperative tense
```

**Examples:**
```bash
fix(Anichin): fix episode parsing for 'END' episodes
feat(LayarKaca21): add fallback for extractor failures
chore: update sync-extractors workflow
docs: add comprehensive skills guide
```

### **Branch Management**

```bash
# Always work on feature branch
git checkout -b fix/episode-parsing

# Commit frequently
git commit -m "fix: initial fix for episode detection"

# Rebase before push
git pull --rebase origin master
git push origin feature-branch

# Squash commits before merge
git rebase -i HEAD~3  # Squash last 3 commits
```

### **Rollback Strategy**

```bash
# If build fails after push
git reset --hard HEAD~1  # Remove last commit
git push --force-with-lease origin master

# Or create fix commit
git revert <commit-hash>
git push origin master
```

---

## 🏗️ Build System

### **GitHub Actions Workflows**

**1. Build Workflow (`.github/workflows/build.yml`)**
```yaml
on:
  push:
    branches: [master]
  workflow_run:
    workflows: ["Sync Master Extractors"]
    types: [completed]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
      - run: ./gradlew make makePluginsJson
```

**2. Sync Extractors Workflow**
```yaml
on:
  push:
    paths:
      - 'docs/MasterExtractors.kt'

jobs:
  sync:
    runs-on: ubuntu-latest
    steps:
      - Copy MasterExtractors.kt to all providers
      - Fix package names
      - Commit and push
```

### **Build Commands**

```bash
# Local build test
./gradlew clean
./gradlew make
./gradlew makePluginsJson

# Check for errors
./gradlew build --warning-mode all

# Build specific module
./gradlew :Anichin:build
```

### **Build Optimization**

- ✅ Use Gradle cache
- ✅ Parallel module builds
- ✅ Selective builds (only changed modules)
- ✅ Cache dependencies

---

## ✅ Development Checklist

### **Before Commit**

- [ ] Code compiles without errors
- [ ] No unused imports
- [ ] Package declarations consistent
- [ ] No hardcoded values (use constants)
- [ ] Error handling implemented
- [ ] Logging added for debugging
- [ ] No duplicate code
- [ ] Follow naming conventions

### **Before Push**

- [ ] Git pull --rebase first
- [ ] Run local build test
- [ ] Check for merge conflicts
- [ ] Commit message follows convention
- [ ] Related docs updated

### **After Push**

- [ ] Monitor GitHub Actions
- [ ] Check build status
- [ ] Review build logs if failed
- [ ] Fix errors immediately
- [ ] Update troubleshooting docs

---

## 🔄 Troubleshooting Flow

### **Build Failed?**

```
1. Check gh run list --limit 1
2. Get run number
3. gh run view <number> --log | grep "e: file:"
4. Identify error type:
   - Compilation error → Fix code
   - Redeclaration → Check duplicate files
   - Unresolved reference → Check imports
   - Deprecated API → Update to new API
5. Fix and commit
6. Push and monitor again
```

### **Extractor Not Working?**

```
1. Check extractor URL format
2. Verify referer headers
3. Test with direct link
4. Check for packed JavaScript
5. Try multiple regex patterns
6. Add fallback to direct URL
7. Log all steps for debugging
```

### **Cache Not Working?**

```
1. Check CacheManager initialization
2. Verify TTL settings
3. Check mutex locks
4. Test get/put separately
5. Monitor cache size
6. Check cleanup logic
```

### **Workflow Sync Failed?**

```
1. Check folder mapping in workflow
2. Verify package name replacement (lowercase!)
3. Check file permissions
4. Review awk script syntax
5. Test with manual sync
6. Check GitHub Actions logs

Common Issue: Package name capitalization
- Folder: LayarKacaProvider
- Package: com.layarKacaProvider (lowercase!)
- NOT: com.LayarKacaProvider (WRONG!)
```

---

## 🔧 Extractor Reliability Improvements

### **Problem: Single Point of Failure**

Old extractor code relied on single regex pattern:
```kotlin
// ❌ FRAGILE: Single regex pattern
Regex(":\\s*\"(.*?m3u8.*?)\"").findAll(script).forEach { ... }
```

If regex fails → No video → PARSING_CONTAINER_MALFORMED error

### **Solution: Multiple Extraction Methods**

```kotlin
// ✅ ROBUST: Multiple extraction methods
var script: String? = null

// Method 1: Unpack packed JavaScript
val packed = getPacked(response.text)
if (!packed.isNullOrEmpty()) {
    script = getAndUnpack(response.text)
}

// Method 2: Look for sources in script tags
if (script == null) {
    script = response.document.selectFirst("script:containsData(sources:)")?.data()
}

// Method 3: Look for direct m3u8/mp4 in meta tags
if (script == null) {
    val videoUrl = response.document.selectFirst("meta[property=og:video]")?.attr("content")
    if (videoUrl.contains(".m3u8")) {
        script = "sources:[{file:\"$videoUrl\"}]"
    }
}

// Multiple regex patterns for extraction
val patterns = listOf(
    ":\\s*\"(.*?m3u8.*?)\"",
    "file:\\s*\"(.*?m3u8.*?)\"",
    "src:\\s*\"(.*?m3u8.*?)\"",
    "\"(https?://[^\"]+?\\.m3u8[^\"]*?)\""
)
```

### **Extractors Enhanced:**

| Extractor | Before | After |
|-----------|--------|-------|
| **Dingtezuni** | 1 regex | ✅ 3 methods + 4 regex |
| **Dintezuvio** | 1 regex | ✅ 3 methods + 4 regex |
| **StreamRuby** | 1 regex | ✅ 3 methods + 4 regex |
| **Vidguard** | JS only | ✅ JS + fallback direct URL |

### **Workflow Sync Package Naming**

**Critical:** Package name MUST be lowercase!

```yaml
# ✅ CORRECT: Explicit package mapping
declare -A SITE_CONFIGS=(
  ["LayarKaca21"]="LayarKacaProvider:layarKacaProvider"
  #                        Folder      ^      ^ Package
  ["IdlixProvider"]="hexated:hexated"
)

# ❌ WRONG: Using folder name as package
awk -v folder="$FOLDER" '{ print "package com." folder }'
# Results in: package com.LayarKacaProvider (WRONG!)
```

**Lesson:** Always use explicit package mapping, not derived from folder name!

---

## 🎓 Real Case Studies

### **Case 1: Episode 52 END (Anichin)**

**Symptom:** Episode 52 with "END" text not detected

**Root Cause:** `toIntOrNull()` fails on "52 END"

**Solution:**
```kotlin
val episodeText = info.selectFirst(".epl-num")?.text()?.trim().orEmpty()
val episodeNumber = episodeText.replace(Regex("[^0-9]"), "").toIntOrNull()
```

**Lesson:** Always sanitize input before parsing

---

### **Case 2: PARSING_CONTAINER_MALFORMED (LayarKaca21)**

**Symptom:** ERROR_CODE_PARSING_CONTAINER_MALFORMED (3001)

**Root Cause:** Extractor fails, no fallback

**Solution:**
```kotlin
try {
    loadExtractor(iframeUrl, referer, subtitleCallback, callback)
} catch (e: Exception) {
    // Fallback to direct URL
    if (directUrl.contains(".mp4") || directUrl.contains(".m3u8")) {
        callback(newExtractorLink(...))
    }
}
```

**Lesson:** Always implement fallback mechanism

---

### **Case 3: Workflow Sync Package Naming**

**Symptom:** Build fails with `Unresolved reference 'AllExtractors'`

**Root Cause:** 
- Workflow sync generates wrong package name
- Folder: `LayarKacaProvider`
- Package generated: `com.LayarKacaProvider` (uppercase L - WRONG!)
- Should be: `com.layarKacaProvider` (lowercase l)

**Solution:**
```yaml
# Use explicit package mapping in workflow
declare -A SITE_CONFIGS=(
  ["LayarKaca21"]="LayarKacaProvider:layarKacaProvider"
)

awk -v pkg="$PACKAGE" '{ print "package com." pkg }'
```

**Lesson:** Package names MUST be lowercase! Don't derive from folder name!

---

### **Case 3: Workflow Sync to Wrong Folder**

**Symptom:** Redeclaration errors after sync

**Root Cause:** Workflow uses site name as folder name

**Solution:**
```yaml
declare -A SITE_FOLDERS=(
  ["LayarKaca21"]="LayarKacaProvider"
  ["IdlixProvider"]="hexated"
)
```

**Lesson:** Map site names to actual folder names

---

## 🔗 References

- [CloudStream3 Documentation](https://recloudstream.github.io/cloudstream/)
- [CloudStream3 GitHub](https://github.com/recloudstream/cloudstream)
- [ExtCloud Repository](https://github.com/Phisher98/ExtCloud)
- [CloudStream Extensions](https://github.com/recloudstream/cloudstream-extensions)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)

---

**Last Updated:** 2026-03-20
**Maintained By:** Development Team
**Version:** 1.0 (Complete)
**Modules:** 8 total (Anichin, Donghuastream, Funmovieslix, Idlix, LayarKaca21, Pencurimovie, Animasu)

---

**Last Updated:** 2026-03-20 - Added Animasu module documentation
**Maintained By:** Development Team
