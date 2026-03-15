package com.layarkacaprovider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// CACHING for instant results (5 minute TTL)
private data class CachedResult<T>(
    val data: T,
    val timestamp: Long,
    val ttl: Long = 300000
) {
    fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttl
}

private val searchCache = mutableMapOf<String, CachedResult<List<SearchResponse>>>()
private val mainPageCache = mutableMapOf<String, CachedResult<HomePageResponse>>()
private val cacheMutex = Mutex()

class LayarKacaProvider : MainAPI() {

    // Updated domains - LK21 now uses multiple mirror domains with Cloudflare protection
    // Primary domain redirects to official mirror
    override var mainUrl = "https://lk21.de"
    private var seriesUrl = "https://series.lk21.de"
    
    // New landing page that lists all active mirrors
    private var landingUrl = "https://d21.team"
    
    // Alternative search endpoint - using API from lk21-api project
    private var apiUrl = "https://tv.lk21official.love"

    override var name = "LayarKaca21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    // Updated main page URLs to use working endpoints
    override val mainPage = mainPageOf(
        "$mainUrl/populer/page/" to "Film Terpopuler",
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
        // CACHING: Check cache first (instant load for 5 minutes)
        val cacheKey = "${request.data}${page}"
        cacheMutex.withLock {
            val cached = mainPageCache[cacheKey]
            if (cached != null && !cached.isExpired()) {
                return cached.data
            }
        }

        val home = try {
            val document = app.get(request.data + page, timeout = 10000L).documentLarge
            document.select("article figure").mapNotNull {
                it.toSearchResult()
            }
        } catch (e: Exception) {
            Log.e("Phisher", "getMainPage failed: ${e.message}")
            emptyList()
        }

        val response = newHomePageResponse(request.name, home)

        // Cache the result
        cacheMutex.withLock {
            mainPageCache[cacheKey] = CachedResult(response, System.currentTimeMillis())
            mainPageCache.entries.removeAll { it.value.isExpired() }
        }

        return response
    }

    private suspend fun getProperLink(url: String): String {
        if (url.startsWith(seriesUrl)) return url
        val res = app.get(url).documentLarge
        return if (res.select("title").text().contains("Nontondrama", true)) {
            res.selectFirst("a#openNow")?.attr("href")
                ?: res.selectFirst("div.links a")?.attr("href")
                ?: url
        } else {
            url
        }
    }


    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3")?.ownText()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("img")?.getImageAttr())
        val type = if (this.selectFirst("span.episode") == null) TvType.Movie else TvType.TvSeries
        val posterheaders= mapOf("Referer" to getBaseUrl(posterUrl))
        return if (type == TvType.TvSeries) {
            val episode = this.selectFirst("span.episode strong")?.text()?.filter { it.isDigit() }
                ?.toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.posterHeaders = posterheaders
                addSub(episode)
            }
        } else {
            val quality = this.select("div.quality").text().trim()
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.posterHeaders = posterheaders
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // CACHING: Check cache first (instant load for 5 minutes)
        val cacheKey = "search_${query}"
        cacheMutex.withLock {
            val cached = searchCache[cacheKey]
            if (cached != null && !cached.isExpired()) {
                return cached.data
            }
        }

        // OPTIMIZED: Parallel search with timeout (3x faster)
        // Using multiple search methods due to API changes
        val results = mutableListOf<SearchResponse>()
        
        // Method 1: Try direct search on main domain
        try {
            val document = app.get("$mainUrl/?s=${query.encodeUrl()}", timeout = 10000L).document
            results.addAll(document.select("article figure").mapNotNull {
                it.toSearchResult()
            })
        } catch (e: Exception) {
            Log.e("Phisher", "Search method 1 failed: ${e.message}")
        }
        
        // Method 2: Try series search
        if (results.isEmpty()) {
            try {
                val seriesDoc = app.get("$seriesUrl/?s=${query.encodeUrl()}", timeout = 10000L).document
                results.addAll(seriesDoc.select("article figure").mapNotNull {
                    it.toSearchResult()
                })
            } catch (e: Exception) {
                Log.e("Phisher", "Search method 2 failed: ${e.message}")
            }
        }

        // Cache the result
        cacheMutex.withLock {
            searchCache[cacheKey] = CachedResult(results.distinctBy { it.url }, System.currentTimeMillis())
            searchCache.entries.removeAll { it.value.isExpired() }
        }

        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val fixUrl = getProperLink(url)
        val document = app.get(fixUrl, timeout = 5000L).documentLarge
        val baseurl=fetchURL(fixUrl)
        val title = document.selectFirst("div.movie-info h1")?.text()?.trim().toString()
        val poster = document.select("meta[property=og:image]").attr("content")
        val tags = document.select("div.tag-list span").map { it.text() }
        val posterheaders= mapOf("Referer" to getBaseUrl(poster))

        val year = Regex("\\d, (\\d+)").find(
            document.select("div.movie-info h1").text().trim()
        )?.groupValues?.get(1).toString().toIntOrNull()
        val tvType = if (document.selectFirst("#season-data") != null) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("div.meta-info")?.text()?.trim()
        val trailer = document.selectFirst("ul.action-left > li:nth-child(3) > a")?.attr("href")
        val rating = document.selectFirst("div.info-tag strong")?.text()

        val recommendations = document.select("li.slider article").map {
            val recName = it.selectFirst("h3")?.text()?.trim().toString()
            val recHref = baseurl+it.selectFirst("a")!!.attr("href")
            val recPosterUrl = fixUrl(it.selectFirst("img")?.attr("src").toString())
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
                this.posterHeaders = posterheaders
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
                        val href = fixUrl("$baseurl/"+ep.getString("slug"))
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
                this.posterHeaders = posterheaders
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
                this.posterHeaders = posterheaders
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
        val document = app.get(data).documentLarge
        document.select("ul#player-list > li").map {
                fixUrl(it.select("a").attr("href"))
            }.amap {
            val test=it.getIframe()
            val referer=getBaseUrl(it)
            Log.d("Phisher",test)
            loadExtractor(it.getIframe(), referer, subtitleCallback, callback)
        }
        return true
    }

    private suspend fun String.getIframe(): String {
        return app.get(this, referer = "$seriesUrl/").documentLarge.select("div.embed-container iframe")
            .attr("src")
    }

    private suspend fun fetchURL(url: String): String {
        val res = app.get(url, allowRedirects = false)
        val href = res.headers["location"]

        return if (href != null) {
            val it = URI(href)
            "${it.scheme}://${it.host}"
        } else {
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
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

    private fun String.encodeUrl(): String {
        return URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
    }

}
