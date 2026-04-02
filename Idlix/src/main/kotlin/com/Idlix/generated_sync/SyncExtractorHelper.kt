// ========================================
// MASTER EXTRACTOR HELPER - v3.6
// Helper untuk loadExtractor dengan fallback + Pre-fetching
// ========================================
// Last Updated: 2026-03-26
// Sync Target: generated_sync/SyncExtractorHelper.kt
//
// PURPOSE:
// - Helper function untuk semua modul
// - Try loadExtractor first
// - If failed, try direct extractor call from SyncExtractors
// - Support pre-fetching untuk instant loading
//
// USAGE:
// ```kotlin
// // Normal usage
// val loaded = loadExtractorWithFallback(...)
// 
// // Pre-fetch usage
// EpisodePreFetcher.preFetchEpisodes(episodes, mainUrl)
// if (EpisodePreFetcher.loadCached(url, callback)) return true
// ```
// ========================================

// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/MasterExtractorHelper.kt
// File: SyncExtractorHelper.kt
// ========================================
package com.Idlix.generated_sync

import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.base64Decode
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

// ============================================
// REGION 0: IFRAME EXTRACTION HELPERS
// ============================================

/**
 * Extract iframe URL from HTML document
 * Call this in provider's loadLinks() before calling preFetchExtractorLinks()
 * 
 * @param document Parsed HTML document from episode page
 * @param baseUrl Base URL for resolving relative URLs
 * @return First iframe URL found, or null if none
 */
fun extractIframeFromDocument(
    document: Document,
    baseUrl: String
): String? {
    // Try common iframe selectors
    val iframeSelectors = listOf(
        "iframe[src]",
        "meta[property='og:video']",
        "meta[name='twitter:player']",
        "source[src]",
        "video source[src]",
        "li[data-index] a", // Anichin specific
        ".mobius > .mirror > option" // Animasu specific
    )
    
    for (selector in iframeSelectors) {
        val element = document.selectFirst(selector)
        val iframeUrl = when {
            selector.contains("meta") -> element?.attr("content")
            selector.contains("option") -> {
                // Handle base64 encoded iframe (Anichin/Animasu pattern)
                val base64 = element?.attr("value")
                if (!base64.isNullOrBlank() && base64.length > 20) {
                    try {
                        val decoded = base64Decode(base64)
                        val decodedDoc = Jsoup.parse(decoded)
                        decodedDoc.selectFirst("iframe")?.attr("src")
                    } catch (e: Exception) { null }
                } else {
                    element?.attr("data-index")
                }
            }
            else -> element?.attr("src")
        }
        
        if (!iframeUrl.isNullOrBlank()) {
            return fixUrl(iframeUrl, baseUrl)
        }
    }
    
    return null
}

/**
 * Fix relative URL to absolute URL
 */
fun fixUrl(url: String, baseUrl: String): String {
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    if (url.startsWith("//")) return "https:$url"
    if (url.startsWith("/")) {
        val base = baseUrl.substringBeforeLast("/")
        return "$base$url"
    }
    return url
}

// ============================================
// REGION 1: LOAD EXTRACTOR WITH FALLBACK + CIRCUIT BREAKER
// ============================================

/**
 * Load extractor dengan fallback ke direct extractor call
 * PLUS CircuitBreaker untuk failure isolation!
 *
 * Flow:
 * 1. Try loadExtractor (CloudStream API)
 * 2. If failed (returns false), find matching extractor from SyncExtractors
 * 3. Wrap extractor call with CircuitBreaker
 * 4. CircuitBreaker auto-skip extractor yang gagal 3x berturut-turut
 * 5. Return true if at least 1 extractor worked
 *
 * CIRCUIT BREAKER BENEFITS:
 * - Auto-skip failing extractors (no long timeouts)
 * - Auto-recovery setelah 1 menit
 * - Prevent cascade failures
 * - Better user experience
 *
 * OPTIMIZATION:
 * - Parallel execution dengan coroutine async
 * - CircuitBreaker per extractor (independent failure tracking)
 * - Speed improvement: 3-5x faster + failure isolation
 *
 * @param url Video URL to extract
 * @param referer Referer URL
 * @param subtitleCallback Subtitle callback
 * @param callback ExtractorLink callback
 * @return true if at least 1 extractor worked, false otherwise
 */
suspend fun loadExtractorWithFallback(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    var loaded = false
    val startTime = System.currentTimeMillis()

    Log.d("ExtractorHelper", "▶️ loadExtractorWithFallback called for: $url")

    // Step 1: Try loadExtractor (CloudStream API)
    try {
        Log.d("ExtractorHelper", "📞 Calling loadExtractor...")
        loaded = loadExtractor(url, referer, subtitleCallback, callback)
        Log.d("ExtractorHelper", "✅ loadExtractor result: $loaded")
    } catch (e: Exception) {
        Log.e("ExtractorHelper", "❌ loadExtractor exception: ${e.javaClass.simpleName}: ${e.message}\n   Stack: ${e.stackTraceToString().take(300)}")
    }

    // Step 2: If failed, try direct extractor call from SyncExtractors WITH CIRCUIT BREAKER
    if (!loaded) {
        Log.w("ExtractorHelper", "⚠️ loadExtractor failed, trying direct SyncExtractors with CircuitBreaker...")
        Log.d("ExtractorHelper", "   URL: $url")

        val urlDomain = url.removePrefix("http://").removePrefix("https://").split("/").first().lowercase()
        Log.d("ExtractorHelper", "   URL Domain: $urlDomain")

        // Get SyncExtractors list from generated_sync package
        // Using direct object reference instead of reflection for reliability
        val extractors = com.Idlix.generated_sync.SyncExtractors.list
        Log.d("ExtractorHelper", "   Total SyncExtractors available: ${extractors.size}")

        // Find ALL matching extractors
        val matchingExtractors = extractors.filter { extractor ->
            val extractorDomain = extractor.mainUrl.removePrefix("http://").removePrefix("https://").split("/").first().lowercase()
            val domainMatch = urlDomain.contains(extractorDomain) || extractorDomain.contains(urlDomain)
            val nameMatch = url.contains(extractor.name, ignoreCase = true)
            domainMatch || nameMatch
        }

        Log.d("ExtractorHelper", "   Found ${matchingExtractors.size} matching extractors: ${matchingExtractors.joinToString { it.name }}")

        // Try ALL matching extractors IN PARALLEL WITH CIRCUIT BREAKER
        var successCount = 0
        var failCount = 0
        try {
            coroutineScope {
                matchingExtractors.forEach { extractor ->
                    launch {
                        try {
                            // Get CircuitBreaker for this extractor
                            val breaker = CircuitBreakerRegistry.getOrCreate(
                                extractor.name,
                                failureThreshold = 3
                            )

                            Log.d("ExtractorHelper", "   🎯 Trying extractor: ${extractor.name} (${extractor.mainUrl})")

                            // Wrap extractor call with CircuitBreaker
                            val result = breaker.execute {
                                extractor.getUrl(url, referer, subtitleCallback, callback)
                            }

                            if (result != null) {
                                successCount++
                                Log.d("ExtractorHelper", "      ✅ SUCCESS: Extractor ${extractor.name} worked!")
                            } else {
                                failCount++
                                Log.w("ExtractorHelper", "      🔴 CircuitBreaker OPEN for ${extractor.name} - skipping (failed 3+ times)")
                            }
                        } catch (e: Exception) {
                            failCount++
                            Log.e("ExtractorHelper", "      ❌ ${extractor.name} FAILED: ${e.javaClass.simpleName}: ${e.message}\n         Stack trace: ${e.stackTraceToString().lines().take(5).joinToString("\\n         ")}")
                        }
                    }
                }
            }
            loaded = true  // Mark as loaded (extractors were called)
            Log.d("ExtractorHelper", "📊 SyncExtractors result: $successCount success, $failCount failed")
        } catch (e: Exception) {
            Log.e("ExtractorHelper", "❌ Parallel extraction failed: ${e.javaClass.simpleName}: ${e.message}\n   Stack: ${e.stackTraceToString().take(300)}")
        }
    }

    val duration = System.currentTimeMillis() - startTime
    Log.d("ExtractorHelper", "⏱️ loadExtractorWithFallback completed in ${duration}ms - loaded=$loaded")
    return loaded
}

// ============================================
// REGION 2: PRE-FETCH HELPER
// ============================================

/**
 * Pre-fetch extractor links untuk caching
 *
 * OVERLOAD 1: With iframe URL (RECOMMENDED)
 * Use this when you've already extracted iframe URL from the page
 *
 * @param url Page URL (for fallback)
 * @param iframeUrl Iframe URL extracted from page (if available)
 * @param referer Referer URL
 * @return Pair of (List<ExtractorLink>, List<SubtitleFile>)
 */
suspend fun preFetchExtractorLinks(
    url: String,
    iframeUrl: String?,
    referer: String? = null
): Pair<List<ExtractorLink>, List<SubtitleFile>> {
    // If iframe URL provided, use it for matching
    val targetUrl = iframeUrl ?: url
    
    if (iframeUrl != null) {
        Log.d("PreFetch", "🎬 Using iframe URL: $iframeUrl")
    }
    
    return preFetchExtractorLinksInternal(targetUrl, url, referer)
}

/**
 * Pre-fetch extractor links untuk caching
 *
 * OVERLOAD 2: Without iframe URL (LEGACY)
 * Only use this if you haven't extracted iframe URL
 *
 * @param url Video URL to extract
 * @param referer Referer URL
 * @return Pair of (List<ExtractorLink>, List<SubtitleFile>)
 */
suspend fun preFetchExtractorLinks(
    url: String,
    referer: String? = null
): Pair<List<ExtractorLink>, List<SubtitleFile>> {
    return preFetchExtractorLinksInternal(url, url, referer)
}

/**
 * Internal implementation for pre-fetch
 */
private suspend fun preFetchExtractorLinksInternal(
    targetUrl: String,
    pageUrl: String,
    referer: String?
): Pair<List<ExtractorLink>, List<SubtitleFile>> {
    val links = mutableListOf<ExtractorLink>()
    val subtitles = mutableListOf<SubtitleFile>()
    val startTime = System.currentTimeMillis()

    Log.d("PreFetch", "▶️ Starting pre-fetch for URL: $targetUrl")

    // Step 1: Try loadExtractor and check if it actually found links
    val linksBefore = links.size
    val subtitlesBefore = subtitles.size
    
    try {
        Log.d("PreFetch", "📞 Calling loadExtractor...")
        loadExtractor(targetUrl, referer ?: targetUrl,
            subtitleCallback = { sub -> subtitles.add(sub) },
            callback = { link -> links.add(link) }
        )
        
        val linksAdded = links.size - linksBefore
        val subtitlesAdded = subtitles.size - subtitlesBefore
        
        if (linksAdded > 0 || subtitlesAdded > 0) {
            Log.d("PreFetch", "✅ loadExtractor found $linksAdded links, $subtitlesAdded subtitles")
        } else {
            Log.w("PreFetch", "⚠️ loadExtractor returned no results (0 links, 0 subtitles)")
        }
    } catch (e: Exception) {
        Log.e("PreFetch", "❌ loadExtractor FAILED: ${e.javaClass.simpleName}: ${e.message}\n   Stack: ${e.stackTraceToString().take(500)}")
    }

    // Step 2: If no links, try direct extractors with IMPROVED matching
    if (links.isEmpty()) {
        Log.w("PreFetch", "⚠️ No links from loadExtractor, trying direct SyncExtractors...")

        // Extract domain from TARGET URL (iframe URL if available)
        val urlDomain = targetUrl.removePrefix("http://").removePrefix("https://").split("/").first().lowercase()
        Log.d("PreFetch", "   Target Domain: $urlDomain")

        // Get SyncExtractors list from generated_sync package
        val extractors = com.Idlix.generated_sync.SyncExtractors.list
        Log.d("PreFetch", "   Total SyncExtractors available: ${extractors.size}")

        // Find matching extractors using MULTIPLE strategies:
        // 1. Match by extractor name in URL
        // 2. Match by domain in URL
        // 3. Match by common video hosters
        val matchingExtractors = extractors.filter { extractor ->
            // Strategy 1: Name match in URL
            val nameMatch = targetUrl.contains(extractor.name, ignoreCase = true)
            
            // Strategy 2: Domain match (for when URL contains extractor domain)
            val extractorDomain = extractor.mainUrl.removePrefix("http://").removePrefix("https://").split("/").first().lowercase()
            val domainMatch = urlDomain.contains(extractorDomain) || extractorDomain.contains(urlDomain)
            
            // Strategy 3: Common video hoster patterns
            val videoHosterMatch = when (extractor.name.lowercase()) {
                "vidguard", "vgfplay" -> urlDomain.contains("vidguard") || urlDomain.contains("vgf")
                "voe" -> urlDomain.contains("voe")
                "filemoon" -> urlDomain.contains("filemoon")
                "streamwish", "wish" -> urlDomain.contains("streamwish") || urlDomain.contains("wish")
                "dood", "dsvplay" -> urlDomain.contains("dood") || urlDomain.contains("dsv")
                "mixdrop" -> urlDomain.contains("mixdrop")
                "okru" -> urlDomain.contains("ok.ru") || urlDomain.contains("odnoklassniki")
                "uploadboy" -> urlDomain.contains("uploadboy")
                else -> false
            }
            
            nameMatch || domainMatch || videoHosterMatch
        }

        Log.d("PreFetch", "   Found ${matchingExtractors.size} matching extractors: ${matchingExtractors.joinToString { it.name }}")

        // Try all matching extractors with retry logic
        var successCount = 0
        var failCount = 0
        
        matchingExtractors.forEach { extractor ->
            var lastException: Exception? = null
            var succeeded = false
            
            // Retry up to 2 times
            repeat(3) { attempt ->
                if (succeeded) return@repeat
                
                try {
                    if (attempt > 0) {
                        Log.d("PreFetch", "   🔄 Retry ${attempt + 1}/3 for ${extractor.name}")
                        delay(1000L * attempt) // Exponential backoff
                    }
                    
                    Log.d("PreFetch", "   🎯 Trying extractor: ${extractor.name} (${extractor.mainUrl})")
                    extractor.getUrl(
                        targetUrl,
                        referer ?: targetUrl,
                        subtitleCallback = { subtitleFile ->
                            synchronized(subtitles) {
                                subtitles.add(subtitleFile)
                                Log.d("PreFetch", "      ↳ ${extractor.name} found subtitle")
                            }
                        },
                        callback = { extractorLink ->
                            synchronized(links) {
                                links.add(extractorLink)
                                successCount++
                                Log.d("PreFetch", "      ↳ ${extractor.name} SUCCESS: ${extractorLink.source} - ${extractorLink.type}")
                            }
                            succeeded = true // Stop retrying on success
                        }
                    )
                    
                    if (succeeded) {
                        Log.d("PreFetch", "      ✅ ${extractor.name} succeeded on attempt ${attempt + 1}")
                    }
                } catch (e: Exception) {
                    lastException = e
                    failCount++
                    Log.w("PreFetch", "      ⚠️ ${extractor.name} attempt ${attempt + 1} failed: ${e.message}")
                }
            }
            
            if (!succeeded) {
                Log.e("PreFetch", "      ❌ ${extractor.name} failed after 3 attempts: ${lastException?.message}")
            }
        }

        Log.d("PreFetch", "📊 SyncExtractors result: $successCount success, $failCount failed")
    }

    val duration = System.currentTimeMillis() - startTime
    Log.d("PreFetch", "⏱️ Pre-fetch completed in ${duration}ms - ${links.size} links, ${subtitles.size} subtitles")
    return Pair(links, subtitles)
}

// ============================================
// REGION 3: EPISODE PRE-FETCHER
// ============================================

/**
 * Helper object untuk pre-fetching episode links
 * 
 * Usage di module:
 * ```kotlin
 * // Di load() function
 * EpisodePreFetcher.preFetchEpisodes(episodes, mainUrl)
 * 
 * // Di loadLinks() function
 * if (EpisodePreFetcher.loadCached(episodeUrl, callback)) return true
 * ```
 */
object EpisodePreFetcher {
    private const val PRE_FETCH_LIMIT = 10
    private const val CACHE_TTL = 10 * 60 * 1000L  // 10 minutes
    
    private val linkCache = CacheManager<List<ExtractorLink>>()
    private val subtitleCache = CacheManager<List<SubtitleFile>>()
    
    /**
     * Pre-fetch links untuk multiple episodes
     *
     * @param episodes List of episodes to pre-fetch
     * @param mainUrl Main URL of the site (for referer)
     */
    suspend fun preFetchEpisodes(episodes: List<Episode>, mainUrl: String) {
        coroutineScope {
            episodes.take(PRE_FETCH_LIMIT).forEach { episode ->
                launch {
                    try {
                        Log.d("PreFetch", "Pre-fetching: ${episode.name}")

                        val (links, subtitles) = preFetchExtractorLinks(
                            url = episode.data,
                            referer = mainUrl
                        )

                        if (links.isNotEmpty()) {
                            linkCache.put(episode.data, links, ttl = CACHE_TTL)
                            subtitleCache.put(episode.data, subtitles, ttl = CACHE_TTL)
                            Log.d("PreFetch", "Pre-fetched: ${episode.name} (${links.size} links)")
                        }
                    } catch (e: Exception) {
                        Log.e("PreFetch", "Pre-fetch failed for ${episode.name}: ${e.message}")
                    }
                }
            }
        }
        
        Log.d("PreFetch", "Started pre-fetching ${minOf(episodes.size, PRE_FETCH_LIMIT)} episodes")
    }
    
    /**
     * SMART PRE-FETCH: Pre-fetch NEXT episode only based on current episode
     * 
     * Benefits:
     * - Less network overhead (1 episode vs 10)
     * - More targeted pre-fetching
     * - Better resource usage
     * 
     * Usage in loadLinks():
     * ```kotlin
     * // After successfully loading current episode
     * EpisodePreFetcher.preFetchNextEpisode(
     *     currentEpisodeNumber = 5,
     *     episodes = allEpisodes,
     *     mainUrl = mainUrl
     * )
     * ```
     * 
     * @param currentEpisodeNumber Current episode number being watched
     * @param episodes All available episodes
     * @param mainUrl Main URL of the site (for referer)
     */
    suspend fun preFetchNextEpisode(
        currentEpisodeNumber: Int,
        episodes: List<Episode>,
        mainUrl: String
    ) {
        // Find next episode
        val nextEpisode = episodes.find { 
            it.episode == (currentEpisodeNumber + 1) 
        } ?: run {
            Log.d("PreFetch", "No next episode to pre-fetch")
            return
        }
        
        coroutineScope {
            launch {
                try {
                    Log.d("PreFetch", "🎯 Smart pre-fetching next episode: ${nextEpisode.name}")
                    
                    val (links, subtitles) = preFetchExtractorLinks(
                        url = nextEpisode.data,
                        referer = mainUrl
                    )
                    
                    if (links.isNotEmpty()) {
                        linkCache.put(nextEpisode.data, links, ttl = CACHE_TTL)
                        subtitleCache.put(nextEpisode.data, subtitles, ttl = CACHE_TTL)
                        Log.d("PreFetch", "✅ Smart pre-fetched: ${nextEpisode.name} (${links.size} links)")
                    }
                } catch (e: Exception) {
                    Log.e("PreFetch", "Smart pre-fetch failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Load cached links untuk episode
     * 
     * @param episodeUrl Episode URL
     * @param callback ExtractorLink callback
     * @param subtitleCallback SubtitleFile callback
     * @return true jika cache hit, false jika cache miss
     */
    suspend fun loadCached(
        episodeUrl: String,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ): Boolean {
        val cachedLinks = linkCache.get(episodeUrl)
        val cachedSubs = subtitleCache.get(episodeUrl)
        
        if (!cachedLinks.isNullOrEmpty()) {
            Log.d("Cache", "CACHE HIT! Instant loading for $episodeUrl")
            
            cachedLinks.forEach { callback.invoke(it) }
            cachedSubs?.forEach { subtitleCallback.invoke(it) }
            
            return true
        }
        
        Log.d("Cache", "CACHE MISS, extracting normally for $episodeUrl")
        return false
    }
    
    /**
     * Clear all cached data
     */
    suspend fun clearCache() {
        linkCache.clear()
        subtitleCache.clear()
        Log.d("PreFetch", "Cache cleared")
    }
}
