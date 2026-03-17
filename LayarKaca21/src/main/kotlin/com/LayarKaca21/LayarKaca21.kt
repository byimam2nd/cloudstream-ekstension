package com.layarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URI
import com.layarKacaProvider.CacheManager
import com.layarKacaProvider.rateLimitDelay
import com.layarKacaProvider.getRandomUserAgent
import com.layarKacaProvider.executeWithRetry
import com.layarKacaProvider.logError
import com.layarKacaProvider.CacheFingerprint
import com.layarKacaProvider.SmartCacheMonitor
import com.layarKacaProvider.CacheValidationResult

// Cache instances dengan TTL berbeda
private val searchCache = CacheManager<List<SearchResponse>>(
    ttl = SEARCH_CACHE_TTL,
    maxSize = MAX_CACHE_SIZE
)

private val mainPageCache = CacheManager<HomePageResponse>(
    ttl = MAINPAGE_CACHE_TTL,
    maxSize = MAX_CACHE_SIZE
)

// Smart Cache Monitor untuk fingerprint-based invalidation
private val monitor = LayarKacaMonitor()
private val fingerprints = mutableMapOf<String, CacheFingerprint>()

class LayarKaca21 : MainAPI() {

    override var mainUrl = "https://lk21.de"
    private var seriesUrl = "https://series.lk21.de"
    private var searchUrl = "https://search.lk21.party"

    override var name = "LayarKaca"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    // Standard timeout untuk semua request (10 detik)
    private val requestTimeout = 10000L

    override val mainPage = mainPageOf(
        "$mainUrl/populer/page/" to "Film Terplopuler",
        "$mainUrl/rating/page/" to "Film Berdasarkan IMDb Rating",
        "$mainUrl/most-commented/page/" to "Film Dengan Komentar Terbanyak",
        "$seriesUrl/latest-series/page/" to "Series Terbaru",
        "$seriesUrl/series/asian/page/" to "Film Asian Terbaru",
        "$mainUrl/latest/page/" to "Film Upload Terbaru",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val cacheKey = "${request.data}${page}"

        // Check cache first
        val cached = mainPageCache.get(cacheKey)
        val cachedFingerprint = fingerprints[cacheKey]
        
        if (cached != null) {
            // SMART CACHE: Check if content has changed
            val checkResult = monitor.checkCacheValidity(
                cacheKey = cacheKey,
                url = request.data + page,
                cachedFingerprint = cachedFingerprint
            )
            
            // If cache is valid, return cached data
            if (checkResult.isValid && checkResult.result == CacheValidationResult.CACHE_VALID) {
                Log.d("LayarKaca", "Cache HIT for $cacheKey (fingerprint match)")
                return cached
            }
            
            // Cache invalid - will fetch new data
            Log.d("LayarKaca", "Cache MISS for $cacheKey (fingerprint changed)")
        }

        // Fetch dengan retry logic dan rate limiting
        val response = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            app.get(
                "${request.data}$page",
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }

        val home = response.select("article figure").mapNotNull {
            runCatching { it.toSearchResult() }.getOrElse { null }
        }

        val result = newHomePageResponse(request.name, home)

        // Cache the result
        mainPageCache.put(cacheKey, result)
        
        // Update fingerprint
        val titles = response.select("article figure h3")
            .mapNotNull { it.ownText()?.trim() }
            .filter { it.isNotEmpty() }
        fingerprints[cacheKey] = monitor.generateFingerprint(cacheKey, titles)

        return result
    }

    private suspend fun getProperLink(url: String): String {
        if (url.startsWith(seriesUrl)) return url

        return try {
            rateLimitDelay()
            val res = app.get(
                url,
                timeout = requestTimeout,
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
        val title = this.selectFirst("h3")?.ownText()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("img")?.getImageAttr())
        val type = if (this.selectFirst("span.episode") == null) TvType.Movie else TvType.TvSeries
        val posterHeaders = mapOf("Referer" to getBaseUrl(posterUrl))

        return if (type == TvType.TvSeries) {
            val episode = this.selectFirst("span.episode strong")?.text()?.filter { it.isDigit() }
                ?.toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.posterHeaders = posterHeaders
                addSub(episode)
            }
        } else {
            val quality = this.select("div.quality").text().trim()
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.posterHeaders = posterHeaders
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // OPTIMIZED: Gunakan CacheManager dengan TTL 30 menit
        val cacheKey = "search_${query}"

        // Check cache first
        val cached = searchCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        // PERBAIKI: Scraping HTML langsung karena API search.lk21.party sudah down (404)
        // Mengikuti cara ExtCloud/LayarKacaProvider tapi dengan scraping HTML
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/?s=$encodedQuery"

        val results = try {
            rateLimitDelay()
            val document = app.get(
                searchUrl,
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge

            // Selector untuk hasil search - sama dengan yang digunakan di getMainPage
            document.select("article figure")
                .mapNotNull {
                    runCatching { it.toSearchResult() }.getOrElse { null }
                }
        } catch (e: Exception) {
            logError("LayarKaca", "Search failed for query: $query", e)
            emptyList()
        }

        // Cache the result
        searchCache.put(cacheKey, results)

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val fixUrl = getProperLink(url)

        val document = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            app.get(
                fixUrl,
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }

        val baseUrl = fetchURL(fixUrl)
        val title = document.selectFirst("div.movie-info h1")?.text()?.trim().orEmpty()
        val poster = document.select("meta[property=og:image]").attr("content")
        val tags = document.select("div.tag-list span").map { it.text() }
        val posterHeaders = mapOf("Referer" to getBaseUrl(poster))

        val year = Regex("\\d, (\\d+)").find(
            document.select("div.movie-info h1").text().trim()
        )?.groupValues?.get(1)?.toIntOrNull()

        val tvType = if (document.selectFirst("#season-data") != null) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("div.meta-info")?.text()?.trim()
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val document = executeWithRetry(maxRetries = 3) {
                rateLimitDelay()
                app.get(
                    data,
                    timeout = requestTimeout,
                    headers = mapOf("User-Agent" to getRandomUserAgent())
                ).documentLarge
            }

            document.select("ul#player-list > li").mapNotNull {
                val link = it.select("a").attr("href")
                if (link.isNotEmpty()) fixUrl(link) else null
            }.amap { url ->
                try {
                    rateLimitDelay()
                    val iframeUrl = url.getIframe()
                    if (iframeUrl.isNotEmpty()) {
                        val referer = getBaseUrl(url)
                        loadExtractor(iframeUrl, referer, subtitleCallback, callback)
                    }
                } catch (e: Exception) {
                    logError("LayarKaca", "Failed to load extractor for: $url", e)
                }
            }

            return true
        } catch (e: Exception) {
            logError("LayarKaca", "loadLinks failed: ${e.message}", e)
            return false
        }
    }

    private suspend fun String.getIframe(): String {
        return try {
            rateLimitDelay()
            app.get(
                this,
                referer = "$seriesUrl/",
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge.select("div.embed-container iframe").attr("src")
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
                timeout = requestTimeout,
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

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("src") -> this.attr("src")
            this.hasAttr("data-src") -> this.attr("data-src")
            else -> this.attr("src")
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
