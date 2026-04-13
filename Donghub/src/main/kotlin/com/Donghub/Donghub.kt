// ========================================
// DONGHUB PROVIDER
// ========================================
// Site: https://donghub.vip
// Type: Donghua (Chinese Animation)
// Language: Indonesian (id)
// Standard: cloudstream-ekstension
// Reference: ExtCloud/Donghub
// ========================================

package com.Donghub

// ============================================
// GROUP 1: Generated Sync Imports
// ============================================
import com.Donghub.generated_sync.*

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

// ============================================
// GROUP 4: External Libraries
// ============================================
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

// Caching using shared CacheManager
private val searchCache = CacheManager<List<SearchResponse>>(defaultTtl = 30 * 60 * 1000L)
private val mainPageCache = CacheManager<HomePageResponse>(defaultTtl = 5 * 60 * 1000L)

class Donghub : MainAPI() {
    override var mainUrl = "https://donghub.vip"
    override var name = "Donghub🐉"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "anime/?order=update&page=" to "Rilisan Terbaru",
        "anime/?status=ongoing&order=update&page=" to "Series Ongoing",
        "anime/?status=completed&order=update&page=" to "Series Completed",
        "anime/?type=movie&order=update&page=" to "Movie Donghua",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val cacheKey = "${request.data}${page}"

        mainPageCache.get(cacheKey)?.let { cached ->
            logDebug("Donghub", "Cache HIT for $cacheKey")
            return cached
        }

        logDebug("Donghub", "Cache MISS for $cacheKey")

        val document = executeWithRetry {
            rateLimitDelay()
            app.get(
                "$mainUrl/${request.data}$page",
                timeout = AutoUsedConstants.FAST_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }

        val items = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }

        val response = newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false
            ),
            hasNext = true
        )

        mainPageCache.put(cacheKey, response)
        return response
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("div.bsx > a").attr("title").trim()
        val href = fixUrl(this.select("div.bsx > a").attr("href"))
        val poster = fixUrlNull(this.selectFirst("div.bsx > a img")?.getImageAttr())

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = poster
        }
    }

    private fun Element.getImageAttr(): String {
        val dataSrc = this.attr("data-src")
        val src = this.attr("src")
        return when {
            dataSrc.startsWith("http") -> dataSrc
            src.startsWith("http") -> src
            else -> dataSrc.ifEmpty { src }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val cacheKey = "search_${query}"

        searchCache.get(cacheKey)?.let { cached ->
            return cached
        }

        val list = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = executeWithRetry {
                rateLimitDelay()
                app.get(
                    "$mainUrl/page/$i/?s=$query",
                    timeout = AutoUsedConstants.FAST_TIMEOUT,
                    headers = mapOf("User-Agent" to getRandomUserAgent())
                ).documentLarge
            }

            val results = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }
            if (results.isEmpty()) break
            list.addAll(results)
        }

        val distinctResults = list.distinctBy { it.url }
        searchCache.put(cacheKey, distinctResults)
        return distinctResults
    }

    override suspend fun load(url: String): LoadResponse {
        val document = executeWithRetry {
            rateLimitDelay()
            app.get(
                url,
                timeout = AutoUsedConstants.FAST_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }

        val title = document.selectFirst("h1.entry-title")?.text()?.trim()
            ?: document.selectFirst("h1.title")?.text()?.trim()
            ?: "Unknown Title"

        val description = document.selectFirst("div.entry-content")?.text()?.trim()
            ?: document.selectFirst("div.description")?.text()?.trim()
            ?: ""

        val typeText = document.selectFirst(".spe")?.text().orEmpty()
        val isMovie = typeText.contains("Movie", ignoreCase = true)

        var poster = document.selectFirst("div.ime > img")?.getImageAttr()
            ?: document.select("meta[property=og:image]").attr("content")
            ?: ""

        // Try multiple episode list selectors
        val epBlocks = document.select(".eplister li").ifEmpty {
            document.select("div.list-episode .episode-item")
        }.ifEmpty {
            document.select("#episodes a")
        }

        if (!isMovie) {
            val episodes = epBlocks.mapNotNull { ep ->
                val link = ep.selectFirst("a")?.attr("href")?.let { fixUrl(it) }
                    ?: return@mapNotNull null
                val epTitle = ep.selectFirst(".epl-title")?.text()?.trim()
                    ?: ep.text().trim()

                newEpisode(link) {
                    this.name = epTitle
                    this.posterUrl = fixUrlNull(poster)
                }
            }.reversed()

            return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = description
            }
        } else {
            val movieLink = document.selectFirst(".eplister li > a")
                ?.attr("href")
                ?.let { fixUrl(it) } ?: url

            return newMovieLoadResponse(title, movieLink, TvType.Movie, movieLink) {
                this.posterUrl = fixUrlNull(poster)
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
        val document = executeWithRetry {
            rateLimitDelay()
            app.get(
                data,
                timeout = AutoUsedConstants.FAST_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }

        // Extract all server options from dropdown
        val options = document.select(".mobius option").ifEmpty {
            document.select("select option")
        }

        if (options.isEmpty()) {
            logError("Donghub", "No server options found")
            return false
        }

        // Process all servers in parallel
        coroutineScope {
            options.map { option ->
                async {
                    try {
                        val base64 = option.attr("value")
                        if (base64.isBlank()) return@async

                        val decoded = base64Decode(base64)
                        val doc = Jsoup.parse(decoded)
                        val iframe = doc.selectFirst("iframe")?.attr("src")
                            ?: return@async

                        loadExtractorWithFallback(
                            url = fixUrl(iframe),
                            referer = mainUrl,
                            subtitleCallback = subtitleCallback,
                            callback = callback
                        )
                    } catch (e: Exception) {
                        logError("Donghub", "Failed to load server ${option.text()}: ${e.message}")
                    }
                }
            }.awaitAll()
        }

        return true
    }
}
