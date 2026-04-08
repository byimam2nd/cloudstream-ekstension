// ========================================
// IDLIX PROVIDER
// ========================================
// Site: https://idlixian.com
// Type: Movie/TV Series/Anime/Asian Drama
// Language: Indonesian (id)
// Standard: cloudstream-ekstension
// Features: AES decryption for video links
// ========================================

package com.Idlix

// ============================================
// GROUP 1: Generated Sync Imports
// ============================================
import com.Idlix.generated_sync.*
import com.Idlix.generated_sync.CloudflareSolver
import com.Idlix.generated_sync.WebViewScraper
import com.Idlix.generated_sync.ScrapeItem

// ============================================
// GROUP 2: CloudStream Library
// ============================================
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson

// ============================================
// GROUP 3: Kotlin Coroutines
// ============================================
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

// ============================================
// GROUP 4: External Libraries
// ============================================
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element

// ============================================
// GROUP 5: Java Standard Library
// ============================================
import java.net.URI
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

    companion object {
        var appContext: android.content.Context? = null
    }

    // Standard timeout untuk semua request (10 detik)
    private val requestTimeout = 10_000L

    // ========================================
    // MAIN PAGE CATEGORIES
    // ========================================
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

    // ========================================
    // GET MAIN PAGE
    // ========================================
    // Fetches category/trending listings with pagination
    // Handles both featured (non-paged) and paged content
    // Results are cached to avoid redundant requests
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val cacheKey = "${request.data}${page}"

        mainPageCache.get(cacheKey)?.let { cached ->
            logDebug("Idlix", "Cache HIT for $cacheKey")
            return cached
        }

        logDebug("Idlix", "Cache MISS for $cacheKey")

        val url = request.data.split("?")
        val nonPaged = request.name == "Featured" && page <= 1

        val fetchUrl = if (nonPaged) {
            request.data
        } else if (url.size > 1) {
            "${url.first()}$page/?${url.last()}"
        } else {
            "${url.first()}$page/"
        }

        // Try WebView-based scraping for SPA website
        val ctx = appContext
        val scrapedItems = if (ctx != null) {
            WebViewScraper.scrapeMainPage(
                url = fetchUrl,
                jsExtract = """
                    (function() {
                        var items = [];
                        var selectors = [
                            'a[href*="/movie/"], a[href*="/tv/"], a[href*="/series/"]',
                            '.movie-card a, .tv-card a, .item a',
                            '[class*="card"] a, [class*="item"] a',
                            'article a, .card a'
                        ];
                        for (var s = 0; s < selectors.length; s++) {
                            var elements = document.querySelectorAll(selectors[s]);
                            if (elements.length > 0) {
                                elements.forEach(function(el) {
                                    var img = el.querySelector('img');
                                    var title = el.getAttribute('title') || 
                                               el.querySelector('[class*="title"]')?.textContent ||
                                               img?.getAttribute('alt') ||
                                               el.textContent.trim().substring(0, 50);
                                    if (title && title.length > 2) {
                                        items.push({
                                            title: title,
                                            poster: img?.src || img?.getAttribute('data-src') || '',
                                            href: el.href || el.getAttribute('href') || ''
                                        });
                                    }
                                });
                                if (items.length > 0) break;
                            }
                        }
                        return items.slice(0, 40);
                    })()
                """.trimIndent(),
                context = ctx,
                timeout = 20000L
            )
        } else {
            emptyList()
        }

        if (scrapedItems.isNotEmpty()) {
            val home = scrapedItems.mapNotNull { item ->
                if (item.title.isBlank() || item.href.isBlank()) null
                else newMovieSearchResponse(item.title, item.href, TvType.Movie) {
                    this.posterUrl = item.posterUrl
                }
            }

            if (home.isNotEmpty()) {
                val response = newHomePageResponse(request.name, home)
                mainPageCache.put(cacheKey, response)
                logDebug("Idlix", "WebView scrape success: ${home.size} items for ${request.name}")
                return response
            }
        }

        // Fallback: HTTP scraping
        logDebug("Idlix", "WebView scraping returned empty, trying HTTP fallback")
        return fallbackHttpGet(fetchUrl, cacheKey, request)
    }

    private suspend fun fallbackHttpGet(
        fetchUrl: String,
        cacheKey: String,
        request: MainPageRequest
    ): HomePageResponse {
        val url = fetchUrl.split("?")
        val nonPaged = request.name == "Featured" && url[0] == request.data

        val req = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            app.get(
                fetchUrl,
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            )
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
        mainPageCache.put(cacheKey, response)
        return response
    }

    private fun normalizeLink(uri: String): String {
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
        
        val href = normalizeLink(titleElement?.attr("href").orEmpty())
        
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

    // ========================================
    // SEARCH
    // ========================================
    // Uses external API for fast search, falls back to scraping if API fails
    // Results cached for 5 minutes
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

    // ========================================
    // LOAD DETAIL PAGE
    // ========================================
    // Loads movie/series details including title, poster, description, actors
    // For series: parses episode list with season/episode numbers
    // Supports pre-fetching for faster link loading
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

    // ========================================
    // LOAD LINKS (VIDEO SOURCES)
    // ========================================
    // Uses AJAX API with AES decryption to extract video links
    // Handles nonce/time-based authentication
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Extract normally (no cache)
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
                                // P1 Fallback: Try direct link extraction as last resort
                                MasterLinkGenerator.createLink(
                                    source = "Idlix",
                                    url = decrypted,
                                    referer = directUrl
                                )?.let { callback(it) }
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

    // ========================================
    // AES DECRYPTION HELPERS
    // ========================================
    // Creates decryption key from nonce and time
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

    // Removes escape characters from decrypted strings
    private fun String.fixBloat(): String {
        return this.replace("\"", "").replace("\\", "")
    }

    // ========================================
    // DATA CLASSES FOR API RESPONSES
    // ========================================
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

