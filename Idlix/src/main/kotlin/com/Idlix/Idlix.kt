package com.Idlix

import com.Idlix.generated_sync.CacheManager

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Element
import java.net.URI
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

// ============================================
// OPTIMIZED: Import shared utilities from generated_sync
// ============================================
import com.Idlix.generated_sync.rateLimitDelay
import com.Idlix.generated_sync.getRandomUserAgent
import com.Idlix.generated_sync.executeWithRetry
import com.Idlix.generated_sync.logError
import com.Idlix.generated_sync.logDebug
import com.Idlix.generated_sync.EpisodePreFetcher
import com.Idlix.generated_sync.SmartCacheMonitor
import com.Idlix.generated_sync.HttpClientFactory
import com.Idlix.generated_sync.CompiledRegexPatterns
import com.Idlix.generated_sync.CircuitBreaker
import com.Idlix.generated_sync.CircuitBreakerRegistry

// Cache instances
private val searchCache = CacheManager<List<SearchResponse>>()
private val mainPageCache = CacheManager<HomePageResponse>()

class Idlix : MainAPI() {
    override var mainUrl = "https://idlixian.com"
    private var directUrl = mainUrl
    override var name = "Idlix"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    // Standard timeout untuk semua request (10 detik)
    private val requestTimeout = 10000L

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Featured",
        "$mainUrl/trending/page/?get=movies" to "Trending Movies",
        "$mainUrl/trending/page/?get=tv" to "Trending TV Series",
        "$mainUrl/movie/page/" to "Movie Terbaru",
        "$mainUrl/tvseries/page/" to "TV Series Terbaru",
        "$mainUrl/network/amazon/page/" to "Amazon Prime",
        "$mainUrl/network/apple-tv/page/" to "Apple TV+ Series",
        "$mainUrl/network/disney/page/" to "Disney+ Series",
        "$mainUrl/network/HBO/page/" to "HBO Series",
        "$mainUrl/network/netflix/page/" to "Netflix Series",
    )

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val cacheKey = "${request.data}${page}"

        // Check cache first (NO RATE LIMIT FOR CACHE HIT!)
        val cached = mainPageCache.get(cacheKey)
        if (cached != null) {
            logDebug("Idlix", "Cache HIT for $cacheKey")
            return cached
        }

        logDebug("Idlix", "Cache MISS for $cacheKey")

        val url = request.data.split("?")
        val nonPaged = request.name == "Featured" && page <= 1

        val req = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            if (nonPaged) {
                app.get(
                    request.data,
                    timeout = requestTimeout,
                    headers = mapOf("User-Agent" to getRandomUserAgent())
                )
            } else {
                app.get(
                    "${url.first()}$page/?${url.lastOrNull()}",
                    timeout = requestTimeout,
                    headers = mapOf("User-Agent" to getRandomUserAgent())
                )
            }
        }

        mainUrl = getBaseUrl(req.url)
        val document = req.documentLarge

        val home = (if (nonPaged) {
            document.select("div.items.featured article")
        } else {
            document.select("div.items.full article, div#archive-content article")
        }).mapNotNull {
            runCatching { it.toSearchResult() }.getOrElse { null }
        }

        val response = newHomePageResponse(request.name, home)

        // Cache the result
        mainPageCache.put(cacheKey, response)

        return response
    }

    private fun getProperLink(uri: String): String {
        return when {
            uri.contains("/episode/") -> {
                var title = uri.substringAfter("$mainUrl/episode/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvseries/$title"
            }

            uri.contains("/season/") -> {
                var title = uri.substringAfter("$mainUrl/season/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvseries/$title"
            }

            else -> uri
        }
    }

    private fun Element.toSearchResult(): SearchResponse {
        // FIXED: Fallback strategy untuk title (2-layer)
        val titleElement = this.selectFirst("h3 > a")
        val title = titleElement?.text()?.replace(Regex("\\(\\d{4}\\)"), "")?.trim()
            ?: this.selectFirst("div.title > a")?.text()?.trim()
            ?: "Unknown Title"
        
        val href = getProperLink(titleElement?.attr("href").orEmpty())
        
        // FIXED: Fallback strategy untuk poster (3-layer)
        val posterUrl = this.select("div.poster > img").attr("src")
            .ifEmpty { this.selectFirst("img[itemprop=image]")?.attr("src").orEmpty() }
            .ifEmpty { this.selectFirst("img")?.attr("src").orEmpty() }
        
        val quality = getQualityFromString(this.select("span.quality").text())

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = quality
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // OPTIMIZED: Gunakan CacheManager dengan TTL 30 menit
        val cacheKey = "search_${query}"

        // Check cache first (NO RATE LIMIT FOR CACHE HIT!)
        val cached = searchCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        // FIXED: Fallback strategy - coba API JSON dulu, jika gagal pakai scraping
        val results = try {
            val referer = app.get(mainUrl, timeout = requestTimeout).url
            val searchUrl = "https://lk21.indianindia.com"

            val res = app.get("$searchUrl/search.php?s=$query", referer = referer, timeout = requestTimeout).text

            val searchResults = mutableListOf<SearchResponse>()

            try {
                val root = org.json.JSONObject(res)
                val arr = root.getJSONArray("data")

                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val title = item.getString("title")
                    val slug = item.getString("slug")
                    val type = item.getString("type")
                    val posterUrl = "https://static-jpg.lk21.party/wp-content/uploads/" + item.optString("poster")

                    when (type) {
                        "series" -> searchResults.add(
                            newTvSeriesSearchResponse(title, "$mainUrl/series/$slug", TvType.TvSeries) {
                                this.posterUrl = posterUrl
                            }
                        )
                        "movie" -> searchResults.add(
                            newMovieSearchResponse(title, "$mainUrl/movie/$slug", TvType.Movie) {
                                this.posterUrl = posterUrl
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                logError("Idlix", "Search JSON parsing failed: ${e.message}")
            }

            // Jika API tidak return hasil, fallback ke scraping
            if (searchResults.isEmpty()) {
                logError("Idlix", "API returned no results, falling back to scraping")
                searchWithScraping(query)
            } else {
                searchResults
            }
        } catch (e: Exception) {
            logError("Idlix", "Search API failed: ${e.message}, falling back to scraping")
            // Fallback to scraping if API fails
            searchWithScraping(query)
        }

        // Cache the result
        searchCache.put(cacheKey, results)

        return results
    }

    // Fallback method jika API JSON gagal
    private suspend fun searchWithScraping(query: String): List<SearchResponse> {
        val cacheKey = "search_${query}_scraping"
        val cached = searchCache.get(cacheKey)
        if (cached != null) return cached
        
        val results = coroutineScope {
            (1..3).map { page ->
                async {
                    try {
                        rateLimitDelay()
                        val req = app.get(
                            "$mainUrl/search/$query/page/$page",
                            timeout = requestTimeout,
                            headers = mapOf("User-Agent" to getRandomUserAgent())
                        )
                        mainUrl = getBaseUrl(req.url)
                        val document = req.documentLarge
                        document.select("div.result-item").mapNotNull {
                            runCatching { it.toSearchResult() }.getOrElse { null }
                        }
                    } catch (e: Exception) {
                        logError("Idlix", "Search scraping page $page failed: ${e.message}", e)
                        emptyList<SearchResponse>()
                    }
                }
            }.awaitAll().flatten().distinctBy { it.url }
        }
        
        searchCache.put(cacheKey, results)
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val request = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            app.get(
                url,
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            )
        }

        directUrl = getBaseUrl(request.url)
        val document = request.documentLarge

        val title = document.selectFirst("div.data > h1")?.text()?.replace(Regex("\\(\\d{4}\\)"), "")?.trim()
            ?: document.selectFirst("h1.title")?.text()?.replace(Regex("\\(\\d{4}\\)"), "")?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: "Unknown Title"
        
        val images = document.select("div.g-item")

        // FIXED: Fallback strategy untuk poster (4-layer)
        val poster = images
            .shuffled()
            .firstOrNull()
            ?.selectFirst("a")
            ?.attr("href")
            ?: document.select("div.poster > img").attr("src")
            .ifEmpty { document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty() }
            .ifEmpty { document.selectFirst("img[itemprop=image]")?.attr("src").orEmpty() }
            .ifEmpty { document.selectFirst("img[data-src]")?.attr("data-src").orEmpty() }

        val tags = document.select("div.sgeneros > a").map { it.text() }
        val year = Regex(",\\s?(\\d+)").find(
            document.select("span.date").text().trim()
        )?.groupValues?.get(1)?.toIntOrNull()

        val tvType = if (document.select("ul#section > li:nth-child(1)").text().contains("Episodes"))
            TvType.TvSeries else TvType.Movie

        // FIXED: Fallback strategy untuk description (4-layer)
        val description = document.select("p:nth-child(3)").text().trim()
            .ifEmpty { document.select("div.wp-content > p").text().trim() }
            .ifEmpty { document.select("div.content > p").text().trim() }
            .ifEmpty { document.selectFirst("meta[name=description]")?.attr("content").orEmpty() }
        
        val trailer = document.selectFirst("div.embed iframe")?.attr("src")
        val rating = document.selectFirst("span.dt_rating_vgs")?.text()

        val actors = document.select("div.persons > div[itemprop=actor]").map {
            Actor(
                it.select("meta[itemprop=name]").attr("content"),
                it.select("img").attr("src")
            )
        }

        val recommendations = document.select("div.owl-item").mapNotNull {
            val recName = it.selectFirst("a")?.attr("href")?.removeSuffix("/")?.split("/")?.last().orEmpty()
            val recHref = it.selectFirst("a")?.attr("href").orEmpty()
            val recPosterUrl = it.selectFirst("img")?.attr("src").orEmpty()

            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
            }
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("ul.episodios > li").map {
                val href = it.select("a").attr("href")
                val name = fixTitle(it.select("div.episodiotitle > a").text().trim())
                val image = it.select("div.imagen > img").attr("src")
                val episode = it.select("div.numerando").text().replace(" ", "").split("-").last().toIntOrNull()
                val season = it.select("div.numerando").text().replace(" ", "").split("-").first().toIntOrNull()

                newEpisode(href) {
                    this.name = name
                    this.season = season
                    this.episode = episode
                    this.posterUrl = image
                }
            }

            // 🎯 PRE-FETCH: Start fetching links in background for first 10 episodes
            EpisodePreFetcher.preFetchEpisodes(episodes, mainUrl)
import com.Idlix.generated_sync.SmartCacheMonitor
import com.Idlix.generated_sync.HttpClientFactory
import com.Idlix.generated_sync.CompiledRegexPatterns
import com.Idlix.generated_sync.CircuitBreaker
import com.Idlix.generated_sync.CircuitBreakerRegistry

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from100(rating)
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from100(rating)
                addActors(actors)
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
        // 🎯 CHECK CACHE FIRST (from pre-fetch)
        if (EpisodePreFetcher.loadCached(data, callback, subtitleCallback)) {
import com.Idlix.generated_sync.SmartCacheMonitor
import com.Idlix.generated_sync.HttpClientFactory
import com.Idlix.generated_sync.CompiledRegexPatterns
import com.Idlix.generated_sync.CircuitBreaker
import com.Idlix.generated_sync.CircuitBreakerRegistry
            return true
        }
        
        // No cache → extract normally
        try {
            val document = executeWithRetry(maxRetries = 3) {
                rateLimitDelay()
                app.get(
                    data,
                    timeout = requestTimeout,
                    headers = mapOf("User-Agent" to getRandomUserAgent())
                ).documentLarge
            }

            val scriptRegex = """window\.idlixNonce=['"]([a-f0-9]+)['"].*?window\.idlixTime=(\d+).*?""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val script = document.select("script:containsData(window.idlix)").toString()
            val match = scriptRegex.find(script)
            val idlixNonce = match?.groups?.get(1)?.value ?: ""
            val idlixTime = match?.groups?.get(2)?.value ?: ""

            document.select("ul#playeroptionsul > li").map {
                Triple(
                    it.attr("data-post"),
                    it.attr("data-nume"),
                    it.attr("data-type")
                )
            }.amap { (id, nume, type) ->
                try {
                    val json = app.post(
                        url = "$directUrl/wp-admin/admin-ajax.php",
                        data = mapOf(
                            "action" to "doo_player_ajax",
                            "post" to id,
                            "nume" to nume,
                            "type" to type,
                            "_n" to idlixNonce,
                            "_p" to id,
                            "_t" to idlixTime
                        ),
                        referer = data,
                        headers = mapOf(
                            "Accept" to "*/*",
                            "X-Requested-With" to "XMLHttpRequest",
                            "User-Agent" to getRandomUserAgent()
                        )
                    ).parsedSafe<ResponseHash>() ?: return@amap

                    val metrix = AppUtils.parseJson<AesData>(json.embed_url).m
                    val password = createKey(json.key, metrix)
                    val decrypted = AesHelper.cryptoAESHandler(json.embed_url, password.toByteArray(), false)?.fixBloat()
                        ?: return@amap

                    when {
                        !decrypted.contains("youtube") -> {
                            val loaded = com.Idlix.generated_sync.loadExtractorWithFallback(
                                url = decrypted,
                                referer = directUrl,
                                subtitleCallback = subtitleCallback,
                                callback = callback
                            )
                            if (!loaded) {
                                logError("Idlix", "loadExtractorWithFallback failed for $decrypted")
                            }
                        }
                        else -> return@amap
                    }
                } catch (e: Exception) {
                    logError("Idlix", "Failed to load links for ($id, $nume, $type): ${e.message}", e)
                }
            }

            return true
        } catch (e: Exception) {
            logError("Idlix", "loadLinks failed: ${e.message}", e)
            return false
        }
    }

    private fun createKey(r: String, m: String): String {
        val rList = r.split("\\x").filter { it.isNotEmpty() }.toTypedArray()
        var n = ""
        var reversedM = m.split("").reversed().joinToString("")
        while (reversedM.length % 4 != 0) reversedM += "="

        val decodedBytes = try {
            base64Decode(reversedM)
        } catch (_: Exception) {
            return ""
        }

        val decodedM = String(decodedBytes.toCharArray())
        for (s in decodedM.split("|")) {
            try {
                val index = Integer.parseInt(s)
                if (index in rList.indices) {
                    n += "\\x" + rList[index]
                }
            } catch (_: Exception) {
            }
        }
        return n
    }

    private fun String.fixBloat(): String {
        return this.replace("\"", "").replace("\\", "")
    }

    data class ResponseSource(
        @JsonProperty("hls") val hls: Boolean,
        @JsonProperty("videoSource") val videoSource: String,
        @JsonProperty("securedLink") val securedLink: String?,
    )

    data class Tracks(
        @JsonProperty("kind") val kind: String?,
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?,
    )

    data class ResponseHash(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("key") val key: String,
    )

    data class AesData(
        @JsonProperty("m") val m: String,
    )
}
