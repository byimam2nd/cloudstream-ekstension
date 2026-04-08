// ========================================
// MASTER CLOUDFLARE BYPASS
// ========================================
// Cloudflare challenge handling untuk providers
// yang dilindungi Cloudflare (503, 403, JS challenge)
// ========================================
// Last Updated: 2026-04-08
// ========================================

// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/MasterCloudflare.kt
// File: SyncCloudflare.kt
// ========================================
package com.Donghuastream.generated_sync

import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.NiceResponse

typealias CFResponse = NiceResponse
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

// ============================================
// REGION: CLOUDFLARE DETECTION
// ============================================

/**
 * Deteksi apakah response terkena Cloudflare challenge.
 *
 * Indikator:
 * - Status 503 + "Checking your browser" 
 * - Status 403 + "Access Denied"
 * - Response mengandung "cf-chl-bypass"
 * - Response mengandung "cf-browser-verification"
 *
 * @param response HTTP response
 * @param body Response body sebagai string
 * @return true jika terkena Cloudflare challenge
 */
fun isCloudflareChallenge(response: CFResponse?, body: String? = null): Boolean {
    // Check HTTP status
    if (response != null) {
        if (response.code == 503 || response.code == 403) {
            val challengeHeaders = listOf("cf-chl-bypass", "cf-browser-verification")
            if (response.headers.names().any { it.lowercase() in challengeHeaders }) {
                return true
            }
        }
    }

    // Check response body
    val html = body ?: response?.text ?: return false
    return html.contains("cf-browser-verification", ignoreCase = true) ||
           html.contains("cf-challenge", ignoreCase = true) ||
           html.contains("Checking your browser", ignoreCase = true) ||
           html.contains("Just a moment", ignoreCase = true) ||
           html.contains("jschl_answer", ignoreCase = true)
}

/**
 * Deteksi apakah URL mengarah ke Cloudflare-protected site.
 *
 * @param url URL untuk dicek
 * @return true jika kemungkinan dilindungi Cloudflare
 */
fun isLikelyCloudflareProtected(url: String): Boolean {
    val protectedDomains = listOf(
        "cloudflare",
        "idlixian",     // Idlix
        "lk21",         // LayarKaca
        "dramacool",
        "gomovies",
        "fmovies"
    )
    return protectedDomains.any { url.contains(it, ignoreCase = true) }
}

// ============================================
// REGION: CLOUDFLARE SOLVER
// ============================================

/**
 * Cloudflare solver menggunakan CloudflareKiller bawaan CloudStream.
 *
 * Cara kerja:
 * 1. Detect Cloudflare challenge
 * 2. Gunakan CloudflareKiller untuk solve JS challenge
 * 3. Retry request dengan cookie cf-clearance
 *
 * Usage:
 * ```kotlin
 * val response = cloudflareGet(url, referer)
 * val document = response?.documentLarge
 * ```
 */
object CloudflareSolver {

    private const val TAG = "CloudflareSolver"
    private val cloudflareKiller = CloudflareKiller()

    /**
     * GET request dengan Cloudflare bypass.
     *
     * Jika response pertama terkena CF challenge,
     * otomatis retry dengan CloudflareKiller.
     *
     * @param url Target URL
     * @param referer Referer header
     * @param userAgent Custom User-Agent (optional)
     * @param maxRetries Maksimal retry (default: 2)
     * @return Response object
     */
    suspend fun cloudflareGet(
        url: String,
        referer: String? = null,
        userAgent: String? = null,
        maxRetries: Int = 2
    ): CFResponse? {
        return try {
            // Attempt 1: Normal request
            val response = app.get(
                url,
                referer = referer,
                headers = userAgent?.let { mapOf("User-Agent" to it) } ?: emptyMap()
            )

            val body = response.text
            if (!isCloudflareChallenge(response, body)) {
                return response  // Success, not CF-protected
            }

            Log.d(TAG, "Cloudflare challenge detected for $url")

            // Attempt 2+: Retry with CloudflareKiller
            var cfResponse = response
            repeat(maxRetries) { attempt ->
                Log.d(TAG, "CloudflareKiller attempt ${attempt + 1}/$maxRetries")

                val cfResponseInner = app.get(
                    url,
                    referer = referer,
                    interceptor = cloudflareKiller,
                    headers = userAgent?.let { mapOf("User-Agent" to it) } ?: emptyMap()
                )

                val cfBody = cfResponseInner.text
                if (!isCloudflareChallenge(cfResponseInner, cfBody)) {
                    Log.d(TAG, "Cloudflare bypassed on attempt ${attempt + 1}")
                    return cfResponseInner
                }
                cfResponse = cfResponseInner
            }

            Log.w(TAG, "Cloudflare bypass failed after $maxRetries attempts")
            cfResponse
        } catch (e: Exception) {
            Log.e(TAG, "cloudflareGet failed: ${e.message}")
            null
        }
    }

    /**
     * POST request dengan Cloudflare bypass.
     *
     * @param url Target URL
     * @param data POST body
     * @param referer Referer header
     * @param headers Additional headers
     * @return Response object
     */
    suspend fun cloudflarePost(
        url: String,
        data: Map<String, String> = emptyMap(),
        referer: String? = null,
        headers: Map<String, String> = emptyMap()
    ): CFResponse? {
        return try {
            val response = app.post(url, data = data, referer = referer, headers = headers)

            val body = response.text
            if (!isCloudflareChallenge(response, body)) {
                return response
            }

            Log.d(TAG, "Cloudflare challenge on POST $url")
            app.post(
                url,
                data = data,
                referer = referer,
                interceptor = cloudflareKiller,
                headers = headers
            )
        } catch (e: Exception) {
            Log.e(TAG, "cloudflarePost failed: ${e.message}")
            null
        }
    }

    /**
     * Parse response menjadi Document.
     *
     * @param response Response dari cloudflareGet
     * @return Jsoup Document atau null
     */
    fun parseDocument(response: CFResponse?): Document? {
        return response?.let {
            try {
                Jsoup.parse(it.text)
            } catch (e: Exception) {
                Log.e(TAG, "parseDocument failed: ${e.message}")
                null
            }
        }
    }

    /**
     * Get cf-clearance cookie dari CloudflareKiller.
     *
     * Cookie ini bisa disimpan dan dipakai ulang
     * untuk request berikutnya (TTL ~30 menit).
     *
     * @return cf-clearance cookie value atau null
     */
    fun getCfClearanceCookie(): String? {
        return cloudflareKiller.cookies.firstOrNull { it.name == "cf-clearance" }?.value
    }

    /**
     * Set cf-clearance cookie manual.
     *
     * Berguna jika user memberikan cookie manual.
     *
     * @param token cf-clearance token
     * @param domain Domain target
     */
    fun setCfClearanceCookie(token: String, domain: String) {
        val cookie = okhttp3.Cookie.Builder()
            .name("cf-clearance")
            .value(token)
            .domain(domain)
            .build()
        cloudflareKiller.cookies.add(cookie)
    }
}
