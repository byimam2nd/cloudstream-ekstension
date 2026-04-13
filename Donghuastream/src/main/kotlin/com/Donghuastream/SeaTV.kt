package com.Donghuastream

import com.Donghuastream.generated_sync.loadExtractorWithFallback
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

open class SeaTV : Donghuastream() {
    override var mainUrl = "https://seatv-24.xyz"
    override var name = "SeaTV"
    override val hasMainPage = true
    override var lang = "zh"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "anime/?status=&type=&order=update&page=" to "Recently Updated",
        "anime/?status=completed&type=&order=update&page=" to "Completed",
        "anime/?status=upcoming&type=&sub=&order=&page=" to "Upcoming",
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").documentLarge
            val results = document.select("div.listupd > article").mapNotNull { it.toSearchResultSeaTV() }

            if (results.isEmpty()) break
            searchResponse.addAll(results)
        }

        return searchResponse.distinctBy { it.url }
    }

    private fun Element.toSearchResultSeaTV(): SearchResponse {
        val title = this.select("div.bsx > a").attr("title").trim()
        val href = fixUrl(this.select("div.bsx > a").attr("href"))
        val poster = fixUrlNull(
            this.selectFirst("div.bsx a img")?.getImageAttrSeaTV()
                ?: this.selectFirst("div.bsx img")?.attr("data-src")
                ?: this.selectFirst("div.bsx img")?.attr("src")
        )

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = poster
        }
    }

    private fun Element.getImageAttrSeaTV(): String {
        val dataSrc = this.attr("data-src")
        val src = this.attr("src")
        return when {
            dataSrc.startsWith("http") -> dataSrc
            src.startsWith("http") -> src
            else -> dataSrc.ifEmpty { src }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).documentLarge

        document.select(".mobius option").amap { server ->
            val base64 = server.attr("value").takeIf { it.isNotEmpty() } ?: return@amap
            val doc = base64Decode(base64).let(Jsoup::parse)
            val iframeUrl = doc.selectFirst("iframe")?.attr("src")?.let(::httpsify)
            val metaUrl = doc.selectFirst("meta[itemprop=embedUrl]")?.attr("content")?.let(::httpsify)
            val url = iframeUrl?.takeIf { it.isNotEmpty() } ?: metaUrl.orEmpty()

            if (url.isEmpty()) return@amap

            when {
                url.contains("vidmoly") -> {
                    val newUrl = url.substringAfter("=\"").substringBefore("\"")
                    val link = "http:$newUrl"
                    loadExtractorWithFallback(
                        url = link,
                        referer = url,
                        subtitleCallback = subtitleCallback,
                        callback = callback
                    )
                }
                url.endsWith(".mp4") || url.contains(".mp4?") -> {
                    callback.invoke(
                        newExtractorLink(
                            "SeaTV MP4",
                            "SeaTV",
                            url = url,
                            INFER_TYPE
                        ) {
                            this.referer = mainUrl
                            this.quality = getQualityFromName("")
                        }
                    )
                }
                else -> {
                    loadExtractorWithFallback(
                        url = url,
                        referer = mainUrl,
                        subtitleCallback = subtitleCallback,
                        callback = callback
                    )
                }
            }
        }
        return true
    }
}
