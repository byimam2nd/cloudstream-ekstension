// ========================================
// MASTER EXTRACTORS COLLECTION - v2.0
// Kumpulan 75+ Extractor untuk CloudStream
// ========================================
// Source: ExtCloud + cloudstream-ekstension + CloudStream Built-in
// Last Updated: 2026-03-18
// Maintainer: Phisher98
// ========================================

package com.MasterExtractors

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
        val headers = mapOf(
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT,
        )

        try {
            val response = app.get(getEmbedUrl(url), referer = referer)
            
            // Try multiple methods to extract video URL
            var script: String? = null
            
            // Method 1: Unpack packed JavaScript
            val packed = getPacked(response.text)
            if (!packed.isNullOrEmpty()) {
                var result = getAndUnpack(response.text)
                if (result.contains("var links")) {
                    result = result.substringAfter("var links")
                }
                script = result
            }
            
            // Method 2: Look for sources in script tags
            if (script == null) {
                script = response.document.selectFirst("script:containsData(sources:)")?.data()
            }
            
            // Method 3: Look for direct m3u8/mp4 in data attributes
            if (script == null) {
                val videoUrl = response.document.selectFirst("meta[property=og:video]")?.attr("content")
                if (!videoUrl.isNullOrEmpty() && (videoUrl.contains(".m3u8") || videoUrl.contains(".mp4"))) {
                    script = "sources:[{file:\"$videoUrl\"}]"
                }
            }
            
            script ?: return

            // Extract m3u8 links with multiple regex patterns for robustness
            val patterns = listOf(
                ":\\s*\"(.*?m3u8.*?)\"",
                "file:\\s*\"(.*?m3u8.*?)\"",
                "src:\\s*\"(.*?m3u8.*?)\"",
                "\"(https?://[^\"]+?\\.m3u8[^\"]*?)\""
            )
            
            val extractedUrls = mutableSetOf<String>()
            
            for (pattern in patterns) {
                Regex(pattern).findAll(script).forEach { match ->
                    val videoUrl = match.groupValues[1].trim()
                    if (videoUrl.isNotEmpty() && videoUrl.contains("m3u8")) {
                        extractedUrls.add(fixUrl(videoUrl))
                    }
                }
            }
            
            // Generate extractor links for all unique URLs found
            extractedUrls.forEach { videoUrl ->
                try {
                    generateM3u8(
                        name,
                        videoUrl,
                        referer = "$mainUrl/",
                        headers = headers
                    ).forEach(callback)
                } catch (e: Exception) {
                    // Skip invalid URLs but continue processing others
                }
            }
        } catch (e: Exception) {
            // Log error but don't crash
            throw Exception("Failed to extract video from $url: ${e.message}")
        }
    }

    private fun getEmbedUrl(url: String): String {
        return when {
            url.contains("/d/") -> url.replace("/d/", "/v/")
            url.contains("/download/") -> url.replace("/download/", "/v/")
            url.contains("/file/") -> url.replace("/file/", "/v/")
            else -> url.replace("/f/", "/v/")
        }
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
        val headers = mapOf(
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT,
        )

        try {
            val response = app.get(getEmbedUrl(url), referer = referer)
            
            // Try multiple methods to extract video URL
            var script: String? = null
            
            // Method 1: Unpack packed JavaScript
            val packed = getPacked(response.text)
            if (!packed.isNullOrEmpty()) {
                var result = getAndUnpack(response.text)
                if (result.contains("var links")) {
                    result = result.substringAfter("var links")
                }
                script = result
            }
            
            // Method 2: Look for sources in script tags
            if (script == null) {
                script = response.document.selectFirst("script:containsData(sources:)")?.data()
            }
            
            // Method 3: Look for direct m3u8/mp4 in meta tags
            if (script == null) {
                val videoUrl = response.document.selectFirst("meta[property=og:video]")?.attr("content")
                if (!videoUrl.isNullOrEmpty() && (videoUrl.contains(".m3u8") || videoUrl.contains(".mp4"))) {
                    script = "sources:[{file:\"$videoUrl\"}]"
                }
            }
            
            script ?: return

            // Extract m3u8 links with multiple regex patterns for robustness
            val patterns = listOf(
                ":\\s*\"(.*?m3u8.*?)\"",
                "file:\\s*\"(.*?m3u8.*?)\"",
                "src:\\s*\"(.*?m3u8.*?)\"",
                "\"(https?://[^\"]+?\\.m3u8[^\"]*?)\""
            )
            
            val extractedUrls = mutableSetOf<String>()
            
            for (pattern in patterns) {
                Regex(pattern).findAll(script).forEach { match ->
                    val videoUrl = match.groupValues[1].trim()
                    if (videoUrl.isNotEmpty() && videoUrl.contains("m3u8")) {
                        extractedUrls.add(fixUrl(videoUrl))
                    }
                }
            }
            
            // Generate extractor links for all unique URLs found
            extractedUrls.forEach { videoUrl ->
                try {
                    generateM3u8(
                        name,
                        videoUrl,
                        referer = "$mainUrl/",
                        headers = headers
                    ).forEach(callback)
                } catch (e: Exception) {
                    // Skip invalid URLs but continue processing others
                }
            }
        } catch (e: Exception) {
            // Log error but don't crash
            throw Exception("Dintezuvio: Failed to extract video from $url: ${e.message}")
        }
    }

    private fun getEmbedUrl(url: String): String {
        return when {
            url.contains("/d/") -> url.replace("/d/", "/v/")
            url.contains("/download/") -> url.replace("/download/", "/v/")
            url.contains("/file/") -> url.replace("/file/", "/v/")
            else -> url.replace("/f/", "/v/")
        }
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
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT,
        )
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

            val quality = video.name.uppercase()
                .replace("MOBILE", "144p")
                .replace("LOWEST", "240p")
                .replace("LOW", "360p")
                .replace("SD", "480p")
                .replace("HD", "720p")
                .replace("FULL", "1080p")
                .replace("QUAD", "1440p")
                .replace("ULTRA", "4k")

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = videoUrl,
                    type = INFER_TYPE
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = getQualityFromName(quality)
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
            
            // Try multiple extraction methods
            var script: String? = null
            
            // Method 1: Unpack packed JavaScript
            val packed = getPacked(response.text)
            if (!packed.isNullOrEmpty()) {
                script = getAndUnpack(response.text)
            }
            
            // Method 2: Look for sources in script tags
            if (script == null) {
                script = response.document.selectFirst("script:containsData(sources:)")?.data()
            }
            
            // Method 3: Look for direct m3u8 in meta tags
            if (script == null) {
                val videoUrl = response.document.selectFirst("meta[property=og:video]")?.attr("content")
                if (!videoUrl.isNullOrEmpty() && videoUrl.contains(".m3u8")) {
                    script = "file:\"$videoUrl\""
                }
            }
            
            script ?: return

            // Extract m3u8 with multiple regex patterns for robustness
            val patterns = listOf(
                "file:\\s*\"(.*?m3u8.*?)\"",
                ":\\s*\"(.*?m3u8.*?)\"",
                "src:\\s*\"(.*?m3u8.*?)\"",
                "\"(https?://[^\"]+?\\.m3u8[^\"]*?)\""
            )
            
            var m3u8: String? = null
            
            for (pattern in patterns) {
                m3u8 = Regex(pattern).find(script)?.groupValues?.getOrNull(1)
                if (!m3u8.isNullOrEmpty()) break
            }
            
            if (!m3u8.isNullOrEmpty()) {
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
            // Log error but don't crash
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

    private val client = okhttp3.OkHttpClient()
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

            val match1 = Regex("""\b[a-zA-Z0-9]{48}\b""").find(responseText)
            val match2 = Regex("""\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b""").find(responseText)
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
                Regex("\"file\":\"(.*?)\"").find(decryptedResponse)?.groupValues?.get(1)
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
// EXTRACTOR REGISTRY (Auto-Register)
// ========================================
// List semua extractor untuk auto-register
// Tambahkan extractor baru di sini setelah dibuat
// ========================================

object AllExtractors {
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

        // VidStack based (7)
        Listeamed(),
        Streamcasthub(),
        Dm21embed(),
        Dm21upns(),
        Pm21p2p(),
        Dm21(),
        Meplayer(),

        // Custom extractors (3)
        Voe(),
        Veev(),
        Dintezuvio(),

        // OK.RU based (3)
        Odnoklassniki(),
        OkRuSSL(),
        OkRuHTTP(),

        // Other extractors (19)
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
    )
}

// ========================================
// TOTAL: 45 EXTRACTOR CLASSES
// ========================================
// Build fix test
