package com.HiAnime

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ActorRole
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addKitsuId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.getDurationFromString
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import okhttp3.OkHttpClient
import org.json.JSONObject

// ============================================
// OPTIMIZED: Import shared utilities
// ============================================
import com.HiAnime.CacheManager
import com.HiAnime.rateLimitDelay
import com.HiAnime.getRandomUserAgent
import com.HiAnime.executeWithRetry
import com.HiAnime.logError
import com.CacheFingerprint
import com.CacheValidationResult

// Cache instances dengan TTL berbeda
private val searchCache = CacheManager<List<SearchResponse>>(
    ttl = SEARCH_CACHE_TTL,
    maxSize = MAX_CACHE_SIZE
)

private val mainPageCache = CacheManager<HomePageResponse>(
    ttl = MAINPAGE_CACHE_TTL,
    maxSize = MAX_CACHE_SIZE
)

// Smart Cache Monitor untuk fingerprint-based invalidation
private val monitor = HiAnimeMonitor()
private val fingerprints = mutableMapOf<String, CacheFingerprint>()

class HiAnime : MainAPI() {
    override var mainUrl = HiAnimeProviderPlugin.currentHiAnimeServer
    override var name = "HiAnime"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    // Standard timeout untuk semua request (10 detik)
    private val requestTimeout = 10000L

    private fun Element.toSearchResult(): SearchResponse {
        val href = fixUrl(this.select("a").attr("href"))
        val title = this.select("h3.film-name").text()
        val subCount = this.selectFirst(".film-poster > .tick.ltr > .tick-sub")?.text()?.toIntOrNull()
        val dubCount = this.selectFirst(".film-poster > .tick.ltr > .tick-dub")?.text()?.toIntOrNull()

        val posterUrl = fixUrl(this.select("img").attr("data-src"))
        val type = getType(this.selectFirst("div.fd-infor > span.fdi-item")?.text() ?: "")

        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            addDubStatus(dubCount != null, subCount != null, dubCount, subCount)
        }
    }

    private fun Element.getActorData(): ActorData? {
        var actor: Actor? = null
        var role: ActorRole? = null
        var voiceActor: Actor? = null
        val elements = this.select(".per-info")
        elements.forEachIndexed { index, actorInfo ->
            val name = actorInfo.selectFirst(".pi-name")?.text() ?: return null
            val image = actorInfo.selectFirst("a > img")?.attr("data-src") ?: return null
            when (index) {
                0 -> {
                    actor = Actor(name, image)
                    val castType = actorInfo.selectFirst(".pi-cast")?.text() ?: "Main"
                    role = ActorRole.valueOf(castType)
                }
                1 -> voiceActor = Actor(name, image)
                else -> {}
            }
        }
        return ActorData(actor ?: return null, role, voiceActor = voiceActor)
    }

    companion object {
        private val client = OkHttpClient()
        const val userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Ubuntu Chromium/70.0.3538.77 Chrome/70.0.3538.77 Safari/537.36"

        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.OVA
            else if (t.contains("Movie")) TvType.AnimeMovie else TvType.Anime
        }

        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Finished Airing" -> ShowStatus.Completed
                "Currently Airing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/recently-updated?page=" to "Latest Episodes",
        "$mainUrl/top-airing?page=" to "Top Airing",
        "$mainUrl/filter?status=2&language=1&sort=recently_updated&page=" to "Recently Updated (SUB)",
        "$mainUrl/filter?status=2&language=2&sort=recently_updated&page=" to "Recently Updated (DUB)",
        "$mainUrl/recently-added?page=" to "New On HiAnime",
        "$mainUrl/most-popular?page=" to "Most Popular",
        "$mainUrl/most-favorite?page=" to "Most Favorite",
        "$mainUrl/completed?page=" to "Latest Completed",
    )

    override suspend fun search(query: String, page: Int): SearchResponseList {
        // OPTIMIZED: Gunakan CacheManager dengan TTL 30 menit
        val cacheKey = "search_${query}_${page}"

        // Check cache first
        val cached = searchCache.get(cacheKey)
        if (cached != null) {
            return cached.toNewSearchResponseList()
        }

        // Fetch dengan retry logic dan rate limiting
        val results = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            val link = "$mainUrl/search?keyword=$query&page=$page"
            app.get(
                link,
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
        }

        val resultItems = results.select("div.flw-item").mapNotNull {
            runCatching { it.toSearchResult() }.getOrElse { null }
        }

        // Cache the result
        searchCache.put(cacheKey, resultItems)

        return resultItems.toNewSearchResponseList()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val cacheKey = "${request.data}${page}"

        // Check cache first
        val cached = mainPageCache.get(cacheKey)
        val cachedFingerprint = fingerprints[cacheKey]
        
        if (cached != null) {
            // SMART CACHE: Check if content has changed
            val checkResult = monitor.checkCacheValidity(
                cacheKey = cacheKey,
                url = "${request.data}$page",
                cachedFingerprint = cachedFingerprint
            )
            
            // If cache is valid, return cached data
            if (checkResult.isValid && checkResult.result == CacheValidationResult.CACHE_VALID) {
                Log.d("HiAnime", "Cache HIT for $cacheKey (fingerprint match)")
                return cached
            }
            
            // Cache invalid - will fetch new data
            Log.d("HiAnime", "Cache MISS for $cacheKey (fingerprint changed)")
        }

        // Fetch dengan retry logic dan rate limiting
        val document = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            app.get(
                "${request.data}$page",
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).document
        }

        val items = document.select("div.flw-item").mapNotNull {
            runCatching { it.toSearchResult() }.getOrElse { null }
        }

        val response = newHomePageResponse(request.name, items)

        // Cache the result
        mainPageCache.put(cacheKey, response)
        
        // Update fingerprint
        val titles = document.select("div.flw-item h3.film-name")
            .mapNotNull { it.text().trim() }
            .filter { it.isNotEmpty() }
        fingerprints[cacheKey] = monitor.generateFingerprint(cacheKey, titles)

        return response
    }

    override suspend fun load(url: String): LoadResponse {
        val document = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            app.get(
                url.replace("watch/", ""),
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).document
        }

        val syncData = tryParseJson<ZoroSyncData>(document.selectFirst("#syncData")?.data())
        val syncMetaData = executeWithRetry(maxRetries = 3) {
            rateLimitDelay()
            app.get(
                "https://api.ani.zip/mappings?mal_id=${syncData?.malId}",
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).toString()
        }

        val animeMetaData = parseAnimeData(syncMetaData)
        val title = document.selectFirst(".anisc-detail > .film-name")?.text().orEmpty()
        val description = document.select("div.film-description > div").text()
            .ifEmpty { document.select("div.film-description div").text() }
        val poster = document.select("#ani_detail div.film-poster img").attr("src")
        val genres = document.select("div.item.item-list:has(> span.item-head:contains(Genres)) a").map { it.text() }
        val backgroundposter = animeMetaData?.images?.find { it.coverType == "Fanart" }?.url
            ?: document.selectFirst(".anisc-poster img")?.attr("src")
        val animeId = URI(url).path.split("-").last()
        val kitsuid = animeMetaData?.mappings?.kitsuid
        val tmdbid = animeMetaData?.mappings?.themoviedbId

        val typeraw = document.select("div.film-stats div.tick").text()
        val type = if (typeraw.contains("Movie", ignoreCase = true)) TvType.Movie else TvType.Anime

        val subCount = document.selectFirst(".anisc-detail .tick-sub")?.text()?.toIntOrNull()
        val dubCount = document.selectFirst(".anisc-detail .tick-dub")?.text()?.toIntOrNull()
        val dubEpisodes = emptyList<Episode>().toMutableList()
        val subEpisodes = emptyList<Episode>().toMutableList()
        val malId = syncData?.malId ?: "0"
        val anilistId = syncData?.aniListId ?: "0"

        val logoUrl = fetchTmdbLogoUrl(
            tmdbAPI = "https://api.themoviedb.org/3",
            apiKey = "98ae14df2b8d8f8f8136499daf79f0e0",
            type = type,
            tmdbId = tmdbid,
            appLangCode = "en"
        )

        // Fetch all episode pages (for anime with 100+ episodes like Renegade Immortal)
        val allEpisodes = mutableListOf<Element>()
        var currentPage = 1
        var hasMorePages = true

        while (hasMorePages) {
            try {
                rateLimitDelay()
                val responseBody = app.get(
                    "$mainUrl/ajax/v2/episode/list/$animeId?page=$currentPage",
                    timeout = requestTimeout,
                    headers = mapOf("User-Agent" to getRandomUserAgent())
                ).body.string()

                val epRes = responseBody.stringParse<Response>()?.getDocument()

                if (epRes == null) {
                    hasMorePages = false
                    continue
                }

                val episodesOnPage = epRes.select(".ss-list > a[href].ssl-item.ep-item")

                if (episodesOnPage.isEmpty()) {
                    hasMorePages = false
                } else {
                    allEpisodes.addAll(episodesOnPage)
                    currentPage++

                    // Safety limit to prevent infinite loops
                    if (currentPage > 20) hasMorePages = false
                }
            } catch (e: Exception) {
                logError("HiAnime", "Failed to fetch episode page $currentPage: ${e.message}", e)
                hasMorePages = false
            }
        }

        // Process all episodes from all pages
        allEpisodes.forEachIndexed { index, ep ->
            val href = ep.attr("href").removePrefix("/")
            val episodeNum = ep.selectFirst(".ssli-order")?.text()?.toIntOrNull() ?: return@forEachIndexed
            val episodeKey = episodeNum.toString()

            fun resolveTitle(ep: Element, episodeKey: String): String {
                val titleMap = animeMetaData?.episodes?.get(episodeKey)?.title
                val jsonTitle = titleMap?.get("en")
                    ?: titleMap?.get("ja")
                    ?: titleMap?.get("x-jat")
                    ?: animeMetaData?.titles?.get("en")
                    ?: animeMetaData?.titles?.get("ja")
                    ?: animeMetaData?.titles?.get("x-jat")
                    ?: ""
                val attrTitle = ep.attr("title")
                return jsonTitle.ifBlank { attrTitle }
            }

            fun createEpisode(source: String): Episode {
                val metaEp = animeMetaData?.episodes?.get(episodeKey)
                return newEpisode("$source|$malId|$href") {
                    this.name = if (type == TvType.AnimeMovie) title else resolveTitle(ep, episodeKey)
                    this.episode = episodeNum
                    this.score = Score.from10(metaEp?.rating)
                    this.posterUrl = metaEp?.image ?: animeMetaData?.images?.firstOrNull()?.url ?: ""
                    this.description = metaEp?.overview ?: "No summary available"
                    this.addDate(metaEp?.airDateUtc)
                    this.runTime = metaEp?.runtime
                }
            }

            subCount?.let { if (index < it) subEpisodes += createEpisode("sub") }
            dubCount?.let { if (index < it) dubEpisodes += createEpisode("dub") }
        }

        val actors = document.select("div.block-actors-content div.bac-item").mapNotNull { it.getActorData() }
        val recommendations = document.select("div.block_area_category div.flw-item").mapNotNull {
            runCatching { it.toSearchResult() }.getOrElse { null }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            engName = title
            posterUrl = poster
            backgroundPosterUrl = backgroundposter
            try { this.logoUrl = logoUrl } catch (_: Throwable) {}
            this.tags = genres
            this.plot = description
            addEpisodes(DubStatus.Subbed, subEpisodes)
            addEpisodes(DubStatus.Dubbed, dubEpisodes)
            this.recommendations = recommendations
            this.actors = actors
            addMalId(malId.toIntOrNull())
            addAniListId(anilistId.toIntOrNull())
            try { addKitsuId(kitsuid) } catch (_: Throwable) {}

            document.select(".anisc-info > .item").forEach { info ->
                val infoType = info.select("span.item-head").text().removeSuffix(":")
                when (infoType) {
                    "Overview" -> plot = info.selectFirst(".text")?.text() ?: description
                    "Japanese" -> japName = info.selectFirst(".name")?.text()
                    "Premiered" -> year = info.selectFirst(".name")?.text()?.substringAfter(" ")?.toIntOrNull()
                    "Duration" -> duration = getDurationFromString(info.selectFirst(".name")?.text())
                    "Status" -> showStatus = getStatus(info.selectFirst(".name")?.text().orEmpty())
                    "Genres" -> tags = info.select("a").map { it.text() }
                    "MAL Score" -> score = Score.from10(info.selectFirst(".name")?.text())
                    else -> {}
                }
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
            val dubType = data.removePrefix("$mainUrl/").substringBefore("|").ifEmpty { "raw" }
            val hrefPart = data.substringAfterLast("|")
            val epId = hrefPart.substringAfter("ep=")

            val doc = executeWithRetry(maxRetries = 3) {
                rateLimitDelay()
                app.get(
                    "$mainUrl/ajax/v2/episode/servers?episodeId=$epId",
                    timeout = requestTimeout,
                    headers = mapOf("User-Agent" to getRandomUserAgent())
                ).parsed<Response>()
            }.getDocument()

            val servers = doc.select(".server-item[data-type=$dubType][data-id], .server-item[data-type=raw][data-id]")
                .mapNotNull {
                    val id = it.attr("data-id")
                    val label = it.selectFirst("a.btn")?.text()?.trim()
                    if (id.isNotEmpty() && label != null) {
                        id to label
                    } else {
                        null
                    }
                }.distinctBy { it.first }

            // OPTIMIZED: Parallel link extraction dengan rate limiting
            supervisorScope {
                servers.map { (id, label) ->
                    async {
                        try {
                            rateLimitDelay()
                            val sourceUrl = app.get(
                                "${mainUrl}/ajax/v2/episode/sources?id=$id",
                                timeout = requestTimeout,
                                headers = mapOf("User-Agent" to getRandomUserAgent())
                            ).parsedSafe<EpisodeServers>()?.link

                            if (sourceUrl != null) {
                                loadCustomExtractor(
                                    "HiAnime [$label]",
                                    sourceUrl,
                                    "",
                                    subtitleCallback,
                                    callback,
                                )
                            }
                        } catch (e: Exception) {
                            logError("HiAnime", "Failed to load server $id: ${e.message}", e)
                        }
                    }
                }.awaitAll()
            }

            return true
        } catch (e: Exception) {
            logError("HiAnime", "Critical error in loadLinks: ${e.localizedMessage}", e)
            return false
        }
    }

    data class Response(
        @SerializedName("status") val status: Boolean,
        @SerializedName("html") val html: String
    ) {
        fun getDocument(): Document {
            return Jsoup.parse(html)
        }
    }

    private data class ZoroSyncData(
        @JsonProperty("mal_id") val malId: String?,
        @JsonProperty("anilist_id") val aniListId: String?,
    )

    // HiAnime Response
    data class HiAnimeHeaders(
        @JsonProperty("Referer") val referer: String,
    )

    data class HiAnimeSource(
        val url: String,
        val isM3U8: Boolean,
        val type: String,
    )

    data class HiAnimeAPI(
        val sources: List<Source>,
        val tracks: List<Track>,
    )

    data class Source(
        val file: String,
        val type: String,
    )

    data class Track(
        val file: String,
        val label: String,
    )

    data class EpisodeServers(
        val type: String,
        val link: String,
        val server: Long,
        val sources: List<Any?>,
        val tracks: List<Any?>,
        val htmlGuide: String,
    )

    // Metadata
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MetaImage(
        @JsonProperty("coverType") val coverType: String?,
        @JsonProperty("url") val url: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MetaEpisode(
        @JsonProperty("episode") val episode: String?,
        @JsonProperty("airDateUtc") val airDateUtc: String?,
        @JsonProperty("runtime") val runtime: Int?,
        @JsonProperty("image") val image: String?,
        @JsonProperty("title") val title: Map<String, String>?,
        @JsonProperty("overview") val overview: String?,
        @JsonProperty("rating") val rating: String?,
        @JsonProperty("finaleType") val finaleType: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MetaAnimeData(
        @JsonProperty("titles") val titles: Map<String, String>?,
        @JsonProperty("images") val images: List<MetaImage>?,
        @JsonProperty("episodes") val episodes: Map<String, MetaEpisode>?,
        @JsonProperty("mappings") val mappings: MetaMappings? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MetaMappings(
        @JsonProperty("themoviedb_id") val themoviedbId: Int? = null,
        @JsonProperty("thetvdb_id") val thetvdbId: Int? = null,
        @JsonProperty("imdb_id") val imdbId: String? = null,
        @JsonProperty("mal_id") val malId: Int? = null,
        @JsonProperty("anilist_id") val anilistId: Int? = null,
        @JsonProperty("kitsu_id") val kitsuid: String? = null,
    )

    private fun parseAnimeData(jsonString: String): MetaAnimeData? {
        return try {
            val objectMapper = ObjectMapper()
            objectMapper.readValue(jsonString, MetaAnimeData::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private inline fun <reified T> String.stringParse(): T? {
        return try {
            Gson().fromJson(this, T::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun loadCustomExtractor(
        name: String? = null,
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        quality: Int? = null,
    ) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            CoroutineScope(Dispatchers.IO).launch {
                callback.invoke(
                    newExtractorLink(
                        name ?: link.source,
                        name ?: link.name,
                        link.url,
                    ) {
                        this.quality = when {
                            link.name == "VidSrc" -> Qualities.P1080.value
                            link.type == ExtractorLinkType.M3U8 -> link.quality
                            else -> quality ?: link.quality
                        }
                        this.type = link.type
                        this.referer = link.referer
                        this.headers = link.headers
                        this.extractorData = link.extractorData
                    }
                )
            }
        }
    }
}

suspend fun fetchTmdbLogoUrl(
    tmdbAPI: String,
    apiKey: String,
    type: TvType,
    tmdbId: Int?,
    appLangCode: String?
): String? {

    if (tmdbId == null) return null

    val url = if (type == TvType.AnimeMovie)
        "$tmdbAPI/movie/$tmdbId/images?api_key=$apiKey"
    else
        "$tmdbAPI/tv/$tmdbId/images?api_key=$apiKey"

    val json = runCatching { JSONObject(app.get(url, timeout = 10000L).text) }.getOrNull() ?: return null
    val logos = json.optJSONArray("logos") ?: return null
    if (logos.length() == 0) return null

    val lang = appLangCode?.trim()?.lowercase()

    fun path(o: JSONObject) = o.optString("file_path")
    fun isSvg(o: JSONObject) = path(o).endsWith(".svg", true)
    fun urlOf(o: JSONObject) = "https://image.tmdb.org/t/p/w500${path(o)}"

    // Language match
    var svgFallback: JSONObject? = null

    for (i in 0 until logos.length()) {
        val logo = logos.optJSONObject(i) ?: continue
        val p = path(logo)
        if (p.isBlank()) continue

        val l = logo.optString("iso_639_1").trim().lowercase()
        if (l == lang) {
            if (!isSvg(logo)) return urlOf(logo)
            if (svgFallback == null) svgFallback = logo
        }
    }
    svgFallback?.let { return urlOf(it) }

    // Highest voted fallback
    var best: JSONObject? = null
    var bestSvg: JSONObject? = null

    fun voted(o: JSONObject) = o.optDouble("vote_average", 0.0) > 0 && o.optInt("vote_count", 0) > 0

    fun better(a: JSONObject?, b: JSONObject): Boolean {
        if (a == null) return true
        val aAvg = a.optDouble("vote_average", 0.0)
        val aCnt = a.optInt("vote_count", 0)
        val bAvg = b.optDouble("vote_average", 0.0)
        val bCnt = b.optInt("vote_count", 0)
        return bAvg > aAvg || (bAvg == aAvg && bCnt > aCnt)
    }

    for (i in 0 until logos.length()) {
        val logo = logos.optJSONObject(i) ?: continue
        if (!voted(logo)) continue

        if (isSvg(logo)) {
            if (better(bestSvg, logo)) bestSvg = logo
        } else {
            if (better(best, logo)) best = logo
        }
    }

    best?.let { return urlOf(it) }
    bestSvg?.let { return urlOf(it) }

    // No language match & no voted logos
    return null
}
