package com.Donghuastream

import com.Donghuastream.generated_sync.loadExtractorWithFallback
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * SeaTV - Donghua streaming provider
 * Site: https://donghuafun.com (formerly seatv-24.xyz, now redirects)
 * Template: MacCMS-style (shoutu45)
 */
open class SeaTV : MainAPI() {
    override var mainUrl = "https://donghuafun.com"
    override var name = "SeaTV"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/index.php/vod/show/id/20/page/{page}" to "Donghua Terbaru",
        "$mainUrl/index.php/vod/show/class/Lianzai/id/20/page/{page}" to "Sedang Tayang",
        "$mainUrl/index.php/vod/show/class/Wanjie/id/20/page/{page}" to "Completed",
        "$mainUrl/index.php/vod/show/id/21/page/{page}" to "Donghua Movies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data.replace("{page}", page.toString())
        val document = app.get(url).document

        // MacCMS uses .vod-list or .wr-list items
        val items = document.select(".vod-list li, .wr-list li, .myui-vodlist li, .hl-list-item").mapNotNull {
            it.toSearchResultSeaTV()
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResultSeaTV(): SearchResponse? {
        val linkEl = this.selectFirst("a") ?: return null
        val title = linkEl.attr("title").ifBlank {
            linkEl.selectFirst(".title")?.text()?.trim() ?: linkEl.text().trim()
        }.ifBlank { return null }

        val href = fixUrl(linkEl.attr("href"))

        val poster = fixUrlNull(
            linkEl.selectFirst("img")?.attr("data-original")
                ?: linkEl.selectFirst("img")?.attr("data-src")
                ?: linkEl.selectFirst("img")?.attr("src")
                ?: this.selectFirst("img")?.attr("data-original")
                ?: this.selectFirst("img")?.attr("data-src")
        )

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val url = "$mainUrl/index.php/vod/search/page/$i/wd/$query.html"
            val document = app.get(url).document
            val results = document.select(".vod-list li, .wr-list li, .myui-vodlist li, .hl-list-item")
                .mapNotNull { it.toSearchResultSeaTV() }

            if (results.isEmpty()) break
            searchResponse.addAll(results)
        }

        return searchResponse.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst(".hl-data-content-title, .stui-content__detail h1, h1, .title")
            ?.text()?.trim() ?: "Unknown Title"

        val poster = fixUrlNull(
            document.selectFirst(".hl-lazy, .stui-content__thumb img, .lazyload, img[data-original]")
                ?.attr("data-original")
                ?: document.selectFirst(".hl-lazy, .stui-content__thumb img, .lazyload, img[data-original]")
                    ?.attr("data-src")
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        )

        val description = document.selectFirst(".hl-text-muted, .stui-content__detail p.detail, .detail, .desc")
            ?.text()?.trim() ?: ""

        val year = document.selectFirst(".hl-data-content-title span, .stui-content__detail p.data a")
            ?.text()?.trim()?.let { Regex("(\\d{4})").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }

        val genres = document.select(".hl-data-content-title a, .stui-content__detail p.data a, .genre a")
            .map { it.text().trim() }

        // Try to find episode links
        val episodes = document.select("#hl-plays-list, #playlist, .anthology-list, .play-list a")
            .mapNotNull { ep ->
                val link = fixUrl(ep.attr("href"))
                val epName = ep.text().trim()
                if (link.isBlank() || link == "#") return@mapNotNull null

                newEpisode(link) {
                    this.name = epName
                }
            }.reversed()

        // If no episodes found, try alternative selectors
        val allEpisodes = if (episodes.isEmpty()) {
            document.select("a[href*='/detail/'], a[href*='/play/']").mapNotNull { ep ->
                val link = fixUrl(ep.attr("href"))
                val epName = ep.text().trim().ifBlank { "Episode" }
                newEpisode(link) { this.name = epName }
            }.distinctBy { it.data }.reversed()
        } else {
            episodes
        }

        if (allEpisodes.isNotEmpty()) {
            return newTvSeriesLoadResponse(title, url, TvType.Anime, allEpisodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = genres
            }
        } else {
            // Movie - use current URL as play link
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = genres
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // If URL is already an extractor URL
        if (data.contains("vidmoly") || data.contains("mp4") || data.contains("m3u8")) {
            return loadExtractorWithFallback(data, mainUrl, subtitleCallback, callback)
        }

        // Try to find iframe in the page
        val document = app.get(data).document

        // Strategy 1: Check for .mobius option (base64 encoded)
        val options = document.select(".mobius option").ifEmpty {
            document.select("select option")
        }

        if (options.isNotEmpty()) {
            options.amap { server ->
                val base64 = server.attr("value").takeIf { it.isNotEmpty() } ?: return@amap
                val decoded = try { base64Decode(base64) } catch (_: Exception) { return@amap }
                val doc = Jsoup.parse(decoded)
                val iframeUrl = doc.selectFirst("iframe")?.attr("src")
                    ?: doc.selectFirst("meta[itemprop=embedUrl]")?.attr("content")

                if (!iframeUrl.isNullOrEmpty()) {
                    val url = fixUrl(iframeUrl)
                    loadExtractorWithFallback(url, mainUrl, subtitleCallback, callback)
                }
            }
            return true
        }

        // Strategy 2: Direct iframe
        val iframes = document.select("iframe[src]")
        if (iframes.isNotEmpty()) {
            iframes.amap { iframe ->
                val url = fixUrl(iframe.attr("src"))
                loadExtractorWithFallback(url, mainUrl, subtitleCallback, callback)
            }
            return true
        }

        // Strategy 3: Check for script-based URLs
        val scriptContent = document.select("script").map { it.data() }
            .firstOrNull { it.contains("player_aaaa") || it.contains("url") || it.contains("videoUrl") }

        if (scriptContent != null) {
            val videoUrl = Regex(""""url"\s*:\s*"([^"]+)"""").find(scriptContent)?.groupValues?.getOrNull(1)
                ?: Regex(""""videoUrl"\s*:\s*"([^"]+)"""").find(scriptContent)?.groupValues?.getOrNull(1)
                ?: Regex("""player_aaaa.*?"url":"([^"]+)""").find(scriptContent)?.groupValues?.getOrNull(1)

            if (!videoUrl.isNullOrEmpty()) {
                return loadExtractorWithFallback(fixUrl(videoUrl), mainUrl, subtitleCallback, callback)
            }
        }

        return false
    }
}
