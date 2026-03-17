package com.Pencurimovie

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidStack

// ========================================
// EXTRACTOR KHUSUS UNTUK SERVER PENCURIMOVIE
// ========================================

// Dhcplay menggunakan StreamWish extractor
class Dhcplay : StreamWishExtractor() {
    override var name = "DHC Play"
    override var mainUrl = "https://dhcplay.com"
}

// Listeamed menggunakan VidStack extractor  
class Listeamed : VidStack() {
    override var name = "Listeamed"
    override var mainUrl = "https://listeamed.net"
    override var requiresReferer = true
}

// Do7go menggunakan StreamWish extractor
class Do7go : StreamWishExtractor() {
    override var name = "Do7go"
    override var mainUrl = "https://do7go.com"
}

// Voe menggunakan custom extractor
class Voe : ExtractorApi() {
    override val name = "Voe"
    override val mainUrl = "https://voe.sx"
    override val requiresReferer = false

    private val pattern =
        Regex("""(?://|\.)(?:voe)\.(?:sx|com)/[ed]/([0-9A-Za-z]+)""")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val mediaId = pattern.find(url)?.groupValues?.get(1) ?: return
        val pageUrl = "$mainUrl/e/$mediaId"
        
        val html = app.get(pageUrl).text
        
        // Extract packed JavaScript
        val script = getAndUnpack(html)
        
        // Extract m3u8 from script
        Regex("""['"]hls['"]\s*:\s*['"]([^'"]+)['"]""").find(script)?.groupValues?.get(1)?.let { hlsUrl ->
            val decodedUrl = hlsUrl.replace("\\/", "/")
            generateM3u8(
                name,
                decodedUrl,
                "$mainUrl/",
                callback
            )
        }
    }
}
