# 📖 Extended Guide

Untuk developer yang ingin membuat extension baru.

---

## 🎯 Selector Patterns

### **Basic (90% cases)**

```kotlin
// Single selector - simple!
val items = document.select("div.animposx")
```

### **With Fallback (when needed)**

```kotlin
// Only if website sering berubah
val items = document.select("div.animposx")
    .ifEmpty { document.select("div.anime-card") }
```

---

## 📋 Common Patterns

### **Main Page**

```kotlin
override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val document = app.get("${request.data}$page").document
    val items = document.select("div.listupd > article").map { it.toSearchResult() }
    return newHomePageResponse(request.name, items)
}

private fun Element.toSearchResult(): AnimeSearchResponse {
    val title = selectFirst("a[title]")?.attr("title").orEmpty()
    val href = fixUrl(selectFirst("a")?.attr("href").orEmpty())
    val poster = selectFirst("img")?.attr("src").orEmpty()
    
    return newAnimeSearchResponse(title, href, TvType.Anime) {
        this.posterUrl = poster
    }
}
```

### **Search**

```kotlin
override suspend fun search(query: String): List<SearchResponse> {
    val document = app.get("$mainUrl/?s=$query").document
    return document.select("div.animposx").map { it.toSearchResult() }
}
```

### **Load (Detail Page)**

```kotlin
override suspend fun load(url: String): LoadResponse {
    val document = app.get(url).document
    
    val title = document.selectFirst("h1.entry-title")?.text().orEmpty()
    val poster = document.selectFirst("div.thumb img")?.attr("src").orEmpty()
    val synopsis = document.selectFirst("div.desc p")?.text().orEmpty()
    
    val episodes = document.select("ul#daftarepisode > li").mapNotNull { ep ->
        val href = ep.selectFirst("a")?.attr("href") ?: return@mapNotNull null
        val epNum = ep.selectFirst(".epl-num")?.text()?.toIntOrNull()
        newEpisode(href) { episode = epNum }
    }.reversed()
    
    return newAnimeLoadResponse(title, url, TvType.Anime, episodes) {
        this.posterUrl = poster
        this.plot = synopsis
    }
}
```

### **Load Links (Download)**

```kotlin
override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val document = app.get(data).document
    
    document.select("div#downloadb ul li").amap { li ->
        val quality = li.selectFirst("strong")?.text().orEmpty()
        val href = li.selectFirst("a")?.attr("href").orEmpty()
        
        loadExtractor(href, subtitleCallback, callback) { link ->
            newExtractorLink(link.name, link.name, link.url, link.type) {
                this.quality = quality
            }
        }
    }
    
    return true
}
```

---

## 🔧 Selectors Cheat Sheet

| Purpose | Selector | Example |
|---------|----------|---------|
| **By Class** | `.classname` | `div.animposx` |
| **By ID** | `#idname` | `#player-list` |
| **By Tag** | `tagname` | `a`, `div`, `img` |
| **Attribute** | `[attr]` | `a[title]`, `img[src]` |
| **Contains** | `[attr*=val]` | `a[href*='episode']` |
| **Direct Child** | `parent > child` | `ul > li` |
| **Descendant** | `ancestor desc` | `div a` |

---

## 🐛 Troubleshooting

### **No items found**

```kotlin
// 1. Inspect element di browser
// 2. Check selector di DevTools
document.select("div.animposx")  // Test di console

// 3. Update selector jika perlu
```

### **Build error**

```bash
# Check imports
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

# Check package name
package com.MySite

# Clean build
./gradlew clean
./gradlew :MySite:make
```

### **Extractor tidak bekerja**

```kotlin
// 1. Test extractor URL langsung
val testUrl = "https://extractor.com/e/VIDEO_ID"
val response = app.get(testUrl).text

// 2. Check response
println(response)

// 3. Debug video URL
val videoUrl = Regex("""file:\s*['"]([^'"]+)['"]""")
    .find(response)?.groupValues?.get(1)
println("Video URL: $videoUrl")
```

---

## 📚 Resources

- **CloudStream Docs:** https://recloudstream.github.io/cloudstream/
- **Jsoup Docs:** https://jsoup.org/apidocs/
- **CSS Selectors:** https://www.w3schools.com/cssref/css_selectors.php

---

**Last Updated:** 2026-03-20  
**Maintained by:** imam2nd
