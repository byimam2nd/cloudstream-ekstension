package com.layarkaca21

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URI

// Caching for instant results (5 minute TTL)
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

open class LayarKaca21 : MainAPI() {
    override var mainUrl = "https://lk21.de"
    override var name = "LayarKaca21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "$mainUrl/populer/page/" to "Film Terpopuler",
        "$mainUrl/rating/page/" to "Film Berdasarkan IMDb Rating",
        "$mainUrl/most-commented/page/" to "Film Dengan Komentar Terbanyak",
        "$mainUrl/latest/page/" to "Film Upload Terbaru",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val cacheKey = "${request.data}$page"
        cacheMutex.withLock {
            val cached = mainPageCache[cacheKey]
            if (cached != null && !cached.isExpired()) {
                return cached.data
            }
        }

        val document = try {
            app.get("${request.data}$page", timeout = 10000L).documentLarge
        } catch (e: Exception) {
            Log.e("LayarKaca21", "getMainPage failed: ${e.message}")
            return newHomePageResponse(request.name, emptyList())
        }

        val home = document.select("article figure").mapNotNull { it.toSearchResult() }
        val response = newHomePageResponse(request.name, home)

        cacheMutex.withLock {
            mainPageCache[cacheKey] = CachedResult(response, System.currentTimeMillis())
            mainPageCache.entries.removeAll { it.value.isExpired() }
        }

        return response
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val cacheKey = "search_$query"
        cacheMutex.withLock {
            val cached = searchCache[cacheKey]
            if (cached != null && !cached.isExpired()) {
                return cached.data
            }
        }

        val results = try {
            val document = app.get("$mainUrl/?s=${query.encodeUrl()}", timeout = 10000L).document
            document.select("article figure").mapNotNull { it.toSearchResult() }
        } catch (e: Exception) {
            Log.e("LayarKaca21", "search failed: ${e.message}")
            emptyList()
        }

        cacheMutex.withLock {
            searchCache[cacheKey] = CachedResult(results, System.currentTimeMillis())
            searchCache.entries.removeAll { it.value.isExpired() }
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, timeout = 10000L).documentLarge
        
        val title = document.selectFirst("div.movie-info h1")?.ownText()?.trim() ?: "Unknown"
        val poster = document.select("meta[property=og:image]").attr("content")
        val tags = document.select("div.tag-list span").map { it.text() }
        val year = Regex("\\d, (\\d+)").find(document.select("div.movie-info h1").text())?.groupValues?.get(1)?.toIntOrNull()
        val rating = document.selectFirst("div.info-tag strong")?.text()
        val description = document.selectFirst("div.meta-info")?.text()?.trim()
        val trailer = document.selectFirst("ul.action-left > li:nth-child(3) > a")?.attr("href")

        val isSeries = document.selectFirst("#season-data") != null
        
        if (isSeries) {
            val episodes = mutableListOf<Episode>()
            val json = document.selectFirst("script#season-data")?.data()
            if (json != null) {
                val root = org.json.JSONObject(json)
                root.keys().forEach { seasonKey ->
                    val seasonArr = root.getJSONArray(seasonKey)
                    for (i in 0 until seasonArr.length()) {
                        val ep = seasonArr.getJSONObject(i)
                        val href = fixUrl(ep.getString("slug"))
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
            
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                addTrailer(trailer)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
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
        document.select("ul#player-list > li a").map { fixUrl(it.attr("href")) }.forEach { link ->
            loadExtractor(link, subtitleCallback, callback)
        }
        return true
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3")?.ownText()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val type = if (this.selectFirst("span.episode") == null) TvType.Movie else TvType.TvSeries
        
        return if (type == TvType.TvSeries) {
            val episode = this.selectFirst("span.episode strong")?.text()?.filter { it.isDigit() }?.toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            val quality = this.select("div.quality").text().trim()
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    private fun String.encodeUrl(): String {
        return java.net.URLEncoder.encode(this, java.nio.charset.StandardCharsets.UTF_8.toString())
    }

    private fun getBaseUrl(url: String?): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}
