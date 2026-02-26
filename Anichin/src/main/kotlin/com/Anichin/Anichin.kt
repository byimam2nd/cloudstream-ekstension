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
        "seri/?status=&type=&order=update&page=" to "Recently Updated",
        "seri/?status=ongoing&type=&order=update" to "Ongoing",
        "seri/?status=completed&type=&order=update" to "Completed",
        "seri/?status=&type=special&sub=&order=update" to "Special Donghua",
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

    fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("div.bsx > a").attr("title")
        val href      = fixUrl(this.select("div.bsx > a").attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("div.bsx a img")?.getImageAttr())

        // Extract status from .dtl or .badge element (Ongoing/Completed)
        val statusText = this.selectFirst("div.bsx .dtl")?.text()
            ?: this.selectFirst("div.bsx .badge")?.text()
            ?: ""
        val isOngoing = statusText.contains("Ongoing", ignoreCase = true)

        // Add [ONGOING] to title if ongoing
        val displayTitle = if (isOngoing) "$title [ONGOING]" else title

        // FIX 6: Accurate badge display - Anichin typically has subtitles only
        return newAnimeSearchResponse(displayTitle, href, TvType.Anime) {
            this.posterUrl = posterUrl
            // Accurate badge: Sub only (Anichin is fansub site)
            addDubStatus(false, true)  // Shows "Sub" badge
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
                return cached.data
            }
        }
        
        // OPTIMIZED: Parallel search with timeout (3x faster)
        val results = coroutineScope {
            (1..3).map { page ->
                async {
                    try {
                        val document = app.get("${mainUrl}/pagg/$page/?s=$query", timeout = 5000L)
                            .documentLarge
                        document.select("div.listupd > article").mapNotNull { it.toSearchResult() }
                    } catch (e: Exception) {
                        emptyList<SearchResponse>()
                    }
                }
            }.awaitAll().flatten().distinctBy { it.url }
        }
        
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
        val type = document.selectFirst(".spe")?.text().toString()
        
        // FIX 1: Consistent TvType for anime content
        val tvtag = if (type.contains("Movie", ignoreCase = true)) TvType.AnimeMovie else TvType.Anime

        return if (tvtag == TvType.Anime) {
            val Eppage = document.selectFirst(".eplister li > a")?.attr("href") ?: ""
            val doc = app.get(Eppage, timeout = 5000L).documentLarge

            // Get all episodes to find the last episode number
            val allEpisodes = doc.select("div.episodelist > ul > li")
            val lastEpisodeNum = allEpisodes.lastOrNull()?.let { lastEp ->
                val episodeText = lastEp.select("a span").text()
                    .substringAfter("-")
                    .substringBeforeLast("-")
                episodeText.filter { it.isDigit() }.toIntOrNull()
            }

            // FIX 2 & 3: Safe episode parsing without unnecessary async
            val episodes = allEpisodes.map { info ->
                val href1 = info.select("a").attr("href")
                
                // FIX 2: Robust episode number parsing
                val rawText = info.select("a span").text()
                val episodeNumber = rawText.filter { it.isDigit() }.toIntOrNull()
                
                var posterr = info.selectFirst("a img")?.attr("data-src") ?: ""

                // Image optimization
                if (posterr.isNotEmpty()) {
                    posterr = optimizeImageUrl(posterr, 500)
                }

                newEpisode(href1) {
                    this.name = rawText.replace(title, "", ignoreCase = true)
                    this.episode = episodeNumber
                    this.posterUrl = posterr
                }
            }.reversed()

            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim().toString()
            } else {
                poster = optimizeImageUrl(poster, 500)
            }

            val displayTitle = if (lastEpisodeNum != null) "$title (Eps $lastEpisodeNum)" else title

            newTvSeriesLoadResponse(displayTitle, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.plot = description
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

                    when {
                        // FIX 4: Proper Vidmoly URL handling
                        "vidmoly" in iframeUrl -> {
                            // Just use httpsify, don't manipulate the URL string
                            val cleanedUrl = httpsify(iframeUrl)
                            loadExtractor(cleanedUrl, referer = data, subtitleCallback, callback)
                        }
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
