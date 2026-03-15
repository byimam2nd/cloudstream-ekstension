package com.layarkaca21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

// =========== MAIN PROVIDER ===========
open class LayarKaca21 : MainAPI() {
    override var mainUrl = "https://lk21.de"
    override var name = "LayarKaca21"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "$mainUrl/populer/page/" to "Popular",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            val doc = app.get("${request.data}$page", timeout = 15000L).document
            val items = doc.select("article figure").mapNotNull { el ->
                val title = el.selectFirst("h3")?.text() ?: return@mapNotNull null
                val href = el.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                newMovieSearchResponse(title, fixUrl(href), TvType.Movie)
            }
            newHomePageResponse(request.name, items)
        } catch (e: Exception) {
            // CRITICAL: Return empty response, DON'T throw!
            // This keeps provider visible in home even if scraping fails
            newHomePageResponse(request.name, emptyList())
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val doc = app.get("$mainUrl/?s=$query", timeout = 15000L).document
            doc.select("article figure").mapNotNull { el ->
                val title = el.selectFirst("h3")?.text() ?: return@mapNotNull null
                val href = el.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                newMovieSearchResponse(title, fixUrl(href), TvType.Movie)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 15000L).document
        val title = doc.selectFirst("div.movie-info h1")?.text() ?: "Unknown"
        val isSeries = doc.selectFirst("#season-data") != null
        
        if (isSeries) {
            val episodes = mutableListOf<Episode>()
            // TODO: Parse season data
            episodes.add(newEpisode(url) { 
                this.name = "Episode 1"
                this.season = 1
            })
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes)
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        doc.select("ul#player-list > li a").map { fixUrl(it.attr("href")) }.forEach { link ->
            loadExtractor(link, subtitleCallback, callback)
        }
        return true
    }
}

// =========== PLUGIN REGISTRATION ===========
@CloudstreamPlugin
class LayarKaca21Provider: BasePlugin() {
    override fun load() {
        registerMainAPI(LayarKaca21())
    }
}
