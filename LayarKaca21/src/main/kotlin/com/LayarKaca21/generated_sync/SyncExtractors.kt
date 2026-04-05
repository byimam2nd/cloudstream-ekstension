// ========================================
// MASTER EXTRACTORS COLLECTION - v3.0 OPTIMIZED
// ========================================
// Kumpulan 75+ Extractor untuk CloudStream
//
// Purpose: Centralized extractor collection
// Philosophy: "Plug-and-play, not reinvent-the-wheel"
//
// Source: ExtCloud + cloudstream-ekstension + CloudStream Built-in
// Last Updated: 2026-03-30
// Maintainer: CloudStream Extension Team
//
// OPTIMIZATIONS (v3.0):
// - ✅ Internal Organization dengan Region Markers
// - ✅ Lazy Initialization untuk extractor lists
// - ✅ Pre-compiled regex patterns
// - ✅ Shared HttpClientFactory untuk connection pooling
// - ✅ O(1) quality mapping dengan HashMap
// - ✅ P1: MasterLinkGenerator (auto-detect quality & type)
// - ✅ P2: SmartM3U8Parser (M3U8 playlist parsing)
//
// STRUCTURE:
// - Region 230-350:  StreamWish Based Extractors
// - Region 351-450:  VidStack Based Extractors
// - Region 451-550:  VeeV Based Extractors
// - Region 551-650:  Voe Based Extractors
// - Region 651-750:  OK.RU Based Extractors
// - Region 751-850:  Rumble Extractor
// - Region 851-950:  StreamRuby Based Extractors
// - Region 1717+:    MASTER LINK GENERATOR (P1)
// - Region 2078+:    SMART M3U8 PARSER (P2)
//
// USAGE:
//   import com.{Provider}.generated_sync.MasterLinkGenerator
//   MasterLinkGenerator.createLink(source, url, referer)
// ========================================

// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/MasterExtractors.kt
// File: SyncExtractors.kt
// ========================================
package com.LayarKaca21.generated_sync

import com.lagradost.api.Log
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
// ========================================
// NOTE: Setelah sync workflow berjalan:
//   - Folder: master/ → generated_sync/
//   - Package: com.{Module} → com.{Module}.generated_sync
//   - Imports: master. → com.{Module}.generated_sync.
//   - Files:
//     * MasterHttpClientFactory.kt → generated_sync/SyncHttpClientFactory.kt
//     * MasterCompiledRegexPatterns.kt → generated_sync/SyncCompiledRegexPatterns.kt
//     * MasterCaches.kt → generated_sync/SyncCaches.kt
//     * MasterMonitors.kt → generated_sync/SyncMonitors.kt
//
// Script sync akan otomatis update semua import paths
// ========================================
import com.LayarKaca21.generated_sync.HttpClientFactory
import com.LayarKaca21.generated_sync.CompiledRegexPatterns

// ========================================
// REGION: CONSTANTS & CONFIG (1-100)
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
// REGION: STREAMWISH BASED EXTRACTORS (230-350)
// ========================================
// Provider: StreamWish, Filewish, Wishembed
// Type: Video hosting platform
// Status: ✅ TESTED & WORKING
//
// Extractors in this region:
// - Movearnpre, Minochinos, Mivalyo, Ryderjet, Bingezove
// - Dingtezuni (base class)
// - Hglink, Ghbrisk, Dhcplay
//
// Usage: These extractors auto-detect video URLs from StreamWish-based hosts
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
            Log.e("MasterExtractors", "`[Dingtezuni] Failed: ${e.message}")
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
// REGION: VIDSTACK BASED EXTRACTORS (351-450)
// Provider: VidStack, Streamcast, DM21
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
            Log.e("MasterExtractors", "`[Dintezuvio] Failed: ${e.message}")
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
// REGION: VEEV EXTRACTOR (451-550)
// ========================================
// Provider: Veev, Kinoger, Doods
// Type: Video hosting with custom encryption
// Status: ✅ TESTED & WORKING
//
// Extractors in this region:
// - Veev (main extractor with decryption)
//
// Usage: Handles encrypted video URLs with custom decoding
// Features: Built-in decryption, multi-source support
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

        val foundValues = CompiledRegexPatterns.VEEV_ENCRYPTED_PATTERN.findAll(html)
            .map { it.groupValues[1] }.toList()

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
// REGION: PENCURIMOVIE EXTRACTORS
// ========================================
// Provider: Pencurimovie custom extractors
// Type: Custom extraction logic
// Status: ✅ TESTED & WORKING
//
// Extractors in this region:
// - Do7go (StreamWish variant)
// - Listeamed (VidStack variant)
//
// Usage: Custom extractors for Pencurimovie provider
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

// ========================================
// REGION: VOE EXTRACTOR (551-650)
// ========================================
// Provider: Voe
// Type: Video hosting platform
// Status: ✅ TESTED & WORKING
//
// Extractors in this region:
// - Voe (main extractor)
// - Dsvplay (DoodLaExtractor variant)
//
// Usage: Handles Voe.sx video extraction with unpacking
// Features: JavaScript unpacker, HLS stream extraction
// ========================================

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
// REGION: OK.RU EXTRACTORS (651-750)
// ========================================
// Provider: Odnoklassniki (OK.RU)
// Type: Social media video hosting
// Status: ✅ TESTED & WORKING
//
// Extractors in this region:
// - Odnoklassniki (main extractor)
// - OkRuSSL (HTTPS variant)
// - OkRuHTTP (HTTP variant)
//
// Usage: Extracts video from OK.RU social platform
// Features: Multiple quality variants, auto-detection
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
        val headers = ExtractorHelpers.getStandardHeaders(mainUrl)

        // Extract video ID
        val videoId = Regex("""/video(?:embed)?/(\d+)""").find(url)?.groupValues?.get(1)
        if (videoId == null) return

        // Method 1: Try OK.ru oembed API (most reliable)
        try {
            val oembedUrl = "https://www.ok.ru/oembed?url=https://ok.ru/video/$videoId&format=json"
            val oembedResp = app.get(oembedUrl, headers = headers, timeout = 10_000).text
            val oembedObj = JSONObject(oembedResp)

            if (oembedObj.has("videoUrl")) {
                val videoUrl = oembedObj.optString("videoUrl")
                if (videoUrl.isNotEmpty()) {
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = videoUrl,
                            type = INFER_TYPE
                        ) {
                            this.referer = "$mainUrl/"
                            this.quality = 480
                            this.headers = headers
                        }
                    )
                    return
                }
            }
        } catch (_: Exception) {}

        // Method 2: Parse HTML embed page with &quot; decoded
        val embedUrl = url.replace("/video/", "/videoembed/")
        val html = app.get(embedUrl, headers = headers, timeout = 10_000).text

        // Decode HTML entities: &quot; -> " then match escaped JSON
        val decoded = html.replace("&quot;", "\"")

        // Pattern in decoded HTML: \"name\":\"mobile\"...\"url\":\"https://...\"
        val videoPattern = Regex("""\\"name\\":\\"(mobile|lowest|low|sd|hd|full)\\".*?\\"url\\":\\"(https://[^\\"]+)""")

        for (match in videoPattern.findAll(decoded)) {
            val qualityName = match.groupValues[1]
            val videoUrl = match.groupValues[2]
                .replace("\\u0026", "&")
            if (videoUrl.isNotEmpty()) {
                val quality = qualityName.toQuality()
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

        // Method 3: Fallback - Try HLS manifest URL
        val hlsPattern = Regex("""\\"hlsManifestUrl\\":\\"(https://[^\\"]+m3u8[^\\"]*)""")
        val hlsMatch = hlsPattern.find(decoded)
        if (hlsMatch != null) {
            val hlsUrl = hlsMatch.groupValues[1]
                .replace("\\u0026", "&")
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = hlsUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = 1080
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
// REGION: RUMBLE EXTRACTOR (751-850)
// ========================================
// Provider: Rumble
// Type: Video platform
// Status: ✅ TESTED & WORKING
//
// Extractors in this region:
// - Rumble (main extractor)
//
// Usage: Extracts video from Rumble.com platform
// Features: Multi-quality extraction, HLS support
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

        val matches = CompiledRegexPatterns.RUMBLE_URL_PATTERN.findAll(scriptData)

        val processedUrls = mutableSetOf<String>()

        for (match in matches) {
            val rawUrl = match.groupValues[1]
            if (rawUrl.isBlank()) continue

            val cleanedUrl = rawUrl.replace("\\/", "/")
            if (!cleanedUrl.contains("rumble.com")) continue
            if (!cleanedUrl.endsWith(".m3u8")) continue
            if (!processedUrls.add(cleanedUrl)) continue

            val m3u8Response = app.get(cleanedUrl)
            val variantCount = CompiledRegexPatterns.M3U8_STREAM_INFO.findAll(m3u8Response.text).count()

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
// REGION: STREAMRUBY EXTRACTOR (851-950)
// ========================================
// Provider: StreamRuby, RubyVidHub
// Type: Video hosting platform
// Status: ✅ TESTED & WORKING
//
// Extractors in this region:
// - StreamRuby (main extractor)
// - Svanila, Svilla (variants)
//
// Usage: Handles StreamRuby-based video hosts
// Features: Multiple source extraction, quality detection
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
            Log.e("MasterExtractors", "`[StreamRuby] Failed: ${e.message}")
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
            Log.e("MasterExtractors", "`[Vidguard] Failed: ${e.message}")
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
        try {
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
        } catch (e: Exception) {
            Log.e("MasterExtractors", "`[Archivd] Failed: ${e.message}")
        }
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
        try {
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
        } catch (e: Exception) {
            Log.e("MasterExtractors", "[Uservideo] Failed: ${e.message}")
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
// DAILYMOTION EXTRACTOR (From ExtCloud - Proven Working)
// ========================================

class Dailymotion : ExtractorApi() {
    override val name = "Dailymotion"
    override val mainUrl = "https://www.dailymotion.com"
    override val requiresReferer = false
    private val baseUrl = "https://www.dailymotion.com"

    private val videoIdRegex = "^[kx][a-zA-Z0-9]+$".toRegex()

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val embedUrl = getEmbedUrl(url) ?: return
            val id = getVideoId(embedUrl) ?: return
            val metaDataUrl = "$baseUrl/player/metadata/video/$id"
            val response = app.get(metaDataUrl, referer = embedUrl).text

            val urls = CompiledRegexPatterns.DAILYMOTION_VIDEO_URL.findAll(response)
                .map { it.groupValues[1] }
                .toList().filter { it.contains(".m3u8") }

            urls.forEach { videoUrl ->
                getStream(videoUrl, this.name, callback)
            }

            // Extract subtitles from subtitle data sections
            val subtitleDataRegex = Regex(""""subtitles"\s*:\s*\{[^}]*"data"\s*:\s*(\[[^\]]*\])""")
            val subtitlesMatches = subtitleDataRegex.findAll(response).map { it.groupValues[1] }.toList()
            subtitlesMatches.forEach { subtitleJson ->
                CompiledRegexPatterns.DAILYMOTION_SUBTITLE.findAll(subtitleJson).forEach { match ->
                    val label = match.groupValues[1]
                    val subUrl = match.groupValues[2]
                    subtitleCallback(SubtitleFile(label, subUrl))
                }
            }
        } catch (e: Exception) {
            Log.e("MasterExtractors", "`[Dailymotion] Failed: ${e.message}")
        }
    }

    private fun getEmbedUrl(url: String): String? {
        if (url.contains("/embed/") || url.contains("/video/")) return url
        if (url.contains("geo.dailymotion.com")) {
            val videoId = url.substringAfter("video=")
            return "$baseUrl/embed/video/$videoId"
        }
        return null
    }

    private fun getVideoId(url: String): String? {
        val path = java.net.URI(url).path
        val id = path.substringAfter("/video/")
        return if (id.matches(videoIdRegex)) id else null
    }

    private suspend fun getStream(
        streamLink: String,
        name: String,
        callback: (ExtractorLink) -> Unit
    ) {
        return M3u8Helper.generateM3u8(name, streamLink, "").forEach(callback)
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
        try {
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
        } catch (e: Exception) {
            Log.e("MasterExtractors", "`[Jeniusplay] Failed: ${e.message}")
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
        try {
            val response = app.get(url).document
            val sources = response.select("script").find { script ->
                script.data().contains("\"sources\"")
            }?.data() ?: return

            CompiledRegexPatterns.ARCHIVE_ORG_URL.findAll(sources).forEach { match ->
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
        } catch (e: Exception) {
            Log.e("MasterExtractors", "`[ArchiveOrg] Failed: ${e.message}")
        }
    }
}

// ========================================
// REGION: MEGACLOUD EXTRACTOR (1200-1400)
// Provider: Megacloud, HiAnime
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
            Log.e("MasterExtractors", "`[Megacloud] Failed: ${e.message}")
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

// ========================================
// REGION: MASTER LINK GENERATOR (P1)
// ========================================
// Purpose: Centralized ExtractorLink builder
// Philosophy: "Auto-detect > Manual configuration"
// Status: ✅ PRODUCTION READY
//
// Features:
// - Auto-detect quality dari URL pattern (1080p, 720p, FHD, HD, dll)
// - Auto-detect type dari file extension (.m3u8 → M3U8, .mp4 → VIDEO)
// - Auto-generate headers (Referer, Origin, User-Agent)
// - M3U8 playlist parsing untuk multiple quality variants
//
// Main Functions:
// - createLink() - Single URL dengan auto-detection
// - createLinksFromM3U8() - M3U8 playlist parsing
// - detectQualityFromUrl() - Quality detection helper
// - isValidVideoUrl() - URL validation
//
// Usage Example:
// kotlin
// MasterLinkGenerator.createLink(
//     source = "Extractor",
//     url = videoUrl,
//     referer = referer
// )?.let { callback(it) }
// 
// ========================================

/**
 * MasterLinkGenerator — Centralized ExtractorLink Builder
 * 
 * Tujuan:
 * - Auto-detect quality dari URL/filename
 * - Auto-detect type (HLS/MP4) menggunakan INFER_TYPE
 * - Auto-generate optimal headers per domain
 * - Reduce boilerplate di setiap extractor
 * - Support M3U8 playlist parsing untuk multiple qualities
 * 
 * Usage:
 *   // Single URL dengan auto-detection
 *   val link = MasterLinkGenerator.createLink(
 *       source = "Dingtezuni",
 *       url = "https://dingtezuni.com/video.m3u8",
 *       referer = "https://dingtezuni.com/"
 *   )
 *   callback(link)
 *   
 *   // M3U8 playlist dengan multiple qualities
 *   MasterLinkGenerator.createLinksFromM3U8(
 *       source = "Dingtezuni",
 *       m3u8Url = "https://dingtezuni.com/playlist.m3u8",
 *       referer = "https://dingtezuni.com/",
 *       callback = callback
 *   )
 */
object MasterLinkGenerator {
    
    // ========================================
    // PUBLIC API - SINGLE LINK
    // ========================================
    
    /**
     * Create ExtractorLink dengan auto-detection quality
     * Menggunakan INFER_TYPE untuk auto-detect type dari URL extension
     * 
     * @param source Nama extractor (contoh: "Dingtezuni")
     * @param url URL video stream
     * @param referer Referer header (optional)
     * @param quality Quality manual override (null = auto-detect)
     * @param headers Custom headers (optional, akan di-merge dengan default)
     * @return ExtractorLink atau null jika URL invalid
     */
    suspend fun createLink(
        source: String,
        url: String,
        referer: String?,
        quality: Int? = null,
        headers: Map<String, String>? = null
    ): ExtractorLink? {
        // Validate URL
        if (!isValidVideoUrl(url)) {
            return null
        }
        
        // Auto-detect quality jika tidak di-override
        val detectedQuality = quality ?: detectQualityFromUrl(url)
        
        // Build headers (default + custom merge)
        val finalHeaders = buildHeaders(referer, headers)
        
        // Gunakan INFER_TYPE untuk auto-detect type dari URL extension
        // INFER_TYPE akan detect M3U8 vs VIDEO berdasarkan file extension
        return newExtractorLink(
            source = source,
            name = source,
            url = url,
            type = INFER_TYPE
        ) {
            this.quality = detectedQuality
            if (referer != null) {
                this.referer = referer
            }
            this.headers = finalHeaders
        }
    }
    
    // ========================================
    // PUBLIC API - M3U8 PLAYLIST
    // ========================================
    
    /**
     * Create multiple ExtractorLink dari M3U8 playlist
     * Auto-parse semua quality variants dari master playlist
     *
     * @param source Nama extractor
     * @param m3u8Url URL M3U8 playlist (master playlist)
     * @param referer Referer header
     * @param callback Callback untuk setiap ExtractorLink
     * @return Jumlah variants yang berhasil di-extract
     */
    suspend fun createLinksFromM3U8(
        source: String,
        m3u8Url: String,
        referer: String?,
        callback: (ExtractorLink) -> Unit
    ): Int {
        try {
            // Fetch playlist content
            val playlistContent = fetchM3U8Playlist(m3u8Url, referer)
            
            // Parse variants
            val variants = parseM3U8VariantsShared(playlistContent, m3u8Url)
            
            // Generate ExtractorLink untuk setiap variant
            variants.forEach { variant ->
                callback(
                    newExtractorLink(
                        source = source,
                        name = "${source} ${variant.quality}p",
                        url = variant.url,
                        type = INFER_TYPE  // Auto-detect dari URL
                    ) {
                        this.quality = variant.quality
                        if (referer != null) {
                            this.referer = referer
                        }
                        this.headers = buildHeaders(referer)
                    }
                )
            }
            
            return variants.size
        } catch (e: Exception) {
            // Fallback: return single link dengan quality default
            createLink(
                source = source,
                url = m3u8Url,
                referer = referer
            )?.let { callback(it) }
            return 1
        }
    }
    
    // ========================================
    // AUTO-DETECTION LOGIC
    // ========================================
    
    /**
     * Auto-detect quality dari URL/filename
     * 
     * Patterns:
     * - .../video_1080p.m3u8 → 1080
     * - .../720/stream.mp4 → 720
     * - .../FHD/video.m3u8 → 1080
     * - .../HD/video.m3u8 → 720
     * - .../SD/video.m3u8 → 480
     * - No match → 480 (safe default)
     */
    fun detectQualityFromUrl(url: String): Int {
        val urlLower = url.lowercase()

        // Pattern 1: Explicit quality keywords (using pre-compiled patterns)
        if (CompiledRegexPatterns.MLG_QUALITY_1080.containsMatchIn(urlLower)) return 1080
        if (CompiledRegexPatterns.MLG_QUALITY_720.containsMatchIn(urlLower)) return 720
        if (CompiledRegexPatterns.MLG_QUALITY_480.containsMatchIn(urlLower)) return 480
        if (CompiledRegexPatterns.MLG_QUALITY_360.containsMatchIn(urlLower)) return 360
        if (CompiledRegexPatterns.MLG_QUALITY_240.containsMatchIn(urlLower)) return 240
        if (CompiledRegexPatterns.MLG_QUALITY_144.containsMatchIn(urlLower)) return 144

        // Pattern 2: Path-based detection (contoh: .../1080p/stream.m3u8)
        CompiledRegexPatterns.MLG_PATH_QUALITY.find(urlLower)?.groupValues?.getOrNull(1)?.let {
            return it.toIntOrNull() ?: 480
        }

        // Pattern 3: Quality suffix (contoh: video_1080.m3u8)
        CompiledRegexPatterns.MLG_SUFFIX_QUALITY.find(urlLower)?.groupValues?.getOrNull(1)?.let {
            return it.toIntOrNull() ?: 480
        }

        // Default: 480 (safe untuk kebanyakan kasus)
        return 480
    }
    
    // ========================================
    // HEADERS BUILDING
    // ========================================
    
    /**
     * Build optimal headers untuk streaming
     * Merge default headers dengan custom headers
     */
    private fun buildHeaders(
        referer: String?,
        customHeaders: Map<String, String>? = null
    ): Map<String, String> {
        val defaultHeaders = mapOf(
            "Accept" to "*/*",
            "Accept-Encoding" to "gzip, deflate, br",
            "Accept-Language" to "en-US,en;q=0.9",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site"
        )
        
        val refererHeader = referer?.let { mapOf("Referer" to it) } ?: emptyMap()
        val originHeader = referer?.let { 
            val origin = extractOrigin(it)
            if (origin != null) mapOf("Origin" to origin) else emptyMap()
        } ?: emptyMap()
        
        return defaultHeaders + refererHeader + originHeader + (customHeaders ?: emptyMap())
    }
    
    private fun extractOrigin(url: String): String? {
        return try {
            val uri = java.net.URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) {
            null
        }
    }
    
    // ========================================
    // URL VALIDATION
    // ========================================
    
    /**
     * Validate URL sebagai video stream yang valid
     */
    fun isValidVideoUrl(url: String): Boolean {
        // Must be HTTP(S)
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false
        }
        
        // Must not be obvious HTML page
        if (url.endsWith(".html") || url.endsWith(".php")) {
            return false
        }
        
        // Must have video extension OR HLS indicator
        val validExtensions = listOf(
            ".m3u8", ".m3u", ".mp4", ".webm", ".mkv", 
            ".mov", ".avi", ".flv", ".wmv"
        )
        
        val hasValidExtension = validExtensions.any { url.lowercase().endsWith(it) }
        val hasHlsIndicator = url.lowercase().contains("hls") || 
                              url.lowercase().contains("m3u8")
        
        return hasValidExtension || hasHlsIndicator
    }
    
    // ========================================
    // M3U8 PARSING HELPERS
    // ========================================
    
    /**
     * Fetch M3U8 playlist content
     */
    private suspend fun fetchM3U8Playlist(
        m3u8Url: String,
        referer: String?
    ): String {
        val headers = buildHeaders(referer)
        val response = app.get(
            m3u8Url,
            headers = headers,
            timeout = AutoUsedConstants.DEFAULT_TIMEOUT
        )
        return response.text
    }
    
    // parseM3U8Variants removed — use parseM3U8VariantsShared directly
    // resolveRelativeUrl removed — use resolveRelativeUrlShared directly
}

/**
 * Data class untuk M3U8 quality variant
 */
data class M3U8QualityVariant(
    val quality: Int,           // 1080, 720, 480, etc.
    val bandwidth: Int,         // Bitrate (contoh: 2500000 = 2.5 Mbps)
    val url: String,            // Direct stream URL
    val resolution: Pair<Int, Int>  // Width x Height (contoh: 1920 x 1080)
)

// ========================================
// SHARED M3U8 PARSING (DRY — used by both MasterLinkGenerator & SmartM3U8Parser)
// ========================================

/**
 * Shared M3U8 variant parser — single source of truth for parsing logic.
 * Used by both MasterLinkGenerator and SmartM3U8Parser to avoid duplication.
 *
 * @param playlistContent Raw M3U8 playlist content
 * @param baseUrl Base URL for resolving relative URLs
 * @return List of quality variants sorted by quality (descending)
 */
internal fun parseM3U8VariantsShared(
    playlistContent: String,
    baseUrl: String
): List<M3U8QualityVariant> {
    val variants = mutableListOf<M3U8QualityVariant>()
    val lines = playlistContent.lines()

    var currentQuality: Int? = null
    var currentBandwidth: Int? = null
    var currentResolution: Pair<Int, Int>? = null

    for (i in lines.indices) {
        val line = lines[i].trim()

        // Parse #EXT-X-STREAM-INF line
        if (line.startsWith("#EXT-X-STREAM-INF:")) {
            // Extract bandwidth — compiled regex (no recompilation)
            val bandwidthMatch = CompiledRegexPatterns.M3U8_BANDWIDTH.find(line)
            currentBandwidth = bandwidthMatch?.groupValues?.getOrNull(1)?.toIntOrNull()

            // Extract resolution — compiled regex (no recompilation)
            val resolutionMatch = CompiledRegexPatterns.M3U8_RESOLUTION.find(line)
            currentResolution = resolutionMatch?.let {
                val w = it.groupValues[1].toIntOrNull() ?: 0
                val h = it.groupValues[2].toIntOrNull() ?: 0
                Pair(w, h)
            }

            // Extract quality dari RESOLUTION height — reuse same match
            currentQuality = currentResolution?.second
        }

        // Parse URL line (setelah #EXT-X-STREAM-INF)
        else if (line.isNotEmpty() && !line.startsWith("#")) {
            currentQuality?.let { quality ->
                val variantUrl = resolveRelativeUrlShared(line, baseUrl)
                variants.add(
                    M3U8QualityVariant(
                        quality = quality,
                        bandwidth = currentBandwidth ?: 0,
                        url = variantUrl,
                        resolution = currentResolution ?: Pair(0, 0)
                    )
                )
            }

            // Reset untuk variant berikutnya
            currentQuality = null
            currentBandwidth = null
            currentResolution = null
        }
    }

    return variants.sortedByDescending { it.quality }
}

/**
 * Shared relative URL resolver — single source of truth.
 * Used by both MasterLinkGenerator and SmartM3U8Parser.
 */
internal fun resolveRelativeUrlShared(url: String, baseUrl: String): String {
    if (url.startsWith("http://") || url.startsWith("https://")) {
        return url
    }

    return try {
        val baseUri = java.net.URI(baseUrl)
        baseUri.resolve(url).toString()
    } catch (e: Exception) {
        // Fallback: manual concat
        val basePath = baseUrl.substringBeforeLast("/")
        "$basePath/$url"
    }
}

// ========================================
// REGION: SMART M3U8 PARSER (P2)
// ========================================
// Advanced M3U8 parser dengan quality selection & bandwidth testing
// ========================================

/**
 * SmartM3U8Parser — Advanced M3U8 Playlist Parser
 * 
 * Fitur:
 * - Parse M3U8 master playlist dengan multiple quality variants
 * - Auto-select best quality berdasarkan bandwidth/network
 * - Test stream accessibility sebelum return
 * - Support adaptive bitrate streaming
 * 
 * Usage:
 *   // Parse playlist manual
 *   val variants = SmartM3U8Parser.parsePlaylist(m3u8Url, referer)
 *   
 *   // Auto-select best quality
 *   val bestUrl = SmartM3U8Parser.selectBestQuality(
 *       m3u8Url = m3u8Url,
 *       referer = referer,
 *       maxQuality = 1080  // Cap di 1080p
 *   )
 *   
 *   // Test stream accessibility
 *   val isAccessible = SmartM3U8Parser.testStreamUrl(url, referer)
 */
object SmartM3U8Parser {
    
    // ========================================
    // PUBLIC API
    // ========================================
    
    /**
     * Parse M3U8 master playlist, extract semua quality variants
     * 
     * @param m3u8Url URL M3U8 master playlist
     * @param referer Referer header
     * @return List quality variants sorted by quality (descending)
     */
    suspend fun parsePlaylist(
        m3u8Url: String,
        referer: String?
    ): List<M3U8QualityVariant> {
        val headers = buildHeaders(referer)
        
        // Fetch master playlist
        val response = app.get(
            m3u8Url,
            headers = headers,
            timeout = AutoUsedConstants.DEFAULT_TIMEOUT
        )
        
        // Inline parsing logic
        return parseM3U8VariantsShared(response.text, m3u8Url)
    }

    // parseM3U8VariantsInline removed — use parseM3U8VariantsShared directly
    // resolveRelativeUrl removed — use resolveRelativeUrlShared directly
    
    /**
     * Auto-select best quality variant dari M3U8 playlist
     * 
     * @param m3u8Url URL M3U8 master playlist
     * @param referer Referer header
     * @param maxQuality Maximum quality yang diinginkan (default: 1080)
     * @param minQuality Minimum quality yang acceptable (default: 240)
     * @return Best quality URL atau null jika playlist invalid
     */
    suspend fun selectBestQuality(
        m3u8Url: String,
        referer: String?,
        maxQuality: Int = 1080,
        minQuality: Int = 240
    ): String? {
        val variants = parsePlaylist(m3u8Url, referer)
        
        if (variants.isEmpty()) {
            return null
        }
        
        // Filter variants yang sesuai dengan quality range
        val filtered = variants.filter { 
            it.quality in minQuality..maxQuality 
        }
        
        // Pilih yang paling tinggi dalam range
        return filtered.maxByOrNull { it.quality }?.url
            ?: variants.minByOrNull { it.quality }?.url  // Fallback ke terendah
    }
    
    /**
     * Test accessibility dari stream URL dengan HEAD request
     * 
     * @param url Stream URL untuk ditest
     * @param referer Referer header
     * @param timeoutMs Timeout dalam milliseconds (default: 5s)
     * @return true jika stream accessible, false jika timeout/error
     */
    suspend fun testStreamUrl(
        url: String,
        referer: String?,
        timeoutMs: Long = 5000L
    ): Boolean {
        return try {
            val headers = buildHeaders(referer)
            
            // HEAD request untuk test accessibility (lebih ringan dari GET)
            val response = app.head(
                url,
                headers = headers,
                timeout = timeoutMs
            )
            
            // Check status code
            response.code in 200..299
        } catch (e: Exception) {
            false  // Timeout atau error
        }
    }

    /**
     * Get bandwidth estimate dari stream URL
     * Menggunakan Content-Length header jika tersedia
     * 
     * @param url Stream URL
     * @param referer Referer header
     * @return Estimated bandwidth dalam bps, atau 0 jika tidak tersedia
     */
    suspend fun getBandwidthEstimate(
        url: String,
        referer: String?
    ): Long {
        return try {
            val headers = buildHeaders(referer)
            
            val response = app.head(
                url,
                headers = headers,
                timeout = AutoUsedConstants.FAST_TIMEOUT
            )
            
            val contentLength = response.headers["Content-Length"]?.toLongOrNull()
                ?: return 0L
            
            // Estimate: content length * 8 / duration (assume 10s segment)
            contentLength * 8 / 10
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Parse M3U8 variant playlist (child playlist) untuk get exact URLs
     * 
     * @param variantUrl URL variant playlist
     * @param referer Referer header
     * @return List of exact stream URLs dari variant playlist
     */
    suspend fun parseVariantPlaylist(
        variantUrl: String,
        referer: String?
    ): List<String> {
        return try {
            val headers = buildHeaders(referer)
            
            val response = app.get(
                variantUrl,
                headers = headers,
                timeout = AutoUsedConstants.DEFAULT_TIMEOUT
            )
            
            // Extract .ts segment URLs dari variant playlist
            val lines = response.text.lines()
            lines.filter { 
                it.isNotBlank() && 
                !it.startsWith("#") &&
                (it.endsWith(".ts") || it.endsWith(".m4s"))
            }.map { line ->
                resolveRelativeUrlShared(line, variantUrl)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // ========================================
    // INTERNAL HELPERS
    // ========================================
    
    /**
     * Build headers untuk M3U8 requests
     */
    private fun buildHeaders(referer: String?): Map<String, String> {
        val defaultHeaders = mapOf(
            "Accept" to "*/*",
            "Accept-Encoding" to "gzip, deflate, br",
            "Accept-Language" to "en-US,en;q=0.9",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site"
        )
        
        return referer?.let {
            defaultHeaders + ("Referer" to it)
        } ?: defaultHeaders
    }
    
    // resolveRelativeUrl removed — use resolveRelativeUrlShared directly
}
