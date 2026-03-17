package com.Pencurimovie

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8


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
        val document = app.get("$mainUrl/${request.data}/page/$page", timeout = 50L).document
        val home = document.select("div.ml-item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("a").attr("oldtitle").substringBefore("(")
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("a img").attr("data-original").toString())
        val quality = getQualityFromString(this.select("span.mli-quality").text())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = quality
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
            val document = app.get("${mainUrl}?s=$query", timeout = 50L).document
            val results =document.select("div.ml-item").mapNotNull { it.toSearchResult() }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, timeout = 50L).document
        val title =
            document.selectFirst("div.mvic-desc h3")?.text()?.trim().toString().substringBefore("(")
        val poster = document.select("meta[property=og:image]").attr("content").toString()
        val description = document.selectFirst("div.desc p.f-desc")?.text()?.trim()
        val tvtag = if (url.contains("series")) TvType.TvSeries else TvType.Movie
        val trailer = document.select("meta[itemprop=embedUrl]").attr("content") ?: ""
        val genre = document.select("div.mvic-info p:contains(Genre)").select("a").map { it.text() }
        val rating = document.selectFirst("span.imdb-r[itemprop=ratingValue]")
            ?.text()
            ?.toDoubleOrNull()
        val duration = document.selectFirst("span[itemprop=duration]")
            ?.text()
            ?.replace(Regex("\\D"), "")
            ?.toIntOrNull()

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
                info.select("div.les-content a").forEach { it ->
                    Log.d("Phis","$it")
                    val name = it.select("a").text().substringAfter("-").trim()
                    val href = it.select("a").attr("href") ?: ""
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
                this.duration = duration ?: 0
                if (rating != null) addScore(rating.toString(), 10)
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
                this.duration = duration ?: 0
                if (rating != null) addScore(rating.toString(), 10)
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
            val document = app.get(data).document
            val links = mutableSetOf<String>()
            
            // =========================
            // STEP 1: Collect ALL possible links (NO HARDCODE)
            // =========================
            // iframe with data-src or src
            document.select("iframe").forEach {
                val src = it.attr("data-src").ifEmpty { it.attr("src") }
                if (src.isNotEmpty() && src.startsWith("http")) {
                    links.add(fixUrl(src))
                }
            }
            
            // All attributes that might contain video URLs
            document.select("[src], [data-src], [data-link], a[href]").forEach {
                val link = it.attr("data-src")
                    .ifEmpty { it.attr("data-link") }
                    .ifEmpty { it.attr("src") }
                    .ifEmpty { it.attr("href") }
                
                if (link.isNotEmpty() && link.startsWith("http")) {
                    links.add(link)
                }
            }
            
            // Fallback: regex for JS-embedded URLs
            Regex("""https?://[^\s'"]+""")
                .findAll(document.html())
                .map { it.value }
                .filter { url ->
                    url.contains("voe") || 
                    url.contains("do7go") || 
                    url.contains("dhcplay") || 
                    url.contains("listeamed") ||
                    url.contains("hglink") ||
                    url.contains("dsvplay")
                }
                .forEach { links.add(it) }
            
            // =========================
            // STEP 2: Deep Resolve & Extract
            // =========================
            links.forEach { link ->
                try {
                    val resolved = deepResolve(link, data)
                    
                    resolved.forEach { realUrl ->
                        // Try built-in extractor first
                        if (!loadExtractor(realUrl, data, subtitleCallback, callback)) {
                            // Fallback to universal extraction
                            universalExtract(realUrl, data, callback)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Pencurimovie", "Error resolving: $link")
                }
            }
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    // =========================
    // DEEP RESOLVER (Multi-Layer)
    // =========================
    private suspend fun deepResolve(url: String, referer: String?): List<String> {
        val results = mutableSetOf<String>()
        
        try {
            // Build session with proper headers
            val headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to (referer ?: url),
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.9"
            )
            
            // Step 1: Initial request (get cookies)
            val res = app.get(url, headers = headers, allowRedirects = true)
            val text = res.text
            val finalUrl = res.url
            
            // Add final URL
            results.add(finalUrl)
            
            // Step 2: Extract m3u8 directly
            Regex("""https?://[^\s'"]+\.m3u8[^\s'"]*""")
                .findAll(text)
                .forEach { results.add(it.value) }
            
            // Step 3: Extract file:/src: patterns
            Regex("""file["']?\s*:\s*["']([^"']+)["']""")
                .findAll(text)
                .forEach { results.add(it.groupValues[1]) }
            
            Regex("""src["']?\s*:\s*["']([^"']+)["']""")
                .findAll(text)
                .forEach { results.add(it.groupValues[1]) }
            
            // Step 4: Extract iframes (nested)
            Regex("""<iframe[^>]*src=["']([^"']+)["']""")
                .findAll(text)
                .forEach { 
                    val iframeUrl = fixUrl(it.groupValues[1])
                    if (iframeUrl.startsWith("http")) {
                        results.add(iframeUrl)
                    }
                }
            
            // Step 5: Detect API endpoints
            val apiMatch = Regex("""fetch\(["']([^"']+)["']""").find(text)
            if (apiMatch != null) {
                val apiUrl = fixUrl(apiMatch.groupValues[1])
                try {
                    val json = app.get(apiUrl, headers = headers).text
                    Regex("""https?://[^\s'"]+\.m3u8[^\s'"]*""")
                        .find(json)?.value?.let { results.add(it) }
                } catch (e: Exception) {
                    // Ignore API errors
                }
            }
            
            // Step 6: Unpack JavaScript (if packed)
            val unpacked = getAndUnpack(text)
            if (unpacked != null) {
                Regex("""https?://[^\s'"]+\.m3u8[^\s'"]*""")
                    .findAll(unpacked)
                    .forEach { results.add(it.value) }
            }
            
        } catch (e: Exception) {
            Log.e("Pencurimovie", "Deep resolve error: $url")
        }
        
        return results.toList()
    }
    
    // =========================
    // UNIVERSAL EXTRACTOR (Fallback)
    // =========================
    private suspend fun universalExtract(
        url: String,
        referer: String?,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to (referer ?: url)
            )
            
            val res = app.get(url, headers = headers)
            val text = getAndUnpack(res.text) ?: res.text
            
            // Priority 1: Direct m3u8
            Regex("""https?://[^\s'"]+\.m3u8[^\s'"]*""")
                .find(text)?.value?.let { m3u8Url ->
                    callback.invoke(
                        newExtractorLink(
                            "Universal",
                            "Universal",
                            m3u8Url,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = referer ?: url
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            
            // Priority 2: file: pattern
            Regex("""file["']?\s*:\s*["']([^"']+)["']""")
                .find(text)?.groupValues?.get(1)?.let { fileUrl ->
                    val isM3u8 = fileUrl.contains(".m3u8")
                    callback.invoke(
                        newExtractorLink(
                            "Universal",
                            "Universal",
                            fileUrl,
                            if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = referer ?: url
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
                
        } catch (e: Exception) {
            // Ignore universal extract errors
        }
    }
}

