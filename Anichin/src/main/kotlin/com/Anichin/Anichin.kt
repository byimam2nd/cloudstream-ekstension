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
        // Get href from a tag
        val href = fixUrl(this.select("a[href*='/seri/'], a[href*='/anime/']").attr("href").ifEmpty { this.attr("href") })
        
        // Get title - try multiple selectors
        val title = this.select("h2 a, a[data-jtitle]").firstOrNull()?.text()
            ?: this.select("[data-jtitle]").attr("data-jtitle")
            ?: this.select("h2").firstOrNull()?.text()
            ?: this.select("a").attr("title")
            ?: "Unknown"
        
        // Get poster - try multiple selectors
        val posterUrl = fixUrl(
            this.select("img").attr("src").ifEmpty {
                this.select("img").attr("data-src")
            }.ifEmpty {
                this.select(".thumb img, .poster img").attr("src")
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

        // Get episode count from badge if available
        val epCount = this.select(".epx, .episode-count, .eps, .badge").firstOrNull()?.text()
            ?.filter { it.isDigit() }?.toIntOrNull()

        // Get status (Ongoing/Completed)
        val statusText = this.select(".status, .type, .film-type, .anime-type").firstOrNull()?.text() ?: ""
        val status = getStatus(statusText)
        
        // Get type
        val typeText = this.select(".anime-type, .film-type, .type, .status").firstOrNull()?.text() ?: ""
        val type = getType(typeText)

        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl.ifEmpty { null }
            // Add episode count if available
            addDubStatus(false, epCount != null, null, epCount)
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
        val items = document.select("div.listupd div.item, div.bsx, div.swiper-slide, div.item")
            .filter { 
                it.select("a[href*='/seri/'], a[href*='/anime/']").isNotEmpty()
            }
            .map { it.toSearchResult() }
        
        return if (items.isNotEmpty()) {
            newHomePageResponse(request.name, items)
        } else {
            // Fallback: try to find any series links
            val fallbackItems = document.select("a[href*='/seri/']")
                .filter { 
                    it.select("img, .thumb, .poster").isNotEmpty()
                }
                .map { it.toSearchResult() }
            
            newHomePageResponse(request.name, fallbackItems)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        // Remove watch/ prefix if exists
        val cleanUrl = url.replace("watch/", "").replace("anime/", "").replace("/seri/", "/")
        val document = app.get(cleanUrl).document

        val title = document.selectFirst("h1.entry-title, .entry-title h1, .infox h1, .single-info h1")?.text().toString()
            .ifEmpty { "Unknown" }

        val description = document.select(".entry-content p, .sinopse p, .synopsis").firstOrNull()?.text()
            ?: document.select(".entry-content").text().substringBefore("Watch").substringBefore("Streaming").trim()

        val poster = document.selectFirst(".thumb img, .poster img, .single-info img, img[itemprop='image']")?.attr("src")
            ?: document.selectFirst("img")?.attr("src")

        val genres = document.select(".genxed a, .genres a, [itemprop='genre'] a").map { it.text() }

        // Get rating
        val rating = document.selectFirst(".rating-prc [itemprop='ratingValue']")?.attr("content")?.toDoubleOrNull()
            ?: document.selectFirst(".rating strong")?.text()?.substringAfter("Rating ")?.substringBefore(" ")?.toDoubleOrNull()
        
        // Get status and other info from .spe div
        var showStatus: ShowStatus? = null
        var year: Int? = null
        var duration: Int? = null
        var totalEpisodes: Int? = null
        
        document.select(".spe span").forEach { info ->
            val text = info.text()
            when {
                text.contains("Status:", ignoreCase = true) -> {
                    val statusText = text.substringAfter("Status:").trim()
                    showStatus = getStatus(statusText)
                }
                text.contains("Released:", ignoreCase = true) -> {
                    val yearText = text.substringAfter("Released:").trim().take(4)
                    year = yearText.toIntOrNull()
                }
                text.contains("Duration:", ignoreCase = true) -> {
                    val durText = text.substringAfter("Duration:").trim()
                    duration = durText.filter { it.isDigit() }.toIntOrNull()
                }
                text.contains("Episodes:", ignoreCase = true) -> {
                    val epText = text.substringAfter("Episodes:").trim()
                    totalEpisodes = epText.filter { it.isDigit() }.toIntOrNull()
                }
            }
        }

        val episodes = mutableListOf<Episode>()

        // Scrape episodes from HTML - Anichin.cafe structure
        // Pattern: /anime-episode-X-subtitle-indonesia/
        document.select("a[href*='-episode-']")
            .filter { 
                it.attr("href").contains("subtitle") || 
                it.attr("href").contains("sub-indo") ||
                it.attr("href").contains("/episode/")
            }
            .forEachIndexed { index, ep ->
                val href = fixUrl(ep.attr("href"))
                
                // Extract episode number from URL or text
                val episodeNum = Regex("episode-(\\d+)").find(ep.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("Episode (\\d+)").find(ep.text())?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("(\\d+)").find(ep.text())?.groupValues?.get(1)?.toIntOrNull()
                    ?: (index + 1)
                
                val epTitle = ep.text().ifEmpty { "Episode $episodeNum" }
                    .replace("Subtitle Indonesia", "")
                    .replace("Sub Indo", "")
                    .replace("Subtitle", "")
                    .trim()

                episodes.add(
                    newEpisode("sub|$href") {
                        this.name = epTitle
                        this.episode = episodeNum
                    }
                )
            }

        // Remove duplicates and reverse (newest first)
        val uniqueEpisodes = episodes.distinctBy { it.episode }.reversed()

        val type = if (document.select(".spe:contains(Type)").text().contains("Movie", ignoreCase = true))
            TvType.AnimeMovie else TvType.Anime

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            engName = title
            posterUrl = poster
            this.tags = genres
            this.plot = description
            this.score = Score.from10(rating)
            this.showStatus = showStatus
            this.year = year
            this.duration = duration
            
            addEpisodes(com.lagradost.cloudstream3.DubStatus.Subbed, uniqueEpisodes)
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
