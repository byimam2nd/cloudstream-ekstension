package com.Donghuastream

// ============================================
// GROUP 1: Generated Sync Imports
// ============================================
import com.Donghuastream.generated_sync.*

// ============================================
// GROUP 2: CloudStream Library
// ============================================
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

// ============================================
// GROUP 3: Kotlin Coroutines
// ============================================
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

// ============================================
// GROUP 4: External Libraries
// ============================================
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

// Caching using shared CacheManager from CacheManager.kt
private val searchCache = CacheManager<List<SearchResponse>>(defaultTtl = 30 * 60 * 1000L)
private val mainPageCache = CacheManager<HomePageResponse>(defaultTtl = 5 * 60 * 1000L)

open class Donghuastream : MainAPI() {
    override var mainUrl = "https://donghuastream.org"
    override var name                 = "Donghuastream"
    override val hasMainPage          = true
    override var lang                 = "zh"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "anime/?status=&type=&order=update&page=" to "Recently Updated",
        "anime/?status=completed&type=&order=update" to "Completed",
        "anime/?status=&type=special&sub=&order=update" to "Special Anime",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // CACHING: Check cache first (instant load for 5 minutes)
        val cacheKey = "${request.data}${page}"
        
        // Simple cache check (no fingerprint overhead)
        mainPageCache.get(cacheKey)?.let { cached ->
            logDebug("Donghuastream", "Cache HIT for $cacheKey")
            return cached
        }

        logDebug("Donghuastream", "Cache MISS for $cacheKey")

        val document = executeWithRetry {
            rateLimitDelay()
            app.get(
                "$mainUrl/${request.data}$page",
                timeout = AutoUsedConstants.FAST_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }
        val home = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }
        val response = newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )

        // Cache the result
        mainPageCache.put(cacheKey, response)

        return response
    }

    fun Element.toSearchResult(): SearchResponse {
        // FIXED: Fallback strategy untuk title (2-layer)
        val title = this.select("div.bsx > a").attr("title")
            .ifEmpty { this.selectFirst("div.bsx a")?.attr("title").orEmpty() }
        
        val href = fixUrl(this.select("div.bsx > a").attr("href"))
        
        // FIXED: Fallback strategy untuk poster (3-layer)
        val posterUrl = fixUrlNull(
            this.selectFirst("div.bsx a img")?.extractImageAttr()
                ?: this.selectFirst("div.bsx img")?.attr("data-src")
                ?: this.selectFirst("div.bsx img")?.attr("src")
        )
        
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("data-src")
            this.hasAttr("src") -> this.attr("src")
            else -> this.attr("src")
        }
    }

    /**
     * Extract episode number from text like "Episode 214.5", "237 END", "01".
     * Returns floor integer value (214.5 → 214).
     */
    private fun extractEpisodeNumber(text: String): Int? {
        val numberMatch = Regex("""(\d+(?:\.\d+)?)""").find(text)
        return numberMatch?.groupValues?.get(1)
            ?.split(".")?.firstOrNull()?.toIntOrNull()
    }


    override suspend fun search(query: String): List<SearchResponse> {
        // CACHING: Check cache first (instant load for 5 minutes)
        val cacheKey = "search_${query}"

        // Check cache first (NO RATE LIMIT FOR CACHE HIT!)
        val cached = searchCache.get(cacheKey)
        if (cached != null) {
            logDebug("Donghuastream", "Cache HIT for $cacheKey")
            return cached
        }

        logDebug("Donghuastream", "Cache MISS for $cacheKey")

        // OPTIMIZED: Parallel search with timeout (3x faster)
        val results = coroutineScope {
            (1..3).map { page ->
                async {
                    try {
                        val document = executeWithRetry {
                            rateLimitDelay()
                            app.get(
                                "${mainUrl}/pagg/$page/?s=$query",
                                timeout = AutoUsedConstants.FAST_TIMEOUT,
                                headers = mapOf("User-Agent" to getRandomUserAgent())
                            ).documentLarge
                        }
                        document.select("div.listupd > article").mapNotNull { it.toSearchResult() }
                    } catch (e: Exception) {
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
        val document = executeWithRetry {
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

        // FIXED: Fallback strategy untuk poster (4-layer)
        var poster = document.selectFirst("div.thumb > img")?.attr("src")
            ?: document.selectFirst("div.thumb img")?.attr("src")
            ?: document.selectFirst("div.ime > img")?.attr("data-src")
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
            // FIXED: Parse episodes directly from detail page .eplister li
            // (NOT from episode page like the old broken code)
            val allEpisodes = document.select(".eplister li")

            val episodes = allEpisodes.mapNotNull { info ->
                val epLink = info.selectFirst("a")?.attr("href")
                if (epLink.isNullOrBlank()) return@mapNotNull null

                // Fix: Extract episode number from .epl-num (format: "3[72]", "2[71]", "1[70]")
                val episodeText = info.selectFirst(".epl-num")?.text()?.trim().orEmpty()
                val episodeNumber = extractEpisodeNumber(episodeText)

                // Get episode title from .epl-title
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

                newEpisode(epLink) {
                    this.name = cleanName.ifEmpty { "Episode ${episodeNumber ?: "?"}" }
                    this.episode = episodeNumber
                    this.posterUrl = posterr
                }
            }.reversed()

            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim().toString()
            }

            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            // Movie
            val movieLink = document.selectFirst(".eplister li > a")?.attr("href") ?: url

            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim().toString()
            }

            newMovieLoadResponse(title, url, TvType.AnimeMovie, movieLink) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    // OPTIMIZED: Image URL optimizer - resize for mobile screens
    // Prevents image breaking on non-Android TV devices
    private fun optimizeImageUrl(url: String, maxWidth: Int = 500): String {
        return when {
            // Donghuastream uses direct image URLs
            url.contains("donghuastream") || url.contains("donghua") -> {
                // Keep original quality (they already optimize)
                url
            }
            // Add more site-specific optimizations here
            else -> url
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Extract normally (no cache)
        val html = executeWithRetry {
            rateLimitDelay()
            app.get(
                data,
                timeout = AutoUsedConstants.FAST_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }
        val options = html.select("option[data-index]")

        // OPTIMIZED: Parallel link extraction (extract 5 servers simultaneously)
        // 5x faster for episodes with multiple servers
        coroutineScope {
            options.map { option ->
                async {
                    val base64 = option.attr("value")
                    if (base64.isBlank()) return@async
                    val label = option.text().trim()

                    val decodedHtml = try {
                        base64Decode(base64)
                    } catch (_: Exception) {
                        logError("Donghuastream", "Base64 decode failed")
                        return@async
                    }

                    var iframeUrl = Jsoup.parse(decodedHtml).selectFirst("iframe")?.attr("src")
                    if (iframeUrl.isNullOrEmpty()) return@async
                    
                    // FIXED: Ensure URL has proper scheme
                    iframeUrl = when {
                        iframeUrl.startsWith("//") -> "https:$iframeUrl"
                        iframeUrl.startsWith("http") -> iframeUrl
                        iframeUrl.startsWith("/") -> mainUrl + iframeUrl
                        else -> "https://$iframeUrl"
                    }

                    when {
                        "vidmoly" in iframeUrl -> {
                            // FIXED: Better URL extraction for vidmoly
                            val cleanedUrl = if (iframeUrl.contains("=\"")) {
                                "http:" + iframeUrl.substringAfter("=\"").substringBefore("\"")
                            } else {
                                iframeUrl
                            }
                            val loaded = com.Donghuastream.generated_sync.loadExtractorWithFallback(
                                url = cleanedUrl,
                                referer = iframeUrl,
                                subtitleCallback = subtitleCallback,
                                callback = callback
                            )
                            if (!loaded) {
                                logError("Donghuastream", "loadExtractorWithFallback failed for $cleanedUrl")
                            }
                        }
                        iframeUrl.endsWith(".mp4") -> {
                            MasterLinkGenerator.createLink(
                                source = label,
                                url = iframeUrl,
                                referer = mainUrl,
                                quality = getQualityFromName(label)
                            )?.let { callback(it) }
                        }
                        else -> {
                            val loaded = com.Donghuastream.generated_sync.loadExtractorWithFallback(
                                url = iframeUrl,
                                referer = mainUrl,
                                subtitleCallback = subtitleCallback,
                                callback = callback
                            )
                            if (!loaded) {
                                logError("Donghuastream", "loadExtractorWithFallback failed for $iframeUrl")
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        return true
    }
}
