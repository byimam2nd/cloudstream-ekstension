package com.Anichin

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
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import okhttp3.OkHttpClient
import org.json.JSONObject

class Anichin : MainAPI() {
    override var mainUrl = AnichinPlugin.currentAnichinServer
    override var name = "Anichin"
    override val hasQuickSearch = true
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val usesWebView = false
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    private fun Element.toSearchResult(): SearchResponse {
        // Try multiple selectors for title
        val href = fixUrl(this.select("a").attr("href").ifEmpty { this.attr("href") })
        val title = this.select("h2 a, a[data-jtitle], h2, a").firstOrNull()?.text()
            ?: this.select("[data-jtitle]").attr("data-jtitle")
            ?: "Unknown"
        
        // Try multiple selectors for poster
        val posterUrl = fixUrl(
            this.select("img").attr("src").ifEmpty { 
                this.select("img").attr("data-src") 
            }.ifEmpty {
                // Check for backdrop style
                this.attr("style")
                    .substringAfter("background-image: url('")
                    .substringBefore("')")
            }.ifEmpty {
                this.select(".backdrop").attr("style")
                    .substringAfter("background-image: url('")
                    .substringBefore("')")
            }
        )

        // Cek tipe anime (Donghua, Movie, dll)
        val typeText = this.select(".anime-type, .film-type, .type, .status").firstOrNull()?.text() ?: ""
        val type = getType(typeText)

        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl.ifEmpty { null }
            // Anichin mostly SUB only
            addDubStatus(false, true, null, null)
        }
    }

    private fun Element.getActorData(): ActorData? {
        // Actor data might not be available on Anichin, skip for now
        return null
    }

    companion object {
        private val client = OkHttpClient()
        const val userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.OVA
            else if (t.contains("Movie", ignoreCase = true)) TvType.AnimeMovie 
            else TvType.Anime
        }

        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Finished", "Completed", "Tamat" -> ShowStatus.Completed
                "Ongoing", "Releasing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage =
            mainPageOf(
                    "$mainUrl/?page=" to "Latest Episodes",
                    "$mainUrl/ongoing?page=" to "Ongoing Anime",
                    "$mainUrl/completed?page=" to "Completed Anime",
                    "$mainUrl/donghua?page=" to "Donghua",
                    "$mainUrl/popular?page=" to "Popular Anime",
            )

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val link = "$mainUrl/search?keyword=$query&page=$page"
        val res = app.get(link).documentLarge

        // Try multiple selectors for compatibility
        val items = res.select("div.item, div.anime-item, div.video-item, div.swiper-slide, a.item, a.anime-item, div.listupd div.item")
            .filter { 
                it.select("img, .backdrop").isNotEmpty() && 
                it.select("a[href]").isNotEmpty() 
            }
            .map { it.toSearchResult() }
        
        return items.toNewSearchResponseList()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}$page").document
        
        // Try multiple selectors for compatibility - match Anichin.cafe structure
        val items = document.select("div.swiper-slide.item, div.item, div.anime-item, div.video-item, div.listupd div.item")
            .filter { 
                it.select("img, .backdrop").isNotEmpty() && 
                it.select("a[href*='/seri/'], a[href*='/anime/']").isNotEmpty() 
            }
            .map { it.toSearchResult() }
        
        return if (items.isNotEmpty()) {
            newHomePageResponse(request.name, items)
        } else {
            // Fallback: try to find any items in listupd div
            val fallbackItems = document.select("div.listupd div.item, div.bsx")
                .filter { 
                    it.select("a[href]").isNotEmpty()
                }
                .map { it.toSearchResult() }
            
            newHomePageResponse(request.name, fallbackItems)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        // Remove watch/ prefix if exists
        val cleanUrl = url.replace("watch/", "").replace("anime/", "")
        val document = app.get(cleanUrl).document

        // Try to get sync data if available
        val syncData = tryParseJson<ZoroSyncData>(document.selectFirst("#syncData")?.data())
        
        val title = document.selectFirst("h1.anime-title, h1.film-name, h1.title")?.text().toString()
            .ifEmpty { document.selectFirst("h2.anime-title")?.text() ?: "Unknown" }
        
        val description = document.select("div.synopsis, div.description, div.anime-description").text()
            .ifEmpty { document.select("div.film-description").text() }
        
        val poster = document.selectFirst("#ani_detail div.anime-poster img, div.anime-poster img, img.poster")?.attr("src")
            ?: document.selectFirst("img")?.attr("src")
        
        val genres = document.select("div.genres a, div.anime-genre a, a[href*='/genre/']").map { it.text() }
        
        // Get anime ID from URL
        val animeId = URI(url).path.split("-").lastOrNull() 
            ?: document.selectFirst("input#anime-id")?.attr("value") 
            ?: ""
        
        val subCount = document.selectFirst(".tick-sub, .episode-count")?.text()?.toIntOrNull()
        val dubCount = document.selectFirst(".tick-dub")?.text()?.toIntOrNull()
        
        val episodes = mutableListOf<Episode>()
        
        // Try to load episodes from AJAX or HTML
        if (animeId.isNotEmpty()) {
            try {
                // Try AJAX endpoint first (if exists)
                val responseBody = app.get("$mainUrl/ajax/episode/list/$animeId").body.string()
                val epRes = responseBody.stringParse<Response>()?.getDocument()
                
                epRes?.select("a.episode-item, a[href*='/episode/'], a[href*='/watch/']")?.forEachIndexed { index, ep ->
                    val href = ep.attr("href").removePrefix("/")
                    val episodeNum = ep.selectFirst(".episode-num, .ep-number")?.text()?.toIntOrNull() ?: (index + 1)
                    val epTitle = ep.text().ifEmpty { "Episode $episodeNum" }
                    
                    episodes.add(
                        newEpisode("sub|$href") {
                            this.name = epTitle
                            this.episode = episodeNum
                        }
                    )
                }
            } catch (e: Exception) {
                // Fallback: parse episodes from HTML
                document.select("div.episode-list a, a.episode-item, a[href*='/episode/']").forEachIndexed { index, ep ->
                    val href = fixUrl(ep.attr("href"))
                    val episodeNum = ep.selectFirst(".episode-num")?.text()?.toIntOrNull() ?: (index + 1)
                    val epTitle = ep.text().ifEmpty { "Episode $episodeNum" }
                    
                    episodes.add(
                        newEpisode("sub|$href") {
                            this.name = epTitle
                            this.episode = episodeNum
                        }
                    )
                }
            }
        }
        
        // Get metadata from ani.zip if sync data available
        val malId = syncData?.malId ?: "0"
        val anilistId = syncData?.aniListId ?: "0"
        var kitsuid: String? = null
        var tmdbid: Int? = null
        var backgroundposter: String? = null
        var logoUrl: String? = null
        
        if (malId != "0") {
            try {
                val syncMetaData = app.get("https://api.ani.zip/mappings?mal_id=$malId").toString()
                val animeMetaData = parseAnimeData(syncMetaData)
                
                kitsuid = animeMetaData?.mappings?.kitsuid
                tmdbid = animeMetaData?.mappings?.themoviedbId
                backgroundposter = animeMetaData?.images?.find { it.coverType == "Fanart" }?.url
                logoUrl = fetchTmdbLogoUrl(
                    tmdbAPI = "https://api.themoviedb.org/3",
                    apiKey = "98ae14df2b8d8f8f8136499daf79f0e0",
                    type = TvType.Anime,
                    tmdbId = tmdbid,
                    appLangCode = "en"
                )
            } catch (e: Exception) {
                Log.e("Anichin", "Failed to fetch metadata: ${e.message}")
            }
        }

        val type = if (document.select("div.film-stats").text().contains("Movie", ignoreCase = true)) 
            TvType.AnimeMovie else TvType.Anime

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            engName = title
            posterUrl = poster
            backgroundPosterUrl = backgroundposter
            try { this.logoUrl = logoUrl } catch(_:Throwable){}
            this.tags = genres
            this.plot = description
            addEpisodes(DubStatus.Subbed, episodes.reversed())
            // addEpisodes(DubStatus.Dubbed, emptyList()) // Anichin mostly SUB only
            
            addMalId(malId.toIntOrNull())
            addAniListId(anilistId.toIntOrNull())
            try { addKitsuId(kitsuid) } catch(_:Throwable){}
            
            // Parse additional info from HTML
            document.select("div.anisc-info .item, div.item").forEach { info ->
                val infoType = info.select("span.item-head").text().removeSuffix(":")
                when (infoType) {
                    "Overview" -> plot = info.selectFirst(".text")?.text() ?: description
                    "Japanese" -> japName = info.selectFirst(".name")?.text()
                    "Premiered" -> year = info.selectFirst(".name")?.text()?.substringAfter(" ")?.toIntOrNull()
                    "Duration" -> duration = getDurationFromString(info.selectFirst(".name")?.text())
                    "Status" -> showStatus = getStatus(info.selectFirst(".name")?.text().toString())
                    "Genres" -> tags = info.select("a").map { it.text() }
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
            val dubType = data.removePrefix("$mainUrl/").substringBefore("|").ifEmpty { "sub" }
            val hrefPart = data.substringAfterLast("|")
            
            // Load episode page
            val document = app.get(hrefPart).document
            
            // Look for video iframes or embed URLs
            val iframes = document.select("iframe[src], iframe[data-src]")
            
            iframes.forEach { iframe ->
                val iframeUrl = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
                if (iframeUrl.isNotEmpty() && iframeUrl.startsWith("http")) {
                    loadCustomExtractor(
                        "Anichin",
                        iframeUrl,
                        mainUrl,
                        subtitleCallback,
                        callback,
                    )
                }
            }
            
            // Also check for direct video links in scripts
            val scripts = document.select("script")
            scripts.forEach { script ->
                val html = script.html()
                // Look for m3u8 or mp4 URLs
                val videoUrl = Regex("""https?://[^\s"'<>]+\.(?:m3u8|mp4)""").find(html)?.value
                if (videoUrl != null) {
                    callback.invoke(
                        newExtractorLink(
                            "Anichin",
                            "Anichin",
                            videoUrl,
                            ExtractorLinkType.M3U8
                        )
                    )
                }
            }
            
            return true
        } catch (e: Exception) {
            Log.e("Anichin", "Critical error in loadLinks: ${e.localizedMessage}")
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

    val json = runCatching { JSONObject(app.get(url).text) }.getOrNull() ?: return null
    val logos = json.optJSONArray("logos") ?: return null
    if (logos.length() == 0) return null

    val lang = appLangCode?.trim()?.lowercase()

    fun path(o: JSONObject) = o.optString("file_path")
    fun isSvg(o: JSONObject) = path(o).endsWith(".svg", true)
    fun urlOf(o: JSONObject) = "https://image.tmdb.org/t/p/w500${path(o)}"

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

    return null
}
