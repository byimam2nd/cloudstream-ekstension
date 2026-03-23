package com.Anichin

import com.Anichin.CacheManager

import com.lagradost.api.Log
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

// ============================================
// OPTIMIZED: Import shared utilities
// ============================================
import com.Anichin.rateLimitDelay
import com.Anichin.getRandomUserAgent
import com.Anichin.executeWithRetry
import com.Anichin.logError

// Cache instances
private val searchCache = CacheManager<List<SearchResponse>>()
private val mainPageCache = CacheManager<HomePageResponse>()

// Smart Cache Monitor untuk fingerprint-based invalidation
private val monitor = AnichinMonitor()

open class Anichin : MainAPI() {
    override var mainUrl = "https://anichin.cafe"
    override var name = "Anichin"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.TvSeries)

    // Standard timeout untuk semua request (10 detik)
    private val requestTimeout = 10000L

    override val mainPage = mainPageOf(
        "seri/?status=&type=&order=popular&page=" to "Popular Donghua",
        "seri/?status=&type=&order=update&page=" to "Recently Updated",
        "seri/?sub=&order=latest&page=" to "Latest Added",
        "seri/?status=ongoing&type=&order=update&page=" to "Ongoing",
        "seri/?status=completed&type=&order=update&page=" to "Completed",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val cacheKey = "${request.data}${page}"

        // Check cache first
        val cached = mainPageCache.get(cacheKey)
        if (cached != null) {
            Log.d("Anichin", "Cache HIT for $cacheKey")
            return cached
        }

        Log.d("Anichin", "Cache MISS for $cacheKey")

        // Fetch dengan retry logic dan rate limiting
        val document = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            app.get(
                "$mainUrl/${request.data}$page",
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }

        val home = document.select("div.listupd > article").mapNotNull {
            runCatching { it.toSearchResult() }.getOrElse { null }
        }

        val response = newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )

        // Cache the result
        mainPageCache.put(cacheKey, response)

        return response
    }

    suspend fun Element.toSearchResult(): SearchResponse {
        val title = this.select("div.bsx > a").attr("title")
        val href = fixUrl(this.select("div.bsx > a").attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("div.bsx a img")?.getImageAttr())

        // Fix: Use correct selector - .epx for status (Ongoing/Completed)
        val statusText = this.selectFirst("div.bsx .epx")?.text() ?: ""
        val isOngoing = statusText.contains("Ongoing", ignoreCase = true)

        // Fetch episode count from detail page for badge display
        val episodeCount = runCatching {
            val doc = app.get(
                href,
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
            doc.select(".eplister li[data-index]").mapNotNull { ep ->
                ep.selectFirst(".epl-num")?.text()?.trim()?.toIntOrNull()
            }.maxOrNull()
        }.getOrNull()

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addDubStatus(
                dubExist = false,
                subExist = true,
                dubEpisodes = null,
                subEpisodes = episodeCount
            )
        }
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("data-src")
            this.hasAttr("src") -> this.attr("src")
            else -> this.attr("src")
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

        // OPTIMIZED: Parallel search dengan rate limiting
        val results = coroutineScope {
            (1..3).map { page ->
                async {
                    try {
                        rateLimitDelay()
                        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")

                        // WordPress search: page 1 has no /page/1/ in URL
                        val searchUrl = if (page == 1) {
                            "${mainUrl}/?s=$encodedQuery"
                        } else {
                            "${mainUrl}/page/$page/?s=$encodedQuery"
                        }

                        val document = app.get(
                            searchUrl,
                            timeout = requestTimeout,
                            headers = mapOf("User-Agent" to getRandomUserAgent())
                        ).documentLarge

                        document.select("div.listupd > article").mapNotNull {
                            runCatching { it.toSearchResult() }.getOrElse { null }
                        }
                    } catch (e: Exception) {
                        logError("Anichin", "Search page $page failed: ${e.message}", e)
                        emptyList<SearchResponse>()
                    }
                }
            }.awaitAll().flatten().distinctBy { it.url }
        }

        // Cache the result
        searchCache.put(cacheKey, results)

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            app.get(
                url,
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }

        val title = document.selectFirst("h1.entry-title")?.text()?.trim().orEmpty()
        val href = document.selectFirst(".eplister li > a")?.attr("href") ?: ""
        var poster = document.select("div.thumb > img").attr("src")
        val description = document.selectFirst("div.entry-content")?.text()?.trim()

        // Fix: Robust type detection with fallback
        val type = document.selectFirst(".spe")?.text() ?: ""
        val isMovie = type.contains("Movie", ignoreCase = true) || url.contains("-movie-", ignoreCase = true)
        val tvType = if (isMovie) TvType.AnimeMovie else TvType.Anime

        // Set showStatus from real .spe element
        val statusText = document.select(".spe").text().lowercase()
        val showStatus = when {
            "ongoing" in statusText -> ShowStatus.Ongoing
            "completed" in statusText -> ShowStatus.Completed
            else -> null
        }

        return if (tvType == TvType.Anime) {
            // FIXED: Use more general selector to catch all episodes including "END" episodes
            val allEpisodes = document.select(".eplister li")

            val episodes = allEpisodes.mapNotNull { info ->
                val href1 = info.select("a").attr("href")
                if (href1.isEmpty()) return@mapNotNull null

                // FIXED: Extract episode number more robustly
                val episodeText = info.selectFirst(".epl-num")?.text()?.trim().orEmpty()
                // Try to extract number from text like "52", "52 END", "END", etc.
                val episodeNumber = episodeText.replace(Regex("[^0-9]"), "").toIntOrNull()
                
                val episodeTitle = info.selectFirst(".epl-title")?.text()?.trim() ?: ""
                val cleanName = episodeTitle.replace(title, "", ignoreCase = true).trim()
                var posterr = info.selectFirst("a img")?.attr("data-src") ?: ""

                // Image optimization
                if (posterr.isNotEmpty()) {
                    posterr = optimizeImageUrl(posterr)
                }

                newEpisode(href1) {
                    this.name = cleanName.ifEmpty { episodeTitle }
                    this.episode = episodeNumber
                    this.posterUrl = posterr
                }
            }.reversed()

            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            } else {
                poster = optimizeImageUrl(poster)
            }

            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.showStatus = showStatus
            }
        } else {
            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            } else {
                poster = optimizeImageUrl(poster)
            }
            newMovieLoadResponse(title, url, TvType.AnimeMovie, href) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    // OPTIMIZED: Image URL optimizer
    private fun optimizeImageUrl(url: String, maxWidth: Int = 500): String {
        return when {
            url.contains("anichin") -> url  // Anichin already optimizes
            else -> url
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            app.get(
                data,
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }

        val options = html.select("option[data-index]")

        // OPTIMIZED: Use supervisorScope for exception safety
        supervisorScope {
            options.map { option ->
                async {
                    val base64 = option.attr("value")
                    if (base64.isBlank()) return@async
                    val label = option.text().trim()

                    val decodedHtml = try {
                        base64Decode(base64)
                    } catch (e: Exception) {
                        logError("Anichin", "Base64 decode failed: $base64", e)
                        return@async
                    }

                    val iframeUrl = Jsoup.parse(decodedHtml).selectFirst("iframe")?.attr("src")?.let(::httpsify)
                    if (iframeUrl.isNullOrEmpty()) return@async

                    // Handle different server types
                    when {
                        iframeUrl.endsWith(".mp4") -> {
                            callback(
                                newExtractorLink(
                                    label,
                                    label,
                                    url = iframeUrl,
                                    INFER_TYPE
                                ) {
                                    this.referer = data
                                    this.quality = getQualityFromName(label)
                                }
                            )
                        }
                        else -> {
                            loadExtractor(iframeUrl, referer = data, subtitleCallback, callback)
                        }
                    }
                }
            }.awaitAll()
        }

        return true
    }
}
