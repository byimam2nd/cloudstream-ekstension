// ========================================
// SAMEHADAKU - Main API Implementation
// ========================================
// Site: https://v1.samehadaku.how
// Type: Anime Streaming Indonesia
// Standard: cloudstream-ekstension
// ========================================

package com.Samehadaku

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.nodes.Element

// ========================================
// CACHE INSTANCES
// ========================================
private val searchCache = CacheManager<List<SearchResponse>>(ttl = 5 * 60 * 1000L)
private val loadCache = CacheManager<LoadResponse>(ttl = 10 * 60 * 1000L)
private val mainPageCache = CacheManager<HomePageResponse>(ttl = 3 * 60 * 1000L)

// ========================================
// RATE LIMITING
// ========================================
private val mutex = Mutex()
private var lastRequestTime = 0L
private const val MIN_REQUEST_DELAY = 500L // 500ms between requests

internal suspend fun rateLimitDelay() = mutex.withLock {
    val now = System.currentTimeMillis()
    val elapsed = now - lastRequestTime
    if (elapsed < MIN_REQUEST_DELAY) {
        delay(MIN_REQUEST_DELAY - elapsed)
    }
    lastRequestTime = System.currentTimeMillis()
}

// ========================================
// RETRY LOGIC
// ========================================
suspend fun <T> executeWithRetry(
    maxRetries: Int = 3,
    initialDelay: Long = 1000L,
    block: suspend () -> T
): T {
    var lastException: Exception? = null
    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastException = e
            Log.w("Samehadaku", "Attempt ${attempt + 1}/$maxRetries failed: ${e.message}")
            if (attempt < maxRetries - 1) {
                delay(initialDelay * (attempt + 1))
            }
        }
    }
    throw lastException ?: Exception("Unknown error")
}

// ========================================
// LOGGING HELPER
// ========================================
internal fun logError(tag: String, message: String, error: Throwable? = null) {
    Log.e(tag, message)
    error?.let { Log.e(tag, "Cause: ${it.message}") }
}

// ========================================
// MAIN API CLASS
// ========================================
class Samehadaku : MainAPI() {
    override var mainUrl = "https://v1.samehadaku.how"
    override var name = "Samehadaku⛩️"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    
    // Standard timeout (10 detik)
    private val requestTimeout = 10000L

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

        val title = selectFirst("a")?.attr("title").ifBlank {
            selectFirst("div.title, h2.entry-title a, div.lftinfo h2")?.text()
        } ?: return null

        val href = fixUrl(a.attr("href"))
        val poster = fixUrlNull(selectFirst("img")?.attr("src"))

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
        
        // Check cache first
        val cached = mainPageCache.get(cacheKey)
        if (cached != null) {
            Log.d("Samehadaku", "Cache HIT for mainPage: $cacheKey")
            return cached
        }
        
        Log.d("Samehadaku", "Cache MISS for mainPage: $cacheKey")
        
        // Fetch dengan retry logic dan rate limiting
        val response = executeWithRetry {
            rateLimitDelay()
            app.get(
                "${request.data}$page",
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            )
        }
        
        val home = if (request.name == "Episode Terbaru") {
            val document = response.document
            document.select("div.post-show ul li").mapNotNull { li ->
                runCatching {
                    val a = li.selectFirst("a") ?: return@mapNotNull null

                    val rawTitle = a.attr("title").ifBlank { a.text() }
                    val title = rawTitle
                        .replace(Regex("(Episode|Ep)\\s*\\d+", RegexOption.IGNORE_CASE), "")
                        .removeBloat()
                        .trim()

                    val href = fixUrl(a.attr("href"))
                    val poster = fixUrlNull(li.selectFirst("img")?.attr("src"))

                    val ep = Regex("(Episode|Ep)\\s*(\\d+)", RegexOption.IGNORE_CASE)
                        .find(li.text())
                        ?.groupValues
                        ?.getOrNull(2)
                        ?.toIntOrNull()

                    newAnimeSearchResponse(title, href, TvType.Anime) {
                        this.posterUrl = poster
                        addSub(ep)
                    }
                }.getOrElse { null }
            }
        } else {
            val document = response.document
            document.select("div.animposx").mapNotNull {
                runCatching { it.toSearchResult() }.getOrElse { null }
            }
        }
        
        val responseObj = newHomePageResponse(
            HomePageList(request.name, home, request.name == "Episode Terbaru"),
            hasNext = home.isNotEmpty()
        )
        
        // Save to cache
        mainPageCache.put(cacheKey, responseObj)
        
        return responseObj
    }

    // ========================================
    // SEARCH
    // ========================================
    override suspend fun search(query: String): List<SearchResponse> {
        // Check cache first
        val cached = searchCache.get(query)
        if (cached != null) {
            Log.d("Samehadaku", "Cache HIT for search: $query")
            return cached
        }
        
        Log.d("Samehadaku", "Cache MISS for search: $query")
        
        // Fetch dengan retry logic
        val document = executeWithRetry {
            rateLimitDelay()
            app.get(
                "$mainUrl/?s=$query",
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).document
        }
        
        val results = document.select("div.animposx").mapNotNull {
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
        // Check cache first
        val cached = loadCache.get(url)
        if (cached != null) {
            Log.d("Samehadaku", "Cache HIT for load: $url")
            return cached
        }
        
        Log.d("Samehadaku", "Cache MISS for load: $url")
        
        // Fetch dengan retry logic
        val document = executeWithRetry {
            rateLimitDelay()
            app.get(
                url,
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).document
        }
        
        val title = document.selectFirst("h1.entry-title")
            ?.text()
            ?.removeBloat()
            ?: throw Exception("Title not found")
        
        val poster = document.selectFirst("div.thumb img")?.attr("src")
        val description = document.select("div.desc p").text()
        val tags = document.select("div.genre-info a").map { it.text() }

        val year = document.selectFirst("div.spe span:contains(Rilis)")
            ?.ownText()
            ?.let { Regex("\\d{4}").find(it)?.value?.toIntOrNull() }

        val statusText = document.selectFirst("div.spe span:contains(Status)")?.ownText()
        val status = getStatus(statusText)

        val type = getType(url)

        val trailer = document
            .selectFirst("iframe[src*=\"youtube\"]")
            ?.attr("src")

        val episodes = document.select("div.lstepsiode ul li")
            .mapNotNull {
                val a = it.selectFirst("a") ?: return@mapNotNull null

                val ep = Regex("(Episode|Ep)\\s*(\\d+)", RegexOption.IGNORE_CASE)
                    .find(a.text())
                    ?.groupValues
                    ?.getOrNull(2)
                    ?.toIntOrNull()

                newEpisode(fixUrl(a.attr("href"))) {
                    episode = ep
                }
            }
            .reversed()

        // Tracker integration (MAL/AniList)
        val tracker = runCatching {
            APIHolder.getTracker(listOf(title), TrackerType.getTypes(type), year, true)
        }.getOrNull()
        
        val loadResponse = newAnimeLoadResponse(title, url, type) {
            posterUrl = tracker?.image ?: poster
            backgroundPosterUrl = tracker?.cover
            plot = description
            this.tags = tags
            this.year = year
            showStatus = status
            addEpisodes(DubStatus.Subbed, episodes)
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
        val document = executeWithRetry {
            rateLimitDelay()
            app.get(
                data,
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).document
        }
        
        // Extract download links
        document.select("div#downloadb ul li")
            .amap { li ->
                try {
                    val quality = li.selectFirst("strong")?.text() ?: "Unknown"
                    li.select("a").amap { a ->
                        try {
                            loadFixedExtractor(
                                fixUrl(a.attr("href")),
                                quality,
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
    private suspend fun loadFixedExtractor(
        url: String,
        quality: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        loadExtractor(url, subtitleCallback = subtitleCallback) { link ->
            runBlocking {
                callback.invoke(
                    newExtractorLink(
                        link.name,
                        link.name,
                        link.url,
                        link.type
                    ) {
                        this.referer = link.referer
                        this.quality = quality.fixQuality()
                        this.headers = link.headers
                        this.extractorData = link.extractorData
                    }
                )
            }
        }
    }
}
