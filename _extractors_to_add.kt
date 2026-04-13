
// ========================================
// NEW EXTRACTORS FROM PHERISHER + EXTCLOUD
// Added: 2026-04-13
// 16 extractors matching user's 8 providers
// ========================================

// --- VideyV2 (from Phisher/Funmovieslix) ---
// AES/CBC decryption, API-based (api.lixstreamingcaio.com)
class VideyV2 : ExtractorApi() {
    override var name = "Videy"
    override var mainUrl = "https://videy.tv"
    override val requiresReferer = false

    private val apiBase = "https://api.lixstreamingcaio.com/v2"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val sid = url.substringAfterLast("/")
        val infoRes = app.post(
            "$apiBase/s/home/resources/$sid",
            data = mapOf(),
            headers = mapOf("Content-Type" to "application/json")
        ).text

        val info = runCatching { JSONObject(infoRes) }.getOrNull() ?: return
        val suid = info.optString("suid") ?: return
        val files = info.optJSONArray("files") ?: return
        if (files.length() == 0) return
        val file = files.optJSONObject(0) ?: return
        val fid = file.optString("id") ?: return
        val assetRes = app.get("$apiBase/s/assets/f?id=$fid&uid=$suid").text
        val asset = runCatching { JSONObject(assetRes) }.getOrNull() ?: return
        val encryptedUrl = asset.optString("url")
        if (encryptedUrl.isNullOrEmpty()) return

        val key = "GNgN1lHXIFCQd8hSEZIeqozKInQTFNXj".toByteArray(Charsets.UTF_8)
        val iv = "2Xk4dLo38c9Z2Q2a".toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

        val decrypted = runCatching {
            val decoded = base64DecodeArray(encryptedUrl)
            String(cipher.doFinal(decoded), Charsets.UTF_8)
        }.getOrNull() ?: return

        callback.invoke(
            newExtractorLink(this.name, this.name, decrypted,
                if (decrypted.contains(".mp4")) ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8
            ) {
                this.referer = url
                this.quality = Qualities.P1080.value
            }
        )
    }
}

// --- ByseSX (from Phisher/Funmovieslix) ---
// AES/GCM decryption, complex API flow
open class ByseSX : ExtractorApi() {
    override var name = "Byse"
    override var mainUrl = "https://byse.sx"
    override val requiresReferer = true

    private fun b64UrlDecode(s: String): ByteArray {
        val fixed = s.replace('-', '+').replace('_', '/')
        val pad = (4 - fixed.length % 4) % 4
        return base64DecodeArray(fixed + "=".repeat(pad))
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }

    private fun getCodeFromUrl(url: String): String {
        val path = URI(url).path ?: return ""
        return Regex("""/e/([^/]+)""").find(path)?.groupValues?.get(1)
            ?: path.trimEnd('/').substringAfterLast('/')
    }

    private suspend fun getDetails(mainUrl: String): ByseDetailsRoot? {
        val base = getBaseUrl(mainUrl)
        val code = getCodeFromUrl(mainUrl)
        return app.get("$base/api/videos/$code/embed/details").parsedSafe<ByseDetailsRoot>()
    }

    private suspend fun getPlayback(mainUrl: String): BysePlaybackRoot? {
        val details = getDetails(mainUrl) ?: return null
        val embedFrameUrl = details.embedFrameUrl
        val embedBase = getBaseUrl(embedFrameUrl)
        val code = getCodeFromUrl(embedFrameUrl)
        val headers = mapOf(
            "accept" to "*/*", "accept-language" to "en-US,en;q=0.5",
            "priority" to "u=1, i", "referer" to embedFrameUrl,
            "x-embed-parent" to mainUrl
        )
        return app.get("$embedBase/api/videos/$code/embed/playback", headers = headers)
            .parsedSafe<BysePlaybackRoot>()
    }

    private fun buildAesKey(playback: BysePlayback): ByteArray {
        return b64UrlDecode(playback.keyParts[0]) + b64UrlDecode(playback.keyParts[1])
    }

    private fun decryptPlayback(playback: BysePlayback): String? {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,
            SecretKeySpec(buildAesKey(playback), "AES"),
            GCMParameterSpec(128, b64UrlDecode(playback.iv)))
        var jsonStr = String(cipher.doFinal(b64UrlDecode(playback.payload)), StandardCharsets.UTF_8)
        if (jsonStr.startsWith("\uFEFF")) jsonStr = jsonStr.substring(1)
        return tryParseJson<BysePlaybackDecrypt>(jsonStr)?.sources?.firstOrNull()?.url
    }

    override suspend fun getUrl(
        url: String, referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val refererUrl = getBaseUrl(url)
        val playbackRoot = getPlayback(url) ?: return
        val streamUrl = decryptPlayback(playbackRoot.playback) ?: return
        M3u8Helper.generateM3u8(name, streamUrl, mainUrl, headers = mapOf("Referer" to refererUrl))
            .forEach(callback)
    }
}

data class ByseDetailsRoot(
    val id: Long, val code: String, val title: String,
    @JsonProperty("poster_url") val posterUrl: String,
    val description: String, @JsonProperty("created_at") val createdAt: String,
    @JsonProperty("owner_private") val ownerPrivate: Boolean,
    @JsonProperty("embed_frame_url") val embedFrameUrl: String
)

data class BysePlaybackRoot(val playback: BysePlayback)

data class BysePlayback(
    val algorithm: String, val iv: String, val payload: String,
    @JsonProperty("key_parts") val keyParts: List<String>,
    @JsonProperty("expires_at") val expiresAt: String,
    @JsonProperty("decrypt_keys") val decryptKeys: ByseDecryptKeys,
    val iv2: String, val payload2: String
)

data class ByseDecryptKeys(
    @JsonProperty("edge_1") val edge1: String,
    @JsonProperty("edge_2") val edge2: String,
    @JsonProperty("legacy_fallback") val legacyFallback: String
)

data class BysePlaybackDecrypt(val sources: List<BysePlaybackSource>)

data class BysePlaybackSource(
    val quality: String, val label: String,
    @JsonProperty("mime_type") val mimeType: String,
    val url: String, @JsonProperty("bitrate_kbps") val bitrateKbps: Long, val height: Any?
)

// --- Bysezoxexe (from Phisher/Funmovieslix) ---
class Bysezoxexe : ByseSX() {
    override var name = "Bysezoxexe"
    override var mainUrl = "https://bysezoxexe.com"
}

// --- Ryderjet (from ExtCloud/Funmovieslix) ---
class Ryderjet : VidhideExtractor() {
    override var mainUrl = "https://ryderjet.com"
}

// --- Vidhideplus (from ExtCloud/Funmovieslix) ---
class Vidhideplus : VidhideExtractor() {
    override var mainUrl = "https://vidhideplus.com"
}

// --- Dhtpre (from ExtCloud/Funmovieslix) ---
class Dhtpre : VidhideExtractor() {
    override var mainUrl = "https://dhtpre.com"
}

// --- F75s (from ExtCloud/Funmovieslix) ---
class F75s : VidhideExtractor() {
    override var mainUrl = "https://f75s.com"
}

// --- EmturbovidExtractor (from ExtCloud/Layarasia) ---
open class EmturbovidExtractor : ExtractorApi() {
    override var name = "Emturbovid"
    override var mainUrl = "https://turbovidhls.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val ref = referer ?: "$mainUrl/"
        val headers = mapOf(
            "Referer" to "$mainUrl/", "Origin" to mainUrl,
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            "Accept" to "*/*"
        )
        val page = app.get(url, referer = ref)
        val playerScript = page.document.selectXpath("//script[contains(text(),'var urlPlay')]").html()
        if (playerScript.isBlank()) return null

        var masterUrl = playerScript.substringAfter("var urlPlay = '").substringBefore("'").trim()
        if (masterUrl.startsWith("//")) masterUrl = "https:$masterUrl"
        if (masterUrl.startsWith("/")) masterUrl = mainUrl + masterUrl

        val masterText = app.get(masterUrl, headers = headers).text
        val lines = masterText.lines()
        val out = mutableListOf<ExtractorLink>()
        for (i in 0 until lines.size) {
            val line = lines[i].trim()
            if (!line.startsWith("#EXT-X-STREAM-INF")) continue
            val qualityLine = lines.getOrNull(i + 1)?.trim() ?: continue
            if (!qualityLine.startsWith("http")) continue
            val resolution = Regex("RESOLUTION=\\d+x(\\d+)").find(line)?.groupValues?.getOrNull(1)
            val quality = resolution?.toIntOrNull() ?: 480
            out.add(newExtractorLink(name, name, qualityLine, ExtractorLinkType.M3U8) {
                this.referer = "$mainUrl/"; this.quality = quality; this.headers = headers
            })
        }
        return out.ifEmpty { null }
    }
}

// --- Nunap2p (from ExtCloud/Layarasia) ---
class Nunap2p : Dingtezuni() {
    override var name = "Nunap2p"
    override var mainUrl = "https://nunap2p.com"
}

// --- Nunastrp (from ExtCloud/Layarasia) ---
class Nunastrp : Dingtezuni() {
    override var name = "Nunastrp"
    override var mainUrl = "https://nunastrp.com"
}

// --- Nunaupns (from ExtCloud/Layarasia) ---
class Nunaupns : Dingtezuni() {
    override var name = "Nunaupns"
    override var mainUrl = "https://nunaupns.com"
}

// --- Nunaxyz (from ExtCloud/Layarasia) ---
class Nunaxyz : Dingtezuni() {
    override var name = "Nunaxyz"
    override var mainUrl = "https://nunaxyz.com"
}

// --- BuzzServer (from ExtCloud/Layarasia) ---
class BuzzServer : ExtractorApi() {
    override var name = "BuzzServer"
    override var mainUrl = "https://buzzserver.xyz"
    override val requiresReferer = true
}

// --- PlayStreamplay (from Phisher/Donghuastream) ---
class PlayStreamplay : ExtractorApi() {
    override var name = "PlayStreamplay"
    override var mainUrl = "https://playstreamplay.com"
    override val requiresReferer = true
}

// --- Ultrahd (from Phisher/Donghuastream) ---
class Ultrahd : ExtractorApi() {
    override var name = "Ultrahd"
    override var mainUrl = "https://ultrahd.to"
    override val requiresReferer = true
}

// --- Vtbe (from Phisher/Donghuastream) ---
class Vtbe : ExtractorApi() {
    override var name = "Vtbe"
    override var mainUrl = "https://vtbe.com"
    override val requiresReferer = true
}

// --- wishfast (from Phisher/Donghuastream) ---
class wishfast : ExtractorApi() {
    override var name = "wishfast"
    override var mainUrl = "https://wishfast.to"
    override val requiresReferer = true
}
