package com.Pencurimovie

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.ExtractorLinkType

// Extractor untuk server-server Pencurimovie
// Server yang digunakan: do7go.com, dhcplay.com, listeamed.net, voe.sx

class Do7go : ExtractorApi() {
    override var name = "Do7go"
    override var mainUrl = "https://do7go.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        try {
            val document = app.get(url, referer = referer ?: mainUrl).document
            val script = document.selectFirst("script:containsData(player.src)")?.data()
            
            if (script != null) {
                val srcRegex = Regex("""player\.src\(\s*\{\s*src:\s*['"]([^'"]+)['"]""")
                val match = srcRegex.find(script)
                val videoUrl = match?.groupValues?.get(1)
                
                if (videoUrl != null) {
                    val isM3u8 = videoUrl.contains(".m3u8")
                    callback(
                        newExtractorLink(
                            name,
                            name,
                            videoUrl,
                            referer ?: mainUrl,
                            if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        )
                    )
                    return
                }
            }
            
            // Fallback: coba extract dari iframe
            val iframeRegex = Regex("""<iframe[^>]*src=['"]([^'"]+)['"]""")
            val iframeMatch = iframeRegex.find(script ?: "")
            val iframeUrl = iframeMatch?.groupValues?.get(1)
            
            if (iframeUrl != null && iframeUrl.startsWith("http")) {
                loadExtractor(iframeUrl, referer, subtitleCallback, callback)
            }
        } catch (e: Exception) {
            // Jika gagal, coba langsung load dengan loadExtractor
            loadExtractor(url, referer, subtitleCallback, callback)
        }
    }
}

class Dhcplay : ExtractorApi() {
    override var name = "Dhcplay"
    override var mainUrl = "https://dhcplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        try {
            val document = app.get(url, referer = referer ?: mainUrl).document
            val script = document.selectFirst("script:containsData(player.src)")?.data()
            
            if (script != null) {
                val srcRegex = Regex("""player\.src\(\s*\{\s*src:\s*['"]([^'"]+)['"]""")
                val match = srcRegex.find(script)
                val videoUrl = match?.groupValues?.get(1)
                
                if (videoUrl != null) {
                    val isM3u8 = videoUrl.contains(".m3u8")
                    callback(
                        newExtractorLink(
                            name,
                            name,
                            videoUrl,
                            referer ?: mainUrl,
                            if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        )
                    )
                    return
                }
            }
            
            // Fallback
            loadExtractor(url, referer, subtitleCallback, callback)
        } catch (e: Exception) {
            loadExtractor(url, referer, subtitleCallback, callback)
        }
    }
}

class Listeamed : ExtractorApi() {
    override var name = "Listeamed"
    override var mainUrl = "https://listeamed.net"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        try {
            val document = app.get(url, referer = referer ?: mainUrl).document
            val script = document.selectFirst("script:containsData(player.src)")?.data()
            
            if (script != null) {
                val srcRegex = Regex("""player\.src\(\s*\{\s*src:\s*['"]([^'"]+)['"]""")
                val match = srcRegex.find(script)
                val videoUrl = match?.groupValues?.get(1)
                
                if (videoUrl != null) {
                    val isM3u8 = videoUrl.contains(".m3u8")
                    callback(
                        newExtractorLink(
                            name,
                            name,
                            videoUrl,
                            referer ?: mainUrl,
                            if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        )
                    )
                    return
                }
            }
            
            // Fallback
            loadExtractor(url, referer, subtitleCallback, callback)
        } catch (e: Exception) {
            loadExtractor(url, referer, subtitleCallback, callback)
        }
    }
}

class Voe : ExtractorApi() {
    override var name = "Voe"
    override var mainUrl = "https://voe.sx"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        try {
            val document = app.get(url, referer = referer ?: mainUrl).document
            val script = document.selectFirst("script:containsData(player.src)")?.data()
            
            if (script != null) {
                val srcRegex = Regex("""player\.src\(\s*\{\s*src:\s*['"]([^'"]+)['"]""")
                val match = srcRegex.find(script)
                val videoUrl = match?.groupValues?.get(1)
                
                if (videoUrl != null) {
                    val isM3u8 = videoUrl.contains(".m3u8")
                    callback(
                        newExtractorLink(
                            name,
                            name,
                            videoUrl,
                            referer ?: mainUrl,
                            if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        )
                    )
                    return
                }
            }
            
            // Fallback
            loadExtractor(url, referer, subtitleCallback, callback)
        } catch (e: Exception) {
            loadExtractor(url, referer, subtitleCallback, callback)
        }
    }
}
