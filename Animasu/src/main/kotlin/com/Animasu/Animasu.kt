// ========================================
// ANIMASU - Main API Implementation
// ========================================
// Site: https://v1.animasu.top
// Type: Anime Streaming Indonesia
// Standard: cloudstream-ekstension
// ========================================

package com.Animasu

// ============================================
// GROUP 1: Generated Sync Imports
// ============================================
import com.Animasu.generated_sync.*

// ============================================
// GROUP 2: CloudStream Library
// ============================================
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*

// ============================================
// GROUP 3: Kotlin Coroutines
// ============================================
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock

// ============================================
// GROUP 4: External Libraries
// ============================================
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

// ============================================
// GROUP 5: Java Standard Library
// ============================================
import java.util.concurrent.ConcurrentHashMap

// ========================================
// CACHE INSTANCES
// Uses CacheManager from master/MasterCaches.kt
// ========================================
private val searchCache = CacheManager<List<SearchResponse>>(defaultTtl = 5 * 60 * 1000L)
private val loadCache = CacheManager<LoadResponse>(defaultTtl = 10 * 60 * 1000L)
private val mainPageCache = CacheManager<HomePageResponse>(defaultTtl = 3 * 60 * 1000L)

// ========================================
// RATE LIMITING
// Using centralized rateLimitDelay from master/MasterUtils.kt
// ========================================
// Note: Use rateLimitDelay(moduleName = "Animasu") directly in functions

// ========================================
// RETRY LOGIC
// Using centralized executeWithRetry from master/MasterUtils.kt
// ========================================

// ========================================
// LOGGING
// Uses logError() from master/MasterUtils.kt
// ========================================

// ========================================
// MAIN API CLASS
// ========================================
class Animasu : MainAPI() {
    override var mainUrl = "https://v1.animasu.top"
    override var name = "Animasu🐰"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    
    // Standard timeout (10 detik)
    private val requestTimeout = 10_000L

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "urutan=update" to "Baru diupdate",
        "status=&tipe=&urutan=publikasi" to "Baru ditambahkan",
        "status=&tipe=&urutan=populer" to "Terpopuler",
        "status=&tipe=&urutan=rating" to "Rating Tertinggi",
        "status=&tipe=Movie&urutan=update" to "Movie Terbaru",
        "status=&tipe=Movie&urutan=populer" to "Movie Terpopuler",
    )

    // ========================================
    // HELPER FUNCTIONS
    // ========================================
    
    companion object {
        fun getType(t: String?): TvType {
            if (t == null) return TvType.Anime
            return when {
                t.contains("Tv", true) -> TvType.Anime
                t.contains("Movie", true) -> TvType.AnimeMovie
                t.contains("OVA", true) || t.contains("Special", true) -> TvType.OVA
                else -> TvType.Anime
            }
        }

        fun getStatus(t: String?): ShowStatus {
            if (t == null) return ShowStatus.Completed
            return when {
                t.contains("Sedang Tayang", true) -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    private fun normalizeLink(uri: String): String {
        return if (uri.contains("/anime/")) {
            uri
        } else {
            var title = uri.substringAfter("$mainUrl/")
            title = when {
                (title.contains("-episode")) && !(title.contains("-movie")) -> 
                    title.substringBefore("-episode")
                (title.contains("-movie")) -> title.substringBefore("-movie")
                else -> title
            }
            "$mainUrl/anime/$title"
        }
    }

    private fun Element.toSearchResult(): AnimeSearchResponse {
        val href = normalizeLink(fixUrlNull(this.selectFirst("a")?.attr("href")).toString())
        
        // FIXED: Fallback strategy untuk title (2-layer)
        val title = this.select("div.tt").text().trim()
            .ifEmpty { this.selectFirst("a")?.attr("title").orEmpty() }
        
        // FIXED: Fallback strategy untuk poster (3-layer)
        val posterUrl = fixUrlNull(
            this.selectFirst("img")?.extractImageAttr()
                ?: this.selectFirst("img[data-src]")?.attr("data-src")
                ?: this.selectFirst("meta[property=og:image]")?.attr("content")
        )
        
        val epNum = this.selectFirst("span.epx")?.text()?.filter { it.isDigit() }?.toIntOrNull()
        
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addSub(epNum)
        }
    }

    /**
     * Extract episode number from text like "Episode 214.5", "Episode 237 END", "Ep 01".
     * Returns floor integer value (214.5 → 214).
     */

    // ========================================
    // GET MAIN PAGE
    // ========================================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val cacheKey = "${request.data}${page}"

        // Simple cache check (no fingerprint overhead)
        mainPageCache.get(cacheKey)?.let { cached ->
            logDebug("Animasu", "Cache HIT for $cacheKey")
            return cached
        }

        logDebug("Animasu", "Cache MISS for $cacheKey")

        // Fetch dengan retry logic dan rate limiting
        val document = executeWithRetry {
            rateLimitDelay(moduleName = "Animasu")
            app.get(
                "$mainUrl/pencarian/?${request.data}&halaman=$page",
                timeout = 30000L,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).document
        }

        val home = document.select("div.listupd div.bs").mapNotNull {
            runCatching { it.toSearchResult() }.getOrElse { null }
        }

        val response = newHomePageResponse(request.name, home)

        // Cache the result
        mainPageCache.put(cacheKey, response)

        return response
    }

    // ========================================
    // SEARCH
    // ========================================
    override suspend fun search(query: String): List<SearchResponse> {
        // Check cache first (NO RATE LIMIT FOR CACHE HIT!)
        val cached = searchCache.get(query)
        if (cached != null) {
            logDebug("Animasu", "Cache HIT for search: $query")
            return cached
        }
        
        logDebug("Animasu", "Cache MISS for search: $query")
        
        // Fetch dengan retry logic
        val document = executeWithRetry {
            rateLimitDelay(moduleName = "Animasu")
            app.get(
                "$mainUrl/?s=$query",
                timeout = 30000L,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).document
        }
        
        val results = document.select("div.listupd div.bs").mapNotNull {
            runCatching { it.toSearchResult() }.getOrElse { null }
        }
        
        // Save to cache
        searchCache.put(query, results)
        
        return results
    }

    // ========================================
    // LOAD
    // ========================================
    override suspend fun load(url: String): LoadResponse {
        // Check cache first (NO RATE LIMIT FOR CACHE HIT!)
        val cached = loadCache.get(url)
        if (cached != null) {
            logDebug("Animasu", "Cache HIT for load: $url")
            return cached
        }
        
        logDebug("Animasu", "Cache MISS for load: $url")
        
        // Fetch dengan retry logic
        val document = executeWithRetry {
            rateLimitDelay(moduleName = "Animasu")
            app.get(
                url,
                timeout = 30000L,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).document
        }
        
        // FIXED: Fallback strategy untuk title (4-layer)
        // Selector utama di atas, alternatif di bawah
        val title = document.selectFirst("div.infox h1")?.text()
            ?.toString()
            ?.replace("Sub Indo", "")
            ?.trim()
            .orEmpty()
            .ifEmpty { 
                // Alternatif 1: itemprop headline (SEO optimized sites)
                document.selectFirst("h1[itemprop=headline]")?.text()?.replace("Sub Indo", "")?.trim().orEmpty() 
            }
            .ifEmpty { 
                // Alternatif 2: WordPress default entry-title
                document.selectFirst("h1.entry-title")?.text()?.replace("Sub Indo", "")?.trim().orEmpty() 
            }
            .ifEmpty { 
                // Fallback terakhir: Open Graph meta
                document.selectFirst("meta[property=og:title]")?.attr("content").orEmpty() 
            }
            ?: ""

        // FIXED: Fallback strategy untuk poster (5-layer)
        // IMPORTANT: Check data-src FIRST (lazy-loaded images), then src
        val poster = document.selectFirst("div.bigcontent img")?.attr("data-src")
            ?: document.selectFirst("div.bigcontent img")?.attr("src")
            ?: document.selectFirst("div.thumb img")?.attr("data-src")
            ?: document.selectFirst("div.thumb img")?.attr("src")
            ?: document.selectFirst("img[data-src]")?.attr("data-src")
            ?: document.selectFirst("img[src]")?.attr("src")
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")

        val table = document.selectFirst("div.infox div.spe")
        val type = getType(table?.selectFirst("span:contains(Jenis:)")?.ownText())
        val year = table?.selectFirst("span:contains(Rilis:)")?.ownText()
            ?.substringAfterLast(",")
            ?.trim()
            ?.toIntOrNull()
        val status = table?.selectFirst("span:contains(Status:) font")?.text()
        val trailer = document.selectFirst("div.trailer iframe")?.attr("src")

        // FIXED: Fallback strategy untuk description (3-layer)
        val plot = document.select("div.sinopsis p").text()
            .ifEmpty { document.select("div.entry-content p").text() }
            .ifEmpty { document.selectFirst("meta[name=description]")?.attr("content").orEmpty() }
        
        val episodes = document.select("ul#daftarepisode > li").mapNotNull {
            val anchor = it.selectFirst("a") ?: return@mapNotNull null
            val link = fixUrl(anchor.attr("href"))
            val name = it.selectFirst("a")?.text() ?: return@mapNotNull null
            // Fix: Support "Episode 214.5", "Episode 237 END", "Ep 01"
            val episode = extractEpisodeNumber(name)
            newEpisode(link) { this.episode = episode }
        }.reversed()

        // 🎯 PRE-FETCH: Start fetching links in background for first 10 episodes

        // Tracker integration (MAL/AniList)
        val tracker = runCatching {
            APIHolder.getTracker(listOf(title), TrackerType.getTypes(type), year, true)
        }.getOrNull()
        
        val loadResponse = newAnimeLoadResponse(title, url, type) {
            posterUrl = tracker?.image ?: poster
            backgroundPosterUrl = tracker?.cover
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = getStatus(status)
            this.plot = plot
            this.tags = table?.select("span:contains(Genre:) a")?.map { it.text() }
            addTrailer(trailer)
            addMalId(tracker?.malId)
            addAniListId(tracker?.aniId?.toIntOrNull())
        }
        
        // Save to cache
        loadCache.put(url, loadResponse)
        
        return loadResponse
    }

    // ========================================
    // LOAD LINKS
    // ========================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Extract normally (no cache)
        val document = executeWithRetry {
            rateLimitDelay(moduleName = "Animasu")
            app.get(
                data,
                timeout = 30000L,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).document
        }
        
        // Extract player links
        val playerLinks = document.select(".mobius > .mirror > option").mapNotNull {
            val value = it.attr("value")
            if (value.isNotEmpty()) {
                fixUrl(Jsoup.parse(base64Decode(value)).select("iframe").attr("src")) to it.text()
            } else null
        }
        
        if (playerLinks.isEmpty()) {
            logError("Animasu", "No player links found")
            return false
        }
        
        // Load all extractors in parallel
        playerLinks.amap { (iframe, quality) ->
            try {
                loadFixedExtractor(iframe, quality, "$mainUrl/", subtitleCallback, callback)
            } catch (e: Exception) {
                logError("Animasu", "Failed to load extractor for $iframe: ${e.message}", e)
            }
        }
        
        return true
    }

    // ========================================
    // LOAD FIXED EXTRACTOR (with quality)
    // ========================================
    // LOAD FIXED EXTRACTOR (UPDATED WITH P1)
    // ========================================
    private suspend fun loadFixedExtractor(
        url: String,
        quality: String?,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val loaded = com.Animasu.generated_sync.loadExtractorWithFallback(
            url = url,
            referer = referer,
            subtitleCallback = subtitleCallback
        ) { link ->
            runBlocking {
                // Use P1 MasterLinkGenerator for simplified ExtractorLink creation
                MasterLinkGenerator.createLink(
                    source = link.name,
                    url = link.url,
                    referer = link.referer,
                    quality = if (link.type == ExtractorLinkType.M3U8 || link.name == "Uservideo") {
                        link.quality  // Keep existing quality for M3U8/Uservideo
                    } else {
                        parseQualityToInt(quality) ?: MasterLinkGenerator.detectQualityFromUrl(link.url)  // Auto-detect from URL
                    },
                    headers = link.headers
                )?.let { extractorLink ->
                    // Copy extractorData from original link
                    extractorLink.extractorData = link.extractorData
                    callback.invoke(extractorLink)
                }
            }
        }

        if (!loaded) {
            logError("Animasu", "loadFixedExtractor failed for $url")
        }
    }

    // ========================================
    // GET INDEX QUALITY
    // ========================================
    private fun parseQualityToInt(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }
}
