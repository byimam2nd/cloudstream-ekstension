package com.layarkacaprovider

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class LayarKaca21 : MainAPI() {
    override var mainUrl = "https://lk21.de"
    override var name = "LayarKaca21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "$mainUrl/latest/page/" to "Latest Movies"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + page, timeout = 10000L).document
        val results = doc.select("article figure").mapNotNull { el ->
            val title = el.selectFirst("h3")?.ownText()?.trim() ?: return@mapNotNull null
            val href = fixUrl(el.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
            val poster = el.selectFirst("img")?.attr("src")
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
        return newHomePageResponse(request.name, results)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query", timeout = 10000L).document
        return doc.select("article figure").mapNotNull { el ->
            val title = el.selectFirst("h3")?.ownText()?.trim() ?: return@mapNotNull null
            val href = fixUrl(el.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
            newMovieSearchResponse(title, href, TvType.Movie)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 10000L).document
        val title = doc.selectFirst("div.movie-info h1")?.ownText()?.trim() ?: "Unknown"
        val poster = doc.select("meta[property=og:image]").attr("content")
        val year = doc.selectFirst("div.info-tag strong")?.text()?.toIntOrNull()
        
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        doc.select("ul#player-list > li a").map { it.attr("href") }.forEach { link ->
            loadExtractor(fixUrl(link), subtitleCallback, callback)
        }
        return true
    }
}
