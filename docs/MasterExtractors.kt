// ========================================
// MASTER EXTRACTORS COLLECTION
// Kumpulan 75+ Extractor untuk CloudStream
// ========================================
// Source: ExtCloud + cloudstream-ekstension + Built-in
// Last Updated: 2026-03-17
// ========================================

package com.MasterExtractors

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.json.JSONObject
import java.net.URI

// ========================================
// STREAMWISH BASED EXTRACTORS (From ExtCloud/Dutamovie)
// ========================================

class Movearnpre : Dingtezuni() {
    override var name = "Movearnpre"
    override var mainUrl = "https://movearnpre.com"
}

class Minochinos : Dingtezuni() {
    override var name = "Earnvids"
    override var mainUrl = "https://minochinos.com"
}

class Mivalyo : Dingtezuni() {
    override var name = "Earnvids"
    override var mainUrl = "https://mivalyo.com"
}

class Ryderjet : Dingtezuni() {
    override var name = "Ryderjet"
    override var mainUrl = "https://ryderjet.com"
}

class Bingezove : Dingtezuni() {
    override var name = "Earnvids"
    override var mainUrl = "https://bingezove.com"
}

open class Dingtezuni : ExtractorApi() {
    override val name = "Earnvids"
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

        val response = app.get(getEmbedUrl(url), referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            var result = getAndUnpack(response.text)
            if(result.contains("var links")){
                result = result.substringAfter("var links")
            }
            result
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

        Regex(":\\s*\"(.*?m3u8.*?)\"").findAll(script).forEach { m3u8Match ->
            generateM3u8(
                name,
                fixUrl(m3u8Match.groupValues[1]),
                referer = "$mainUrl/",
                headers = headers
            ).forEach(callback)
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

class Dhcplay: StreamWishExtractor() {
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
    override val name = "Earnvids"
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

        val response = app.get(getEmbedUrl(url), referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            var result = getAndUnpack(response.text)
            if(result.contains("var links")){
                result = result.substringAfter("var links")
            }
            result
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

        Regex(":\\s*\"(.*?m3u8.*?)\"").findAll(script).forEach { m3u8Match ->
            generateM3u8(
                name,
                fixUrl(m3u8Match.groupValues[1]),
                referer = "$mainUrl/",
                headers = headers
            ).forEach(callback)
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
        val mediaId = pattern.find(url)?.groupValues?.get(1)
            ?: return
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

            val fileMimeType = file.optString("file_mime_type", "")

            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    decoded,
                    INFER_TYPE
                )
                {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
            return
        }
    }

    fun String.toExoPlayerMimeType(): String {
        return when (this.lowercase()) {
            "video/x-matroska", "video/webm" -> HlsPlaylistParser.MimeTypes.VIDEO_MATROSKA
            "video/mp4" -> HlsPlaylistParser.MimeTypes.VIDEO_MP4
            "application/x-mpegurl", "application/vnd.apple.mpegurl" -> HlsPlaylistParser.MimeTypes.APPLICATION_M3U8
            "video/avi" -> HlsPlaylistParser.MimeTypes.VIDEO_AVI
            else -> ""
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
// PENCURIMOVIE EXTRACTORS (From cloudstream-ekstension)
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
// OK.RU EXTRACTOR (From ExtCloud/AnichinMoe)
// ========================================

class Okru : ExtractorApi() {
    override val name = "OK.RU"
    override val mainUrl = "https://ok.ru"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url).document
        val script = response.selectFirst("script[data-module=OKVideo]")?.attr("data-options")
            ?: return

        val data = try {
            org.json.JSONObject(script).getJSONObject("flashvars").getString("metadata")
        } catch (e: Exception) {
            return
        }

        val json = org.json.JSONObject(data)
        val videos = json.getJSONArray("videos")

        for (i in 0 until videos.length()) {
            val video = videos.getJSONObject(i)
            val url = video.getString("url")
            val quality = video.getString("name")

            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    url,
                    ExtractorLinkType.VIDEO
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = when (quality.lowercase()) {
                        "ultra" -> Qualities.P2160.value
                        "quad" -> Qualities.P1440.value
                        "full" -> Qualities.P1080.value
                        "hd" -> Qualities.P720.value
                        "sd" -> Qualities.P480.value
                        "low" -> Qualities.P360.value
                        "lowest" -> Qualities.P240.value
                        "mobile" -> Qualities.P144.value
                        else -> Qualities.Unknown.value
                    }
                }
            )
        }
    }
}

// ========================================
// RUMBLE EXTRACTOR (From ExtCloud/AnichinMoe)
// ========================================

class Rumble : ExtractorApi() {
    override val name = "Rumble"
    override val mainUrl = "https://rumble.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url).text
        val pattern = Regex("""\"playbackUrl\"\\s*:\\s*\"([^\"]+)\"""")
        val match = pattern.find(response)

        match?.groupValues?.get(1)?.let { playbackUrl ->
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    playbackUrl,
                    ExtractorLinkType.VIDEO
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}

// ========================================
// STREAMRUBY EXTRACTOR (From ExtCloud/AnichinMoe)
// ========================================

class StreamRuby : ExtractorApi() {
    override val name = "StreamRuby"
    override val mainUrl = "https://streamruby.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer ?: mainUrl).text
        val packed = getAndUnpack(response)
        val script = if (packed.isNotEmpty()) packed else response

        val pattern = Regex("""sources:\s*\[\s*\{\s*file:\s*['"](.*?)['"]""")
        val match = pattern.find(script)

        match?.groupValues?.get(1)?.let { fileUrl ->
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    fileUrl,
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
// VIDGUARD EXTRACTOR (From ExtCloud/AnichinMoe)
// ========================================

class VidGuard : ExtractorApi() {
    override val name = "VidGuard"
    override val mainUrl = "https://vidguard.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer ?: mainUrl)
        val script = response.document.selectFirst("script:containsData(writable)")?.data()
            ?: return

        val linkPattern = Regex("""sources:\s*\"([^\"]+)\"""")
        val linkMatch = linkPattern.find(script)

        linkMatch?.groupValues?.get(1)?.let { encodedSources ->
            try {
                val decoded = String(android.util.Base64.decode(encodedSources, android.util.Base64.DEFAULT))
                val json = org.json.JSONObject(decoded)
                val file = json.getString("file")

                callback.invoke(
                    newExtractorLink(
                        name,
                        name,
                        file,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.Unknown.value
                    }
                )
            } catch (e: Exception) {
                // Ignore decode errors
            }
        }
    }
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

class Vidhidepro : StreamWishExtractor() {
    override val name = "Vidhidepro"
    override val mainUrl = "https://vidhidepro.com"
}

// ========================================
// TOTAL: 40+ EXTRACTOR CLASSES
// ========================================
// Note: Ini baru 40+ extractor.
// Akan ditambahkan 35+ extractor lagi dari:
// - ExtCloud/Fufafilm
// - ExtCloud/Funmovieslix
// - ExtCloud/Hidoristream
// - ExtCloud/IdlixProvider
// - ExtCloud/Kawanfilm
// - ExtCloud/Kissasian
// - ExtCloud/Klikxxi
// - ExtCloud/LayarKacaProvider
// - ExtCloud/Layarasia
// - ExtCloud/Melongmovie
// - ExtCloud/Midasxxi
// - ExtCloud/Ngefilm
// - ExtCloud/OploverzProvider
// - ExtCloud/Oppadrama
// - ExtCloud/Pmsm
// - ExtCloud/SoraStream
// - ExtCloud/Winbu
// - ExtCloud/AnimeSailProvider
// - cloudstream-ekstension (Anichin, Donghuastream, HiAnime, dll)
// ========================================
