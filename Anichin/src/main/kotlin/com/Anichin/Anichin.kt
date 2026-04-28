package com.Anichin

// ============================================
// GROUP 1: Generated Sync Imports
// ============================================
import com.Anichin.generated_sync.*

// ============================================
// GROUP 2: CloudStream Library
// ============================================
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

// ============================================
// GROUP 3: Kotlin Coroutines
// ============================================
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope

// ============================================
// GROUP 4: External Libraries
// ============================================
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

// ============================================
// GROUP 5: Java Standard Library
// ============================================
import java.util.concurrent.ConcurrentHashMap

// Cache instances
private val searchCache = CacheManager<List<SearchResponse>>(defaultTtl = 30 * 60 * 1000L)
private val mainPageCache = CacheManager<HomePageResponse>(defaultTtl = 5 * 60 * 1000L)

open class Anichin : MainAPI() {
    override var mainUrl = "https://anichin.cafe"
    override var name = "Anichin"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "seri/?status=&type=&order=popular&page=" to "Popular Donghua",
        "seri/?status=&type=&order=update&page=" to "Recently Updated",
        "seri/?sub=&order=latest&page=" to "Latest Added",
        "seri/?status=ongoing&type=&order=update&page=" to "Ongoing",
        "seri/?status=completed&type=&order=update&page=" to "Completed",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val cacheKey = "${request.data}${page}"

        // Try cache first
        mainPageCache.get(cacheKey)?.let { 
            logDebug("Anichin", "Cache HIT for $cacheKey")
            return it 
        }

        logDebug("Anichin", "Cache MISS for $cacheKey")

        // Fetch dengan retry logic dan rate limiting
        val document = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            app.get(
                "$mainUrl/${request.data}$page",
                timeout = 30000L,
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

    private suspend fun Element.toSearchResult(): SearchResponse {
        // FIXED: Fallback strategy untuk title (2-layer)
        val title = this.select("div.bsx > a").attr("title")
            .ifEmpty { this.selectFirst("div.bsx a")?.attr("title").orEmpty() }

        val href = fixUrl(this.select("div.bsx > a").attr("href"))
        
        // FIXED: Fallback strategy untuk poster image (3-layer)
        val posterUrl = fixUrlNull(
            this.selectFirst("div.bsx a img")?.extractImageAttr()
                ?: this.selectFirst("div.bsx img")?.attr("data-src")
                ?: this.selectFirst("div.bsx img")?.attr("src")
        )

        // Fix: Use correct selector - .epx for status (Ongoing/Completed)
        val statusText = this.selectFirst("div.bsx .epx")?.text() ?: ""
        val isOngoing = statusText.contains("Ongoing", ignoreCase = true)

        // Fetch episode count from detail page for badge display
        val episodeCount = runCatching {
            val doc = app.get(
                href,
                timeout = 30000L,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
            doc.select(".eplister li[data-index]").mapNotNull { ep ->
                val epText = ep.selectFirst(".epl-num")?.text()?.trim().orEmpty()
                extractEpisodeNumberLocal(epText)
            }.maxOrNull()
        }.getOrNull()

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            if (episodeCount != null) {
                this.addSub(episodeCount.toInt())
            }
            if (isOngoing) {
                this.showStatus = ShowStatus.Ongoing
            } else if (statusText.contains("Completed", ignoreCase = true)) {
                this.showStatus = ShowStatus.Completed
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val cacheKey = "search_${query}"
        searchCache.get(cacheKey)?.let { return it }

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
                            timeout = 30000L,
                            headers = mapOf("User-Agent" to getRandomUserAgent())
                        ).documentLarge

                        document.select("div.listupd > article").mapNotNull {
                            runCatching { it.toSearchResult() }.getOrElse { null }
                        }
                    } catch (e: Exception) {
                        emptyList<SearchResponse>()
                    }
                }
            }.awaitAll().flatten().distinctBy { it.url }
        }

        if (results.isNotEmpty()) {
            searchCache.put(cacheKey, results)
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            app.get(
                url,
                timeout = 30000L,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }

        // FIXED: Fallback strategy untuk title (3-layer)
        val title = document.selectFirst("h1.entry-title")?.text()?.trim()
            ?: document.selectFirst("h1.title")?.text()?.trim()
            ?: document.selectFirst(".infox h1")?.text()?.trim()
            ?: "Unknown Title"

        // FIXED: Fallback strategy untuk poster (2-layer)
        var poster = document.selectFirst("div.thumb > img")?.attr("src")
            ?: document.selectFirst("img.ts-post-image")?.attr("src")
            ?: ""

        val description = document.selectFirst("div.entry-content")?.text()?.trim() ?: ""

        // Status parsing (Enhanced)
        val statusText = document.select(".spe").text().lowercase()
        val showStatus = when {
            "ongoing" in statusText || "continuing" in statusText -> ShowStatus.Ongoing
            "completed" in statusText || "finished" in statusText -> ShowStatus.Completed
            else -> null
        }

        val type = document.selectFirst(".spe")?.text() ?: ""
        val isMovie = type.contains("Movie", ignoreCase = true) || url.contains("-movie-", ignoreCase = true)

        return if (!isMovie) {
            val allEpisodes = document.select(".eplister li")

            val episodes = allEpisodes.mapNotNull { info ->
                val href1 = info.select("a").attr("href")
                if (href1.isEmpty()) return@mapNotNull null

                val episodeText = info.selectFirst(".epl-num")?.text()?.trim().orEmpty()
                val episodeNumber = extractEpisodeNumberLocal(episodeText)

                val episodeTitle = info.selectFirst(".epl-title")?.text()?.trim() ?: ""
                val cleanName = episodeTitle.replace(title, "", ignoreCase = true).trim()
                
                var posterr = info.selectFirst("a img")?.attr("data-src")
                    ?: info.selectFirst("a img")?.attr("src")
                    ?: ""

                newEpisode(href1) {
                    this.name = cleanName.ifEmpty { episodeTitle }
                    this.episode = episodeNumber
                    this.posterUrl = posterr
                }
            }.reversed()

            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.showStatus = showStatus
            }
        } else {
            // Anime Movie handling
            val href = document.selectFirst(".eplister li > a")?.attr("href") ?: url
            newMovieLoadResponse(title, url, TvType.AnimeMovie, href) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Extract normally (no cache)
        val html = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            app.get(
                data,
                timeout = 30000L,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }

        // Try multiple selectors for video options
        val options = html.select("option[data-index]")
            .ifEmpty { html.select("select option") }
            .filter { it.attr("value").isNotBlank() }

        var successCount = 0

        supervisorScope {
            options.map { option ->
                async {
                    try {
                        val base64 = option.attr("value").trim()
                        val label = option.text().trim()
                        
                        logDebug("Anichin", "Processing server: $label")

                        val decodedHtml = base64Decode(base64)
                        val iframeUrl = Jsoup.parse(decodedHtml).selectFirst("iframe")?.attr("src")?.let(::httpsify)

                        if (!iframeUrl.isNullOrEmpty()) {
                            logDebug("Anichin", "Found iframe for $label: $iframeUrl")
                            
                            // loadExtractorWithFallback will extract iframe URL internally
                            val loaded = com.Anichin.generated_sync.loadExtractorWithFallback(
                                url = iframeUrl,
                                referer = data,
                                subtitleCallback = subtitleCallback,
                                callback = callback
                            )
                            if (loaded) successCount++
                        }
                    } catch (e: Exception) {
                        logError("Anichin", "Server failed: ${e.message}")
                    }
                }
            }.awaitAll()
        }

        return successCount > 0
    }

    private fun extractEpisodeNumberLocal(text: String): Int? {
        return Regex("""\d+""").find(text)?.value?.toIntOrNull()
    }

    private fun logDebug(tag: String, message: String) {
        Log.d("$name:$tag", message)
    }

    private fun logError(tag: String, message: String) {
        Log.e("$name:$tag", message)
    }
}
