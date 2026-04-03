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


open class SeaTV : Donghuastream() {
    override var mainUrl = "https://seatv-24.xyz"
    override var name                 = "SeaTV"
    override val hasMainPage          = true
    override var lang                 = "zh"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "anime/?status=&type=&order=update&page=" to "Recently Updated",
        "anime/?status=completed&type=&order=update" to "Completed",
        "anime/?status=upcoming&type=&sub=&order=" to "Upcoming",
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").documentLarge

            val results = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).documentLarge
        
        document.select(".mobius option").amap { server ->
            val base64 = server.attr("value").takeIf { it.isNotEmpty() }
            val doc = base64?.let { base64Decode(it).let(Jsoup::parse) }
            val iframeUrl = doc?.select("iframe")?.attr("src")?.let(::httpsify)
            val metaUrl = doc?.select("meta[itemprop=embedUrl]")?.attr("content")?.let(::httpsify)
            val url = iframeUrl?.takeIf { it.isNotEmpty() } ?: metaUrl.orEmpty()
            
            if (url.isNotEmpty()) {
                when {
                    url.endsWith("mp4") -> {
                        // Direct MP4 - no extractor needed
                        callback.invoke(
                            newExtractorLink(
                                "SeaTV MP4",
                                "SeaTV",
                                url = url,
                                INFER_TYPE
                            ) {
                                this.referer = ""
                                this.quality = getQualityFromName("")
                            }
                        )
                    }
                    else -> {
                        // ✅ USE loadExtractorWithFallback dengan CircuitBreaker
                        val loaded = loadExtractorWithFallback(
                            url = url,
                            referer = mainUrl,
                            subtitleCallback = subtitleCallback,
                            callback = callback
                        )
                        
                        if (!loaded) {
                            // Fallback untuk vidmoly (special case)
                            if (url.contains("vidmoly")) {
                                val newUrl = url.substringAfter("=\"").substringBefore("\"")
                                val link = "http:$newUrl"
                                loadExtractorWithFallback(
                                    url = link,
                                    referer = url,
                                    subtitleCallback = subtitleCallback,
                                    callback = callback
                                )
                            }
                        }
                    }
                }
            }
        }
        return true
    }
}