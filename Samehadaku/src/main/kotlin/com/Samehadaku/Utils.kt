// ========================================
// UTILS - Helper Functions
// ========================================
// Standard: cloudstream-ekstension
// Berisi helper functions untuk:
// - Image attribute extraction
// - Quality parsing
// - User agent generation
// - URL fixing
// ========================================

package com.Samehadaku

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import org.jsoup.nodes.Element
import kotlin.random.Random

/**
 * Extension function untuk extract image URL dari Element
 * Mendukung berbagai attribute: src, data-src, data-lazy-src, srcset
 */
fun Element.getImageAttr(): String? {
    return when {
        // Check data-src attribute
        this.hasAttr("data-src") -> this.attr("abs:data-src")
        
        // Check data-lazy-src attribute
        this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
        
        // Check srcset attribute (ambil URL pertama)
        this.hasAttr("srcset") -> {
            val srcset = this.attr("abs:srcset")
            srcset.substringBefore(" ").trim().ifEmpty { null }
        }
        
        // Check src attribute
        this.hasAttr("src") -> this.attr("abs:src")
        
        // Fallback: check data-srcset
        this.hasAttr("data-srcset") -> {
            val srcset = this.attr("abs:data-srcset")
            srcset.substringBefore(" ").trim().ifEmpty { null }
        }
        
        else -> null
    }
}

/**
 * Parse quality dari string (contoh: "720p", "1080p", "HD")
 * @param str String yang berisi quality
 * @return Quality dalam angka (720, 1080, dll) atau Qualities.Unknown.value
 */
fun getIndexQuality(str: String?): Int {
    if (str.isNullOrEmpty()) return com.lagradost.cloudstream3.utils.Qualities.Unknown.value
    
    // Try to find numeric quality (720, 1080, 480, etc)
    val numericQuality = Regex("(\\d{3,4})[pP]").find(str)?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (numericQuality != null) return numericQuality
    
    // Check for quality keywords
    return when {
        str.contains("4k", ignoreCase = true) || str.contains("2160", ignoreCase = true) -> 2160
        str.contains("1080", ignoreCase = true) || str.contains("fhd", ignoreCase = true) -> 1080
        str.contains("720", ignoreCase = true) || str.contains("hd", ignoreCase = true) -> 720
        str.contains("480", ignoreCase = true) || str.contains("sd", ignoreCase = true) -> 480
        str.contains("360", ignoreCase = true) -> 360
        else -> com.lagradost.cloudstream3.utils.Qualities.Unknown.value
    }
}

/**
 * Generate random User-Agent
 * @return Random User-Agent string
 */
fun getRandomUserAgent(): String {
    val androidVersions = listOf("10", "11", "12", "13")
    val devices = listOf(
        "SM-G998B",
        "SM-G991B",
        "Pixel 6",
        "Pixel 7",
        "OnePlus 9",
        "OnePlus 10",
        "Mi 11",
        "Mi 12"
    )
    
    val androidVersion = androidVersions.random()
    val device = devices.random()
    
    return "Mozilla/5.0 (Linux; Android $androidVersion; $device Build/SP1A.210812.016; wv) " +
           "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.230 " +
           "Mobile Safari/537.36"
}

/**
 * Fix URL dengan validation
 * @param url URL yang akan di-fix
 * @param baseUrl Base URL (optional, default: mainUrl)
 * @return Fixed URL atau URL asli jika sudah valid
 */
fun fixUrlSafe(url: String, baseUrl: String = "https://v1.animasu.top"): String {
    // Skip jika sudah http/https
    if (url.startsWith("http://") || url.startsWith("https://")) {
        return url
    }
    
    // Fix relative URL dengan menambahkan baseUrl
    return if (url.startsWith("/")) {
        "$baseUrl$url"
    } else {
        "$baseUrl/$url"
    }
}

/**
 * Base64 decode dengan error handling
 * @param str String yang akan di-decode
 * @return Decoded string atau original string jika gagal
 */
fun safeBase64Decode(str: String): String {
    return try {
        String(java.util.Base64.getDecoder().decode(str.trim()))
    } catch (e: Exception) {
        str
    }
}

/**
 * Extract JSON dari JavaScript code
 * @param js JavaScript code
 * @param regex Regex pattern untuk extract JSON
 * @return JSON string atau null
 */
fun extractJsonFromJs(js: String, regex: String): String? {
    return try {
        Regex(regex).find(js)?.groupValues?.getOrNull(1)
    } catch (e: Exception) {
        null
    }
}

/**
 * Sleep dengan jitter (random delay)
 * @param baseDelay Base delay dalam milliseconds
 * @param jitter Max jitter dalam milliseconds
 */
suspend fun sleepWithJitter(baseDelay: Long = 500L, jitter: Long = 200L) {
    val delay = baseDelay + Random.nextLong(jitter)
    kotlinx.coroutines.delay(delay)
}

/**
 * Check apakah URL valid
 * @param url URL yang akan di-check
 * @return true jika valid, false jika tidak
 */
fun isValidUrl(url: String): Boolean {
    return try {
        url.startsWith("http://") || url.startsWith("https://")
    } catch (e: Exception) {
        false
    }
}

/**
 * Normalize title untuk comparison
 * @param title Title yang akan di-normalize
 * @return Normalized title
 */
fun normalizeTitle(title: String): String {
    return title
        .lowercase()
        .replace(Regex("[^a-z0-9\\s]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/**
 * Extract episode number dari string
 * @param text Text yang berisi episode number
 * @return Episode number atau null
 */
fun extractEpisodeNumber(text: String): Int? {
    // Try various patterns
    val patterns = listOf(
        Regex("Episode\\s*(\\d+)"),
        Regex("Ep\\s*(\\d+)"),
        Regex("(\\d+)"),
        Regex("#(\\d+)")
    )
    
    for (pattern in patterns) {
        val match = pattern.find(text)
        val episode = match?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (episode != null) return episode
    }
    
    return null
}
