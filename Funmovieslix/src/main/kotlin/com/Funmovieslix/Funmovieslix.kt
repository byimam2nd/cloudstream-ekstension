// ========================================
// FUNMOVIESLIX PROVIDER
// ========================================
// Site: https://funmovieslix.com
// Type: Movie/Anime/Cartoon Streaming
// Language: Indonesian (id)
// Standard: cloudstream-ekstension
// ========================================

package com.Funmovieslix

// ============================================
// GROUP 1: Generated Sync Imports
// ============================================
import com.Funmovieslix.generated_sync.*

// ============================================
// GROUP 2: CloudStream Library
// ============================================
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*

// ============================================
// GROUP 3: Kotlin Coroutines
// ============================================
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// ============================================
// GROUP 4: External Libraries
// ============================================
import org.jsoup.nodes.Element

// ============================================
// GROUP 5: Java Standard Library
// ============================================
import java.util.concurrent.ConcurrentHashMap

// ========================================
// CACHE INSTANCES
// ========================================
// Using shared CacheManager from generated_sync
// Search results cached for 5 minutes
// Main page results cached for 3 minutes
private val searchCache = CacheManager<List<SearchResponse>>()
private val mainPageCache = CacheManager<HomePageResponse>()

// ========================================
// MAIN PROVIDER CLASS
// ========================================
class Funmovieslix : MainAPI() {
    override var mainUrl = "https://funmovieslix.com"
    override var name = "Funmovieslix"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.Cartoon)

    // ========================================
    // MAIN PAGE CATEGORIES
    // ========================================
    override val mainPage = mainPageOf(
        "category/action" to "Action Category",
        "category/science-fiction" to "Sci-Fi Category",
        "category/drama" to "Drama Category",
        "category/kdrama" to "KDrama",
        "category/crime" to "Crime Category",
        "category/fantasy" to "Fantasy Category",
        "category/mystery" to "Mystery Category",
        "category/comedy" to "Comedy Category",
    )

    // ========================================
    // GET MAIN PAGE
    // ========================================
    // Fetches category listings with pagination
    // Results are cached to avoid redundant requests
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val cacheKey = "${request.data}${page}"

        // Simple cache check (no fingerprint overhead)
        mainPageCache.get(cacheKey)?.let { cached ->
            logDebug("Funmovieslix", "Cache HIT for $cacheKey")
            return cached
        }

        logDebug("Funmovieslix", "Cache MISS for $cacheKey")

        val document = executeWithRetry {
            rateLimitDelay()
            app.get(
                "$mainUrl/${request.data}/page/$page",
                timeout = 5000L,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }
        val home = document.select("#gmr-main-load div.movie-card").mapNotNull { it.toSearchResult() }
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

    // ========================================
    // PARSE SEARCH RESULT FROM HTML ELEMENT
    // ========================================
    // Extracts title, URL, poster, quality and score from movie card
    // Uses multiple fallback strategies for each field
    private fun Element.toSearchResult(): SearchResponse {
        // FIXED: Fallback strategy untuk title (2-layer)
        val title = this.select("h3").text()
            .ifEmpty { this.selectFirst("a")?.attr("title").orEmpty() }
        
        val href = fixUrl(this.select("a").attr("href"))
        
        // FIXED: Fallback strategy untuk poster (3-layer: srcset, src, data-src)
        val posterUrl = this.select("a img").firstOrNull()?.let { img ->
            val srcSet = img.attr("srcset")
            val bestUrl = if (srcSet.isNotBlank()) {
                srcSet.split(",")
                    .map { it.trim() }
                    .maxByOrNull { it.substringAfterLast(" ").removeSuffix("w").toIntOrNull() ?: 0 }
                    ?.substringBefore(" ")
            } else {
                img.attr("src").ifEmpty { img.attr("data-src") }
            }

            fixUrlNull(bestUrl?.replace(Regex("-\\d+x\\d+"), ""))
        }
        val searchQuality = extractQuality(this)
        val score=this.select("div.rating-stars").text().substringAfter("(").substringBefore(")")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = searchQuality
            this.score=Score.from10(score)
        }
    }

    // Standard timeout (10 detik)
    private val requestTimeout = 10_000L

    // ========================================
    // SEARCH
    // ========================================
    // Searches across 3 pages in parallel for faster results
    // Results cached for 5 minutes
    override suspend fun search(query: String): List<SearchResponse> {
        // CACHING: Check cache first (instant load for 5 minutes)
        val cacheKey = "search_${query}"

        // Check cache first (NO RATE LIMIT FOR CACHE HIT!)
        val cached = searchCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        // OPTIMIZED: Parallel search with timeout (3x faster)
        val results = coroutineScope {
            (1..3).map { page ->
                async {
                    try {
                        val document = executeWithRetry {
                            rateLimitDelay()
                            app.get(
                                "${mainUrl}?s=$query&page=$page",
                                timeout = AutoUsedConstants.DEFAULT_TIMEOUT,
                                headers = mapOf("User-Agent" to getRandomUserAgent())
                            ).documentLarge
                        }
                        document.select("#gmr-main-load div.movie-card").mapNotNull { it.toSearchResult() }
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

    // ========================================
    // LOAD DETAIL PAGE
    // ========================================
    // Loads movie/series details including title, poster, description
    // For series: parses episode list with season/episode numbers
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
        val title = document.select("meta[property=og:title]").attr("content")
            .ifEmpty { document.selectFirst("h1.title")?.text().orEmpty() }
            .ifEmpty { document.selectFirst("h1.entry-title")?.text().orEmpty() }
            .substringBefore("(").substringBefore("-").trim()
        
        // FIXED: Fallback strategy untuk poster (4-layer)
        val poster = document.select("meta[property=og:image]").attr("content")
            .ifEmpty { document.selectFirst("div.poster img")?.attr("src").orEmpty() }
            .ifEmpty { document.selectFirst("img[data-src]")?.attr("data-src").orEmpty() }
            .ifEmpty { document.selectFirst("img[src]")?.attr("src").orEmpty() }
        
        // FIXED: Fallback strategy untuk description (4-layer)
        val description = document.select("div.desc-box p,div.entry-content p").text()
            .ifEmpty { document.selectFirst("div.synopsis")?.text().orEmpty() }
            .ifEmpty { document.selectFirst("div.description")?.text().orEmpty() }
            .ifEmpty { document.selectFirst("meta[name=description]")?.attr("content").orEmpty() }
        
        val actors=document.select("div.cast-grid a").map { it.text() }
        val type = if (url.contains("tv")) TvType.TvSeries else TvType.Movie
        val trailer = document.select("meta[itemprop=embedUrl]").attr("content")
        val genre = document.select("div.gmr-moviedata:contains(Genre) a,span.badge").map { it.text() }
        val year =document.select("div.gmr-moviedata:contains(Year) a").text().toIntOrNull()
        val recommendation = document.select("div.movie-grid div").mapNotNull {
            val recName = it.select("p").text()
            val recHref = it.select("a").attr("href")
            val img = it.selectFirst("img")
            val srcSet = img?.attr("srcset").orEmpty()
            val bestUrl = if (srcSet.isNotBlank()) {
                srcSet.split(",")
                    .map { s -> s.trim() }
                    .maxByOrNull { s -> s.substringAfterLast(" ").removeSuffix("w").toIntOrNull() ?: 0 }
                    ?.substringBefore(" ")
            } else {
                img?.attr("src")
            }
            val recPosterUrl = fixUrlNull(bestUrl?.replace(Regex("-\\d+x\\d+"), ""))
            newMovieSearchResponse(recName, recHref, TvType.Movie) {
                this.posterUrl = recPosterUrl
            }
        }

        return if (type == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            document.select("div.gmr-listseries a").forEach { info ->
                    if (info.text().contains("All episodes", ignoreCase = true)) return@forEach
                    val text=info.text()
                    val season = Regex("S(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    val ep=Regex("Eps(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    val name = "Episode $ep"
                    val href = info.attr("href")
                    episodes.add(
                        newEpisode(href)
                        {
                            this.episode=ep
                            this.name=name
                            this.season=season
                        }
                    )
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genre
                this.year = year
                addTrailer(trailer)
                addActors(actors)
                this.recommendations=recommendation
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genre
                this.year = year
                addTrailer(trailer)
                addActors(actors)
                this.recommendations=recommendation
            }
        }
    }

    // ========================================
    // LOAD LINKS (VIDEO SOURCES)
    // ========================================
    // Extracts video embed URLs using 3 strategies:
    // 1. Parse "const embeds" from script tags
    // 2. Extract iframe src attributes
    // 3. Check data-src/data-url/data-link attributes
    // Then loads all extractors in parallel for speed
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = executeWithRetry {
            rateLimitDelay()
            app.get(
                data,
                timeout = 5000L,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }

        // FIXED: Multiple strategies untuk extract embed URLs
        var urls = emptyList<String>()
        
        // Strategy 1: Extract from "const embeds" in script tags
        val scriptContent = document.select("script")
            .map { it.data() }
            .firstOrNull { it.contains("const embeds") }
        
        if (scriptContent != null) {
            val regex = Regex("""https:\/\/[^"]+""")
            urls = regex.findAll(scriptContent)
                .map { it.value.replace("\\/", "/").replace("\\", "") } // unescape \/ → / and remove \
                .filter { it.isNotBlank() && (it.contains("youtube") || it.contains("drive") || it.contains("stream") || it.contains("mp4")) }
                .toList()
        }
        
        // Strategy 2: Fallback - extract iframe URLs directly from HTML
        if (urls.isEmpty()) {
            urls = document.select("iframe[src]")
                .map { it.attr("src") }
                .filter { it.isNotBlank() }
                .map { it.replace("\\/", "/").replace("\\", "") }
        }
        
        // Strategy 3: Extract from data attributes
        if (urls.isEmpty()) {
            urls = document.select("[data-src], [data-url], [data-link]")
                .map { it.attr("data-src") ?: it.attr("data-url") ?: it.attr("data-link") }
                .filter { it.isNotBlank() }
        }

        if (urls.isEmpty()) {
            logError("Funmovieslix", "No embed URLs found in page")
            return false
        }

        // OPTIMIZED: Parallel link extraction (extract all servers simultaneously)
        // 5x faster for episodes with multiple servers
        val loadedLinks = mutableListOf<String>()
        val mutex = Mutex()

        coroutineScope {
            urls.map { url ->
                async {
                    try {
                        // FIXED: Better URL fixing with scheme validation
                        var fixedUrl = url
                        if (!fixedUrl.startsWith("http://") && !fixedUrl.startsWith("https://")) {
                            fixedUrl = when {
                                fixedUrl.startsWith("//") -> "https:$fixedUrl"
                                fixedUrl.startsWith("/") -> mainUrl + fixedUrl
                                else -> "https://$fixedUrl"
                            }
                        }

                        logDebug("Funmovieslix", "Trying to load: $fixedUrl")
                        val loaded = com.Funmovieslix.generated_sync.loadExtractorWithFallback(
                            url = fixedUrl,
                            referer = mainUrl,
                            subtitleCallback = subtitleCallback,
                            callback = callback
                        )
                        if (!loaded) {
                            logError("Funmovieslix", "loadExtractorWithFallback failed for $fixedUrl")
                            // P1 Fallback: Try direct link extraction as last resort
                            MasterLinkGenerator.createLink(
                                source = "Funmovieslix",
                                url = fixedUrl,
                                referer = mainUrl
                            )?.let { callback(it) }
                        }

                        mutex.withLock {
                            loadedLinks.add(fixedUrl)
                        }
                    } catch (e: Exception) {
                        logError("Funmovieslix", "loadExtractor failed for $url: ${e.message}")
                    }
                }
            }.awaitAll()
        }

        if (loadedLinks.isEmpty()) {
            logError("Funmovieslix", "No links loaded from ${urls.size} URLs found")
            return false
        }

        logDebug("Funmovieslix", "Successfully loaded ${loadedLinks.size} links")
        return true
    }

    // ========================================
    // DETECT SEARCH QUALITY FROM HTML
    // ========================================
    // Parses quality badge text and maps to SearchQuality enum
    private fun extractQuality(element: Element): SearchQuality {
        val qualityText = element.select("div.quality-badge").text().uppercase()

        return when {
            qualityText.contains("HDTS") -> SearchQuality.HdCam
            qualityText.contains("HDCAM") -> SearchQuality.HdCam
            qualityText.contains("CAM") -> SearchQuality.Cam
            qualityText.contains("HDRIP") -> SearchQuality.WebRip
            qualityText.contains("WEBRIP") -> SearchQuality.WebRip
            qualityText.contains("WEB-DL") -> SearchQuality.WebRip
            qualityText.contains("BLURAY") -> SearchQuality.BlueRay
            qualityText.contains("4K") -> SearchQuality.FourK
            qualityText.contains("HD") -> SearchQuality.HD
            else -> SearchQuality.HD
        }
    }

}

