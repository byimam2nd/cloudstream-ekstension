// ========================================
// BASE EXTRACTOR — Shared Logic, Unique Regex
// ========================================
// Arsitektur: 1 base class untuk logika umum,
// setiap extractor hanya perlu override 2 hal:
//   1. Regex pattern (unik per video host)
//   2. Nama + mainUrl (unik per extractor)
// ========================================

package com.{MODULE}

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLinkType.*
import com.lagradost.cloudstream3.network.WebViewResolver

// ========================================
// BASE CLASS — Logika Ekstraktor Umum
// ========================================

/**
 * BaseExtractor — Template pattern untuk semua extractor.
 *
 * Setiap extractor hanya perlu set:
 * - `name` — Nama extractor
 * - `mainUrl` — URL utama
 * - `extractUrlRegex` — Regex untuk cari video URL (WAJIB)
 * - `extractM3u8Regex` — Regex opsional untuk M3U8 (jika beda dari extractUrlRegex)
 * - `urlType` — VIDEO atau M3U8 (default: INFER)
 * - `useUnpack` — Apakah perlu getAndUnpack? (default: false)
 * - `requiresCaptcha` — Apakah perlu bypass captcha? (default: false)
 * - `refererUrl` — Custom referer (default: mainUrl)
 *
 * Flow standar:
 * 1. app.get(embedUrl) → HTML
 * 2. Optional: getAndUnpack(HTML) jika useUnpack = true
 * 3. extractUrlRegex.find(HTML/SCRIPT) → Video URL
 * 4. callback.invoke(newExtractorLink(...))
 */
abstract class BaseExtractor : ExtractorApi() {

    // ===== WAJIB OVERRIDE =====
    override val requiresReferer = true

    // Regex untuk cari video URL (m3u8, mp4, dll)
    abstract val extractUrlRegex: Regex

    // ===== OPSIONAL OVERRIDE =====

    // Regex alternatif khusus M3U8 (jika beda dari extractUrlRegex)
    open val extractM3u8Regex: Regex? = null

    // Tipe link yang dihasilkan (default: auto-detect)
    open val urlType: ExtractorLinkType = INFER_TYPE

    // Apakah perlu unpack packed JS? (default: false)
    open val useUnpack: Boolean = false

    // Apakah perlu bypass captcha/cloudflare? (default: false)
    open val requiresCaptcha: Boolean = false

    // Custom referer (default: mainUrl)
    open val refererUrl: String get() = mainUrl

    // User-Agent custom (default: CloudStream USER_AGENT)
    open val customUserAgent: String? = null

    // ===== STANDARD GET HEADERS =====
    protected open fun buildHeaders(referer: String? = null): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "Accept-Encoding" to "gzip, deflate, br",
        "Accept-Language" to "en-US,en;q=0.9",
        "Connection" to "keep-alive",
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "cross-site"
    ) + (referer?.let { mapOf("Referer" to it) } ?: mapOf("Referer" to refererUrl))

    // ===== MAIN GETURL (TEMPLATE PATTERN) =====

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            // Step 1: Fetch page
            val response = app.get(
                url,
                referer = referer ?: refererUrl,
                headers = customUserAgent?.let {
                    buildHeaders(referer) + ("User-Agent" to it)
                } ?: buildHeaders(referer)
            )

            // Step 2: Extract script content
            val content = if (requiresCaptcha) {
                response.document.selectFirst("script:containsData(sources:)")?.data()
                    ?: response.document.selectFirst("script:containsData(file:)")?.data()
                    ?: response.text
            } else if (useUnpack) {
                val packed = getPacked(response.text)
                if (!packed.isNullOrEmpty()) getAndUnpack(response.text)
                else response.document.selectFirst("script:containsData(sources:)")?.data()
                    ?: response.document.selectFirst("script:containsData(file:)")?.data()
                    ?: response.text
            } else {
                response.document.selectFirst("script:containsData(sources:)")?.data()
                    ?: response.document.selectFirst("script:containsData(file:)")?.data()
                    ?: response.text
            }

            // Step 3: Extract video URL
            val videoUrl = extractM3u8Regex?.find(content)?.groupValues?.getOrNull(1)
                ?: extractUrlRegex.find(content)?.groupValues?.getOrNull(1)

            if (videoUrl == null) {
                Log.d("BaseExtractor", "[$name] No video URL found for: $url")
                return
            }

            val cleanUrl = videoUrl.replace("\\/", "/")

            // Step 4: Build and return ExtractorLink
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = cleanUrl,
                    type = urlType
                ) {
                    this.referer = refererUrl
                    this.headers = buildHeaders(refererUrl)
                }
            )

            Log.d("BaseExtractor", "[$name] Found: $cleanUrl")

        } catch (e: Exception) {
            Log.e("BaseExtractor", "[$name] Error: ${e.message}")
        }
    }
}

// ========================================
// CONVENIENCE SUBCLASSES — Pola Umum
// ========================================

/**
 * RegexOnlyExtractor — Hanya butuh regex.
 * Untuk video host yang taruh URL langsung di HTML (tanpa packed JS).
 *
 * Contoh: OK.ru, Rumble
 */
abstract class RegexExtractor : BaseExtractor() {
    override val useUnpack = false
}

/**
 * PackedJsExtractor — Butuh unpack JS dulu.
 * Untuk video host yang hide URL di eval(function(p,a,c,k,e,d).
 *
 * Contoh: Voe, MixDropBz, FilemoonNl
 */
abstract class PackedJsExtractor : BaseExtractor() {
    override val useUnpack = true
}

/**
 * M3U8Extractor — Khusus untuk M3U8 playlist.
 * Auto-set type = M3U8, regex cari pattern m3u8.
 *
 * Contoh: StreamRuby, EmturbovidExtractorM3U8
 */
abstract class M3U8Extractor : BaseExtractor() {
    override val urlType = ExtractorLinkType.M3U8
    override val extractM3u8Regex = Regex("file:\\s*\"(.*?m3u8.*?)\"")
}

// ========================================
// CONTOH MIGRASI — Extractor yang Sudah Ada
// ========================================

// --- SEBELUM (40+ baris) ---
// class OkRuSSL : ExtractorApi() {
//     override val name = "OkRuSSL"
//     override val mainUrl = "https://ok.ru"
//     override val requiresReferer = false
//     override suspend fun getUrl(...) {
//         val headers = mapOf(...)
//         val embedUrl = url.replace("/video/", "/videoembed/")
//         val videoReq = app.get(embedUrl, headers = headers).text
//             .replace("\\&quot;", "\"").replace("\\\\", "\\")
//             .replace(Regex("\\\\u([0-9A-Fa-f]{4})")) { ... }
//         val videosStr = Regex("""\"videos\":(\[[^]]*])""").find(videoReq)?.groupValues?.get(1) ?: return
//         val videos = tryParseJson<List<OkRuVideo>>(videosStr) ?: return
//         for (video in videos) { ... }
//     }
// }

// --- SESUDAH (5 baris) ---
// class OkRuSSL : RegexExtractor() {
//     override val name = "OkRuSSL"
//     override val mainUrl = "https://ok.ru"
//     override val requiresReferer = false
//     override val extractUrlRegex = Regex(""""url":"(https://[^"]+)"""")
//     // (perlu override getUrl untuk JSON parsing khusus OK.ru)
// }

// --- SESUDAH untuk extractor simple (3 baris) ---
// class Rumble : RegexExtractor() {
//     override val name = "Rumble"
//     override val mainUrl = "https://rumble.com"
//     override val extractUrlRegex = Regex(""""hls":\{"url":"(https?:[^"]+playlist\.m3u8)"""")
// }

// --- SESUDAH untuk extractor packed JS (5 baris) ---
// class FilemoonNlExtractor : PackedJsExtractor() {
//     override val name = "FilemoonNl"
//     override val mainUrl = "https://filemoon.nl"
//     override val extractUrlRegex = Regex("file:\\s*\"(.*?m3u8.*?)\"")
//     override val urlType = ExtractorLinkType.M3U8
// }
