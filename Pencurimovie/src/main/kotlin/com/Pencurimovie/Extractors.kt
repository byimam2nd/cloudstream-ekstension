package com.Pencurimovie

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities

// Extractor untuk server-server Pencurimovie
// Server: do7go.com, dhcplay.com, listeamed.net, voe.sx

@Suppress("DEPRECATION")
class Do7go : ExtractorApi() {
    override var name = "Do7go"
    override var mainUrl = "https://do7go.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
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
                        callback(
                            ExtractorLink(
                                name,
                                name,
                                videoUrl,
                                mainUrl,
                                Qualities.P1080.value,
                                if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            )
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

@Suppress("DEPRECATION")
class Dhcplay : ExtractorApi() {
    override var name = "Dhcplay"
    override var mainUrl = "https://dhcplay.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
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
                        callback(
                            ExtractorLink(
                                name,
                                name,
                                videoUrl,
                                mainUrl,
                                Qualities.P1080.value,
                                if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            )
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

@Suppress("DEPRECATION")
class Listeamed : ExtractorApi() {
    override var name = "Listeamed"
    override var mainUrl = "https://listeamed.net"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
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
                        callback(
                            ExtractorLink(
                                name,
                                name,
                                videoUrl,
                                mainUrl,
                                Qualities.P1080.value,
                                if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            )
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

@Suppress("DEPRECATION")
class Voe : ExtractorApi() {
    override var name = "Voe"
    override var mainUrl = "https://voe.sx"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
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
                        callback(
                            ExtractorLink(
                                name,
                                name,
                                videoUrl,
                                mainUrl,
                                Qualities.P1080.value,
                                if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            )
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
