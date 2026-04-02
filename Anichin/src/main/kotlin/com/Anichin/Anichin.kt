package com.Anichin

import com.Anichin.generated_sync.CacheManager
import com.Anichin.generated_sync.AutoUsedConstants

import com.lagradost.api.Log
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.concurrent.ConcurrentHashMap

// ============================================
// OPTIMIZED: Import shared utilities from generated_sync
// ============================================
import com.Anichin.generated_sync.rateLimitDelay
import com.Anichin.generated_sync.getRandomUserAgent
import com.Anichin.generated_sync.executeWithRetry
import com.Anichin.generated_sync.logError
import com.Anichin.generated_sync.logDebug
import com.Anichin.generated_sync.EpisodePreFetcher
import com.Anichin.generated_sync.SmartCacheMonitor
import com.Anichin.generated_sync.HttpClientFactory
import com.Anichin.generated_sync.CompiledRegexPatterns
import com.Anichin.generated_sync.CircuitBreaker
import com.Anichin.generated_sync.CircuitBreakerRegistry
import com.Anichin.generated_sync.MasterLinkGenerator

// Cache instances
private val searchCache = CacheManager<List<SearchResponse>>()
private val mainPageCache = CacheManager<HomePageResponse>()

// Smart Cache Monitor for fingerprint-based cache validation
class AnichinMonitor : SmartCacheMonitor() {
    override suspend fun fetchTitles(url: String): List<String> {
        val document = executeWithRetry {
            rateLimitDelay(moduleName = "Anichin")
            app.get(
                url,
                timeout = CHECK_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }
        return document.select("div.listupd > article div.bsx > a")
            .mapNotNull { it.attr("title").trim() }
            .filter { it.isNotEmpty() }
    }
}

private val monitor = AnichinMonitor()
private val cacheFingerprints = ConcurrentHashMap<String, SmartCacheMonitor.CacheFingerprint>()

open class Anichin : MainAPI() {
    override var mainUrl = "https://anichin.cafe"
    override var name = "Anichin"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "seri/?status=&type=&order=popular&page=" to "Popular Donghua",
        "seri/?status=&type=&order=update&page=" to "Recently Updated",
        "seri/?sub=&order=latest&page=" to "Latest Added",
        "seri/?status=ongoing&type=&order=update&page=" to "Ongoing",
        "seri/?status=completed&type=&order=update&page=" to "Completed",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val cacheKey = "${request.data}${page}"

        // Check cache with fingerprint validation
        val cached = mainPageCache.get(cacheKey)
        val storedFingerprint = cacheFingerprints[cacheKey]
        
        if (cached != null) {
            if (storedFingerprint != null) {
                // Validate cache with fingerprint
                val validity = monitor.checkCacheValidity(mainUrl, storedFingerprint)
                when (validity) {
                    SmartCacheMonitor.CacheValidationResult.CACHE_VALID -> {
                        logDebug("Anichin", "Cache HIT (validated) for $cacheKey")
                        return cached
                    }
                    SmartCacheMonitor.CacheValidationResult.CACHE_INVALID -> {
                        logDebug("Anichin", "Cache INVALID - refetching for $cacheKey")
                        // Invalidate by putting null
                        cacheFingerprints.remove(cacheKey)
                    }
                    else -> {
                        logDebug("Anichin", "Cache validation failed, using cached for $cacheKey")
                        return cached
                    }
                }
            } else {
                logDebug("Anichin", "Cache HIT (no fingerprint) for $cacheKey")
                return cached
            }
        }

        logDebug("Anichin", "Cache MISS for $cacheKey")

        // Fetch dengan retry logic dan rate limiting
        val document = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            app.get(
                "$mainUrl/${request.data}$page",
                timeout = AutoUsedConstants.DEFAULT_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }

        val home = document.select("div.listupd > article").mapNotNull {
            runCatching { it.toSearchResult() }.getOrElse { null }
        }

        val response = newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )

        // Generate and store fingerprint
        val fingerprint = monitor.generateFingerprint(mainUrl)
        if (fingerprint != null) {
            cacheFingerprints[cacheKey] = fingerprint
        }
        
        // Cache the result
        mainPageCache.put(cacheKey, response)

        return response
    }

    suspend fun Element.toSearchResult(): SearchResponse {
        // FIXED: Fallback strategy untuk title (2-layer)
        val title = this.select("div.bsx > a").attr("title")
            .ifEmpty { this.selectFirst("div.bsx a")?.attr("title").orEmpty() }

        val href = fixUrl(this.select("div.bsx > a").attr("href"))
        
        // FIXED: Fallback strategy untuk poster image (3-layer)
        val posterUrl = fixUrlNull(
            this.selectFirst("div.bsx a img")?.getImageAttr()
                ?: this.selectFirst("div.bsx img")?.attr("data-src")
                ?: this.selectFirst("div.bsx img")?.attr("src")
        )

        // Fix: Use correct selector - .epx for status (Ongoing/Completed)
        val statusText = this.selectFirst("div.bsx .epx")?.text() ?: ""
        val isOngoing = statusText.contains("Ongoing", ignoreCase = true)

        // Fetch episode count from detail page for badge display
        val episodeCount = runCatching {
            val doc = app.get(
                href,
                timeout = AutoUsedConstants.DEFAULT_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
            doc.select(".eplister li[data-index]").mapNotNull { ep ->
                ep.selectFirst(".epl-num")?.text()?.trim()?.toIntOrNull()
            }.maxOrNull()
        }.getOrNull()

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addDubStatus(
                dubExist = false,
                subExist = true,
                dubEpisodes = null,
                subEpisodes = episodeCount
            )
        }
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("data-src")
            this.hasAttr("src") -> this.attr("src")
            else -> this.attr("src")
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // OPTIMIZED: Gunakan CacheManager dengan TTL 30 menit
        val cacheKey = "search_${query}"

        // Check cache first (NO RATE LIMIT FOR CACHE HIT!)
        val cached = searchCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        // OPTIMIZED: Parallel search dengan rate limiting
        val results = coroutineScope {
            (1..3).map { page ->
                async {
                    try {
                        rateLimitDelay()
                        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")

                        // WordPress search: page 1 has no /page/1/ in URL
                        val searchUrl = if (page == 1) {
                            "${mainUrl}/?s=$encodedQuery"
                        } else {
                            "${mainUrl}/page/$page/?s=$encodedQuery"
                        }

                        val document = app.get(
                            searchUrl,
                            timeout = AutoUsedConstants.DEFAULT_TIMEOUT,
                            headers = mapOf("User-Agent" to getRandomUserAgent())
                        ).documentLarge

                        document.select("div.listupd > article").mapNotNull {
                            runCatching { it.toSearchResult() }.getOrElse { null }
                        }
                    } catch (e: Exception) {
                        logError("Anichin", "Search page $page failed: ${e.message}", e)
                        emptyList<SearchResponse>()
                    }
                }
            }.awaitAll().flatten().distinctBy { it.url }
        }

        // Cache the result
        searchCache.put(cacheKey, results)

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            app.get(
                url,
                timeout = AutoUsedConstants.DEFAULT_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }

        // FIXED: Fallback strategy untuk title (3-layer)
        val title = document.selectFirst("h1.entry-title")?.text()?.trim()
            ?: document.selectFirst("h1.title")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: "Unknown Title"

        val href = document.selectFirst(".eplister li > a")?.attr("href") ?: ""

        // FIXED: Fallback strategy untuk poster (4-layer)
        var poster = document.selectFirst("div.thumb > img")?.attr("src")
            ?: document.selectFirst("div.thumb img")?.attr("src")
            ?: document.selectFirst("img.ts-post-image")?.attr("src")
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: ""

        // FIXED: Fallback strategy untuk description (4-layer)
        val description = document.selectFirst("div.entry-content")?.text()?.trim()
            ?: document.selectFirst("div.description")?.text()?.trim()
            ?: document.selectFirst("div.synopsis")?.text()?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content")
            ?: ""

        // FIXED: Robust type detection dengan fallback strategy
        val type = document.selectFirst(".spe")?.text()
            ?: document.selectFirst(".meta .type")?.text()
            ?: document.selectFirst("span.type")?.text()
            ?: ""
        val isMovie = type.contains("Movie", ignoreCase = true) || url.contains("-movie-", ignoreCase = true)
        val tvType = if (isMovie) TvType.AnimeMovie else TvType.Anime

        // FIXED: Robust status detection dengan fallback
        val statusText = document.select(".spe").text().lowercase()
            .ifEmpty { document.select(".meta .status").text().lowercase() }
            .ifEmpty { document.select("span.status").text().lowercase() }
        val showStatus = when {
            "ongoing" in statusText || "continuing" in statusText -> ShowStatus.Ongoing
            "completed" in statusText || "finished" in statusText -> ShowStatus.Completed
            else -> null
        }

        return if (tvType == TvType.Anime) {
            // FIXED: Use more general selector to catch all episodes including "END" episodes
            val allEpisodes = document.select(".eplister li")

            val episodes = allEpisodes.mapNotNull { info ->
                val href1 = info.select("a").attr("href")
                if (href1.isEmpty()) return@mapNotNull null

                // FIXED: Extract episode number more robustly
                val episodeText = info.selectFirst(".epl-num")?.text()?.trim().orEmpty()
                // Try to extract number from text like "52", "52 END", "END", etc.
                val episodeNumber = episodeText.replace(Regex("[^0-9]"), "").toIntOrNull()

                val episodeTitle = info.selectFirst(".epl-title")?.text()?.trim() ?: ""
                val cleanName = episodeTitle.replace(title, "", ignoreCase = true).trim()
                
                // FIXED: Fallback strategy untuk episode poster (3-layer)
                var posterr = info.selectFirst("a img")?.attr("data-src")
                    ?: info.selectFirst("a img")?.attr("src")
                    ?: info.selectFirst("img[data-lazy-src]")?.attr("data-lazy-src")
                    ?: ""

                // Image optimization
                if (posterr.isNotEmpty()) {
                    posterr = optimizeImageUrl(posterr)
                }

                newEpisode(href1) {
                    this.name = cleanName.ifEmpty { episodeTitle }
                    this.episode = episodeNumber
                    this.posterUrl = posterr
                }
            }.reversed()

            // 🎯 PRE-FETCH: DISABLED - Always fails with page URL (anichin.cafe ≠ vidguard.to)
            // PreFetch requires iframe URL, not page URL
            // Will be re-enabled when iframe extraction is implemented
            // EpisodePreFetcher.preFetchEpisodes(episodes, mainUrl)

            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            } else {
                poster = optimizeImageUrl(poster)
            }

            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.showStatus = showStatus
            }
        } else {
            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            } else {
                poster = optimizeImageUrl(poster)
            }
            newMovieLoadResponse(title, url, TvType.AnimeMovie, href) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    // OPTIMIZED: Image URL optimizer
    private fun optimizeImageUrl(url: String, maxWidth: Int = 500): String {
        return when {
            url.contains("anichin") -> url  // Anichin already optimizes
            else -> url
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 🎯 CHECK CACHE FIRST (from pre-fetch)
        if (EpisodePreFetcher.loadCached(data, callback, subtitleCallback)) {
            return true
        }
        
        // No cache → extract normally
        val html = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            app.get(
                data,
                timeout = AutoUsedConstants.DEFAULT_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }

        // Try multiple selectors for video options
        val options = html.select("option[data-index]")
            .ifEmpty { html.select("option[value]") }
            .ifEmpty { html.select("select option") }

        logDebug("Anichin", "Found ${options.size} video options")

        if (options.isEmpty()) {
            logError("Anichin", "No video options found! Website structure may have changed.")
            return false
        }

        // Track successful links
        var successCount = 0

        // OPTIMIZED: Use supervisorScope for exception safety
        supervisorScope {
            options.map { option ->
                async {
                    try {
                        val base64 = option.attr("value").trim()
                        if (base64.isBlank()) {
                            logDebug("Anichin", "Skipping empty value option")
                            return@async
                        }

                        val label = option.text().trim()
                        logDebug("Anichin", "Processing server: $label")

                        val decodedHtml = try {
                            base64Decode(base64)
                        } catch (e: Exception) {
                            logError("Anichin", "Base64 decode failed for $label: ${e.message}")
                            return@async
                        }

                        val iframeUrl = Jsoup.parse(decodedHtml)
                            .selectFirst("iframe")?.attr("src")
                            ?.let(::httpsify)

                        if (iframeUrl.isNullOrEmpty()) {
                            logDebug("Anichin", "No iframe found for $label")
                            return@async
                        }

                        logDebug("Anichin", "Found iframe URL for $label: ${iframeUrl.take(50)}...")

                        // Handle different server types
                        when {
                            iframeUrl.endsWith(".mp4") -> {
                                MasterLinkGenerator.createLink(
                                    source = label,
                                    url = iframeUrl,
                                    referer = data,
                                    quality = getQualityFromName(label)
                                )?.let {
                                    callback(it)
                                    successCount++
                                }
                            }
                            else -> {
                                logDebug("Anichin", "Using loadExtractorWithFallback for $label")

                                // ✅ USE loadExtractorWithFallback dengan CircuitBreaker
                                // Note: Using loadExtractorWithFallback instead of preFetchExtractorLinks
                                // because PreFetch always fails with page URL (anichin.cafe)
                                // loadExtractorWithFallback will extract iframe URL internally
                                val loaded = com.Anichin.generated_sync.loadExtractorWithFallback(
                                    url = iframeUrl,
                                    referer = data,
                                    subtitleCallback = subtitleCallback,
                                    callback = callback
                                )

                                if (loaded) {
                                    successCount++
                                    logDebug("Anichin", "✅ loadExtractorWithFallback succeeded")
                                } else {
                                    logDebug("Anichin", "⚠️ loadExtractorWithFallback returned no results")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logError("Anichin", "Unexpected error processing server ${option.text()}: ${e.message}")
                        // Continue to next server - don't fail all!
                    }
                }
            }.awaitAll()
        }

        logDebug("Anichin", "loadLinks completed: $successCount/${options.size} servers working")

        // 🎯 SMART PRE-FETCH: DISABLED - Always fails with page URL
        // PreFetch requires iframe URL, not page URL
        // Will be re-enabled when iframe extraction is implemented
        // EpisodePreFetcher.preFetchEpisodes(episodes, mainUrl)

        // Return true if at least 1 server works
        return successCount > 0
    }
}
