// ========================================
// SAMEHADAKU - Main API Implementation
// ========================================
// Site: https://v1.samehadaku.how
// Type: Anime Streaming Indonesia
// Standard: cloudstream-ekstension
// ========================================

package com.Samehadaku

// ============================================
// GROUP 1: Generated Sync Imports
// ============================================
import com.Samehadaku.generated_sync.*

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
import kotlinx.coroutines.runBlocking

// ============================================
// GROUP 4: External Libraries
// ============================================
import org.jsoup.nodes.Element

// ========================================
// CACHE INSTANCES
// ========================================
private val searchCache = CacheManager<List<SearchResponse>>(defaultTtl = 5 * 60 * 1000L)
private val loadCache = CacheManager<LoadResponse>(defaultTtl = 10 * 60 * 1000L)
private val mainPageCache = CacheManager<HomePageResponse>(defaultTtl = 3 * 60 * 1000L)

// ========================================
// RATE LIMITING
// Using centralized rateLimitDelay from master/
// ========================================

// ========================================
// RETRY LOGIC
// Using centralized executeWithRetry from master/
// ========================================

// ========================================
// LOGGING
// Uses logError() from SyncUtils.kt
// ========================================

// ========================================
// MAIN API CLASS
// ========================================
class Samehadaku : MainAPI() {
    override var mainUrl = "https://v1.samehadaku.how"
    override var name = "Samehadaku⛩️"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Episode Terbaru",
        "daftar-anime-2/?title=&status=&type=TV&order=popular&page=" to "TV Populer",
        "daftar-anime-2/?title=&status=&type=OVA&order=title&page=" to "OVA",
        "daftar-anime-2/?title=&status=&type=Movie&order=title&page=" to "Movie"
    )

    // ========================================
    // HELPER FUNCTIONS
    // ========================================
    
    private fun String.removeBloat(): String =
        replace(
            Regex("(Nonton|Anime|Subtitle\\s*Indonesia)", RegexOption.IGNORE_CASE),
            ""
        ).trim()

    /**
     * Extract episode number from text like "Episode 214.5", "Ep 237 END", "01".
     * Returns floor integer value (214.5 → 214).
     */
    private fun extractEpisodeNumber(text: String): Int? {
        val numberMatch = Regex("""(\d+(?:\.\d+)?)""").find(text)
        return numberMatch?.groupValues?.get(1)
            ?.split(".")?.firstOrNull()?.toIntOrNull()
    }

    private fun String.fixQuality(): Int = when (uppercase()) {
        "4K" -> Qualities.P2160.value
        "FULLHD" -> Qualities.P1080.value
        "MP4HD" -> Qualities.P720.value
        else -> filter { it.isDigit() }.toIntOrNull() ?: Qualities.Unknown.value
    }

    private fun getStatus(statusText: String?): ShowStatus {
        if (statusText == null) return ShowStatus.Completed
        return when {
            statusText.contains("Ongoing", ignoreCase = true) -> ShowStatus.Ongoing
            else -> ShowStatus.Completed
        }
    }

    private fun getType(url: String): TvType {
        return when {
            url.contains("/ova/", true) -> TvType.OVA
            url.contains("/movie/", true) -> TvType.AnimeMovie
            else -> TvType.Anime
        }
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val a = selectFirst("a") ?: return null

        // FIXED: Fallback strategy untuk title (3-layer)
        val title = (selectFirst("a")?.attr("title").orEmpty().ifBlank {
            selectFirst("div.title, h2.entry-title a, div.lftinfo h2")?.text().orEmpty()
        }).ifEmpty {
            selectFirst("h2")?.text().orEmpty()
        }
        if (title.isEmpty()) return null

        val href = fixUrl(a.attr("href"))

        // FIXED: Fallback strategy untuk poster (3-layer)
        val poster = fixUrlNull(
            selectFirst("img")?.attr("src")
                ?: selectFirst("img[data-src]")?.attr("data-src")
                ?: selectFirst("meta[property=og:image]")?.attr("content")
        )

        val type = getType(href)

        return newAnimeSearchResponse(title.trim().removeBloat(), href, type) {
            posterUrl = poster
        }
    }

    // ========================================
    // GET MAIN PAGE
    // ========================================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val cacheKey = "${request.data}${page}"

        // Simple cache check (no fingerprint overhead)
        mainPageCache.get(cacheKey)?.let { cached ->
            logDebug("Samehadaku", "Cache HIT for $cacheKey")
            return cached
        }

        logDebug("Samehadaku", "Cache MISS for $cacheKey")

        // Fix: Handle relative URLs properly (like ExtCloud)
        val url = if (request.data.startsWith("http")) {
            "${request.data}$page"
        } else {
            "$mainUrl/${request.data}$page"
        }

        val httpResult = executeWithRetry {
            rateLimitDelay(moduleName = "Samehadaku")
            app.get(
                url,
                timeout = 30000L,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            )
        }
        
        val homeList = if (request.name == "Episode Terbaru") {
            val doc = httpResult.document
            doc.select("div.post-show ul li").mapNotNull { li ->
                runCatching {
                    val anchor = li.selectFirst("a") ?: return@mapNotNull null

                    val rawTitle = anchor.attr("title").ifBlank { anchor.text() }
                    val cleanTitle = rawTitle
                        .replace(Regex("(Episode|Ep)\\s*\\d+", RegexOption.IGNORE_CASE), "")
                        .removeBloat()
                        .trim()

                    val episodeHref = fixUrl(anchor.attr("href"))
                    val posterUrl = fixUrlNull(li.selectFirst("img")?.attr("src"))

                    val epNum = extractEpisodeNumber(li.text())

                    newAnimeSearchResponse(cleanTitle, episodeHref, TvType.Anime) {
                        this.posterUrl = posterUrl
                        addSub(epNum)
                    }
                }.getOrElse { null }
            }
        } else {
            val doc = httpResult.document
            doc.select("div.animposx").mapNotNull {
                runCatching { it.toSearchResult() }.getOrElse { null }
            }
        }
        
        val responseObj = newHomePageResponse(
            HomePageList(request.name, homeList, request.name == "Episode Terbaru"),
            hasNext = homeList.isNotEmpty()
        )

        // Save to cache
        mainPageCache.put(cacheKey, responseObj)

        return responseObj
    }

    // ========================================
    // SEARCH
    // ========================================
    override suspend fun search(query: String): List<SearchResponse> {
        // Check cache first (NO RATE LIMIT FOR CACHE HIT!)
        val cached = searchCache.get(query)
        if (cached != null) {
            logDebug("Samehadaku", "Cache HIT for search: $query")
            return cached
        }

        logDebug("Samehadaku", "Cache MISS for search: $query")
        
        val searchResult = executeWithRetry {
            rateLimitDelay(moduleName = "Samehadaku")
            app.get(
                "$mainUrl/?s=$query",
                timeout = 30000L,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).document
        }
        
        val results = searchResult.select("div.animposx").mapNotNull {
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
            logDebug("Samehadaku", "Cache HIT for load: $url")
            return cached
        }

        logDebug("Samehadaku", "Cache MISS for load: $url")
        
        val loadResult = executeWithRetry {
            rateLimitDelay(moduleName = "Samehadaku")
            app.get(
                url,
                timeout = 30000L,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).document
        }
        
        val animeTitle = loadResult.selectFirst("h1.entry-title")
            ?.text()
            ?.removeBloat()
            ?: loadResult.selectFirst("h1.title")?.text()?.removeBloat()
            ?: loadResult.selectFirst("meta[property=og:title]")?.attr("content")
            ?: throw Exception("Title not found")

        // FIXED: Fallback strategy untuk poster (4-layer)
        val posterUrlValue = loadResult.selectFirst("div.thumb img")?.attr("src")
            ?: loadResult.selectFirst("meta[property=og:image]")?.attr("content")
            ?: loadResult.selectFirst("img[data-src]")?.attr("data-src")
            ?: loadResult.selectFirst("img[src]")?.attr("src")

        // FIXED: Fallback strategy untuk description (3-layer)
        val description = loadResult.select("div.desc p").text()
            .ifEmpty { loadResult.select("div.description p").text() }
            .ifEmpty { loadResult.selectFirst("meta[name=description]")?.attr("content").orEmpty() }
        
        val tags = loadResult.select("div.genre-info a").map { it.text() }

        val year = loadResult.selectFirst("div.spe span:contains(Rilis)")
            ?.ownText()
            ?.let { Regex("\\d{4}").find(it)?.value?.toIntOrNull() }

        val statusText = loadResult.selectFirst("div.spe span:contains(Status)")?.ownText()
        val status = getStatus(statusText)

        val type = getType(url)

        val trailerUrl = loadResult
            .selectFirst("iframe[src*=\"youtube\"]")
            ?.attr("src")

        val episodes = loadResult.select("div.lstepsiode ul li")
            .mapNotNull {
                val a = it.selectFirst("a") ?: return@mapNotNull null

                val epNum = extractEpisodeNumber(a.text())

                newEpisode(fixUrl(a.attr("href"))) {
                    episode = epNum
                }
            }
            .reversed()

        // Tracker integration (MAL/AniList)
        val tracker = runCatching {
            APIHolder.getTracker(listOf(animeTitle), TrackerType.getTypes(type), year, true)
        }.getOrNull()
        
        val loadResponse = newAnimeLoadResponse(animeTitle, url, type) {
            posterUrl = tracker?.image ?: posterUrlValue
            backgroundPosterUrl = tracker?.cover
            plot = description
            this.tags = tags
            this.year = year
            showStatus = status
            addEpisodes(DubStatus.Subbed, episodes)
            addTrailer(trailerUrl)
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
        val downloadDoc = executeWithRetry {
            rateLimitDelay(moduleName = "Samehadaku")
            app.get(
                data,
                timeout = 30000L,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).document
        }
        
        // Extract download links
        downloadDoc.select("div#downloadb li")
            .amap { li ->
                try {
                    val qualityText = li.selectFirst("strong")?.text() ?: "Unknown"
                    li.select("a").amap { anchor ->
                        try {
                            loadFixedExtractor(
                                fixUrl(anchor.attr("href")),
                                qualityText,
                                subtitleCallback,
                                callback
                            )
                        } catch (e: Exception) {
                            logError("Samehadaku", "Failed to load link: ${e.message}", e)
                        }
                    }
                } catch (e: Exception) {
                    logError("Samehadaku", "Failed to process download link: ${e.message}", e)
                }
            }
        
        return true
    }

    // ========================================
    // LOAD FIXED EXTRACTOR (with quality)
    // ========================================
    // ========================================
    // LOAD FIXED EXTRACTOR (UPDATED WITH P1)
    // ========================================
    private suspend fun loadFixedExtractor(
        url: String,
        quality: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val loaded = com.Samehadaku.generated_sync.loadExtractorWithFallback(
            url = url,
            referer = mainUrl,
            subtitleCallback = subtitleCallback
        ) { link ->
            runBlocking {
                // Use P1 MasterLinkGenerator for simplified ExtractorLink creation
                MasterLinkGenerator.createLink(
                    source = link.name,
                    url = link.url,
                    referer = link.referer,
                    quality = quality.fixQuality(),  // Keep custom Samehadaku quality logic
                    headers = link.headers
                )?.let { extractorLink ->
                    // Copy extractorData from original link
                    extractorLink.extractorData = link.extractorData
                    callback.invoke(extractorLink)
                }
            }
        }
        if (!loaded) {
            logError("Samehadaku", "loadExtractorWithFallback failed for $url")
        }
    }
}

