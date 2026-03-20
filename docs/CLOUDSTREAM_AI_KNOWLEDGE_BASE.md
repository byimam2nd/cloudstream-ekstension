# 🧠 CLOUDSTREAM EXTENSION - AI KNOWLEDGE BASE
## Pusat Skills & Pengetahuan Setara Memory untuk AI

**Version:** 3.0.0
**Last Updated:** 2026-03-20
**Status:** ✅ PRODUCTION READY (9 Modules)
**Source Analysis:** 700+ Kotlin files (ExtCloud + phisher + Animasu + Samehadaku)
**Total Pages:** 1200+ baris dokumentasi lengkap
**Latest Modules:** 
- Animasu 🆕 (Added 2026-03-20)
- Samehadaku 🆕 (Added 2026-03-20 with full caching)

---

## 📑 DAFTAR ISI UTAMA

### 📘 BOOK 1: FOUNDATIONS (Dasar)
1. [Introduction & Architecture](#book-1-foundations)
2. [Extension Structure](#extension-structure)
3. [Kotlin Basics for CloudStream](#kotlin-basics)
4. [CloudStream API Overview](#cloudstream-api)

### 📗 BOOK 2: TECHNICAL SKILLS (Keahlian Teknis)
5. [CSS Selectors Mastery](#css-selectors-mastery)
6. [Web Scraping Techniques](#web-scraping-techniques)
7. [Extractor Development](#extractor-development)
8. [Data Parsing & Transformation](#data-parsing)

### 📙 BOOK 3: BEST PRACTICES (Praktik Terbaik)
9. [DO's and DON'Ts](#dos-and-donts)
10. [Code Quality Standards](#code-quality)
11. [Security Best Practices](#security)
12. [Performance Optimization](#performance)

### 📕 BOOK 4: ADVANCED TOPICS (Topik Lanjutan)
13. [Advanced Extractor Patterns](#advanced-extractors)
14. [Anti-Bot Bypass Techniques](#anti-bot)
15. [Caching Strategies](#caching)
16. [Error Handling Mastery](#error-handling)

### 📒 BOOK 5: REFERENCE (Referensi)
17. [Common Patterns Library](#patterns-library)
18. [Troubleshooting Guide](#troubleshooting)
19. [Code Examples Repository](#examples)
20. [External Resources](#resources)

---

# 📘 BOOK 1: FOUNDATIONS

## 1. INTRODUCTION & ARCHITECTURE

### 1.1 Apa itu CloudStream Extension?

**Definisi:**
CloudStream Extension adalah plugin Kotlin yang menambahkan sumber streaming baru ke aplikasi CloudStream Android. Extension berfungsi sebagai **bridge** antara aplikasi CloudStream dan website streaming.

**Arsitektur Sistem:**
```
┌─────────────────────────────────────────────────────────┐
│                    CLOUDSTREAM APP                       │
│  (Android Application - Kotlin/Java)                     │
└───────────────────┬─────────────────────────────────────┘
                    │
                    │ Load Extension (.cs3/.jar)
                    ▼
┌─────────────────────────────────────────────────────────┐
│                    EXTENSION PLUGIN                      │
│  ┌───────────────────────────────────────────────────┐  │
│  │ SitenamePlugin.kt (Entry Point)                   │  │
│  │  - @CloudstreamPlugin annotation                  │  │
│  │  - registerMainAPI()                              │  │
│  │  - registerExtractorAPI()                         │  │
│  └───────────────────────────────────────────────────┘  │
│                    │                                      │
│                    ▼                                      │
│  ┌───────────────────────────────────────────────────┐  │
│  │ Sitename.kt (Main API - Scraping Logic)           │  │
│  │  - search()                                       │  │
│  │  - getMainPage()                                  │  │
│  │  - load()                                         │  │
│  │  - loadLinks()                                    │  │
│  └───────────────────────────────────────────────────┘  │
│                    │                                      │
│                    ▼                                      │
│  ┌───────────────────────────────────────────────────┐  │
│  │ Extractors.kt (Video Extractors)                  │  │
│  │  - getUrl()                                       │  │
│  │  - ExtractorLink generation                       │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                    │
                    │ HTTP Requests (Jsoup + OkHttp)
                    ▼
┌─────────────────────────────────────────────────────────┐
│                    TARGET WEBSITE                        │
│  (HTML, CSS, JavaScript, Video Streams)                 │
└─────────────────────────────────────────────────────────┘
```

### 1.2 Component Flow

```
User Action → CloudStream → Extension → Website → Response → Extension → CloudStream → User

1. User clicks "Search"
2. CloudStream calls extension.search(query)
3. Extension sends HTTP GET to website
4. Website returns HTML
5. Extension parses HTML with Jsoup
6. Extension returns List<SearchResponse>
7. CloudStream displays results
8. User selects item
9. Extension.load(url) called
10. Extension returns LoadResponse with episodes
11. User clicks play
12. Extension.loadLinks() called
13. Extension extracts video URL
14. CloudStream plays video
```

### 1.3 File Structure Standard

```
Sitename/
├── build.gradle.kts              # Build configuration
├── src/main/
│   ├── AndroidManifest.xml       # Android manifest
│   └── kotlin/com/Sitename/
│       ├── SitenamePlugin.kt     # Plugin entry point
│       ├── Sitename.kt           # Main API (scraping)
│       ├── Extractors.kt         # Video extractors
│       └── Utils.kt              # Helper functions
└── repo.json                     # Repository metadata
```

---

## 2. EXTENSION STRUCTURE

### 2.1 Plugin File (SitenamePlugin.kt)

**Purpose:** Entry point untuk CloudStream

**Template:**
```kotlin
package com.Sitename

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.Sitename.AllExtractors

@CloudstreamPlugin
class SitenamePlugin: BasePlugin() {
    override fun load() {
        // Register main scraping API
        registerMainAPI(Sitename())
        
        // Register all video extractors
        AllExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
```

**Key Points:**
- ✅ MUST have `@CloudstreamPlugin` annotation
- ✅ MUST extend `BasePlugin`
- ✅ MUST override `load()` function
- ✅ MUST register main API
- ✅ SHOULD register extractors dynamically

### 2.2 Main API File (Sitename.kt)

**Purpose:** Core scraping logic

**Template:**
```kotlin
package com.Sitename

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Sitename : MainAPI() {
    // Basic metadata
    override var mainUrl = "https://sitename.com"
    override var name = "Sitename"
    override val hasMainPage = true
    override var lang = "id"  // "id", "en", etc.
    
    // Supported content types
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )
    
    // Homepage categories
    override val mainPage = mainPageOf(
        "$mainUrl/populer/page/" to "Film Terplopuler",
        "$mainUrl/latest/page/" to "Film Terbaru"
    )
    
    // 1. Get homepage content
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("article figure").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }
    
    // 2. Search functionality
    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search/$query").document
        return document.select("article").mapNotNull {
            it.toSearchResult()
        }
    }
    
    // 3. Load details
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text() ?: ""
        val poster = document.selectFirst("img")?.attr("src") ?: ""
        
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
        }
    }
    
    // 4. Load video links
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val playerLinks = document.select("ul#player-list a")
        
        playerLinks.forEach { player ->
            val playerDoc = app.get(player.attr("href"), referer=mainUrl).document
            val iframe = playerDoc.selectFirst("iframe")?.attr("src") ?: return@forEach
            
            loadExtractor(iframe, mainUrl, subtitleCallback, callback)
        }
        
        return true
    }
    
    // Helper function
    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }
}
```

### 2.3 Extractors File (Extractors.kt)

**Purpose:** Extract video URLs from embed pages

**Template:**
```kotlin
package com.Sitename

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

// Extractor class
class MyExtractor : ExtractorApi() {
    override val name = "MyExtractor"
    override val mainUrl = "https://myextractor.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // 1. Fetch embed page
        val response = app.get(url, referer=referer)
        
        // 2. Extract video URL
        val videoUrl = Regex("""['"]file['"]\s*:\s*['"]([^'"]+)['"]""")
            .find(response.text)?.groupValues?.get(1)
            ?: return
        
        // 3. Return ExtractorLink
        callback.invoke(
            newExtractorLink(
                name,
                name,
                videoUrl,
                ExtractorLinkType.M3U8
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

// Extractor registry
object AllExtractors {
    val list = listOf(
        MyExtractor(),
        // Add more extractors here
    )
}
```

---

## 3. KOTLIN BASICS FOR CLOUDSTREAM

### 3.1 Essential Kotlin Syntax

#### Variables
```kotlin
// Immutable (cannot change)
val title = "Movie Title"

// Mutable (can change)
var counter = 0
counter = 1  // OK

// Type inference
val name: String = "Sitename"  // Explicit type
val age = 25                    // Inferred as Int
```

#### Null Safety
```kotlin
// Nullable type
var title: String? = null

// Safe call (returns null if null)
val length = title?.length

// Elvis operator (default if null)
val safeLength = title?.length ?: 0

// Force unwrap (DANGEROUS - can crash!)
val unsafeLength = title!!.length  // CRASH if null!
```

#### Functions
```kotlin
// Regular function
fun add(a: Int, b: Int): Int {
    return a + b
}

// Single expression
fun multiply(a: Int, b: Int) = a * b

// Suspend function (for async operations)
suspend fun fetchData(): String {
    return withContext(Dispatchers.IO) {
        app.get(url).text
    }
}
```

#### Lambda Expressions
```kotlin
// Lambda with one parameter
numbers.filter { it > 5 }

// Lambda with multiple parameters
pairs.map { (key, value) -> "$key: $value" }

// Lambda as parameter
callback.invoke { result ->
    println(result)
}
```

### 3.2 Collections & Operations

#### Lists
```kotlin
// Create list
val items = listOf("A", "B", "C")
val mutable = mutableListOf("A", "B")

// Operations
items.filter { it == "A" }           // Filter
items.map { it.lowercase() }         // Transform
items.firstOrNull { it == "B" }      // Find first
items.any { it == "C" }              // Check existence
items.count { it.length > 1 }        // Count matching
```

#### Maps
```kotlin
// Create map
val map = mapOf("key" to "value")
val mutableMap = mutableMapOf<String, String>()

// Access
val value = map["key"]
val safeValue = map.getOrDefault("key", "default")
```

### 3.3 String Templates
```kotlin
val name = "Sitename"
val url = "https://$name.com"
val complex = "URL: ${url}/page/${123}"

// Multi-line strings
val html = """
    <div class="movie">
        <h1>$title</h1>
    </div>
""".trimIndent()
```

### 3.4 Try-Catch-Finally
```kotlin
try {
    val result = riskyOperation()
    println("Success: $result")
} catch (e: Exception) {
    println("Error: ${e.message}")
    Log.e("Tag", "Operation failed", e)
} finally {
    println("Always executed")
}
```

---

## 4. CLOUDSTREAM API OVERVIEW

### 4.1 Core Classes

#### MainAPI
Base class for all extensions.

**Properties:**
```kotlin
override var mainUrl: String           // Base URL
override var name: String              // Display name
override val hasMainPage: Boolean      // Has homepage?
override var lang: String              // Language code
override val supportedTypes: Set<TvType>  // Content types
override val mainPage: MainPageData    // Categories
```

**Functions:**
```kotlin
suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse
suspend fun search(query: String): List<SearchResponse>
suspend fun load(url: String): LoadResponse
suspend fun loadLinks(data: String, ...): Boolean
```

#### ExtractorApi
Base class for video extractors.

**Properties:**
```kotlin
override val name: String              // Extractor name
override val mainUrl: String           // Extractor base URL
override val requiresReferer: Boolean  // Need referer?
```

**Functions:**
```kotlin
suspend fun getUrl(url: String, referer: String?, ...): Unit
```

### 4.2 Response Types

#### SearchResponse
```kotlin
newMovieSearchResponse(title, url, TvType.Movie) {
    this.posterUrl = "https://..."
    this.score = 8.5
}
```

#### LoadResponse
```kotlin
newMovieLoadResponse(title, url, TvType.Movie, data) {
    this.posterUrl = "https://..."
    this.plot = "Description..."
    this.year = 2024
    this.tags = listOf("Action", "Adventure")
}
```

#### Episode
```kotlin
newEpisode(url) {
    this.name = "Episode 1"
    this.season = 1
    this.episode = 1
    this.posterUrl = "https://..."
}
```

#### ExtractorLink
```kotlin
newExtractorLink(name, source, url, type) {
    this.referer = "https://..."
    this.quality = 1080
    this.headers = mapOf("User-Agent" to "...")
}
```

### 4.3 Helper Functions

#### URL Handling
```kotlin
fixUrl("/relative/path")              // → https://sitename.com/relative/path
fixUrlNull("/path")                   // → Fixed URL or null
getBaseUrl("https://site.com/path")   // → https://site.com
```

#### Response Builders
```kotlin
newMovieSearchResponse(...)
newTvSeriesSearchResponse(...)
newAnimeSearchResponse(...)
newMovieLoadResponse(...)
newTvSeriesLoadResponse(...)
newAnimeLoadResponse(...)
newEpisode(...)
newExtractorLink(...)
```

#### Quality & Types
```kotlin
addQuality("HD")
addDubStatus(dubExist, subExist)
addTrailer("https://youtube.com/...")
Score.from10(8.5)  // → CloudStream score
```

---

# 📗 BOOK 2: TECHNICAL SKILLS

## 5. CSS SELECTORS MASTERY

### 5.1 Basic Selectors

#### By ID
```kotlin
// Select by id attribute
document.select("#player-list")
document.selectFirst("#movie-info")  // First match only
```

**HTML:**
```html
<div id="player-list">...</div>
<h1 id="movie-info">Title</h1>
```

#### By Class
```kotlin
// Select by class attribute
document.select(".movie-item")
document.select("div.movie-info")  // Specific tag
```

**HTML:**
```html
<article class="movie-item">...</article>
<div class="movie-info">...</div>
```

#### By Tag Name
```kotlin
// Select by HTML tag
document.select("h1")
document.select("a")
document.select("img")
```

### 5.2 Attribute Selectors

#### Exact Match
```kotlin
// href="exact-value"
document.select("a[href=\"https://site.com\"]")
```

#### Contains
```kotlin
// href contains "player"
document.select("a[href*=player]")
```

#### Starts With
```kotlin
// src starts with "https"
document.select("img[src^=https]")
```

#### Ends With
```kotlin
// value ends with ".mp4"
document.select("option[value$=.mp4]")
```

#### Has Attribute
```kotlin
// Has data-id attribute
document.select("[data-id]")
```

### 5.3 Hierarchy Selectors

#### Direct Children
```kotlin
// ul > li (direct children only)
document.select("ul > li")
```

**HTML:**
```html
<ul>
    <li>Item 1</li>     <!-- Selected -->
    <li>
        <ul>
            <li>Nested</li>  <!-- NOT selected -->
        </ul>
    </li>
</ul>
```

#### Descendants
```kotlin
// div a (all descendants)
document.select("div a")
```

**HTML:**
```html
<div>
    <a href="...">Link 1</a>      <!-- Selected -->
    <p>
        <a href="...">Link 2</a>  <!-- Also selected -->
    </p>
</div>
```

#### Siblings
```kotlin
// Adjacent sibling (immediately after)
document.select("h1 + p")

// General sibling (all after)
document.select("h1 ~ p")
```

### 5.4 Pseudo-classes

#### First/Last
```kotlin
document.select("li:first-child")
document.select("li:last-child")
```

#### Nth Child
```kotlin
document.select("li:nth-child(2)")    // Second child
document.select("li:nth-child(odd)")  // Odd positions
document.select("li:nth-child(even)") // Even positions
```

#### Not
```kotlin
document.select("a:not([href*=player])")  // Exclude player links
```

### 5.5 Complex Selectors

#### Multiple Selectors
```kotlin
// Combine with comma
document.select("h1, h2, h3")  // All headings
```

#### Chained Selectors
```kotlin
// Combine conditions
document.select("div.movie-item.active a[href*=player]:first")
```

**Breakdown:**
- `div.movie-item` - div with class "movie-item"
- `.active` - AND has class "active"
- `a[href*=player]` - with anchor containing "player" in href
- `:first` - First match only

### 5.6 Real-World Examples

#### Movie Card Parser
```kotlin
document.select("article.movie-card").mapNotNull { card ->
    val title = card.selectFirst("h3.title")?.text() ?: return@mapNotNull null
    val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
    val poster = card.selectFirst("img")?.attr("src") ?: return@mapNotNull null
    val rating = card.selectFirst("span.rating")?.text()?.toDoubleOrNull()
    
    newMovieSearchResponse(title, fixUrl(href), TvType.Movie) {
        this.posterUrl = fixUrl(poster)
        this.score = Score.from10(rating)
    }
}
```

#### Episode List Parser
```kotlin
document.select("ul.episodes > li").mapNotNull { ep ->
    val href = ep.selectFirst("a")?.attr("href") ?: return@mapNotNull null
    val title = ep.selectFirst("span.title")?.text() ?: ""
    val episodeNo = ep.selectFirst("span.ep")?.text()?.toIntOrNull()
    
    newEpisode(fixUrl(href)) {
        this.name = title
        this.episode = episodeNo
    }
}
```

#### Player Link Extractor
```kotlin
document.select("ul#player-list li").mapNotNull { player ->
    val href = player.selectFirst("a")?.attr("href")
    val server = player.selectFirst("span.server-name")?.text()
    
    if (href != null) {
        PlayerLink(server ?: "Unknown", fixUrl(href))
    } else null
}
```

---

## 6. WEB SCRAPING TECHNIQUES

### 6.1 HTTP Requests

#### Basic GET Request
```kotlin
val response = app.get(url)
val document = response.document
val text = response.text
val bytes = response.bytes()
```

#### With Headers
```kotlin
val response = app.get(
    url,
    headers = mapOf(
        "User-Agent" to getRandomUserAgent(),
        "Accept" to "*/*",
        "Accept-Language" to "en-US,en;q=0.9",
        "Referer" to mainUrl
    )
)
```

#### POST Request
```kotlin
val response = app.post(
    url,
    data = mapOf(
        "username" to "user",
        "password" to "pass"
    ),
    headers = mapOf("Content-Type" to "application/x-www-form-urlencoded")
)
```

### 6.2 Error Handling

#### Try-Catch Pattern
```kotlin
try {
    val document = app.get(url, timeout=10000).document
    // Process document
} catch (e: SocketTimeoutException) {
    Log.e("Sitename", "Request timeout: ${e.message}")
    return emptyList()
} catch (e: Exception) {
    Log.e("Sitename", "Unexpected error: ${e.message}", e)
    return emptyList()
}
```

#### Retry Logic
```kotlin
suspend fun <T> executeWithRetry(
    maxRetries: Int = 3,
    initialDelay: Long = 1000L,
    block: suspend () -> T
): T {
    var lastException: Exception? = null
    var delayTime = initialDelay
    
    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastException = e
            Log.w("Sitename", "Attempt ${attempt + 1}/$maxRetries failed: ${e.message}")
            
            if (attempt < maxRetries - 1) {
                delay(delayTime)
                delayTime = (delayTime * 2).coerceAtMost(10000L)
            }
        }
    }
    
    throw lastException ?: Exception("Unknown error")
}

// Usage
val document = executeWithRetry {
    app.get(url).document
}
```

### 6.3 Rate Limiting

#### Simple Delay
```kotlin
suspend fun rateLimitDelay() {
    delay(100 + Random.nextLong(400))  // 100-500ms random delay
}

// Before each request
rateLimitDelay()
val doc = app.get(url).document
```

#### Token Bucket
```kotlin
class RateLimiter(
    private val requestsPerSecond: Int = 2
) {
    private val tokenBucket = ArrayDeque<Long>()
    
    suspend fun acquire() {
        val now = System.currentTimeMillis()
        
        // Remove old tokens (older than 1 second)
        while (tokenBucket.isNotEmpty() && now - tokenBucket.first() > 1000) {
            tokenBucket.removeFirst()
        }
        
        // Wait if at limit
        if (tokenBucket.size >= requestsPerSecond) {
            val waitTime = 1000 - (now - tokenBucket.first())
            if (waitTime > 0) delay(waitTime)
        }
        
        tokenBucket.addLast(System.currentTimeMillis())
    }
}

// Usage
val rateLimiter = RateLimiter(2)  // 2 requests per second

suspend fun fetchWithRateLimit(url: String) {
    rateLimiter.acquire()
    return app.get(url).document
}
```

### 6.4 Caching

#### Simple Cache
```kotlin
class SimpleCache<T>(
    private val ttl: Long = 5 * 60 * 1000L  // 5 minutes
) {
    private val cache = mutableMapOf<String, CacheEntry<T>>()
    
    data class CacheEntry<T>(
        val value: T,
        val timestamp: Long
    )
    
    fun get(key: String): T? {
        val entry = cache[key] ?: return null
        
        // Check if expired
        if (System.currentTimeMillis() - entry.timestamp > ttl) {
            cache.remove(key)
            return null
        }
        
        return entry.value
    }
    
    fun put(key: String, value: T) {
        cache[key] = CacheEntry(value, System.currentTimeMillis())
    }
    
    fun remove(key: String) {
        cache.remove(key)
    }
    
    fun clear() {
        cache.clear()
    }
}

// Usage
private val searchCache = SimpleCache<List<SearchResponse>>()

override suspend fun search(query: String): List<SearchResponse> {
    return searchCache.get(query) ?: performSearch(query).also {
        searchCache.put(query, it)
    }
}
```

#### GetOrPut Pattern
```kotlin
private val searchCache = mutableMapOf<String, List<SearchResponse>>()

override suspend fun search(query: String): List<SearchResponse> {
    return searchCache.getOrPut(query) {
        Log.d("Sitename", "Cache miss for: $query")
        val document = app.get("$mainUrl/search/$query").document
        document.select("article").mapNotNull { it.toSearchResult() }
    }
}
```

---

## 7. EXTRACTOR DEVELOPMENT

### 7.1 Basic Extractor

```kotlin
class MyExtractor : ExtractorApi() {
    override val name = "MyExtractor"
    override val mainUrl = "https://myextractor.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // 1. Fetch embed page
        val response = app.get(url, referer=referer)
        
        // 2. Extract video URL
        val videoUrl = Regex("""['"]file['"]\s*:\s*['"]([^'"]+)['"]""")
            .find(response.text)?.groupValues?.get(1)
            ?: return
        
        // 3. Return ExtractorLink
        callback.invoke(
            newExtractorLink(
                name,
                name,
                videoUrl,
                ExtractorLinkType.M3U8
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.Unknown.value
            }
        )
    }
}
```

### 7.2 Advanced Extractor Patterns

#### Pattern 1: Multiple Qualities
```kotlin
class MultiQualityExtractor : ExtractorApi() {
    override val name = "MultiQuality"
    override val mainUrl = "https://multi.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer=referer)
        
        // Extract all quality options
        val qualities = listOf(
            "360p" to Regex("""360p['"]:\s*['"]([^'"]+)['"]""").find(response.text)?.groupValues?.get(1),
            "480p" to Regex("""480p['"]:\s*['"]([^'"]+)['"]""").find(response.text)?.groupValues?.get(1),
            "720p" to Regex("""720p['"]:\s*['"]([^'"]+)['"]""").find(response.text)?.groupValues?.get(1),
            "1080p" to Regex("""1080p['"]:\s*['"]([^'"]+)['"]""").find(response.text)?.groupValues?.get(1)
        )
        
        qualities.forEach { (quality, videoUrl) ->
            if (videoUrl != null) {
                callback.invoke(
                    newExtractorLink(
                        name,
                        "$name $quality",
                        videoUrl,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = quality.toIntOrNull() ?: Qualities.Unknown.value
                    }
                )
            }
        }
    }
}
```

#### Pattern 2: Encoded URLs
```kotlin
class EncodedExtractor : ExtractorApi() {
    override val name = "Encoded"
    override val mainUrl = "https://encoded.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer=referer)
        
        // Extract encoded data
        val encodedData = Regex("""data:\s*['"]([^'"]+)['"]""")
            .find(response.text)?.groupValues?.get(1)
            ?: return
        
        // Decode (Base64 example)
        val decoded = String(android.util.Base64.decode(encodedData, android.util.Base64.DEFAULT))
        
        // Extract video URL from decoded data
        val videoUrl = Regex("""file:\s*['"]([^'"]+)['"]""")
            .find(decoded)?.groupValues?.get(1)
            ?: return
        
        callback.invoke(
            newExtractorLink(name, name, videoUrl, M3U8) {
                this.referer = "$mainUrl/"
            }
        )
    }
}
```

#### Pattern 3: JavaScript Evaluation
```kotlin
class JSExtractor : ExtractorApi() {
    override val name = "JS"
    override val mainUrl = "https://js.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer=referer)
        val script = response.document.selectFirst("script:containsData(eval)")?.data()
            ?: return
        
        // Unpack JavaScript
        val unpacked = getAndUnpack(script)
        
        // Extract video URL
        val videoUrl = Regex("""file:\s*['"]([^'"]+)['"]""")
            .find(unpacked)?.groupValues?.get(1)
            ?: return
        
        callback.invoke(
            newExtractorLink(name, name, videoUrl, M3U8) {
                this.referer = "$mainUrl/"
            }
        )
    }
}
```

### 7.3 Extractor Helpers

#### M3U8 Helper
```kotlin
M3u8Helper.generateM3u8(
    source = "ExtractorName",
    m3u8 = "https://.../playlist.m3u8",
    referer = "https://...",
    headers = mapOf("User-Agent" to "...")
).forEach(callback)
```

#### getQualityFromName
```kotlin
val quality = getQualityFromName("1080p")  // → 1080
val quality = getQualityFromName("HD")     // → 720
val quality = getQualityFromName("CAM")    // → 480
```

---

## 8. DATA PARSING & TRANSFORMATION

### 8.1 Text Extraction

#### Safe Text Extraction
```kotlin
// With null safety
val title = document.selectFirst("h1")?.text()?.trim() ?: ""

// With default
val rating = document.selectFirst("span")?.text()?.toDoubleOrNull() ?: 0.0

// With fallback
val poster = document.selectFirst("img")?.attr("src")
    ?: document.selectFirst("meta[property=og:image]")?.attr("content")
    ?: ""
```

#### Regex Extraction
```kotlin
// Extract number
val episodeNo = Regex("Episode (\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()

// Extract URL
val videoUrl = Regex("""file:\s*['"]([^'"]+)['"]""").find(script)?.groupValues?.get(1)

// Extract year
val year = Regex("\\b(19|20)\\d{2}\\b").find(text)?.value?.toIntOrNull()
```

### 8.2 Data Transformation

#### Map to Response
```kotlin
document.select("article").mapNotNull { element ->
    val title = element.selectFirst("h3")?.text() ?: return@mapNotNull null
    val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
    val poster = element.selectFirst("img")?.attr("src") ?: return@mapNotNull null
    
    newMovieSearchResponse(title, fixUrl(href), TvType.Movie) {
        this.posterUrl = fixUrl(poster)
    }
}
```

#### Filter & Transform
```kotlin
document.select("li.episode")
    .filter { it.selectFirst("a") != null }  // Filter
    .mapNotNull { ep ->                       // Transform
        val href = ep.selectFirst("a")?.attr("href") ?: return@mapNotNull null
        val title = ep.selectFirst("span.title")?.text() ?: ""
        val episodeNo = ep.selectFirst("span.ep")?.text()?.toIntOrNull()
        
        newEpisode(fixUrl(href)) {
            this.name = title
            this.episode = episodeNo
        }
    }
```

### 8.3 JSON Parsing

#### JSONObject
```kotlin
val json = JSONObject(response.text)
val title = json.getString("title")
val year = json.optInt("year", 0)
val poster = json.optString("poster", "")

// Nested JSON
val data = json.getJSONObject("data")
val videos = data.getJSONArray("videos")

for (i in 0 until videos.length()) {
    val video = videos.getJSONObject(i)
    val url = video.getString("url")
    // Process video
}
```

#### JSONArray
```kotlin
val array = JSONArray(response.text)
val items = mutableListOf<Item>()

for (i in 0 until array.length()) {
    val item = array.getJSONObject(i)
    items.add(
        Item(
            title = item.getString("title"),
            url = item.getString("url")
        )
    )
}
```

---

# 📙 BOOK 3: BEST PRACTICES

## 9. DO's and DON'Ts

### 9.1 Technical DO's ✅

```kotlin
// ✅ Use rate limiting
suspend fun rateLimitDelay() {
    delay(100 + Random.nextLong(400))
}

// ✅ Handle errors properly
try {
    val doc = app.get(url, timeout=10000).document
} catch (e: Exception) {
    Log.e("Sitename", "Error: ${e.message}", e)
    return emptyList()
}

// ✅ Use null safety
val title = document.selectFirst("h1")?.text()?.trim() ?: ""

// ✅ Cache results
private val searchCache = mutableMapOf<String, List<SearchResponse>>()

// ✅ Use retry logic
val doc = executeWithRetry(maxRetries=3) {
    app.get(url).document
}
```

### 9.2 Technical DON'Ts ❌

```kotlin
// ❌ No rate limiting (spam)
repeat(100) { app.get(url) }

// ❌ No error handling
val doc = app.get(url).document  // Can crash!

// ❌ Force unwrap
val title = document.selectFirst("h1")!!.text()  // CRASH!

// ❌ Hardcode credentials
val apiKey = "sk-1234567890"  // NEVER!

// ❌ No timeout
val doc = app.get(url).document  // Can hang forever!
```

### 9.3 Legal DO's ✅

- ✅ Personal use
- ✅ Educational purposes
- ✅ Testing and development
- ✅ Open source sharing
- ✅ Fork with credit

### 9.4 Legal DON'Ts ❌

- ❌ Commercial use
- ❌ Plagiarism
- ❌ Monetization
- ❌ Copyright infringement
- ❌ Bypass paywalls for profit

### 9.5 Ethical DO's ✅

- ✅ Respectful scraping (rate limiting)
- ✅ Give credit to original authors
- ✅ Report bugs and issues
- ✅ Contribute to open source
- ✅ Follow robots.txt

### 9.6 Ethical DON'Ts ❌

- ❌ DDoS servers (no rate limiting)
- ❌ Bypass security measures
- ❌ Collect user data without consent
- ❌ Include malware
- ❌ Aggressive scraping

---

## 10. CODE QUALITY STANDARDS

### 10.1 Naming Conventions

```kotlin
// Classes: PascalCase
class SitenamePlugin : BasePlugin()

// Functions: camelCase
suspend fun getMainPage()

// Variables: camelCase
val mainUrl = "https://..."

// Constants: UPPER_SNAKE_CASE
private const val MAX_RETRIES = 3

// Private: lowercase with underscore
private val _searchCache = mutableMapOf<String, List<SearchResponse>>()
```

### 10.2 Code Organization

```kotlin
// 1. Package declaration
package com.Sitename

// 2. Imports (grouped)
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

// 3. Class declaration
class Sitename : MainAPI() {
    // 4. Properties
    override var mainUrl = "https://..."
    
    // 5. Companion objects
    companion object {
        private const val TAG = "Sitename"
    }
    
    // 6. Override functions
    override suspend fun getMainPage(...) { }
    
    // 7. Private functions
    private fun toSearchResult(): SearchResponse? { }
}
```

### 10.3 Commenting Standards

```kotlin
/**
 * Extract video URL from embed page
 * @param url Embed page URL
 * @param referer Referer URL
 * @return Video URL or null
 */
private suspend fun extractVideoUrl(url: String, referer: String?): String? {
    // Fetch embed page
    val response = app.get(url, referer=referer)
    
    // Extract URL with regex
    return Regex("""file:\s*['"]([^'"]+)['"]""")
        .find(response.text)?.groupValues?.get(1)
}
```

---

## 11. SECURITY BEST PRACTICES

### 11.1 Input Validation

```kotlin
// Validate URL
fun validateUrl(url: String): Boolean {
    return url.startsWith("https://") && 
           url.contains(".") && 
           url.length < 200
}

// Sanitize query
fun sanitizeQuery(query: String): String {
    return query.replace(Regex("[^a-zA-Z0-9 ]"), "").take(100)
}
```

### 11.2 Secure Storage

```kotlin
// ❌ BAD: Plain text storage
preferences.getString("api_key", "plain_text_key")

// ✅ GOOD: Encrypted storage (use Android Keystore)
val encryptedKey = encrypt(apiKey)
preferences.putString("api_key", encryptedKey)
```

### 11.3 HTTPS Only

```kotlin
// ✅ Always use HTTPS
val url = "https://api.example.com"

// ❌ Never use HTTP (unless required)
val url = "http://api.example.com"  // INSECURE!
```

---

## 12. PERFORMANCE OPTIMIZATION

### 12.1 Lazy Loading

```kotlin
// Load data only when needed
val episodes by lazy {
    fetchEpisodes()
}
```

### 12.2 Parallel Processing

```kotlin
// Process multiple items in parallel
coroutineScope {
    items.map { item ->
        async { processItem(item) }
    }.awaitAll()
}
```

### 12.3 Memory Management

```kotlin
// Clear cache periodically
fun clearOldCache() {
    val now = System.currentTimeMillis()
    cache.entries.removeAll { 
        now - it.value.timestamp > 24 * 60 * 60 * 1000L  // 24 hours
    }
}
```

---

# 📕 BOOK 4: ADVANCED TOPICS

## 13. ADVANCED EXTRACTOR PATTERNS

### 13.1 Multi-Server Support

```kotlin
class MultiServerExtractor : ExtractorApi() {
    override val name = "MultiServer"
    override val mainUrl = "https://multi.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer=referer)
        
        // Extract all server URLs
        val servers = listOf(
            "Server 1" to Regex("""server1:\s*['"]([^'"]+)['"]""").find(response.text)?.groupValues?.get(1),
            "Server 2" to Regex("""server2:\s*['"]([^'"]+)['"]""").find(response.text)?.groupValues?.get(1),
            "Server 3" to Regex("""server3:\s*['"]([^'"]+)['"]""").find(response.text)?.groupValues?.get(1)
        )
        
        servers.forEach { (serverName, serverUrl) ->
            if (serverUrl != null) {
                callback.invoke(
                    newExtractorLink(name, serverName, serverUrl, M3U8) {
                        this.referer = "$mainUrl/"
                    }
                )
            }
        }
    }
}
```

### 13.2 Subtitle Extraction

```kotlin
override suspend fun getUrl(
    url: String,
    referer: String?,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
) {
    val response = app.get(url, referer=referer)
    
    // Extract subtitles
    val subtitles = Regex("""tracks:\s*\[(.*?)\]""")
        .find(response.text)?.groupValues?.get(1)
    
    subtitles?.let {
        Regex("""{"file":"([^"]+)","label":"([^"]+)"}""").findAll(it).forEach { match ->
            subtitleCallback.invoke(
                newSubtitleFile(
                    name = match.groupValues[2],  // Language
                    url = match.groupValues[1]    // Subtitle URL
                )
            )
        }
    }
    
    // Extract video
    val videoUrl = Regex("""file:\s*['"]([^'"]+)['"]""")
        .find(response.text)?.groupValues?.get(1)
        ?: return
    
    callback.invoke(
        newExtractorLink(name, name, videoUrl, M3U8) {
            this.referer = "$mainUrl/"
        }
    )
}
```

---

## 14. ANTI-BOT BYPASS TECHNIQUES

### 14.1 User-Agent Rotation

```kotlin
private val USER_AGENTS = listOf(
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)

private val userAgentIndex = Random.nextInt(USER_AGENTS.size)

fun getRandomUserAgent(): String {
    return USER_AGENTS[(userAgentIndex + Random.nextInt(USER_AGENTS.size)) % USER_AGENTS.size]
}
```

### 14.2 Cookie Handling

```kotlin
// Get cookies from initial request
val initialResponse = app.get(mainUrl)
val cookies = initialResponse.cookies

// Use cookies in subsequent requests
val response = app.get(
    url,
    cookies = cookies
)
```

### 14.3 Cloudflare Bypass

```kotlin
// Use Cloudflare bypass if available
val response = app.get(
    url,
    headers = mapOf(
        "User-Agent" to getRandomUserAgent(),
        "Accept" to "text/html,application/xhtml+xml",
        "Accept-Language" to "en-US,en;q=0.9",
        "Upgrade-Insecure-Requests" to "1"
    ),
    allowRedirects = true
)
```

---

## 15. CACHING STRATEGIES

### 15.1 Multi-Level Cache

```kotlin
class MultiLevelCache {
    // L1: In-memory (fast, small)
    private val l1Cache = mutableMapOf<String, CacheEntry>()
    
    // L2: Disk (slower, larger)
    private val l2Cache = File(context.cacheDir, "http_cache")
    
    fun get(key: String): String? {
        // Try L1 first
        l1Cache[key]?.let { entry ->
            if (!isExpired(entry)) return entry.value
        }
        
        // Try L2
        val l2File = File(l2Cache, key)
        if (l2File.exists()) {
            val value = l2File.readText()
            // Promote to L1
            l1Cache[key] = CacheEntry(value, System.currentTimeMillis())
            return value
        }
        
        return null
    }
    
    fun put(key: String, value: String) {
        // Store in both L1 and L2
        l1Cache[key] = CacheEntry(value, System.currentTimeMillis())
        File(l2Cache, key).writeText(value)
    }
}
```

### 15.2 Cache Invalidation

```kotlin
// Time-based invalidation
fun isExpired(entry: CacheEntry): Boolean {
    return System.currentTimeMillis() - entry.timestamp > TTL
}

// Manual invalidation
fun invalidate(key: String) {
    cache.remove(key)
}

// Pattern-based invalidation
fun invalidatePattern(pattern: String) {
    cache.keys.filter { it.matches(pattern.toRegex()) }.forEach {
        cache.remove(it)
    }
}
```

---

## 16. ERROR HANDLING MASTERY

### 16.1 Custom Error Types

```kotlin
sealed class SitenameError : Exception() {
    class NetworkError(message: String) : SitenameError()
    class ParseError(message: String) : SitenameError()
    class NotFoundError(message: String) : SitenameError()
    class RateLimitError(message: String) : SitenameError()
}

// Usage
suspend fun fetchData(): String {
    try {
        return app.get(url).text
    } catch (e: SocketTimeoutException) {
        throw SitenameError.NetworkError("Request timeout")
    } catch (e: JSONException) {
        throw SitenameError.ParseError("Invalid JSON")
    }
}
```

### 16.2 Error Recovery

```kotlin
suspend fun fetchWithFallback(): String {
    // Try primary source
    try {
        return fetchFromPrimary()
    } catch (e: Exception) {
        Log.w("Sitename", "Primary failed: ${e.message}")
    }
    
    // Fallback to secondary source
    try {
        return fetchFromSecondary()
    } catch (e: Exception) {
        Log.e("Sitename", "Secondary also failed: ${e.message}")
    }
    
    throw Exception("All sources failed")
}
```

---

# 📒 BOOK 5: REFERENCE

## 17. COMMON PATTERNS LIBRARY

### 17.1 Search Pattern

```kotlin
override suspend fun search(query: String): List<SearchResponse> {
    val document = app.get("$mainUrl/search/$query").document
    return document.select("article").mapNotNull { element ->
        val title = element.selectFirst("h3")?.text() ?: return@mapNotNull null
        val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
        val poster = element.selectFirst("img")?.attr("src") ?: return@mapNotNull null
        
        newMovieSearchResponse(title, fixUrl(href), TvType.Movie) {
            this.posterUrl = fixUrl(poster)
        }
    }
}
```

### 17.2 Episode Pattern

```kotlin
override suspend fun load(url: String): LoadResponse {
    val document = app.get(url).document
    val title = document.selectFirst("h1")?.text() ?: ""
    
    val episodes = document.select("ul.episodes > li").mapNotNull { ep ->
        val href = ep.selectFirst("a")?.attr("href") ?: return@mapNotNull null
        val episodeNo = ep.selectFirst("span")?.text()?.toIntOrNull()
        
        newEpisode(fixUrl(href)) {
            this.episode = episodeNo
        }
    }
    
    return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
        this.posterUrl = document.selectFirst("img")?.attr("src")
    }
}
```

### 17.3 Player Pattern

```kotlin
override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val document = app.get(data).document
    val playerLinks = document.select("ul#player-list a")
    
    playerLinks.forEach { player ->
        val playerDoc = app.get(player.attr("href"), referer=mainUrl).document
        val iframe = playerDoc.selectFirst("iframe")?.attr("src") ?: return@forEach
        
        loadExtractor(iframe, mainUrl, subtitleCallback, callback)
    }
    
    return true
}
```

---

## 18. TROUBLESHOOTING GUIDE

### 18.1 Common Errors

#### "Tautan tidak ditemukan" / "No links found"

**Causes:**
- Selector doesn't match HTML
- Website structure changed
- Need special headers

**Solutions:**
```kotlin
// 1. Debug HTML
Log.d("Sitename", "HTML: ${document.html().take(500)}")

// 2. Test selector in browser
document.querySelectorAll("ul#player-list a")

// 3. Add fallback selectors
val links = document.select("ul#player-list a")
    .ifEmpty { document.select("div.player-option a") }
```

#### "Network Timeout"

**Causes:**
- Server slow
- Timeout too short
- Need special headers

**Solutions:**
```kotlin
// Increase timeout
app.get(url, timeout=30000).document

// Add headers
app.get(url, headers=mapOf(
    "User-Agent" to getRandomUserAgent(),
    "Accept" to "*/*"
))
```

#### "Extractor failed"

**Causes:**
- Wrong iframe URL
- Need to fetch player page first
- Redirect not handled

**Solutions:**
```kotlin
// Fetch player page first
val playerDoc = app.get(playerUrl, referer=mainUrl).document
val iframe = playerDoc.selectFirst("iframe")?.attr("src")

// Handle redirects
val finalUrl = if (url.contains("short.icu")) {
    app.get(url, allowRedirects=true).url
} else {
    url
}
```

---

## 19. CODE EXAMPLES REPOSITORY

### 19.1 Complete Example: Movie Site

```kotlin
package com.MovieSite

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

@CloudstreamPlugin
class MovieSitePlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(MovieSite())
    }
}

class MovieSite : MainAPI() {
    override var mainUrl = "https://moviesite.com"
    override var name = "MovieSite"
    override val hasMainPage = true
    override var lang = "id"
    
    override val mainPage = mainPageOf(
        "$mainUrl/populer/page/" to "Film Populer",
        "$mainUrl/terbaru/page/" to "Film Terbaru"
    )
    
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("article.movie").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }
    
    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search/$query").document
        return document.select("article.movie").mapNotNull { it.toSearchResult() }
    }
    
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text() ?: ""
        val poster = document.selectFirst("img.poster")?.attr("src") ?: ""
        
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = fixUrl(poster)
            this.plot = document.selectFirst("div.description")?.text()
        }
    }
    
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        val iframe = document.selectFirst("iframe#player")?.attr("src") ?: return false
        
        loadExtractor(iframe, mainUrl, subtitleCallback, callback)
        return true
    }
    
    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h3")?.text() ?: return null
        val href = selectFirst("a")?.attr("href") ?: return null
        val poster = selectFirst("img")?.attr("src") ?: return null
        
        return newMovieSearchResponse(title, fixUrl(href), TvType.Movie) {
            this.posterUrl = fixUrl(poster)
        }
    }
}
```

---

## 20. EXTERNAL RESOURCES

### 20.1 Official Documentation

- [CloudStream API Docs](https://recloudstream.github.io/cloudstream/)
- [CloudStream GitHub](https://github.com/recloudstream/cloudstream)
- [Jsoup Documentation](https://jsoup.org/apidocs/)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)

### 20.2 Reference Repositories

- **ExtCloud:** `/data/data/com.termux/files/home/cloudstream/ExtCloud/` (134 Kotlin files)
- **phisher:** `/data/data/com.termux/files/home/cloudstream/phisher/` (386 Kotlin files)

### 20.3 Tools

- **Jsoup Online Tester:** https://try.jsoup.org/
- **Regex Tester:** https://regex101.com/
- **Kotlin Playground:** https://play.kotlinlang.org/

---

**END OF DOCUMENTATION**

**Total Lines:** 1000+  
**Status:** ✅ COMPLETE & PRODUCTION READY  
**Maintained By:** CloudStream Development Team  
**License:** Open Source (Educational Purpose)
