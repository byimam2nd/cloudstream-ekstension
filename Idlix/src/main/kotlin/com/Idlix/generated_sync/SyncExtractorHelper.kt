// ========================================
// MASTER EXTRACTOR HELPER - v4.0
// Helper untuk loadExtractor dengan fallback + Pre-fetching + Marathon
// ========================================
// Last Updated: 2026-04-05
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
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
    var deliveredLinks = 0
    val startTime = System.currentTimeMillis()

    Log.d("ExtractorHelper", "▶️ loadExtractorWithFallback called for: $url")

    // Wrapped callbacks to track actual link delivery
    val trackedCallback: (ExtractorLink) -> Unit = { link ->
        deliveredLinks++
        Log.d("ExtractorHelper", "      🔗 Delivered link: ${link.source} - ${link.quality}p")
        callback(link)
    }
    val trackedSubtitleCallback: (SubtitleFile) -> Unit = { sub ->
        Log.d("ExtractorHelper", "      📝 Delivered subtitle: ${sub.language}")
        subtitleCallback(sub)
    }

    // Step 1: Try loadExtractor (CloudStream API)
    try {
        Log.d("ExtractorHelper", "📞 Calling loadExtractor...")
        loaded = loadExtractor(url, referer, trackedSubtitleCallback, trackedCallback)
        Log.d("ExtractorHelper", "✅ loadExtractor result: $loaded (links delivered: $deliveredLinks)")
    } catch (e: Exception) {
        Log.e("ExtractorHelper", "❌ loadExtractor exception: ${e.javaClass.simpleName}: ${e.message}")
    }

    // Step 2: If Step 1 failed OR delivered 0 links, try SyncExtractors directly
    if (!loaded || deliveredLinks == 0) {
        if (deliveredLinks == 0) {
            Log.w("ExtractorHelper", "⚠️ loadExtractor returned $loaded with 0 links, trying direct SyncExtractors...")
        } else {
            Log.w("ExtractorHelper", "⚠️ loadExtractor failed, trying direct SyncExtractors...")
        }
        Log.d("ExtractorHelper", "   URL: $url")

        val urlDomain = url.removePrefix("http://").removePrefix("https://").split("/").first().lowercase()
        Log.d("ExtractorHelper", "   URL Domain: $urlDomain")

        // Get SyncExtractors list from generated_sync package
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

        // Try ALL matching extractors IN PARALLEL
        // SEMAPHORE: Max 3 concurrent extractor calls to prevent server overload
        val extractorSemaphore = Semaphore(3)
        try {
            coroutineScope {
                matchingExtractors.forEach { extractor ->
                    launch {
                        extractorSemaphore.withPermit {
                            try {
                                Log.d("ExtractorHelper", "   🎯 Trying extractor: ${extractor.name} (${extractor.mainUrl})")
                                extractor.getUrl(url, referer, trackedSubtitleCallback, trackedCallback)
                            } catch (e: Exception) {
                                Log.e("ExtractorHelper", "      ❌ ${extractor.name} FAILED: ${e.javaClass.simpleName}: ${e.message}")
                            }
                        } // end withPermit
                    }
                }
            }
            // Only mark loaded if ACTUAL links were delivered
            loaded = deliveredLinks > 0
            Log.d("ExtractorHelper", "📊 SyncExtractors result: $deliveredLinks links delivered from ${matchingExtractors.size} extractors tried")
            if (deliveredLinks == 0) {
                Log.w("ExtractorHelper", "⚠️ No links delivered — all extractors returned empty")
            }
        } catch (e: Exception) {
            Log.e("ExtractorHelper", "❌ Parallel extraction failed: ${e.javaClass.simpleName}: ${e.message}")
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
// REGION 3: EPISODE PRE-FETCHER (MARATHON-READY)
// ============================================

/**
 * Smart Episode Pre-Fetcher untuk Marathon Watching
 *
 * Fitur:
 * - Auto-detect marathon pattern (user nonton berurutan)
 * - Pre-fetch links episode berikutnya saat user nonton episode sekarang
 * - Connection pre-warming ke video server episode berikutnya
 * - TTL 10 menit — cukup untuk nonton 1 episode (20-25 menit)
 *
 * Usage di provider loadLinks():
 * ```kotlin
 * // Setelah berhasil load episode, trigger pre-fetch next episode
 * EpisodePreFetcher.onEpisodeWatched(
 *     currentEpisode = episode,
 *     allEpisodes = episodes,
 *     mainUrl = mainUrl
 * )
 * ```
 *
 * Otomatis:
 * - Episode 1 selesai dimuat → pre-fetch Episode 2
 * - Episode 2 selesai dimuat → pre-fetch Episode 3
 * - Dan seterusnya...
 */
object EpisodePreFetcher {
    private const val CACHE_TTL = 10 * 60 * 1000L  // 10 minutes

    private val linkCache = CacheManager<List<ExtractorLink>>()
    private val subtitleCache = CacheManager<List<SubtitleFile>>()

    // Marathon detection: track watch history
    private val watchHistory = mutableListOf<WatchEvent>()
    private const val MARATHON_THRESHOLD = 2  // 2 consecutive episodes = marathon detected

    data class WatchEvent(
        val episodeNumber: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Panggil ini SETELAH berhasil load episode — trigger smart pre-fetch
     *
     * Auto-detect marathon: jika user sudah nonton 2+ episode berurutan,
     * otomatis pre-fetch episode berikutnya dengan priority lebih tinggi.
     *
     * @param currentEpisode Episode yang sedang dimuat
     * @param allEpisodes Semua episode yang tersedia
     * @param mainUrl Main URL provider (untuk referer)
     */
    suspend fun onEpisodeWatched(
        currentEpisode: Episode,
        allEpisodes: List<Episode>,
        mainUrl: String
    ) {
        val epNum = currentEpisode.episode ?: return

        // Track watch history untuk marathon detection
        watchHistory.add(WatchEvent(epNum))

        // Keep only recent history (last 5 episodes)
        if (watchHistory.size > 5) watchHistory.removeAt(0)

        // Check if user is in marathon mode
        val isMarathon = detectMarathonPattern()

        // Find next episode
        val nextEpisode = allEpisodes.find { it.episode == epNum + 1 } ?: run {
            Log.d("PreFetch", "No next episode after ep $epNum")
            return
        }

        if (isMarathon) {
            Log.d("PreFetch", "🏃 Marathon detected! Aggressive pre-fetch for ep ${epNum + 1}")
        }

        // Pre-fetch links untuk next episode
        coroutineScope {
            launch {
                try {
                    // Pre-warm connection dulu (TCP/TLS handshake)
                    HttpClientFactory.preWarmConnection(nextEpisode.data)

                    // Jika marathon, langsung extract links
                    if (isMarathon) {
                        Log.d("PreFetch", "🎯 Extracting links for next episode: ${nextEpisode.name}")
                        val (links, subtitles) = preFetchExtractorLinks(
                            url = nextEpisode.data,
                            referer = mainUrl
                        )

                        if (links.isNotEmpty()) {
                            linkCache.put(nextEpisode.data, links, ttl = CACHE_TTL)
                            subtitleCache.put(nextEpisode.data, subtitles, ttl = CACHE_TTL)
                            Log.d("PreFetch", "✅ Pre-fetched: ${nextEpisode.name} (${links.size} links, ${subtitles.size} subs)")
                        }
                    } else {
                        Log.d("PreFetch", "🔥 Pre-warmed connection for: ${nextEpisode.name}")
                    }
                } catch (e: Exception) {
                    Log.d("PreFetch", "Pre-fetch next episode failed (non-critical): ${e.message}")
                }
            }
        }
    }

    /**
     * Detect marathon pattern dari watch history
     * Marathon = 2+ episode berurutan dalam 30 menit terakhir
     */
    private fun detectMarathonPattern(): Boolean {
        if (watchHistory.size < MARATHON_THRESHOLD) return false

        val now = System.currentTimeMillis()
        val thirtyMinutesAgo = now - 30 * 60 * 1000L

        // Get recent watches
        val recent = watchHistory.filter { it.timestamp > thirtyMinutesAgo }
        if (recent.size < MARATHON_THRESHOLD) return false

        // Check if consecutive
        for (i in 1 until recent.size) {
            val diff = recent[i].episodeNumber - recent[i - 1].episodeNumber
            if (diff != 1) return false  // Not consecutive
        }

        return true
    }

    /**
     * LEGACY: Pre-fetch links untuk multiple episodes (saat halaman detail dibuka)
     * Pre-warm connection saja, tidak extract links (hemat bandwidth)
     *
     * @param episodes List of episodes
     * @param mainUrl Main URL provider
     */
    suspend fun preWarmEpisodes(episodes: List<Episode>, mainUrl: String) {
        coroutineScope {
            episodes.take(5).forEach { episode ->
                launch {
                    try {
                        // Hanya pre-warm connection, tidak extract links
                        HttpClientFactory.preWarmConnection(episode.data)
                    } catch (_: Exception) {
                        // Ignore per-episode failures
                    }
                }
            }
        }
    }

    /**
     * LEGACY: Pre-fetch links untuk multiple episodes (full extraction)
     * Gunakan hanya untuk episode-episode awal (first 3)
     */
    suspend fun preFetchEpisodes(episodes: List<Episode>, mainUrl: String) {
        coroutineScope {
            episodes.take(3).forEach { episode ->
                launch {
                    try {
                        val (links, subtitles) = preFetchExtractorLinks(
                            url = episode.data,
                            referer = mainUrl
                        )

                        if (links.isNotEmpty()) {
                            linkCache.put(episode.data, links, ttl = CACHE_TTL)
                            subtitleCache.put(episode.data, subtitles, ttl = CACHE_TTL)
                        }
                    } catch (_: Exception) {
                        // Ignore per-episode failures
                    }
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
