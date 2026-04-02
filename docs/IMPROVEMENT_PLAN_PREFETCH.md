# 📋 PREFETCH IMPROVEMENT PLAN

**Date:** 2026-04-02  
**Status:** Planning Phase  
**Priority:** HIGH

---

## 🐛 PROBLEMS IDENTIFIED FROM LOGS

### **Problem 1: Domain Matching Not Working** ❌

**Log Evidence:**
```
04-02 08:28:01.739 13710 32097 D PreFetch:    URL Domain: anichin.cafe
04-02 08:28:01.739 13710 32097 D PreFetch:    Total SyncExtractors available: 59
04-02 08:28:01.741 13710 32097 D PreFetch:    Found 0 matching extractors:
```

**Impact:**
- PreFetch cannot find matching extractors
- All 59 extractors ignored
- Pre-fetch always returns 0 links

**Root Cause Analysis:**
```kotlin
// Current logic (BROKEN):
val extractorDomain = extractor.mainUrl.removePrefix("http://")...
// Example: "https://vidguard.to" → "vidguard.to"

val urlDomain = url.removePrefix("http://")...
// Example: "https://anichin.cafe/episode-1" → "anichin.cafe"

// Matching logic:
val domainMatch = urlDomain.contains(extractorDomain) || extractorDomain.contains(urlDomain)
// "anichin.cafe".contains("vidguard.to") → false ❌
// "vidguard.to".contains("anichin.cafe") → false ❌
```

**Why It Fails:**
- Extractor mainUrl ≠ Iframe URL domain
- Extractors registered with their main site (e.g., `vidguard.to`)
- But iframe URLs are from different domains (e.g., `anichin.cafe`)
- Matching compares wrong domains!

---

### **Problem 2: loadExtractor Returns No Links** ❌

**Log Evidence:**
```
04-02 08:28:01.738 13710 32097 D PreFetch: ✅ loadExtractor completed successfully
04-02 08:28:01.738 13710 32097 W PreFetch: ⚠️ No links from loadExtractor...
```

**Impact:**
- loadExtractor runs without errors
- But returns 0 links
- Falls back to SyncExtractors (which also fail due to Problem 1)

**Root Cause:**
- `loadExtractor` is CloudStream's built-in function
- It tries to match iframe URLs with registered extractors
- If no match found → no links returned
- Same domain matching issue as Problem 1

---

### **Problem 3: PreFetch Always Returns 0 Links** ❌

**Log Evidence:**
```
04-02 08:28:01.742 13710 32097 D PreFetch: ⏱️ Pre-fetch completed in 70ms - 0 links, 0 subtitles
```

**Impact:**
- PreFetch feature is useless
- No caching benefit
- Every episode load requires full extraction (slow)

**Root Cause:**
- Combination of Problem 1 + Problem 2
- No links extracted → nothing to cache

---

## 🎯 IMPROVEMENT RECOMMENDATIONS

### **Recommendation 1: Fix Domain Matching Logic** 🔧

**Priority:** CRITICAL  
**Effort:** Medium  
**Impact:** HIGH

**Current Logic:**
```kotlin
val matchingExtractors = extractors.filter { extractor ->
    val extractorDomain = extractor.mainUrl.removePrefix("http://")...
    val domainMatch = urlDomain.contains(extractorDomain) || extractorDomain.contains(urlDomain)
    val nameMatch = url.contains(extractor.name, ignoreCase = true)
    domainMatch || nameMatch
}
```

**Proposed Logic:**
```kotlin
val matchingExtractors = extractors.filter { extractor ->
    // Extract iframe URL from page first
    val iframeUrl = extractIframeUrl(url) ?: return@filter false
    
    val iframeDomain = iframeUrl.removePrefix("http://")...
    val extractorDomain = extractor.mainUrl.removePrefix("http://")...
    
    // Match iframe domain with extractor domain
    val domainMatch = iframeDomain.contains(extractorDomain) || 
                      extractorDomain.contains(iframeDomain)
    
    // Also try extractor name match
    val nameMatch = iframeUrl.contains(extractor.name, ignoreCase = true)
    
    domainMatch || nameMatch
}
```

**Alternative Approach:**
```kotlin
// Instead of domain matching, try ALL extractors
// Let each extractor decide if it can handle the URL
val matchingExtractors = extractors.filter { extractor ->
    extractor.canHandleUrl(url) // New function to implement
}
```

**Implementation Steps:**
1. Analyze iframe URL patterns in provider pages
2. Update matching logic to use iframe URL, not page URL
3. Add logging for iframe domain extraction
4. Test with multiple providers (Anichin, Animasu, etc.)

---

### **Recommendation 2: Improve loadExtractor Fallback** 🔧

**Priority:** HIGH  
**Effort:** Low  
**Impact:** MEDIUM

**Current Behavior:**
```kotlin
try {
    loadExtractor(url, referer, subtitleCallback, callback)
    Log.d("PreFetch", "✅ loadExtractor completed successfully")
} catch (e: Exception) {
    Log.e("PreFetch", "❌ loadExtractor FAILED...")
}

// Problem: "completed successfully" but no links!
```

**Proposed Fix:**
```kotlin
val linksBefore = links.size
val subtitlesBefore = subtitles.size

try {
    loadExtractor(url, referer, subtitleCallback, callback)
    
    val linksAdded = links.size - linksBefore
    val subtitlesAdded = subtitles.size - subtitlesBefore
    
    if (linksAdded > 0 || subtitlesAdded > 0) {
        Log.d("PreFetch", "✅ loadExtractor found $linksAdded links, $subtitlesAdded subtitles")
    } else {
        Log.w("PreFetch", "⚠️ loadExtractor returned no results (0 links, 0 subtitles)")
    }
} catch (e: Exception) {
    Log.e("PreFetch", "❌ loadExtractor FAILED...")
}
```

**Benefits:**
- Clear indication if loadExtractor actually found anything
- Better debugging
- Can skip fallback if loadExtractor already found links

---

### **Recommendation 3: Add Iframe URL Extraction** 🔧

**Priority:** HIGH  
**Effort:** Medium  
**Impact:** HIGH

**New Function:**
```kotlin
/**
 * Extract iframe URL from episode page
 * @return First iframe URL found, or null if none
 */
suspend fun extractIframeUrl(
    url: String,
    referer: String? = null
): String? {
    return try {
        val document = app.get(url, referer = referer).document
        
        // Try common iframe selectors
        val iframeSelectors = listOf(
            "iframe[src]",
            "meta[property='og:video']",
            "meta[name='twitter:player']",
            "source[src]",
            "video source[src]"
        )
        
        for (selector in iframeSelectors) {
            val iframeUrl = document.selectFirst(selector)?.attr("src")
                ?: document.selectFirst(selector)?.attr("content")
            
            if (!iframeUrl.isNullOrBlank()) {
                Log.d("PreFetch", "🎬 Found iframe URL via $selector: $iframeUrl")
                return fixUrl(iframeUrl, url)
            }
        }
        
        Log.w("PreFetch", "⚠️ No iframe URL found on page")
        null
    } catch (e: Exception) {
        Log.e("PreFetch", "❌ Failed to extract iframe URL: ${e.message}")
        null
    }
}
```

**Integration:**
```kotlin
// In preFetchExtractorLinks:
val iframeUrl = extractIframeUrl(url, referer)

if (iframeUrl != null) {
    // Use iframe URL for matching instead of page URL
    val matchingExtractors = findMatchingExtractors(iframeUrl)
    // ...
}
```

---

### **Recommendation 4: Add Extractor Priority/Ranking** 🎯

**Priority:** MEDIUM  
**Effort:** Medium  
**Impact:** MEDIUM

**Current Behavior:**
- All matching extractors tried equally
- No priority based on success rate

**Proposed Enhancement:**
```kotlin
data class ExtractorStats(
    val name: String,
    val successRate: Float,
    val avgResponseTime: Long,
    val lastUsed: Long
)

// Track extractor performance
object ExtractorPerformanceTracker {
    private val stats = ConcurrentHashMap<String, ExtractorStats>()
    
    fun recordSuccess(extractorName: String, durationMs: Long) {
        // Update stats
    }
    
    fun recordFailure(extractorName: String) {
        // Update stats
    }
    
    fun getRankedExtractors(extractors: List<ExtractorApi>): List<ExtractorApi> {
        return extractors.sortedByDescending { 
            stats[it.name]?.successRate ?: 0.5f 
        }
    }
}
```

**Benefits:**
- Faster extraction (try best extractors first)
- Self-improving over time
- Can skip extractors with 0% success rate

---

### **Recommendation 5: Add PreFetch Cache Validation** ✅

**Priority:** MEDIUM  
**Effort:** Low  
**Impact:** MEDIUM

**Current Behavior:**
- Cache stored with TTL
- No validation if content changed

**Proposed Enhancement:**
```kotlin
suspend fun validateCache(
    url: String,
    cachedLinks: List<ExtractorLink>,
    cachedTimestamp: Long
): Boolean {
    // Quick HEAD request to check if URL still valid
    return try {
        val response = app.head(url)
        val lastModified = response.headers["Last-Modified"]?.toLongOrNull() ?: 0L
        lastModified < cachedTimestamp
    } catch (e: Exception) {
        false // URL not accessible, invalidate cache
    }
}
```

---

### **Recommendation 6: Improve Error Handling & Retry** 🔄

**Priority:** HIGH  
**Effort:** Low  
**Impact:** HIGH

**Current Behavior:**
```kotlin
extractor.getUrl(url, referer, subtitleCallback, callback)
```

**Proposed Enhancement:**
```kotlin
// Add retry logic for extractor calls
suspend fun callExtractorWithRetry(
    extractor: ExtractorApi,
    url: String,
    referer: String?,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    maxRetries: Int = 2
): Boolean {
    var lastException: Exception? = null
    
    repeat(maxRetries + 1) { attempt ->
        try {
            if (attempt > 0) {
                Log.d("PreFetch", "🔄 Retry ${attempt + 1}/${maxRetries + 1} for ${extractor.name}")
                delay(1000L * attempt) // Exponential backoff
            }
            
            extractor.getUrl(url, referer, subtitleCallback, callback)
            return true // Success
        } catch (e: Exception) {
            lastException = e
            Log.w("PreFetch", "⚠️ ${extractor.name} attempt ${attempt + 1} failed: ${e.message}")
        }
    }
    
    Log.e("PreFetch", "❌ ${extractor.name} failed after ${maxRetries + 1} attempts: ${lastException?.message}")
    return false
}
```

---

## 📊 IMPLEMENTATION PRIORITY

| Priority | Recommendation | Effort | Impact | Order |
|----------|---------------|--------|--------|-------|
| **P0** | Fix Domain Matching | Medium | HIGH | 1st |
| **P0** | Improve Error Handling | Low | HIGH | 2nd |
| **P1** | Add Iframe URL Extraction | Medium | HIGH | 3rd |
| **P1** | Improve loadExtractor Fallback | Low | MEDIUM | 4th |
| **P2** | Add Extractor Priority | Medium | MEDIUM | 5th |
| **P2** | Add Cache Validation | Low | MEDIUM | 6th |

---

## 🎯 SUCCESS METRICS

After implementing improvements:

| Metric | Current | Target |
|--------|---------|--------|
| **Matching Extractors Found** | 0 | ≥3 per URL |
| **PreFetch Links Returned** | 0 | ≥1 per episode |
| **PreFetch Success Rate** | 0% | ≥80% |
| **Avg PreFetch Duration** | 70-120ms | ≤100ms |
| **Cache HIT Rate** | 0% | ≥60% |

---

## 📝 NEXT STEPS

1. **Review and approve this plan**
2. **Start with P0 recommendations** (Domain Matching + Error Handling)
3. **Test with real URLs** from Anichin, Animasu, etc.
4. **Measure improvements** against success metrics
5. **Iterate** based on results

---

**Prepared by:** AI Code Analyst  
**Review Status:** Pending  
**Approved by:** -
