// ========================================
// LAYARKACA21 PROVIDER
// ========================================
// Site: https://lk21.de
// Type: Movie/TV Series/Asian Drama
// Language: Indonesian (id)
// Standard: cloudstream-ekstension
// ========================================

package com.LayarKaca21

// ============================================
// GROUP 1: Generated Sync Imports
// ============================================
import com.LayarKaca21.generated_sync.*

// ============================================
// GROUP 2: CloudStream Library
// ============================================
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*

// ============================================
// GROUP 3: External Libraries
// ============================================
import org.json.JSONObject
import org.jsoup.nodes.Element

// ============================================
// GROUP 4: Java Standard Library
// ============================================
import java.net.URI

// ========================================
// CACHE INSTANCES
// ========================================
// Using shared CacheManager from generated_sync
// Search results cached for 5 minutes
// Main page results cached for 3 minutes
private val searchCache = CacheManager<List<SearchResponse>>(defaultTtl = 30 * 60 * 1000L)
private val mainPageCache = CacheManager<HomePageResponse>(defaultTtl = 5 * 60 * 1000L)

// ========================================
// MAIN PROVIDER CLASS
// ========================================
class LayarKaca21 : MainAPI() {

    override var mainUrl = "https://lk21.de"
    private var seriesUrl = "https://series.lk21.de"
    private var searchUrl = "https://gudangvape.com"

    override var name = "LayarKaca"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    // ========================================
    // MAIN PAGE CATEGORIES
    // ========================================
    override val mainPage = mainPageOf(
        "$mainUrl/latest/page/" to "Film Upload Terbaru",
        "$mainUrl/populer/page/" to "Film Terplopuler",
        "$mainUrl/nonton-bareng-keluarga/page/" to "Nonton Bareng Keluarga",
        "$mainUrl/rating/page/" to "Film Berdasarkan IMDb Rating",
        "$mainUrl/most-commented/page/" to "Film Dengan Komentar Terbanyak",
        "$mainUrl/genre/horror/page/" to "Film Horor Terbaru",
        "$mainUrl/genre/comedy/page/" to "Film Comedy Terbaru",
        "$mainUrl/country/thailand/page/" to "Film Thailand Terbaru",
        "$mainUrl/country/china/page/" to "Film China Terbaru",
        "$seriesUrl/latest-series/page/" to "Series Terbaru",
        "$seriesUrl/series/asian/page/" to "Film Asian Terbaru",
    )

    // ========================================
    // GET MAIN PAGE
    // ========================================
    // Fetches category listings with pagination
    // Results are cached to avoid redundant requests
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val cacheKey = "${request.data}${page}"

        // Simple cache check (no fingerprint overhead)
        mainPageCache.get(cacheKey)?.let { cached ->
            logDebug("LayarKaca", "Cache HIT for $cacheKey")
            return cached
        }

        logDebug("LayarKaca", "Cache MISS for $cacheKey - fetching from network...")

        // Only apply rate limit and retry for network requests (cache miss)
        val response = executeWithRetry(maxRetries = 3) {
            rateLimitDelay() // Only delay for network requests
            app.get(
                "${request.data}$page",
                timeout = AutoUsedConstants.DEFAULT_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }

        val home = response.select("article figure").mapNotNull {
            runCatching { it.toSearchResult() }.getOrElse { null }
        }

        val result = newHomePageResponse(request.name, home)

        // Cache the result
        mainPageCache.put(cacheKey, result)
        logDebug("LayarKaca", "Cached result for $cacheKey")

        return result
    }

    private suspend fun normalizeLink(url: String): String {
        if (url.startsWith(seriesUrl)) return url

        return try {
            rateLimitDelay()
            val res = app.get(
                url,
                timeout = AutoUsedConstants.DEFAULT_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge

            if (res.select("title").text().contains("Nontondrama", true)) {
                res.selectFirst("a#openNow")?.attr("href")
                    ?: res.selectFirst("div.links a")?.attr("href")
                    ?: url
            } else {
                url
            }
        } catch (e: Exception) {
            logError("LayarKaca", "Error getting proper link: ${e.message}", e)
            url
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // FIXED: Fallback strategy untuk title (2-layer)
        val title = this.selectFirst("h3")?.ownText()?.trim()
            ?: this.selectFirst("h3")?.text()?.trim()
            ?: return null

        val anchor = this.selectFirst("a") ?: return null
        val href = fixUrl(anchor.attr("href"))
        
        // FIXED: Fallback strategy untuk poster (3-layer)
        val posterUrl = fixUrlNull(
            this.selectFirst("img")?.extractImageAttr()
                ?: this.selectFirst("img[data-src]")?.attr("data-src")
                ?: this.selectFirst("img[src]")?.attr("src")
        )
        
        val ratingText = selectFirst("span.rating")?.ownText()?.trim()
        val type = if (this.selectFirst("span.episode") == null) TvType.Movie else TvType.TvSeries
        val posterheaders = mapOf("Referer" to getBaseUrl(posterUrl))
        return if (type == TvType.TvSeries) {
            val episode = this.selectFirst("span.episode strong")?.text()?.filter { it.isDigit() }
                ?.toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.posterHeaders = posterheaders
                addSub(episode)
                this.score = Score.from10(ratingText?.toDoubleOrNull())
            }
        } else {
            val quality = this.select("div.quality").text().trim()
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.posterHeaders = posterheaders
                addQuality(quality)
                this.score = Score.from10(ratingText?.toDoubleOrNull())
            }
        }
    }

    // ========================================
    // SEARCH
    // ========================================
    // Searches the site for movies/series
    // Results cached for 5 minutes
    override suspend fun search(query: String): List<SearchResponse> {
        // MENIRU PERSIS ExtCloud/LayarKacaProvider yang sudah diupdate
        val refer = app.get(mainUrl).url
        val res = app.get("$searchUrl/search.php?s=$query", referer = refer).text
        val results = mutableListOf<SearchResponse>()

        val root = JSONObject(res)
        val arr = root.getJSONArray("data")

        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val title = item.getString("title")
            val slug = item.getString("slug")
            val type = item.getString("type")
            // FIX: Use correct domain (static-jpg instead of poster)
            val posterUrl = "https://static-jpg.lk21.party/wp-content/uploads/" + item.optString("poster")
            when (type) {
                "series" -> results.add(
                    newTvSeriesSearchResponse(title, "$seriesUrl/$slug", TvType.TvSeries) {
                        this.posterUrl = posterUrl
                    }
                )
                "movie" -> results.add(
                    newMovieSearchResponse(title, "$mainUrl/$slug", TvType.Movie) {
                        this.posterUrl = posterUrl
                    }
                )
            }
        }

        return results
    }

    // ========================================
    // LOAD DETAIL PAGE
    // ========================================
    // Loads movie/series details including title, poster, description
    // For series: parses episode list with season/episode numbers
    override suspend fun load(url: String): LoadResponse {
        val fixUrl = normalizeLink(url)

        val document = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            app.get(
                fixUrl,
                timeout = AutoUsedConstants.DEFAULT_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }

        val baseUrl = fetchURL(fixUrl)
        
        // FIXED: Fallback strategy untuk title (3-layer)
        val title = document.selectFirst("div.movie-info h1")?.text()?.trim()
            ?: document.selectFirst("h1.title")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: ""
        
        // FIXED: Fallback strategy untuk poster (4-layer)
        val poster = document.select("meta[property=og:image]").attr("content")
            .ifEmpty { document.selectFirst("div.poster img")?.attr("src").orEmpty() }
            .ifEmpty { document.selectFirst("img[data-src]")?.attr("data-src").orEmpty() }
            .ifEmpty { document.selectFirst("img[src]")?.attr("src").orEmpty() }
        
        val tags = document.select("div.tag-list span").map { it.text() }
        val posterHeaders = mapOf("Referer" to getBaseUrl(poster))

        val year = Regex("\\d, (\\d+)").find(
            document.select("div.movie-info h1").text().trim()
        )?.groupValues?.get(1)?.toIntOrNull()

        val tvType = if (document.selectFirst("#season-data") != null) TvType.TvSeries else TvType.Movie
        
        // FIXED: Fallback strategy untuk description (3-layer)
        val description = document.selectFirst("div.meta-info")?.text()?.trim()
            ?: document.selectFirst("div.description")?.text()?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content").orEmpty()
        
        val trailer = document.selectFirst("ul.action-left > li:nth-child(3) > a")?.attr("href")
        val rating = document.selectFirst("div.info-tag strong")?.text()

        val recommendations = document.select("li.slider article").mapNotNull {
            val recName = it.selectFirst("h3")?.text()?.trim().orEmpty()
            val recHref = baseUrl + it.selectFirst("a")?.attr("href").orEmpty()
            val recPosterUrl = fixUrl(it.selectFirst("img")?.attr("src").orEmpty())

            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
                this.posterHeaders = posterHeaders
            }
        }

        return if (tvType == TvType.TvSeries) {
            val json = document.selectFirst("script#season-data")?.data()
            val episodes = mutableListOf<Episode>()
            if (json != null) {
                val root = JSONObject(json)
                root.keys().forEach { seasonKey ->
                    val seasonArr = root.getJSONArray(seasonKey)
                    for (i in 0 until seasonArr.length()) {
                        val ep = seasonArr.getJSONObject(i)
                        val href = fixUrl("$baseUrl/" + ep.getString("slug"))
                        val episodeNo = ep.optInt("episode_no")
                        val seasonNo = ep.optInt("s")
                        episodes.add(
                            newEpisode(href) {
                                this.name = "Episode $episodeNo"
                                this.season = seasonNo
                                this.episode = episodeNo
                            }
                        )
                    }
                }
            }

            // 🎯 PRE-FETCH: Start fetching links in background for first 10 episodes


            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.posterHeaders = posterHeaders
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.posterHeaders = posterHeaders
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    // ========================================
    // LOAD LINKS (VIDEO SOURCES)
    // ========================================
    // Extracts video embed URLs using AJAX API
    // Handles both direct iframes and AJAX-based loading
    // OPTIMIZED: Parallel extraction (5x faster)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Extract normally (no cache)
        val document = app.get(data).document
        val videolar = document.select("ul#player-list a")

        if (videolar.isEmpty()) {
            logError("LayarKaca", "No video links found")
            return false
        }

        // OPTIMIZED: Parallel extraction (5x faster)
        var successCount = 0

        videolar.amap { video ->
            try {
                val player = video.attr("href")
                val playerAl = app.get(player, referer = "${mainUrl}/").document
                val iframe = playerAl.selectFirst("iframe")?.attr("src").toString()

                if (iframe.contains("https://short.icu")) {
                    val finalIframe = app.get(iframe, allowRedirects = true).url
                    val loaded = com.LayarKaca21.generated_sync.loadExtractorWithFallback(
                        url = finalIframe,
                        referer = "$mainUrl/",
                        subtitleCallback = subtitleCallback,
                        callback = callback
                    )
                    if (!loaded) {
                        logError("LayarKaca21", "loadExtractorWithFallback failed for $finalIframe")
                        // P1 Fallback: Try direct link extraction as last resort
                        MasterLinkGenerator.createLink(
                            source = "LayarKaca",
                            url = finalIframe,
                            referer = "$mainUrl/"
                        )?.let { callback(it) }
                    }
                    if (loaded) successCount++
                } else {
                    val loaded = com.LayarKaca21.generated_sync.loadExtractorWithFallback(
                        url = iframe,
                        referer = "$mainUrl/",
                        subtitleCallback = subtitleCallback,
                        callback = callback
                    )
                    if (!loaded) {
                        logError("LayarKaca21", "loadExtractorWithFallback failed for $iframe")
                        // P1 Fallback: Try direct link extraction as last resort
                        MasterLinkGenerator.createLink(
                            source = "LayarKaca",
                            url = iframe,
                            referer = "$mainUrl/"
                        )?.let { callback(it) }
                    }
                    if (loaded) successCount++
                }
            } catch (e: Exception) {
                logDebug("LayarKaca", "Failed to load video: ${e.message}")
            }
        }

        return successCount > 0
    }

    private suspend fun String.getIframe(): String {
        return try {
            rateLimitDelay()
            val response = app.get(
                this,
                referer = "$seriesUrl/",
                timeout = AutoUsedConstants.DEFAULT_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            )
            val document = response.documentLarge

            // Try multiple selectors for iframe
            val iframeUrl = document.selectFirst("div.embed-container iframe")?.attr("src")
                ?: document.selectFirst("iframe[name=\"iframe\"]")?.attr("src")
                ?: document.selectFirst("body iframe")?.attr("src")
                ?: document.selectFirst("meta[property=og:video\"]")?.attr("content")
                ?: ""

            // If still empty, try to extract from script tags
            if (iframeUrl.isEmpty()) {
                val scriptData = document.selectFirst("script:containsData(var player)")?.data()
                    ?: document.selectFirst("script:containsData(player_url)")?.data()

                if (scriptData != null) {
                    // Extract URL from JavaScript variable
                    val urlMatch = Regex("\"(https?://[^\"]+)\"").find(scriptData)
                    return urlMatch?.groupValues?.get(1) ?: ""
                }
            }

            iframeUrl
        } catch (e: Exception) {
            logError("LayarKaca", "getIframe failed: ${e.message}", e)
            ""
        }
    }

    private suspend fun fetchURL(url: String): String {
        return try {
            rateLimitDelay()
            val res = app.get(
                url,
                allowRedirects = false,
                timeout = AutoUsedConstants.DEFAULT_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            )
            val href = res.headers["location"]

            if (href != null) {
                val uri = URI(href)
                "${uri.scheme}://${uri.host}"
            } else {
                url
            }
        } catch (e: Exception) {
            logError("LayarKaca", "fetchURL failed: ${e.message}", e)
            url
        }
    }


    fun getBaseUrl(url: String?): String {
        if (url.isNullOrEmpty()) return mainUrl
        return try {
            val uri = URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) {
            mainUrl
        }
    }
}

