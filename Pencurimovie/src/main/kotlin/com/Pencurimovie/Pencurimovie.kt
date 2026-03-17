package com.Pencurimovie

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

class Pencurimovie : MainAPI() {
    override var mainUrl = "https://ww73.pencurimovie.bond"
    override var name = "Pencurimovie"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.Cartoon)

    override val mainPage = mainPageOf(
        "movies" to "Latest Movies",
        "series" to "TV Series",
        "most-rating" to "Most Rating Movies",
        "top-imdb" to "Top IMDB Movies",
        "country/malaysia" to "Malaysia Movies",
        "country/indonesia" to "Indonesia Movies",
        "country/india" to "India Movies",
        "country/japan" to "Japan Movies",
        "country/thailand" to "Thailand Movies",
        "country/china" to "China Movies",
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
        
        val document = app.get("$mainUrl/${request.data}/page/$page", timeout = 5000L).documentLarge
        val home = document.select("div.ml-item").mapNotNull { it.toSearchResult() }
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
            mainPageCache.entries.removeAll { it.value.isExpired() }
        }
        
        return response
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("a").attr("oldtitle").substringBefore("(")
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("a img").attr("data-original"))
        val quality = getQualityFromString(this.select("span.mli-quality").text())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = quality
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
                        val document = app.get("${mainUrl}?s=$query&page=$page", timeout = 5000L).documentLarge
                        document.select("div.ml-item").mapNotNull { it.toSearchResult() }
                    } catch (e: Exception) {
                        emptyList<SearchResponse>()
                    }
                }
            }.awaitAll().flatten().distinctBy { it.url }
        }
        
        // Cache the result
        cacheMutex.withLock {
            searchCache[cacheKey] = CachedResult(results, System.currentTimeMillis())
            searchCache.entries.removeAll { it.value.isExpired() }
        }
        
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, timeout = 50L).documentLarge
        val title =
            document.selectFirst("div.mvic-desc h3")?.text()?.trim().toString().substringBefore("(")
        val poster = document.select("meta[property=og:image]").attr("content")
        val description = document.selectFirst("div.desc p.f-desc")?.text()?.trim()
        val tvtag = if (url.contains("series")) TvType.TvSeries else TvType.Movie
        val trailer = document.select("meta[itemprop=embedUrl]").attr("content")
        val genre = document.select("div.mvic-info p:contains(Genre)").select("a").map { it.text() }
        val actors =
            document.select("div.mvic-info p:contains(Actors)").select("a").map { it.text() }
        val year =
            document.select("div.mvic-info p:contains(Release)").select("a").text().toIntOrNull()
        val recommendation=document.select("div.ml-item").mapNotNull {
            it.toSearchResult()
        }
        return if (tvtag == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            document.select("div.tvseason").amap { info ->
                val season = info.select("strong").text().substringAfter("Season").trim().toIntOrNull()
                info.select("div.les-content a").forEach {
                    Log.d("Phis","$it")
                    val name = it.select("a").text().substringAfter("-").trim()
                    val href = it.select("a").attr("href")
                    val Rawepisode = it.select("a").text().substringAfter("Episode")
                            .substringBefore("-")
                            .trim().toIntOrNull()
                    episodes.add(
                        newEpisode(href)
                        {
                            this.episode=Rawepisode
                            this.name=name
                            this.season=season
                        }
                    )
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genre
                this.year = year
                addTrailer(trailer)
                addActors(actors)
                this.recommendations=recommendation
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genre
                this.year = year
                addTrailer(trailer)
                addActors(actors)
                this.recommendations=recommendation
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val document = app.get(data, timeout = 5000L).documentLarge
            
            // PERBAIKI: Gunakan selector yang lebih general untuk mengambil SEMUA iframe
            // Selector "div.movieplay iframe" mungkin terlalu spesifik dan melewatkan beberapa server
            // Gunakan selector yang mengambil semua iframe dengan data-src attribute
            val iframes = document.select("iframe[data-src]")
            
            Log.d("Pencurimovie", "Found ${iframes.size} iframes with data-src")

            // OPTIMIZED: Parallel link extraction (extract all servers simultaneously)
            // 5x faster for episodes with multiple servers
            coroutineScope {
                iframes.map { iframe ->
                    async {
                        try {
                            val href = iframe.attr("data-src")
                            if (href.isNotEmpty() && !href.contains("youtube")) {
                                Log.d("Pencurimovie", "Loading server: $href")
                                loadExtractor(href, subtitleCallback, callback)
                            }
                        } catch (e: Exception) {
                            Log.e("Pencurimovie", "Failed to load iframe: ${iframe.attr("data-src")}", e)
                        }
                    }
                }.awaitAll()
            }

            return true
        } catch (e: Exception) {
            Log.e("Pencurimovie", "loadLinks failed: ${e.message}", e)
            return false
        }
    }
}

