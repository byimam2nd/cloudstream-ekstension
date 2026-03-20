# 📚 PUSAT SKILLS & PENGETAHUAN CLOUDSTREAM
## Panduan Lengkap Development Extension Profesional

---

## 🎯 DAFTAR ISI

1. [Introduction](#introduction)
2. [Struktur Dasar Extension](#struktur-dasar-extension)
3. [Selector & Scraping](#selector--scraping)
4. [Extractor Development](#extractor-development)
5. [Best Practices](#best-practices)
6. [Common Patterns](#common-patterns)
7. [Troubleshooting](#troubleshooting)
8. [References](#references)

---

## 📖 INTRODUCTION

### Apa itu CloudStream Extension?

CloudStream Extension adalah plugin yang menambahkan sumber streaming baru ke aplikasi CloudStream. Extension ini menggunakan **Kotlin** dan **Jsoup** untuk scraping website.

### Arsitektur Extension

```
Extension/
├── src/main/kotlin/com/Sitename/
│   ├── Sitename.kt          ← Main API (scraping logic)
│   ├── SitenamePlugin.kt    ← Plugin entry point
│   ├── Extractors.kt        ← Video extractors
│   └── Utils.kt             ← Helper functions
└── build.gradle.kts         ← Build configuration
```

---

## 🏗️ STRUKTUR DASAR EXTENSION

### 1. Plugin File (SitenamePlugin.kt)

```kotlin
package com.Sitename

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.Sitename.AllExtractors

@CloudstreamPlugin
class SitenamePlugin: BasePlugin() {
    override fun load() {
        // Register main API
        registerMainAPI(Sitename())
        
        // Register all extractors
        AllExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
```

**Best Practices:**
- ✅ Gunakan `@CloudstreamPlugin` annotation
- ✅ Extend `BasePlugin`
- ✅ Register main API dan extractors
- ✅ Jangan hardcode extractor satu-satu (gunakan `AllExtractors.list`)

---

### 2. Main API File (Sitename.kt)

```kotlin
package com.Sitename

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Sitename : MainAPI() {
    override var mainUrl = "https://sitename.com"
    override var name = "Sitename"
    override val hasMainPage = true
    override var lang = "id"
    
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )
    
    // Main page categories
    override val mainPage = mainPageOf(
        "$mainUrl/populer/page/" to "Film Terplopuler",
        "$mainUrl/latest/page/" to "Film Terbaru"
    )
    
    // Get main page content
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
    
    // Search functionality
    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search/$query").document
        return document.select("article").mapNotNull {
            it.toSearchResult()
        }
    }
    
    // Load details
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text() ?: ""
        val poster = document.selectFirst("img")?.attr("src") ?: ""
        
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
        }
    }
    
    // Load video links
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Extract player links
        val playerLinks = document.select("ul#player-list a")
        
        playerLinks.forEach { player ->
            val playerDoc = app.get(player.attr("href"), referer=mainUrl).document
            val iframe = playerDoc.selectFirst("iframe")?.attr("src") ?: return@forEach
            
            // Handle redirects
            val finalIframe = if (iframe.contains("short.icu")) {
                app.get(iframe, allowRedirects=true).url
            } else {
                iframe
            }
            
            // Load extractor
            loadExtractor(finalIframe, mainUrl, subtitleCallback, callback)
        }
        
        return true
    }
}
```

---

## ⚠️ DO's and DON'Ts (YANG BOLEH & TIDAK BOLEH)

### 🟢 DO's (YANG BOLEH DILAKUKAN)

#### 1. Technical DO's ✅

```kotlin
// ✅ BOLEH: Gunakan rate limiting untuk mengurangi load server
suspend fun rateLimitDelay() {
    delay(100 + Random.nextLong(400))  // 100-500ms delay
}

// ✅ BOLEH: Cache results untuk mengurangi requests
private val searchCache = mutableMapOf<String, List<SearchResponse>>()
override suspend fun search(query: String): List<SearchResponse> {
    return searchCache.getOrPut(query) { performSearch(query) }
}

// ✅ BOLEH: Gunakan user-agent rotation
private val USER_AGENTS = listOf(
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1.15"
)
fun getRandomUserAgent(): String = USER_AGENTS.random()

// ✅ BOLEH: Handle error dengan baik
try {
    val doc = app.get(url, timeout=10000).document
    // Process
} catch (e: Exception) {
    Log.e("Sitename", "Error: ${e.message}")
    return emptyList()
}

// ✅ BOLEH: Gunakan retry logic untuk failed requests
suspend fun <T> executeWithRetry(
    maxRetries: Int = 3,
    block: suspend () -> T
): T {
    var lastException: Exception? = null
    repeat(maxRetries) { attempt ->
        try { return block() }
        catch (e: Exception) {
            lastException = e
            if (attempt < maxRetries - 1) delay(1000 * (attempt + 1))
        }
    }
    throw lastException ?: Exception("Unknown error")
}
```

#### 2. Legal DO's ✅

```kotlin
// ✅ BOLEH: Extension untuk personal use
// ✅ BOLEH: Extension untuk belajar development
// ✅ BOLEH: Extension untuk testing purposes
// ✅ BOLEH: Share extension sebagai open source
// ✅ BOLEH: Fork dan modify dari repository lain (dengan credit)
```

#### 3. Ethical DO's ✅

```kotlin
// ✅ BOLEH: Berikan credit ke original developer
@CloudstreamPlugin
class SitenamePlugin: BasePlugin() {
    // Original concept by: @username
}

// ✅ BOLEH: Gunakan referensi dari repository lain
// ✅ BOLEH: Contribute ke open source projects
// ✅ BOLEH: Report bugs dan issues
// ✅ BOLEH: Request features yang reasonable
```

---

### 🔴 DON'Ts (YANG TIDAK BOLEH DILAKUKAN)

#### 1. Technical DON'Ts ❌

```kotlin
// ❌ JANGAN: Spam requests tanpa delay
// BAD: No rate limiting
override suspend fun search(query: String): List<SearchResponse> {
    return app.get("$mainUrl/search/$query").document.select("article").mapNotNull {
        it.toSearchResult()
    }
}

// ❌ JANGAN: Skip error handling
// BAD: No try-catch
val doc = app.get(url).document  // Can crash!

// ❌ JANGAN: Hardcode credentials
// BAD: API keys in code
val apiKey = "sk-1234567890abcdef"  // NEVER DO THIS!

// ❌ JANGAN: Ignore timeout
// BAD: No timeout specified
val doc = app.get(url).document  // Can hang forever!

// ❌ JANGAN: Skip null checks
// BAD: Force unwrap
val title = document.selectFirst("h1")!!.text()  // CRASH if null!
```

#### 2. Legal DON'Ts ❌

```kotlin
// ❌ JANGAN: Monetize extension (jual extension)
// ❌ JANGAN: Claim sebagai karya sendiri (plagiarism)
// ❌ JANGAN: Gunakan nama brand tanpa permission
// ❌ JANGAN: Host konten ilegal di repository
// ❌ JANGAN: Bypass paywalls untuk commercial gain
// ❌ JANGAN: Distribusikan konten berbayar secara gratis
```

#### 3. Ethical DON'Ts ❌

```kotlin
// ❌ JANGAN: Spam server dengan requests berlebihan
// BAD: No delay between requests
repeat(100) { app.get(url) }  // Can DDoS server!

// ❌ JANGAN: Scraping dengan interval terlalu cepat
// BAD: No rate limiting
suspend fun scrape() {
    while(true) {
        app.get(url)
        delay(10)  // 10ms = TOO FAST!
    }
}

// ❌ JANGAN: Bypass security measures
// BAD: Bypass Cloudflare protection
val headers = mapOf(
    "X-Forwarded-For" to "1.2.3.4",  // Fake IP
    "CF-Connecting-IP" to "5.6.7.8"   // Bypass Cloudflare
)

// ❌ JANGAN: Collect user data tanpa permission
// BAD: Send user data to external server
app.post("https://analytics.com/track", data=mapOf(
    "user_id" to getUserId(),
    "watch_history" to getHistory()
))

// ❌ JANGAN: Include malware atau virus
// ❌ JANGAN: Backdoor atau spyware
// ❌ JANGAN: Cryptominer
// ❌ JANGAN: Adware yang mengganggu
```

#### 4. Performance DON'Ts ❌

```kotlin
// ❌ JANGAN: Load semua data sekaligus
// BAD: No pagination
override suspend fun search(query: String): List<SearchResponse> {
    return (1..1000).map { page ->  // Load 1000 pages at once!
        app.get("$mainUrl/search/$query?page=$page").document
    }.flatMap { it.select("article").mapNotNull { it.toSearchResult() } }
}

// ❌ JANGAN: Fetch semua gambar di homepage
// BAD: Load all posters
val posters = document.select("img").map { app.get(it.attr("src")).bytes }

// ❌ JANGAN: Skip caching
// BAD: Fetch same data repeatedly
override suspend fun search(query: String): List<SearchResponse> {
    return app.get("$mainUrl/search/$query").document.select("article").mapNotNull {
        it.toSearchResult()
    }
    // Same query = new request every time!
}
```

---

### 📊 Comparison Table

| Aspek | ✅ DO (Boleh) | ❌ DON'T (Tidak Boleh) |
|-------|--------------|------------------------|
| **Rate Limiting** | 100-500ms delay | < 50ms delay (spam) |
| **Timeout** | 10-30 seconds | No timeout (hang) |
| **Error Handling** | Try-catch, logging | Ignore errors |
| **Caching** | Cache search results | Fetch same data repeatedly |
| **User Agent** | Rotate real UAs | Fake/spoof UAs |
| **Credentials** | Env variables, secrets | Hardcoded in code |
| **Data Collection** | Minimal, with consent | Track everything |
| **Server Load** | Respectful, limited | Aggressive scraping |
| **Credit** | Give proper attribution | Claim as own work |
| **Distribution** | Open source, free | Sell for profit |

---

### ⚖️ Legal Disclaimer

**PENTING:** Extension ini dibuat untuk:
- ✅ Personal use
- ✅ Educational purposes
- ✅ Testing dan development

**BUKAN untuk:**
- ❌ Commercial use
- ❌ Distribution of copyrighted content
- ❌ Bypassing paid services
- ❌ Any illegal activities

**Pengguna bertanggung jawab atas:**
- Kepatuhan terhadap hukum lokal
- Terms of Service website
- Copyright laws di negara masing-masing

---

### 🛡️ Security Best Practices

```kotlin
// ✅ DO: Validate input
fun validateUrl(url: String): Boolean {
    return url.startsWith("https://") && 
           url.contains(".") && 
           url.length < 200
}

// ✅ DO: Sanitize user input
fun sanitizeQuery(query: String): String {
    return query.replace(Regex("[^a-zA-Z0-9 ]"), "").take(100)
}

// ✅ DO: Use HTTPS
val url = "https://api.example.com"  // NOT http://

// ✅ DO: Verify SSL certificates
val doc = app.get(url, verifySsl = true).document

// ❌ DON'T: Trust user input blindly
val maliciousUrl = userInput  // Can be javascript:alert(1)!

// ❌ DON'T: Log sensitive data
Log.d("Auth", "Password: $password")  // NEVER!

// ❌ DON'T: Store credentials in plain text
val apiKey = preferences.getString("api_key", "plain_text_key")
```

---

### 📝 Code Review Checklist

Sebelum commit, pastikan:

- [ ] ✅ Null safety (?. !!)
- [ ] ✅ Error handling (try-catch)
- [ ] ✅ Rate limiting (delay)
- [ ] ✅ Timeout specified
- [ ] ✅ No hardcoded credentials
- [ ] ✅ Proper headers (referer, user-agent)
- [ ] ✅ No sensitive data in logs
- [ ] ✅ Credit given to original authors
- [ ] ✅ No copyright violations
- [ ] ✅ Code is clean and readable

---

## 🎯 SELECTOR & SCRAPING

### CSS Selectors yang Sering Digunakan

#### 1. Select by ID
```kotlin
document.select("#player-list")        // id="player-list"
document.selectFirst("#movie-info")    // First match
```

#### 2. Select by Class
```kotlin
document.select(".movie-item")         // class="movie-item"
document.select("div.movie-info")      // div with class
```

#### 3. Select by Attribute
```kotlin
document.select("a[href*=player]")     // href contains "player"
document.select("img[src^=https]")     // src starts with "https"
document.select("option[value$=.mp4]") // value ends with ".mp4"
```

#### 4. Select by Hierarchy
```kotlin
document.select("ul > li > a")         // Direct children
document.select("div.movie a")         // Descendants
document.select("article figure img")  // Nested elements
```

#### 5. Select with Pseudo-classes
```kotlin
document.select("a[href*=player]:first")  // First match
document.select("li:nth-child(2)")        // Second child
```

---

### Best Practices Scraping

#### ✅ DO (Lakukan)

```kotlin
// 1. Gunakan selectFirst() untuk single element
val title = document.selectFirst("h1")?.text() ?: ""

// 2. Handle null dengan safe calls
val poster = document.selectFirst("img")?.attr("src") ?: ""

// 3. Gunakan mapNotNull untuk filtering null
val items = document.select("article").mapNotNull {
    it.toSearchResult()
}

// 4. Fix relative URLs
val href = fixUrl(it.attr("href"))

// 5. Use proper headers
val doc = app.get(url, referer=mainUrl).document

// 6. Add rate limiting
rateLimitDelay()
val doc = app.get(url).document

// 7. Use retry logic
val doc = executeWithRetry(maxRetries=3) {
    app.get(url).document
}
```

#### ❌ DON'T (Jangan Lakukan)

```kotlin
// 1. Jangan langsung access tanpa null check
val title = document.selectFirst("h1").text()  // ERROR jika null!

// 2. Jangan hardcode full URL
val url = "https://sitename.com/video/123"  // BAD!

// 3. Jangan skip error handling
app.get(url).document  // No error handling!

// 4. Jangan lupa referer
app.get(embedUrl).document  // Missing referer!
```

---

## 🎬 EXTRACTOR DEVELOPMENT

### Basic Extractor Template

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

### Common Extractor Patterns

#### 1. Direct MP4/M3U8

```kotlin
val videoUrl = document.selectFirst("source")?.attr("src")
    ?: document.selectFirst("video > source")?.attr("src")
```

#### 2. Iframe Extraction

```kotlin
val iframeUrl = document.selectFirst("iframe")?.attr("src")
    ?: document.selectFirst("div.embed-container iframe")?.attr("src")
```

#### 3. JSON API

```kotlin
val json = JSONObject(response.text)
val videoUrl = json.getString("videoUrl")
```

#### 4. JavaScript Evaluation

```kotlin
val script = document.selectFirst("script:containsData(eval)")?.data()
val unpacked = getAndUnpack(script)
val videoUrl = Regex("""file:\s*['"]([^'"]+)['"]""")
    .find(unpacked)?.groupValues?.get(1)
```

#### 5. Multiple Qualities

```kotlin
val qualities = document.select("option[data-quality]")
qualities.forEach { option ->
    val quality = option.attr("data-quality")
    val url = option.attr("value")
    callback.invoke(
        newExtractorLink(name, "$name $quality", url, M3U8) {
            this.quality = quality.toIntOrNull() ?: Qualities.Unknown.value
        }
    )
}
```

---

## 📋 BEST PRACTICES

### 1. Error Handling

```kotlin
try {
    val doc = app.get(url, timeout=10000).document
    // Process document
} catch (e: Exception) {
    Log.e("Sitename", "Error: ${e.message}")
    return false
}
```

### 2. Rate Limiting

```kotlin
// Add delay before each request
suspend fun rateLimitDelay() {
    delay(100 + Random.nextLong(400))  // 100-500ms random delay
}
```

### 3. Caching

```kotlin
// Cache search results
private val searchCache = mutableMapOf<String, List<SearchResponse>>()

override suspend fun search(query: String): List<SearchResponse> {
    return searchCache.getOrPut(query) {
        // Perform search
        document.select("article").mapNotNull { it.toSearchResult() }
    }
}
```

### 4. User Agent Rotation

```kotlin
private val USER_AGENTS = listOf(
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1.15",
    "Mozilla/5.0 (X11; Linux x86_64) Firefox/121.0"
)

fun getRandomUserAgent(): String = USER_AGENTS.random()
```

### 5. Retry Logic

```kotlin
suspend fun <T> executeWithRetry(
    maxRetries: Int = 3,
    block: suspend () -> T
): T {
    var lastException: Exception? = null
    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastException = e
            if (attempt < maxRetries - 1) delay(1000 * (attempt + 1))
        }
    }
    throw lastException ?: Exception("Unknown error")
}
```

---

## 🔧 COMMON PATTERNS

### 1. Search Result Parser

```kotlin
private fun Element.toSearchResult(): SearchResponse? {
    val title = this.selectFirst("h3")?.text()?.trim() ?: return null
    val href = fixUrl(this.selectFirst("a")!!.attr("href"))
    val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
    val rating = this.selectFirst("span.rating")?.text()?.toDoubleOrNull()
    
    return newMovieSearchResponse(title, href, TvType.Movie) {
        this.posterUrl = posterUrl
        this.score = Score.from10(rating)
    }
}
```

### 2. Episode Parser

```kotlin
val episodes = document.select("ul.episodes > li").mapNotNull { ep ->
    val href = ep.selectFirst("a")?.attr("href") ?: return@mapNotNull null
    val title = ep.selectFirst("span.title")?.text() ?: ""
    val episodeNo = ep.selectFirst("span.ep")?.text()?.toIntOrNull()
    
    newEpisode(href) {
        this.name = title
        this.episode = episodeNo
    }
}
```

### 3. Trailer Parser

```kotlin
val trailerUrl = document.selectFirst("div.trailer a")?.attr("href")
addTrailer(trailerUrl)
```

### 4. Genre/Tags Parser

```kotlin
val tags = document.select("div.tags span").map { it.text() }
```

### 5. Recommendation Parser

```kotlin
val recommendations = document.select("div.related article").mapNotNull { rec ->
    val title = rec.selectFirst("h3")?.text() ?: return@mapNotNull null
    val href = fixUrl(rec.selectFirst("a")!!.attr("href"))
    val poster = rec.selectFirst("img")?.attr("src") ?: ""
    
    newMovieSearchResponse(title, href, TvType.Movie) {
        this.posterUrl = poster
    }
}
```

---

## 🐛 TROUBLESHOOTING

### 1. "Tautan tidak ditemukan" / "No links found"

**Penyebab:**
- Selector tidak match dengan HTML
- Website update struktur
- Perlu referer/header khusus

**Solusi:**
```kotlin
// 1. Cek HTML structure dengan curl
curl -A "Mozilla/5.0" https://sitename.com/page | grep -o "player-list"

// 2. Test selector di browser console
document.querySelectorAll("ul#player-list a")

// 3. Add logging
Log.d("Sitename", "HTML: ${document.html().take(500)}")
```

### 2. "Network Timeout"

**Penyebab:**
- Server lambat
- Timeout terlalu pendek
- Perlu headers khusus

**Solusi:**
```kotlin
// Increase timeout
app.get(url, timeout=30000).document

// Add proper headers
app.get(url, headers=mapOf(
    "User-Agent" to getRandomUserAgent(),
    "Accept" to "*/*",
    "Referer" to mainUrl
)).document
```

### 3. "Extractor failed"

**Penyebab:**
- Iframe URL salah
- Perlu fetch player page dulu
- Ada redirect

**Solusi:**
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

// Try multiple extractors
loadExtractor(url, referer, subtitleCallback, callback) ||
loadExtractor(url, referer, subtitleCallback, callback)
```

### 4. "ClassCastException" / Type Errors

**Penyebab:**
- Null pointer
- Wrong type conversion

**Solusi:**
```kotlin
// Safe conversion
val rating = selectFirst("span")?.text()?.toDoubleOrNull() ?: 0.0

// Null check
val title = selectFirst("h1")?.text() ?: return null
```

---

## 📚 REFERENCES

### ExtCloud Repository
- **Location:** `/data/data/com.termux/files/home/cloudstream/ExtCloud/`
- **Total Files:** 134 Kotlin files
- **Best Examples:**
  - `LayarKacaProvider/` - Simple & effective scraping
  - `Donghub/` - Good extractor patterns
  - `AnichinMoe/` - Clean code structure

### Phisher Repository  
- **Location:** `/data/data/com.termux/files/home/cloudstream/phisher/`
- **Total Files:** 386 Kotlin files
- **Best Examples:**
  - `Anichi/` - Advanced caching & rate limiting
  - `AnimeCloud/` - Complex extractor handling
  - `AllMovieLandProvider/` - Comprehensive error handling

### Official Documentation
- [CloudStream API Docs](https://recloudstream.github.io/cloudstream/)
- [Jsoup Documentation](https://jsoup.org/apidocs/)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)

---

## 🎓 LEARNING PATH

### Beginner (Pemula)
1. ✅ Pahami struktur dasar extension
2. ✅ Pelajari CSS selectors dasar
3. ✅ Buat extractor sederhana
4. ✅ Implement search & load functions

### Intermediate (Menengah)
1. ✅ Pelajari error handling yang baik
2. ✅ Implement caching & rate limiting
3. ✅ Buat extractor complex (JavaScript unpacking)
4. ✅ Handle multiple quality & servers

### Advanced (Lanjut)
1. ✅ Optimasi performance
2. ✅ Handle anti-bot protection
3. ✅ Buat custom extractor framework
4. ✅ Implement advanced features (subtitles, recommendations)

---

**Last Updated:** 2026-03-20
**Maintainer:** CloudStream Development Team
**Status:** ✅ Active & Maintained
