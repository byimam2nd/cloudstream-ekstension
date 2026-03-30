# 🚀 QUICK START GUIDE

**For:** New & Existing Developers  
**Time:** 5 minutes  
**Goal:** Get you productive fast!

---

## 📚 STEP 1: Understand Architecture (1 min)

```
┌─────────────────────────────────────────────────────────┐
│                    MASTER FOLDER                        │
│              (Single Source of Truth)                   │
│                                                         │
│  • MasterExtractors.kt       → 75+ extractors + P1 + P2 │
│  • MasterHttpClientFactory.kt → HTTP optimization       │
│  • MasterAutoUsed.kt         → Auto-used utilities      │
│  • MasterCaches.kt           → Caching system           │
│  • ... (9 files total)                                  │
└────────────────────┬────────────────────────────────────┘
                     │
                     │ Sync Workflow (Auto)
                     ▼
┌─────────────────────────────────────────────────────────┐
│              GENERATED_SYNC FOLDERS                     │
│         (Auto-generated per provider)                   │
│                                                         │
│  Anichin/generated_sync/    ← Sync*.kt                 │
│  Animasu/generated_sync/    ← Sync*.kt                 │
│  Idlix/generated_sync/      ← Sync*.kt                 │
│  ... (8 providers total)                                │
└─────────────────────────────────────────────────────────┘
```

**Key Concept:** Edit in `master/` → Auto-sync to all providers

---

## 💻 STEP 2: Common Patterns (2 min)

### **Pattern 1: Auto-Detect Quality (RECOMMENDED)**

```kotlin
import com.{Provider}.generated_sync.MasterLinkGenerator

class MyExtractor : ExtractorApi() {
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val videoUrl = extractVideoUrl(...)
        
        // ✅ BEST: Auto-detect quality, type, headers
        MasterLinkGenerator.createLink(
            source = "MyExtractor",
            url = videoUrl,
            referer = referer
        )?.let { callback(it) }
    }
}
```

**What it does:**
- ✅ Auto-detect quality (1080p, 720p, 480p)
- ✅ Auto-detect type (M3U8, VIDEO)
- ✅ Auto-generate headers (Referer, Origin, User-Agent)

---

### **Pattern 2: M3U8 Playlist (Multiple Qualities)**

```kotlin
import com.{Provider}.generated_sync.MasterLinkGenerator

class M3U8Extractor : ExtractorApi() {
    override suspend fun getUrl(...) {
        val playlistUrl = extractPlaylistUrl(...)
        
        // ✅ Parse M3U8, return ALL quality variants
        MasterLinkGenerator.createLinksFromM3U8(
            source = "MyExtractor",
            m3u8Url = playlistUrl,
            referer = referer,
            callback = callback
        )
        // Result: [1080p, 720p, 480p, 360p] - User can switch!
    }
}
```

**What it does:**
- ✅ Fetch M3U8 master playlist
- ✅ Parse all quality variants
- ✅ Return multiple ExtractorLink (user can switch quality manually)

---

### **Pattern 3: Manual Quality Override**

```kotlin
MasterLinkGenerator.createLink(
    source = "MyExtractor",
    url = videoUrl,
    referer = referer,
    quality = 720  // ← Force 720p
)?.let { callback(it) }
```

**When to use:**
- When auto-detect fails
- When you want specific quality cap

---

### **Pattern 4: Search with Caching**

```kotlin
import com.{Provider}.generated_sync.CacheManager

private val searchCache = CacheManager<List<SearchResponse>>()

override suspend fun search(query: String): List<SearchResponse> {
    val cacheKey = "search_$query"
    
    // ✅ Check cache first (NO RATE LIMIT FOR CACHE HIT!)
    searchCache.get(cacheKey)?.let { 
        return it  // Cache HIT - instant response!
    }
    
    // Cache MISS - fetch from network
    val results = fetchResults(query)
    
    // Save to cache (TTL: 30 minutes)
    searchCache.put(cacheKey, results)
    
    return results
}
```

**What it does:**
- ✅ Check cache before network call
- ✅ Auto-save with TTL (30 min default)
- ✅ Prevent duplicate requests

---

### **Pattern 5: Retry Logic**

```kotlin
import com.{Provider}.generated_sync.executeWithRetry

override suspend fun fetchResults(query: String): List<SearchResponse> {
    return executeWithRetry(maxRetries = 3) {
        app.get("$mainUrl/search?q=$query").document
    }
}
```

**What it does:**
- ✅ Auto-retry on failure (3 attempts)
- ✅ Consistent error handling
- ✅ Better reliability

---

## ✅ DO's & ❌ DON'Ts (1 min)

### **✅ DO:**

```kotlin
// ✅ Use MasterLinkGenerator for single URLs
MasterLinkGenerator.createLink(source, url, referer)

// ✅ Use INFER_TYPE for auto-detect type
newExtractorLink(source, name, url, INFER_TYPE) { ... }

// ✅ Import from generated_sync
import com.{Provider}.generated_sync.MasterLinkGenerator

// ✅ Use CacheManager for repeated requests
private val cache = CacheManager<T>()

// ✅ Use executeWithRetry for network calls
executeWithRetry { app.get(url) }
```

### **❌ DON'T:**

```kotlin
// ❌ Manual quality detection (use auto-detect)
val quality = when {
    url.contains("1080") -> 1080
    url.contains("720") -> 720
    else -> 480
}

// ❌ Manual type detection (use INFER_TYPE)
val type = if (url.endsWith(".m3u8")) {
    ExtractorLinkType.M3U8
} else {
    ExtractorLinkType.VIDEO
}

// ❌ Edit generated_sync files directly (edit master/ instead)
// ❌ Forget to import from generated_sync
```

---

## 🔧 TROUBLESHOOTING (1 min)

### **Q: Build failed with "Unresolved reference: MasterLinkGenerator"**

**A:** Pastikan sync workflow sudah selesai:
```bash
# Check if generated_sync exists
ls */generated_sync/SyncExtractors.kt

# If not exists, trigger sync manually
gh workflow run "Sync All Master Files"
```

---

### **Q: Extractor tidak return links**

**A:** Check logs dan gunakan CircuitBreaker:
```kotlin
// Add logging
logDebug("MyExtractor", "Extracting URL: $url")

// Use CircuitBreaker for reliability
val breaker = CircuitBreakerRegistry.get("MyExtractor")
breaker.execute {
    // extraction logic
}
```

---

### **Q: Cache tidak bekerja**

**A:** Pastikan TTL dan cache key benar:
```kotlin
// ✅ Correct
cache.put(key, data, ttl = 30 * 60 * 1000L)  // 30 minutes

// ❌ Wrong (TTL too short)
cache.put(key, data, ttl = 1000L)  // 1 second!
```

---

### **Q: Sync workflow gagal**

**A:** Check logs di GitHub Actions:
```bash
# View latest sync run
gh run list --limit 1 --json databaseId

# View logs
gh run view <ID> --log
```

---

## 📚 NEXT STEPS

**After mastering these patterns:**

1. **Read:** [PHILOSOPHY_AND_ARCHITECTURE.md](PHILOSOPHY_AND_ARCHITECTURE.md) - Deep dive
2. **Read:** [CODE_EXAMPLES.md](CODE_EXAMPLES.md) - More examples
3. **Read:** [FUNCTION_INDEX.md](FUNCTION_INDEX.md) - Function lookup

---

**Last Updated:** 2026-03-30  
**Maintainer:** CloudStream Extension Team  
**Questions?** Check troubleshooting or ask in team chat
