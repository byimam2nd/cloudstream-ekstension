package com.LayarKaca21

import com.LayarKaca21.CacheManager

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URI
import com.LayarKaca21.rateLimitDelay
import com.LayarKaca21.getRandomUserAgent
import com.LayarKaca21.executeWithRetry
import com.LayarKaca21.logError
import com.LayarKaca21.SmartCacheMonitor

// Cache instances
private val searchCache = CacheManager<List<SearchResponse>>()
private val mainPageCache = CacheManager<HomePageResponse>()

// Smart Cache Monitor untuk fingerprint-based invalidation
private val monitor = LayarKacaMonitor()

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

    // Standard timeout untuk semua request (10 detik)
    private val requestTimeout = 10000L

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

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val cacheKey = "${request.data}${page}"

        // Check cache first
        val cached = mainPageCache.get(cacheKey)
        if (cached != null) {
            Log.d("LayarKaca", "Cache HIT for $cacheKey")
            return cached
        }

        Log.d("LayarKaca", "Cache MISS for $cacheKey")

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
        // Simplified like ExtCloud - direct approach
        val document = app.get(data).document

        val playerLinks = document.select("ul#player-list a")

        playerLinks.forEach { video ->
            val player = video.attr("href")
            val playerDoc = app.get(player, referer = "$mainUrl/").document
            val iframe = playerDoc.selectFirst("iframe")?.attr("src").orEmpty()

            if (iframe.isNotEmpty()) {
                // Handle short.icu redirect
                val finalIframe = if (iframe.contains("short.icu")) {
                    app.get(iframe, allowRedirects = true).url
                } else {
                    iframe
                }

                // Pass iframe URL directly to callback as ExtractorLink
                // CloudStream's extractor system will handle it automatically
                callback(
                    newExtractorLink(
                        "LayarKaca21",
                        "LayarKaca21",
                        finalIframe
                    ) {
                        this.referer = "$mainUrl/"
                    }
                )
            }
        }

        return true
    }

    private suspend fun String.getIframe(): String {
        return try {
            rateLimitDelay()
            val response = app.get(
                this,
                referer = "$seriesUrl/",
                timeout = requestTimeout,
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
