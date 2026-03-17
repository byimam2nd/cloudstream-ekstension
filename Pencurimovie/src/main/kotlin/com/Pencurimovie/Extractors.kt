package com.Pencurimovie

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

// Extractor untuk server-server Pencurimovie
// Server: do7go.com, dhcplay.com, listeamed.net, voe.sx

class Do7go : ExtractorApi() {
    override val name: String = "Do7go"
    override val mainUrl: String = "https://do7go.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val document = app.get(url).document
            // Cari script yang mengandung player.src atau sources
            val scripts = document.select("script")
            for (script in scripts) {
                val data = script.data()
                if (data.contains("player.src") || data.contains("sources")) {
                    // Extract URL dari player.src({src: "url"})
                    val srcRegex = Regex("""src:\s*['"]([^'"]+)['"]""")
                    val match = srcRegex.find(data)
                    val videoUrl = match?.groupValues?.get(1)
                    
                    if (videoUrl != null && videoUrl.startsWith("http")) {
                        val isM3u8 = videoUrl.contains(".m3u8")
                        callback.invoke(
                            newExtractorLink(
                                this.name,
                                this.name,
                                videoUrl,
                                INFER_TYPE
                            ) {
                                this.referer = mainUrl
                                this.quality = Qualities.P1080.value
                                this.isM3u8 = isM3u8
                            }
                        )
                        return
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback ke loadExtractor jika gagal
        }
        // Jika gagal extract, coba loadExtractor bawaan
        loadExtractor(url, referer, subtitleCallback, callback)
    }
}

class Dhcplay : ExtractorApi() {
    override val name: String = "Dhcplay"
    override val mainUrl: String = "https://dhcplay.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val document = app.get(url).document
            val scripts = document.select("script")
            for (script in scripts) {
                val data = script.data()
                if (data.contains("player.src") || data.contains("sources")) {
                    val srcRegex = Regex("""src:\s*['"]([^'"]+)['"]""")
                    val match = srcRegex.find(data)
                    val videoUrl = match?.groupValues?.get(1)
                    
                    if (videoUrl != null && videoUrl.startsWith("http")) {
                        val isM3u8 = videoUrl.contains(".m3u8")
                        callback.invoke(
                            newExtractorLink(
                                this.name,
                                this.name,
                                videoUrl,
                                INFER_TYPE
                            ) {
                                this.referer = mainUrl
                                this.quality = Qualities.P1080.value
                                this.isM3u8 = isM3u8
                            }
                        )
                        return
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback
        }
        loadExtractor(url, referer, subtitleCallback, callback)
    }
}

class Listeamed : ExtractorApi() {
    override val name: String = "Listeamed"
    override val mainUrl: String = "https://listeamed.net"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val document = app.get(url).document
            val scripts = document.select("script")
            for (script in scripts) {
                val data = script.data()
                if (data.contains("player.src") || data.contains("sources")) {
                    val srcRegex = Regex("""src:\s*['"]([^'"]+)['"]""")
                    val match = srcRegex.find(data)
                    val videoUrl = match?.groupValues?.get(1)
                    
                    if (videoUrl != null && videoUrl.startsWith("http")) {
                        val isM3u8 = videoUrl.contains(".m3u8")
                        callback.invoke(
                            newExtractorLink(
                                this.name,
                                this.name,
                                videoUrl,
                                INFER_TYPE
                            ) {
                                this.referer = mainUrl
                                this.quality = Qualities.P1080.value
                                this.isM3u8 = isM3u8
                            }
                        )
                        return
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback
        }
        loadExtractor(url, referer, subtitleCallback, callback)
    }
}

class Voe : ExtractorApi() {
    override val name: String = "Voe"
    override val mainUrl: String = "https://voe.sx"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val document = app.get(url).document
            val scripts = document.select("script")
            for (script in scripts) {
                val data = script.data()
                if (data.contains("player.src") || data.contains("sources")) {
                    val srcRegex = Regex("""src:\s*['"]([^'"]+)['"]""")
                    val match = srcRegex.find(data)
                    val videoUrl = match?.groupValues?.get(1)
                    
                    if (videoUrl != null && videoUrl.startsWith("http")) {
                        val isM3u8 = videoUrl.contains(".m3u8")
                        callback.invoke(
                            newExtractorLink(
                                this.name,
                                this.name,
                                videoUrl,
                                INFER_TYPE
                            ) {
                                this.referer = mainUrl
                                this.quality = Qualities.P1080.value
                                this.isM3u8 = isM3u8
                            }
                        )
                        return
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback
        }
        loadExtractor(url, referer, subtitleCallback, callback)
    }
}
