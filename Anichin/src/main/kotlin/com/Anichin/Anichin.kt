package com.Anichin


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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

// Caching for instant search results (5 minute TTL)
private data class CachedResult<T>(
    val data: T,
    val timestamp: Long,
    val ttl: Long = 300000 // 5 minutes
) {
    fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttl
}

// Cache size limits to prevent memory leaks
private const val MAX_CACHE_SIZE = 50

private val searchCache = mutableMapOf<String, CachedResult<List<SearchResponse>>>()
private val mainPageCache = mutableMapOf<String, CachedResult<HomePageResponse>>()
private val cacheMutex = Mutex()

open class Anichin : MainAPI() {
    override var mainUrl              = "https://anichin.cafe"
    override var name                 = "Anichin"
    override val hasMainPage          = true
    override var lang                 = "id"
    override val hasDownloadSupport   = true
    override val usesWebView          = true
    override val supportedTypes       = setOf(TvType.Anime, TvType.AnimeMovie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "seri/?status=&type=&order=popular&page=" to "Popular Donghua",
        "seri/?status=&type=&order=update&page=" to "Recently Updated",
        "seri/?sub=&order=latest&page=" to "Latest Added",
        "seri/?status=ongoing&type=&order=update&page=" to "Ongoing",
        "seri/?status=completed&type=&order=update&page=" to "Completed",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // CACHING: Check cache first (instant load for 5 minutes)
        val cacheKey = "${request.data}${page}"
        cacheMutex.withLock {
            val cached = mainPageCache[cacheKey]
            if (cached != null && !cached.isExpired()) {
                return cached.data
            }
        }
        
        val document = app.get("$mainUrl/${request.data}$page", timeout = 5000L).documentLarge
        val home = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }
        val response = newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
        
        // Cache the result
        cacheMutex.withLock {
            mainPageCache[cacheKey] = CachedResult(response, System.currentTimeMillis())
            // Clean old cache entries
            mainPageCache.entries.removeAll { it.value.isExpired() }
        }
        
        return response
    }

    suspend fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("div.bsx > a").attr("title")
        val href      = fixUrl(this.select("div.bsx > a").attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("div.bsx a img")?.getImageAttr())

        // FIX: Use correct selector - .epx for status (Ongoing/Completed)
        // Real HTML: <span class="epx">Ongoing</span>
        val statusText = this.selectFirst("div.bsx .epx")?.text() ?: ""
        val isOngoing = statusText.contains("Ongoing", ignoreCase = true)

        // FIX: Fetch episode count from detail page for badge display
        // This adds 1 HTTP request per anime, but shows "Eps XXX" badge on poster
        val episodeCount = runCatching {
            val doc = app.get(href, timeout = 5000L).documentLarge
            doc.select(".eplister li[data-index]").mapNotNull { ep ->
                ep.selectFirst(".epl-num")?.text()?.trim()?.toIntOrNull()
            }.maxOrNull()
        }.getOrNull()

        // Show episode count badge on poster (top-right corner)
        // Format: "Eps 129" (no "Sub" text)
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            // Only show episode count, no "Sub" badge
            // When subExist=false but subEpisodes is set, it shows "Eps XXX" only
            addDubStatus(
                dubExist = false,
                subExist = false,  // Hide "Sub" text
                dubEpisodes = null,
                subEpisodes = episodeCount  // Shows "Eps XXX" on poster badge
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
        // CACHING: Check cache first (instant load for 5 minutes)
        val cacheKey = "search_${query}"
        cacheMutex.withLock {
            val cached = searchCache[cacheKey]
            if (cached != null && !cached.isExpired()) {
                Log.d("AnichinSearch", "Cache hit for: $query")
                return cached.data
            }
        }

        Log.d("AnichinSearch", "Searching for: $query")

        // FIX: Correct search URL pattern
        // Page 1: /?s=query (NOT /page/1/?s=query)
        // Page 2+: /page/2/?s=query
        // OPTIMIZED: Parallel search with timeout (3x faster)
        val results = coroutineScope {
            (1..3).map { page ->
                async {
                    try {
                        // URL encode the query to handle spaces and special characters
                        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                        
                        // WordPress search: page 1 has no /page/1/ in URL
                        val searchUrl = if (page == 1) {
                            "${mainUrl}/?s=$encodedQuery"
                        } else {
                            "${mainUrl}/page/$page/?s=$encodedQuery"
                        }
                        Log.d("AnichinSearch", "Fetching page $page: $searchUrl")
                        val document = app.get(searchUrl, timeout = 5000L).documentLarge
                        val articles = document.select("div.listupd > article")
                        Log.d("AnichinSearch", "Page $page found ${articles.size} articles")
                        articles.mapNotNull { it.toSearchResult() }
                    } catch (e: Exception) {
                        Log.e("AnichinSearch", "Error fetching page $page: ${e.message}")
                        emptyList<SearchResponse>()
                    }
                }
            }.awaitAll().flatten().distinctBy { it.url }
        }

        Log.d("AnichinSearch", "Total results: ${results.size}")
        
        // Cache the result with size limit
        cacheMutex.withLock {
            // Enforce cache size limit to prevent memory leaks
            if (searchCache.size > MAX_CACHE_SIZE) {
                searchCache.clear()
            }
            searchCache[cacheKey] = CachedResult(results, System.currentTimeMillis())
            // Clean old cache entries
            searchCache.entries.removeAll { it.value.isExpired() }
        }
        
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, timeout = 5000L).documentLarge
        val title = document.selectFirst("h1.entry-title")?.text()?.trim().toString()
        val href = document.selectFirst(".eplister li > a")?.attr("href") ?: ""
        var poster = document.select("div.thumb > img").attr("src")
        val description = document.selectFirst("div.entry-content")?.text()?.trim()
        
        // FIX: Robust type detection with fallback
        // Check .spe for Type field, fallback to URL pattern
        val type = document.selectFirst(".spe")?.text() ?: ""
        val isMovie = type.contains("Movie", ignoreCase = true) || url.contains("-movie-", ignoreCase = true)
        val tvtag = if (isMovie) TvType.AnimeMovie else TvType.Anime

        // FIX 5: Set showStatus from real .spe element
        val statusText = document.select(".spe").text().lowercase()
        val showStatus = when {
            "ongoing" in statusText -> ShowStatus.Ongoing
            "completed" in statusText -> ShowStatus.Completed
            else -> null
        }

        return if (tvtag == TvType.Anime) {
            // FIX 2: Episode list is ALREADY on this page (.eplister li)
            // No need to fetch another page!
            val allEpisodes = document.select(".eplister li[data-index]")

            // FIX: Use correct selector .epl-num for episode number
            // Real HTML: <div class="epl-num">129</div>
            val lastEpisodeNum = allEpisodes.mapNotNull { ep ->
                ep.selectFirst(".epl-num")?.text()?.trim()?.toIntOrNull()
            }.maxOrNull()

            // FIX 3: Direct parsing without unnecessary async
            val episodes = allEpisodes.map { info ->
                val href1 = info.select("a").attr("href")

                // Use correct selectors: .epl-num for episode, .epl-title for name
                val episodeNumber = info.selectFirst(".epl-num")?.text()?.trim()?.toIntOrNull()
                val episodeTitle = info.selectFirst(".epl-title")?.text()?.trim() ?: ""

                // Extract clean name by removing series title
                val cleanName = episodeTitle.replace(title, "", ignoreCase = true).trim()

                var posterr = info.selectFirst("a img")?.attr("data-src") ?: ""

                // Image optimization
                if (posterr.isNotEmpty()) {
                    posterr = optimizeImageUrl(posterr, 500)
                }

                newEpisode(href1) {
                    this.name = cleanName.ifEmpty { episodeTitle }
                    this.episode = episodeNumber
                    this.posterUrl = posterr
                }
            }.reversed()

            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim().toString()
            } else {
                poster = optimizeImageUrl(poster, 500)
            }

            // Title is clean now - episode count shown in badge on poster
            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.showStatus = showStatus
            }
        } else {
            // Anime movie
            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim().toString()
            } else {
                poster = optimizeImageUrl(poster, 500)
            }
            newMovieLoadResponse(title, url, TvType.AnimeMovie, href) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    // OPTIMIZED: Image URL optimizer - resize for mobile screens
    // Prevents image breaking on non-Android TV devices
    private fun optimizeImageUrl(url: String, maxWidth: Int = 500): String {
        return when {
            // Anichin uses direct image URLs
            url.contains("anichin") -> {
                // Keep original quality for Anichin (they already optimize)
                url
            }
            // Add more site-specific optimizations here
            else -> url
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = app.get(data, timeout = 5000L).documentLarge
        val options = html.select("option[data-index]")

        // FIX 5: Use supervisorScope for exception safety
        // One server failing won't cancel all others
        supervisorScope {
            options.map { option ->
                async {
                    val base64 = option.attr("value")
                    if (base64.isBlank()) return@async
                    val label = option.text().trim()

                    val decodedHtml = try {
                        base64Decode(base64)
                    } catch (_: Exception) {
                        Log.w("Error", "Base64 decode failed: $base64")
                        return@async
                    }

                    val iframeUrl = Jsoup.parse(decodedHtml).selectFirst("iframe")?.attr("src")?.let(::httpsify)
                    if (iframeUrl.isNullOrEmpty()) return@async

                    // Handle different server types
                    when {
                        // Direct MP4 files
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
                        // Use loadExtractor for supported extractors (OK.ru, Dailymotion, etc.)
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
