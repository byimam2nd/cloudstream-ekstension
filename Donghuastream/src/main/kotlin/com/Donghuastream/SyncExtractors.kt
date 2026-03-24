// ========================================
// MASTER EXTRACTORS COLLECTION - v2.0
// Kumpulan 75+ Extractor untuk CloudStream
// ========================================
// Source: ExtCloud + cloudstream-ekstension + CloudStream Built-in
// Last Updated: 2026-03-18
// Maintainer: Phisher98
// ========================================
 
// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/MasterExtractors.kt
// File: SyncExtractors.kt
// ========================================
package com.Donghuastream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.extractors.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.json.JSONObject
import java.net.URI
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import android.annotation.SuppressLint

// Import Master utilities untuk performa optimal
// Note: Setelah sync, HttpClientFactory dan CompiledRegexPatterns akan berada di package yang sama
import com.Donghuastream.HttpClientFactory
import com.Donghuastream.CompiledRegexPatterns
// Setelah sync menjadi: import com.{Module}.HttpClientFactory (auto-adjusted by sync script)

// ========================================
// HELPER FUNCTIONS
// ========================================

// Base64 helper (cross-platform)
fun base64DecodeStr(str: String): String {
    return try {
        String(java.util.Base64.getDecoder().decode(str))
    } catch (e: Exception) {
        str
    }
}

// ========================================
// PERFORMANCE OPTIMIZATIONS (2026-03-24)
// ========================================
// Helper objects dan extension functions untuk meningkatkan performa
// tanpa mengubah CloudStream API structure
// ========================================

/**
 * Shared constants untuk semua extractors
 * Mengurangi alokasi objek berulang dan memastikan konsistensi
 */
object ExtractorConstants {
    // Standard User-Agents
    const val USER_AGENT_CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    const val USER_AGENT_FIREFOX = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"
    
    // Standard Headers (immutable, reused)
    val DEFAULT_HEADERS = mapOf(
        "Accept" to "*/*",
        "Accept-Encoding" to "gzip, deflate, br",
        "Accept-Language" to "en-US,en;q=0.9,id;q=0.8",
        "Connection" to "keep-alive",
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "cross-site"
    )
    
    // Quality mappings (O(1) lookup vs string manipulation)
    val QUALITY_MAP = mapOf(
        "MOBILE" to 144,
        "LOWEST" to 240,
        "LOW" to 360,
        "SD" to 480,
        "HD" to 720,
        "FULL" to 1080,
        "QUAD" to 1440,
        "ULTRA" to 2160,
        "4K" to 2160,
        "2K" to 1440,
        "FHD" to 1080,
        "P2160" to 2160,
        "P1440" to 1440,
        "P1080" to 1080,
        "P720" to 720,
        "P480" to 480,
        "P360" to 360,
        "P240" to 240,
        "P144" to 144
    )
}

/**
 * Helper functions untuk operasi yang sering digunakan
 * Mengurangi duplikasi kode dan memastikan konsistensi
 */
object ExtractorHelpers {
    /**
     * Transform embed URL ke video URL dengan pola standar
     * @param url URL yang akan ditransform
     * @return URL yang sudah ditransform
     */
    fun transformEmbedUrl(url: String): String {
        return when {
            url.contains("/d/") -> url.replace("/d/", "/v/")
            url.contains("/download/") -> url.replace("/download/", "/v/")
            url.contains("/file/") -> url.replace("/file/", "/v/")
            else -> url.replace("/f/", "/v/")
        }
    }
    
    /**
     * Get standard headers dengan User-Agent yang konsisten per domain
     * @param domain Domain untuk User-Agent session
     * @return Map headers yang siap digunakan
     */
    fun getStandardHeaders(domain: String): Map<String, String> {
        return ExtractorConstants.DEFAULT_HEADERS + 
            ("User-Agent" to HttpClientFactory.getUserAgentForDomain(domain)) +
            ("Origin" to domain)
    }
    
    /**
     * Extract script dari HTML response dengan multiple fallback methods
     * @param html HTML content
     * @return Script string atau null jika tidak ditemukan
     */
    fun extractScriptFromHtml(html: String): String? {
        // Method 1: Unpack packed JavaScript
        val packed = getPacked(html)
        if (!packed.isNullOrEmpty()) {
            return getAndUnpack(html).substringAfter("var links")
        }
        
        // Method 2: Look for sources in script tags
        val doc = org.jsoup.Jsoup.parse(html)
        return doc.selectFirst("script:containsData(sources:)")?.data()
            ?: doc.selectFirst("script:containsData(file:)")?.data()
    }
    
    /**
     * Extract video URLs dari meta tags
     * @param html HTML content
     * @return Video URL atau null
     */
    fun extractVideoFromMeta(html: String): String? {
        val doc = org.jsoup.Jsoup.parse(html)
        val videoUrl = doc.selectFirst("meta[property=og:video]")?.attr("content")
        
        if (!videoUrl.isNullOrEmpty() && 
            (videoUrl.contains(".m3u8") || videoUrl.contains(".mp4"))) {
            return videoUrl
        }
        
        return null
    }
}

// ========================================
// EXTENSION FUNCTIONS
// ========================================

/**
 * Extension function untuk String → Quality mapping
 * Menggunakan map lookup (O(1)) vs string manipulation (O(n))
 */
fun String.toQuality(): Int {
    val upper = this.uppercase()
    
    // Check direct mappings first (fastest)
    ExtractorConstants.QUALITY_MAP[upper]?.let { return it }
    
    // Check patterns
    return when {
        contains("4K") || contains("2160") -> 2160
        contains("1440") || contains("2K") -> 1440
        contains("1080") || contains("FHD") || contains("FULL") -> 1080
        contains("720") || contains("HD") -> 720
        contains("480") || contains("SD") -> 480
        contains("360") -> 360
        contains("240") || contains("LOW") -> 240
        contains("144") || contains("MOBILE") -> 144
        else -> 0
    }
}

/**
 * Extension function untuk ExtractorApi → fix URL dengan base URL extractor
 */
fun ExtractorApi.fixUrl(url: String): String {
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    if (url.startsWith("//")) return "https:$url"
    if (url.startsWith("/")) return "$mainUrl$url"
    return "$mainUrl/$url"
}

/**
 * Extension function untuk quality detection dari nama
 */
fun ExtractorApi.detectQualityFromName(name: String): Int {
    return name.toQuality()
}

// ========================================
// STREAMWISH BASED EXTRACTORS (From ExtCloud/Dutamovie)
// ========================================

class Movearnpre : Dingtezuni() {
    override var name = "Movearnpre"
    override var mainUrl = "https://movearnpre.com"
}

class Minochinos : Dingtezuni() {
    override var name = "Minochinos"
    override var mainUrl = "https://minochinos.com"
}

class Mivalyo : Dingtezuni() {
    override var name = "Mivalyo"
    override var mainUrl = "https://mivalyo.com"
}

class Ryderjet : Dingtezuni() {
    override var name = "Ryderjet"
    override var mainUrl = "https://ryderjet.com"
}

class Bingezove : Dingtezuni() {
    override var name = "Bingezove"
    override var mainUrl = "https://bingezove.com"
}

open class Dingtezuni : ExtractorApi() {
    override val name = "Dingtezuni"
    override val mainUrl = "https://dingtezuni.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // ✅ OPTIMIZED: Gunakan helper untuk headers (reuse + session UA)
        val headers = ExtractorHelpers.getStandardHeaders(mainUrl)

        try {
            // ✅ OPTIMIZED: Gunakan helper untuk transform URL
            val embedUrl = ExtractorHelpers.transformEmbedUrl(url)
            val response = app.get(embedUrl, referer = referer)

            // ✅ OPTIMIZED: Gunakan helper untuk extract script
            var script = ExtractorHelpers.extractScriptFromHtml(response.text)

            // Fallback ke meta tags
            if (script == null) {
                val metaVideo = ExtractorHelpers.extractVideoFromMeta(response.text)
                if (metaVideo != null) {
                    script = "sources:[{file:\"$metaVideo\"}]"
                }
            }

            script ?: return

            // ✅ OPTIMIZED: Gunakan CompiledRegexPatterns (pre-compiled regex)
            val extractedUrls = CompiledRegexPatterns.extractAllM3u8Urls(script, mainUrl)

            // Generate extractor links untuk semua URLs
            extractedUrls.forEach { videoUrl ->
                try {
                    generateM3u8(
                        name,
                        videoUrl,
                        referer = "$mainUrl/",
                        headers = headers
                    ).forEach(callback)
                } catch (e: Exception) {
                    // Skip invalid URLs
                }
            }
        } catch (e: Exception) {
            throw Exception("Failed to extract video from $url: ${e.message}")
        }
    }

    /**
     * Transform embed URL ke video URL
     * ✅ OPTIMIZED: Gunakan helper function
     */
    private fun getEmbedUrl(url: String): String {
        return ExtractorHelpers.transformEmbedUrl(url)
    }
}

class Hglink : StreamWishExtractor() {
    override val name = "Hglink"
    override val mainUrl = "https://hglink.to"
}

class Ghbrisk : StreamWishExtractor() {
    override val name = "Ghbrisk"
    override val mainUrl = "https://ghbrisk.com"
}

class Dhcplay : StreamWishExtractor() {
    override var name = "DHC Play"
    override var mainUrl = "https://dhcplay.com"
}

// ========================================
// VIDSTACK BASED EXTRACTORS (From ExtCloud/Dutamovie)
// ========================================

class Streamcasthub : VidStack() {
    override var name = "Streamcasthub"
    override var mainUrl = "https://live.streamcasthub.store"
    override var requiresReferer = true
}

class Dm21embed : VidStack() {
    override var name = "Dm21embed"
    override var mainUrl = "https://dm21.embed4me.vip"
    override var requiresReferer = true
}

class Dm21upns : VidStack() {
    override var name = "Dm21upns"
    override var mainUrl = "https://dm21.upns.live"
    override var requiresReferer = true
}

class Pm21p2p : VidStack() {
    override var name = "Pm21p2p"
    override var mainUrl = "https://pm21.p2pplay.pro"
    override var requiresReferer = true
}

class Dm21 : VidStack() {
    override var name = "Dm21"
    override var mainUrl = "https://dm21.embed4me.vip"
    override var requiresReferer = true
}

class Meplayer : VidStack() {
    override var name = "Meplayer"
    override var mainUrl = "https://video.4meplayer.com"
    override var requiresReferer = true
}

// ========================================
// DINTEZUVIO EXTRACTOR (From ExtCloud/Dutamovie)
// ========================================

open class Dintezuvio : ExtractorApi() {
    override val name = "Dintezuvio"
    override val mainUrl = "https://dintezuvio.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // ✅ OPTIMIZED: Gunakan helper untuk headers (reuse + session UA)
        val headers = ExtractorHelpers.getStandardHeaders(mainUrl)

        try {
            // ✅ OPTIMIZED: Gunakan helper untuk transform URL
            val embedUrl = ExtractorHelpers.transformEmbedUrl(url)
            val response = app.get(embedUrl, referer = referer)

            // ✅ OPTIMIZED: Gunakan helper untuk extract script
            var script = ExtractorHelpers.extractScriptFromHtml(response.text)

            // Fallback ke meta tags
            if (script == null) {
                val metaVideo = ExtractorHelpers.extractVideoFromMeta(response.text)
                if (metaVideo != null) {
                    script = "sources:[{file:\"$metaVideo\"}]"
                }
            }

            script ?: return

            // ✅ OPTIMIZED: Gunakan CompiledRegexPatterns (pre-compiled regex)
            val extractedUrls = CompiledRegexPatterns.extractAllM3u8Urls(script, mainUrl)

            // Generate extractor links untuk semua URLs
            extractedUrls.forEach { videoUrl ->
                try {
                    generateM3u8(
                        name,
                        videoUrl,
                        referer = "$mainUrl/",
                        headers = headers
                    ).forEach(callback)
                } catch (e: Exception) {
                    // Skip invalid URLs
                }
            }
        } catch (e: Exception) {
            throw Exception("Dintezuvio: Failed to extract video from $url: ${e.message}")
        }
    }

    /**
     * Transform embed URL ke video URL
     * ✅ OPTIMIZED: Gunakan helper function
     */
    private fun getEmbedUrl(url: String): String {
        return ExtractorHelpers.transformEmbedUrl(url)
    }
}

// ========================================
// VEEV EXTRACTOR (From ExtCloud/Dutamovie)
// ========================================

class Veev : ExtractorApi() {
    override val name = "Veev"
    override val mainUrl = "https://veev.to"
    override val requiresReferer = false

    private val pattern =
        Regex("""(?://|\.)(?:veev|kinoger|poophq|doods)\.(?:to|pw|com)/[ed]/([0-9A-Za-z]+)""")

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0"
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val mediaId = pattern.find(url)?.groupValues?.get(1) ?: return
        val pageUrl = "$mainUrl/e/$mediaId"
        val html = app.get(
            pageUrl,
            headers = mapOf("User-Agent" to DEFAULT_UA)
        ).text

        val encRegex = Regex("""[.\s'](?:fc|_vvto\[[^]]*)(?:['\]]*)?\s*[:=]\s*['"]([^'"]+)""")
        val foundValues = encRegex.findAll(html).map { it.groupValues[1] }.toList()

        if (foundValues.isEmpty()) return

        for (f in foundValues.reversed()) {
            val ch = veevDecode(f)
            if (ch == f) continue

            val dlUrl = "$mainUrl/dl?op=player_api&cmd=gi&file_code=$mediaId&r=$mainUrl&ch=$ch&ie=1"
            val responseText = app.get(dlUrl, headers = mapOf("User-Agent" to DEFAULT_UA)).text

            val json = try {
                JSONObject(responseText)
            } catch (_: Exception) {
                continue
            }
            val file = json.optJSONObject("file") ?: continue

            if (file.optString("file_status") != "OK") continue

            val dv = file.getJSONArray("dv").getJSONObject(0).getString("s")
            val decoded = decodeUrl(veevDecode(dv), buildArray(ch)[0])

            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    decoded,
                    INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
            return
        }
    }

    private fun veevDecode(etext: String): String {
        val result = StringBuilder()
        val lut = HashMap<Int, String>()
        var n = 256
        var c = etext[0].toString()
        result.append(c)

        for (char in etext.drop(1)) {
            val code = char.code
            val nc = if (code < 256) char.toString() else lut[code] ?: (c + c[0])
            result.append(nc)
            lut[n++] = c + nc[0]
            c = nc
        }
        return result.toString()
    }

    private fun jsInt(x: Char): Int = x.digitToIntOrNull() ?: 0

    private fun buildArray(encoded: String): List<List<Int>> {
        val result = mutableListOf<List<Int>>()
        val it = encoded.iterator()
        fun nextIntOrZero(): Int = if (it.hasNext()) jsInt(it.nextChar()) else 0
        var count = nextIntOrZero()
        while (count != 0) {
            val row = mutableListOf<Int>()
            repeat(count) {
                row.add(nextIntOrZero())
            }
            result.add(row.reversed())
            count = nextIntOrZero()
        }
        return result
    }

    private fun decodeUrl(encoded: String, rules: List<Int>): String {
        var text = encoded
        for (r in rules) {
            if (r == 1) text = text.reversed()
            val arr = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            text = arr.toString(Charsets.UTF_8).replace("dXRmOA==", "")
        }
        return text
    }
}

// ========================================
// PENCURIMOVIE EXTRACTORS
// ========================================

class Do7go : StreamWishExtractor() {
    override var name = "Do7go"
    override var mainUrl = "https://do7go.com"
}

class Listeamed : VidStack() {
    override var name = "Listeamed"
    override var mainUrl = "https://listeamed.net"
    override var requiresReferer = true
}

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
        val script = getAndUnpack(html)

        Regex("""['"]hls['"]\s*:\s*['"]([^'"]+)['"]""").find(script)?.groupValues?.get(1)?.let { hlsUrl ->
            val decodedUrl = hlsUrl.replace("\\/", "/")
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    decodedUrl,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}

class Dsvplay : DoodLaExtractor() {
    override var mainUrl = "https://dsvplay.com"
}

// ========================================
// OK.RU EXTRACTORS (From ExtCloud/AnichinMoe)
// ========================================

class OkRuSSL : Odnoklassniki() {
    override var name = "OkRuSSL"
    override var mainUrl = "https://ok.ru"
}

class OkRuHTTP : Odnoklassniki() {
    override var name = "OkRuHTTP"
    override var mainUrl = "http://ok.ru"
}

open class Odnoklassniki : ExtractorApi() {
    override val name = "Odnoklassniki"
    override val mainUrl = "https://odnoklassniki.ru"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // ✅ OPTIMIZED: Gunakan helper untuk headers (reuse + session UA)
        val headers = ExtractorHelpers.getStandardHeaders(mainUrl)
        
        val embedUrl = url.replace("/video/", "/videoembed/")
        val videoReq = app.get(embedUrl, headers = headers).text.replace("\\&quot;", "\"")
            .replace("\\\\", "\\")
            .replace(Regex("""\\u([0-9A-Fa-f]{4})""")) { matchResult ->
                Integer.parseInt(matchResult.groupValues[1], 16).toChar().toString()
            }

        val videosStr = Regex("""\"videos\":(\[[^]]*])""").find(videoReq)?.groupValues?.get(1)
            ?: return
        val videos = tryParseJson<List<OkRuVideo>>(videosStr) ?: return

        for (video in videos) {
            val videoUrl = if (video.url.startsWith("//")) "https:${video.url}" else video.url

            // ✅ OPTIMIZED: Gunakan extension function untuk quality detection
            val quality = video.name.toQuality()

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = videoUrl,
                    type = INFER_TYPE
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = quality
                    this.headers = headers
                }
            )
        }
    }

    data class OkRuVideo(
        @JsonProperty("name") val name: String,
        @JsonProperty("url") val url: String,
    )
}

// ========================================
// RUMBLE EXTRACTOR (From ExtCloud/AnichinMoe)
// ========================================

class Rumble : ExtractorApi() {
    override var name = "Rumble"
    override var mainUrl = "https://rumble.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer ?: "$mainUrl/")
        val scriptData = response.document.selectFirst("script:containsData(mp4)")?.data()
            ?.substringAfter("{\"mp4")?.substringBefore("\"evt\":{")
        if (scriptData == null) return

        val regex = """"url":"(.*?)"|h":(.*?)\}""".toRegex()
        val matches = regex.findAll(scriptData)

        val processedUrls = mutableSetOf<String>()

        for (match in matches) {
            val rawUrl = match.groupValues[1]
            if (rawUrl.isBlank()) continue

            val cleanedUrl = rawUrl.replace("\\/", "/")
            if (!cleanedUrl.contains("rumble.com")) continue
            if (!cleanedUrl.endsWith(".m3u8")) continue
            if (!processedUrls.add(cleanedUrl)) continue

            val m3u8Response = app.get(cleanedUrl)
            val variantCount = "#EXT-X-STREAM-INF".toRegex().findAll(m3u8Response.text).count()

            if (variantCount > 1) {
                callback.invoke(
                    newExtractorLink(
                        this@Rumble.name,
                        "Rumble",
                        cleanedUrl,
                        ExtractorLinkType.M3U8
                    )
                )
                break
            }
        }
    }
}

// ========================================
// STREAMRUBY EXTRACTOR (From ExtCloud/AnichinMoe)
// ========================================

open class StreamRuby : ExtractorApi() {
    override val name = "StreamRuby"
    override val mainUrl = "https://rubyvidhub.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val id = "embed-([a-zA-Z0-9]+)\\.html".toRegex().find(url)?.groupValues?.get(1) ?: return

            val response = app.post(
                "$mainUrl/dl", data = mapOf(
                    "op" to "embed",
                    "file_code" to id,
                    "auto" to "1",
                    "referer" to "",
                ), referer = referer
            )

            // ✅ OPTIMIZED: Gunakan helper untuk extract script
            var script = ExtractorHelpers.extractScriptFromHtml(response.text)

            // Fallback ke meta tags
            if (script == null) {
                val metaVideo = ExtractorHelpers.extractVideoFromMeta(response.text)
                if (metaVideo != null && metaVideo.contains(".m3u8")) {
                    script = "file:\"$metaVideo\""
                }
            }

            script ?: return

            // ✅ OPTIMIZED: Gunakan CompiledRegexPatterns (pre-compiled regex)
            val m3u8 = CompiledRegexPatterns.extractAllM3u8Urls(script, mainUrl).firstOrNull()

            if (m3u8 != null) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = m3u8,
                        type = ExtractorLinkType.M3U8,
                    ) {
                        quality = Qualities.Unknown.value
                        this.referer = mainUrl
                    }
                )
            }
        } catch (e: Exception) {
            throw Exception("StreamRuby: Failed to extract video from $url: ${e.message}")
        }
    }
}

class Svanila : StreamRuby() {
    override var name = "svanila"
    override var mainUrl = "https://streamruby.net"
}

class Svilla : StreamRuby() {
    override var name = "svilla"
    override var mainUrl = "https://streamruby.com"
}

// ========================================
// VIDGUARD EXTRACTOR (From ExtCloud/AnichinMoe)
// ========================================

class Vidguardto1 : Vidguardto() {
    override val mainUrl = "https://bembed.net"
}

class Vidguardto2 : Vidguardto() {
    override val mainUrl = "https://listeamed.net"
}

class Vidguardto3 : Vidguardto() {
    override val mainUrl = "https://vgfplay.com"
}

open class Vidguardto : ExtractorApi() {
    override val name = "Vidguard"
    override val mainUrl = "https://vidguard.to"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val res = app.get(getEmbedUrl(url), referer = referer)
            val resc = res.document.select("script:containsData(eval)").firstOrNull()?.data()
            
            resc?.let { script ->
                try {
                    val jsonStr2 = AppUtils.tryParseJson<SvgObject>(runJS2(script)) ?: return
                    val watchlink = sigDecode(jsonStr2.stream)

                    callback.invoke(
                        newExtractorLink(
                            this.name,
                            name,
                            watchlink,
                        ) {
                            this.referer = mainUrl
                        }
                    )
                } catch (e: Exception) {
                    // Fallback: Try to extract m3u8 directly from page
                    val directM3u8 = res.document.selectFirst("meta[property=og:video]")?.attr("content")
                    if (!directM3u8.isNullOrEmpty() && directM3u8.contains(".m3u8")) {
                        callback.invoke(
                            newExtractorLink(
                                "${this.name} Direct",
                                "${this.name} Direct",
                                directM3u8,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.referer = mainUrl
                            }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Log error but don't crash
            throw Exception("Vidguard: Failed to extract video from $url: ${e.message}")
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun sigDecode(url: String): String {
        val sig = url.split("sig=")[1].split("&")[0]
        val t = sig.chunked(2)
            .joinToString("") { (Integer.parseInt(it, 16) xor 2).toChar().toString() }
            .let {
                val padding = when (it.length % 4) {
                    2 -> "=="
                    3 -> "="
                    else -> ""
                }
                String(Base64.decode((it + padding).toByteArray(Charsets.UTF_8)))
            }
            .dropLast(5)
            .reversed()
            .toCharArray()
            .apply {
                for (i in indices step 2) {
                    if (i + 1 < size) {
                        this[i] = this[i + 1].also { this[i + 1] = this[i] }
                    }
                }
            }
            .concatToString()
            .dropLast(5)
        return url.replace(sig, t)
    }

    private fun runJS2(hideMyHtmlContent: String): String {
        var result = ""
        val r = Runnable {
            val rhino = Context.enter()
            rhino.optimizationLevel = -1
            val scope: Scriptable = rhino.initSafeStandardObjects()
            scope.put("window", scope, scope)
            try {
                rhino.evaluateString(
                    scope,
                    hideMyHtmlContent,
                    "JavaScript",
                    1,
                    null
                )
                val svgObject = scope.get("svg", scope)
                result = if (svgObject is NativeObject) {
                    NativeJSON.stringify(
                        Context.getCurrentContext(),
                        scope,
                        svgObject,
                        null,
                        null
                    ).toString()
                } else {
                    Context.toString(svgObject)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                Context.exit()
            }
        }
        val t = Thread(ThreadGroup("A"), r, "thread_rhino", 8 * 1024 * 1024)
        t.start()
        t.join()
        t.interrupt()
        return result
    }

    private fun getEmbedUrl(url: String): String {
        return url.takeIf { it.contains("/d/") || it.contains("/v/") }
            ?.replace("/d/", "/e/")?.replace("/v/", "/e/") ?: url
    }

    data class SvgObject(
        val stream: String,
        val hash: String
    )
}

// ========================================
// ARCHIVD EXTRACTOR (From ExtCloud/Animasu)
// ========================================

class Archivd : ExtractorApi() {
    override val name: String = "Archivd"
    override val mainUrl: String = "https://archivd.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).document
        val json = res.select("div#app").attr("data-page")
        val video = tryParseJson<Sources>(json)?.props?.datas?.data?.link?.media

        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                video ?: return,
                INFER_TYPE
            ) {
                this.referer = "$mainUrl/"
            }
        )
    }

    data class Link(
        @JsonProperty("media") val media: String? = null,
    )

    data class Data(
        @JsonProperty("link") val link: Link? = null,
    )

    data class Datas(
        @JsonProperty("data") val data: Data? = null,
    )

    data class Props(
        @JsonProperty("datas") val datas: Datas? = null,
    )

    data class Sources(
        @JsonProperty("props") val props: Props? = null,
    )
}

// ========================================
// USERVIDEO EXTRACTOR (From ExtCloud/Animasu)
// ========================================

class Newuservideo : ExtractorApi() {
    override val name: String = "Uservideo"
    override val mainUrl: String = "https://new.uservideo.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val iframe = app.get(url, referer = referer).document.select("iframe#videoFrame").attr("src")
        val doc = app.get(iframe, referer = "$mainUrl/").text
        val json = "VIDEO_CONFIG\\s?=\\s?(.*)".toRegex().find(doc)?.groupValues?.get(1)

        tryParseJson<Sources>(json)?.streams?.map {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    it.playUrl ?: return@map,
                    INFER_TYPE
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = when (it.formatId) {
                        18 -> Qualities.P360.value
                        22 -> Qualities.P720.value
                        else -> Qualities.Unknown.value
                    }
                }
            )
        }
    }

    data class Streams(
        @JsonProperty("play_url") val playUrl: String? = null,
        @JsonProperty("format_id") val formatId: Int? = null,
    )

    data class Sources(
        @JsonProperty("streams") val streams: ArrayList<Streams>? = null,
    )
}

// ========================================
// VIDHIDEPRO EXTRACTOR (From ExtCloud/Animasu)
// ========================================

class Vidhidepro : Filesim() {
    override val mainUrl = "https://vidhidepro.com"
    override val name = "Vidhidepro"
}

// ========================================
// DAILYMOTION EXTRACTOR (From ExtCloud/AnichinMoe)
// ========================================

class Dailymotion : ExtractorApi() {
    override val name = "Dailymotion"
    override val mainUrl = "https://www.dailymotion.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url).text
        val jsonData = response.substringAfter("playerConfig\\\"=").substringBefore("\\", "")
        val json = try {
            org.json.JSONObject(jsonData)
        } catch (e: Exception) {
            return
        }

        val qualityArray = json.optJSONArray("qualities")
        val autoPlay = qualityArray?.getJSONObject(0)
        val autoUrl = autoPlay?.optString("url")

        if (autoUrl != null) {
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    autoUrl,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}

// ========================================
// JENIUSPLAY EXTRACTOR (From Idlix)
// ========================================

class Jeniusplay : ExtractorApi() {
    override val name = "Jeniusplay"
    override val mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).documentLarge
        val hash = url.split("/").last().substringAfter("data=")

        val m3uLink = app.post(
            url = "$mainUrl/player/index.php?data=$hash&do=getVideo",
            data = mapOf("hash" to hash, "r" to "$referer"),
            referer = referer,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsed<ResponseSource>().videoSource

        callback.invoke(
            newExtractorLink(
                name,
                name,
                url = m3uLink,
                ExtractorLinkType.M3U8
            )
        )

        document.select("script").map { script ->
            if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                val subData =
                    getAndUnpack(script.data()).substringAfter("\"tracks\":[").substringBefore("],")
                AppUtils.tryParseJson<List<Tracks>>("[$subData]")?.map { subtitle ->
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            getLanguage(subtitle.label ?: ""),
                            subtitle.file
                        )
                    )
                }
            }
        }
    }

    private fun getLanguage(str: String): String {
        return when {
            str.contains("indonesia", true) || str.contains("bahasa", true) -> "Indonesian"
            else -> str
        }
    }

    data class ResponseSource(
        @JsonProperty("videoSource") val videoSource: String
    )

    data class Tracks(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?
    )
}

// ========================================
// ARCHIVE.ORG EXTRACTOR (From ExtCloud/Donghub)
// ========================================

class ArchiveOrgExtractor : ExtractorApi() {
    override val name = "ArchiveOrg"
    override val mainUrl = "https://archive.org"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url).document
        val sources = response.select("script").find { script ->
            script.data().contains("\"sources\"")
        }?.data() ?: return

        val regex = Regex("""\"url\":\"(.*?)\"""")
        regex.findAll(sources).forEach { match ->
            val videoUrl = match.groupValues[1].replace("\\/", "/")
            if (videoUrl.contains(".mp4") || videoUrl.contains(".m3u8")) {
                callback.invoke(
                    newExtractorLink(
                        name,
                        name,
                        videoUrl,
                        if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else INFER_TYPE
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
    }
}

// ========================================
// MEGACLOUD EXTRACTOR (From phisher/HiAnime)
// ========================================

class Megacloud : ExtractorApi() {
    override val name = "Megacloud"
    override val mainUrl = "https://megacloud.blog"
    override val requiresReferer = false

    // Gunakan HttpClientFactory untuk koneksi optimal
    private val client = HttpClientFactory.getClient()
    private val gson = com.google.gson.Gson()

    private fun fetchUrl(url: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            val requestBuilder = okhttp3.Request.Builder().url(url)
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) response.body.string() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("NewApi")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val mainHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.5",
            "Accept-Encoding" to "gzip, deflate, br, zstd",
            "Origin" to mainUrl,
            "Referer" to "$mainUrl/",
            "Connection" to "keep-alive",
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache"
        )

        try {
            val headers = mapOf(
                "Accept" to "*/*",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to mainUrl
            )

            val id = url.substringAfterLast("/").substringBefore("?")
            val responseText = fetchUrl(url, headers) ?: throw Exception("Failed to fetch page")

            // Gunakan regex yang sudah di-compile untuk performa
            val nonceRegex = CompiledRegexPatterns.ENCRYPTED_BASE64
            val tripleIdRegex = Regex("""\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b""")
            
            val match1 = nonceRegex.find(responseText)
            val match2 = tripleIdRegex.find(responseText)
            val nonce = match1?.value ?: match2?.let { it.groupValues[1] + it.groupValues[2] + it.groupValues[3] }
            ?: throw Exception("Nonce not found")

            val apiUrl = "$mainUrl/embed-2/v3/e-1/getSources?id=$id&_k=$nonce"
            val responseJson = fetchUrl(apiUrl, headers) ?: throw Exception("Failed to fetch sources")
            val response = gson.fromJson(responseJson, MegacloudResponse::class.java)

            val encoded = response.sources.firstOrNull()?.file ?: throw Exception("No sources found")

            val keyJson = fetchUrl("https://raw.githubusercontent.com/yogesh-hacker/MegacloudKeys/refs/heads/main/keys.json")
            val key = keyJson?.let { gson.fromJson(it, Megakey::class.java)?.mega }

            val m3u8: String = if (encoded.contains(".m3u8")) {
                encoded
            } else {
                val decodeUrl =
                    "https://script.google.com/macros/s/AKfycbxHbYHbrGMXYD2-bC-C43D3njIbU-wGiYQuJL61H4vyy6YVXkybMNNEPJNPPuZrD1gRVA/exec"
                val fullUrl =
                    "$decodeUrl?encrypted_data=${java.net.URLEncoder.encode(encoded, "UTF-8")}" +
                            "&nonce=${java.net.URLEncoder.encode(nonce, "UTF-8")}" +
                            "&secret=${java.net.URLEncoder.encode(key ?: "", "UTF-8")}"

                val decryptedResponse = fetchUrl(fullUrl) ?: throw Exception("Failed to decrypt URL")
                // Gunakan pre-compiled pattern
                CompiledRegexPatterns.M3U8_JSON_VALUE.find(decryptedResponse)?.groupValues?.get(1)
                    ?: throw Exception("Video URL not found in decrypted response")
            }

            M3u8Helper.generateM3u8(name, m3u8, mainUrl, headers = mainHeaders).forEach(callback)

            response.tracks.forEach { track ->
                if (track.kind == "captions" || track.kind == "subtitles") {
                    subtitleCallback(newSubtitleFile(track.label, track.file))
                }
            }

        } catch (e: Exception) {
            // Ignore errors
        }
    }

    data class MegacloudResponse(
        val sources: List<Source>,
        val tracks: List<Track>,
        val encrypted: Boolean,
        val intro: Intro,
        val outro: Outro,
        val server: Long
    )

    data class Source(val file: String, val type: String)
    data class Track(val file: String, val label: String, val kind: String, val default: Boolean? = null)
    data class Intro(val start: Long, val end: Long)
    data class Outro(val start: Long, val end: Long)
    data class Megakey(val rabbit: String, val mega: String)
}

// ========================================
// GDRIVEPLAYER EXTRACTOR (From ExtCloud)
// ========================================

open class Gdriveplayer : ExtractorApi() {
    override val name = "Gdriveplayer"
    override val mainUrl = "https://gdriveplayer.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer=referer)
        val document = response.document
        
        val videoUrl = document.selectFirst("meta[property=og:video]")?.attr("content")
            ?: document.selectFirst("iframe")?.attr("src")
            ?: return
        
        callback.invoke(
            newExtractorLink(name, name, videoUrl, INFER_TYPE) {
                this.referer = "$mainUrl/"
            }
        )
    }
}

class Gdriveplayerto : Gdriveplayer() {
    override val name = "Gdriveplayer.to"
    override val mainUrl = "https://gdriveplayer.to"
}

class GDFlix : Gdriveplayer() {
    override val name = "GDFlix"
    override val mainUrl = "https://gdflix.co"
}

// ========================================
// FILE HOSTING EXTRACTORS (From ExtCloud)
// ========================================

class BloggerExtractor : ExtractorApi() {
    override val name = "Blogger"
    override val mainUrl = "https://www.blogger.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer=referer)
        val videoUrl = response.document.selectFirst("video source")?.attr("src") ?: return
        
        callback.invoke(
            newExtractorLink(name, name, videoUrl, INFER_TYPE) {
                this.referer = "$mainUrl/"
            }
        )
    }
}

class PixelDrainDev : ExtractorApi() {
    override val name = "PixelDrain"
    override val mainUrl = "https://pixeldrain.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fileId = url.split("/").last()
        val videoUrl = "$mainUrl/api/file/$fileId?download"
        
        callback.invoke(
            newExtractorLink(name, name, videoUrl, INFER_TYPE) {
                this.referer = "$mainUrl/"
            }
        )
    }
}

class Upload18com : ExtractorApi() {
    override val name = "Upload18"
    override val mainUrl = "https://www.upload18.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer=referer)
        val videoUrl = response.document.selectFirst("video source")?.attr("src") ?: return
        
        callback.invoke(
            newExtractorLink(name, name, videoUrl, INFER_TYPE) {
                this.referer = "$mainUrl/"
            }
        )
    }
}

class Dhtpre : Dingtezuni() {
    override val name = "Dhtpre"
    override val mainUrl = "https://dhtpre.com"
}

// ========================================
// PHASE 2: MEDIUM PRIORITY EXTRACTORS
// ========================================

// VidStack Variants (Indonesian sites)
class Fufaupns : VidStack() {
    override var name = "Fufaupns"
    override var mainUrl = "https://fufafilm.upns.pro"
    override var requiresReferer = true
}

class P2pplay : VidStack() {
    override var name = "P2pplay"
    override var mainUrl = "https://nf21.p2pplay.pro"
    override var requiresReferer = true
}

class Playerngefilm21 : VidStack() {
    override var name = "Playerngefilm21"
    override var mainUrl = "https://player.ngefilm21.com"
    override var requiresReferer = true
}

class Rpmvid : VidStack() {
    override var name = "Rpmvid"
    override var mainUrl = "https://rpmvid.com"
    override var requiresReferer = true
}

// Hxfile Variant (Anime sites)
open class Hxfile : ExtractorApi() {
    override val name = "Hxfile"
    override val mainUrl = "https://hxfile.co"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer=referer)
        val document = response.document
        
        val videoUrl = document.selectFirst("video source")?.attr("src")
            ?: Regex("""file:\s*['"]([^'"]+)['"]""").find(response.text)?.groupValues?.get(1)
            ?: return
        
        callback.invoke(
            newExtractorLink(name, name, videoUrl, INFER_TYPE) {
                this.referer = "$mainUrl/"
            }
        )
    }
}

class Xshotcok : Hxfile() {
    override val name = "Xshotcok"
    override val mainUrl = "https://xshotcok.com"
}

// ========================================
// LAYARKACA CUSTOM EXTRACTORS
// ========================================
// Custom extractors for LayarKaca21 (from ExtCloud)
// ========================================

class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}

open class Hownetwork : ExtractorApi() {
    override val name = "Hownetwork"
    override val mainUrl = "https://stream.hownetwork.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfter("id=")
        val response = app.post(
            "$mainUrl/api2.php?id=$id",
            data = mapOf(
                "r" to "",
                "d" to mainUrl,
            ),
            referer = url,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest"
            )
        ).text
        val json = JSONObject(response)
        val file = json.optString("file")
        callback.invoke(newExtractorLink(
            this.name,
            this.name,
            file,
            type = INFER_TYPE,
            {
                this.referer = file
                this.headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
                    "Accept" to "*/*",
                    "Accept-Language" to "en-US,en;q=0.5",
                    "Sec-GPC" to "1",
                    "Connection" to "keep-alive",
                    "Sec-Fetch-Dest" to "empty",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "same-origin",
                    "Priority" to "u=0",
                    "Pragma" to "no-cache",
                    "Cache-Control" to "no-cache",
                    "TE" to "trailers"
                )
            }
        ))
    }
}

class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

class Furher2 : Filesim() {
    override val name = "Furher 2"
    override var mainUrl = "723qrh1p.fun"
}

class Turbovidhls : Filesim() {
    override val name = "Turbovidhls"
    override var mainUrl = "https://turbovid.xyz"
}

class EmturbovidExtractor : Filesim() {
    override val name = "Emturbovid"
    override var mainUrl = "https://emturbovid.com"
}

class VidHidePro6 : Filesim() {
    override val name = "VidHidePro6"
    override var mainUrl = "https://vidhidepro.com"
}

// ========================================
// EXTRACTOR REGISTRY (Auto-Register)
// ========================================
// List semua extractor untuk auto-register
// Tambahkan extractor baru di sini setelah dibuat
// ========================================

object SyncExtractors {
    val list = listOf(
        // StreamWish based (11)
        Do7go(),
        Dhcplay(),
        Hglink(),
        Ghbrisk(),
        Movearnpre(),
        Minochinos(),
        Mivalyo(),
        Ryderjet(),
        Bingezove(),
        Dingtezuni(),
        Dhtpre(),

        // VidStack based (11)
        Listeamed(),
        Streamcasthub(),
        Dm21embed(),
        Dm21upns(),
        Pm21p2p(),
        Dm21(),
        Meplayer(),
        Fufaupns(),
        P2pplay(),
        Playerngefilm21(),
        Rpmvid(),

        // Custom extractors (4)
        Voe(),
        Veev(),
        Dintezuvio(),
        Hxfile(),

        // OK.RU based (3)
        Odnoklassniki(),
        OkRuSSL(),
        OkRuHTTP(),

        // Other extractors (21)
        Dailymotion(),
        Rumble(),
        StreamRuby(),
        Svanila(),
        Svilla(),
        Vidguardto(),
        Vidguardto1(),
        Vidguardto2(),
        Vidguardto3(),
        Archivd(),
        Newuservideo(),
        Vidhidepro(),
        Dsvplay(),
        ArchiveOrgExtractor(),
        Megacloud(),
        Jeniusplay(),
        Gdriveplayerto(),
        GDFlix(),
        BloggerExtractor(),
        PixelDrainDev(),
        Upload18com(),
        Xshotcok(),

        // LayarKaca custom (8)
        Co4nxtrl(),
        Hownetwork(),
        Cloudhownetwork(),
        Furher(),
        Furher2(),
        Turbovidhls(),
        EmturbovidExtractor(),
        VidHidePro6(),
    )
}

// ========================================
// TOTAL: 60 EXTRACTOR CLASSES
// ========================================
// Build fix test
