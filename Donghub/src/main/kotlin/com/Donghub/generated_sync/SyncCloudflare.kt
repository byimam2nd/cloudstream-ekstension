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
package com.Donghub.generated_sync

import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

// ============================================
// REGION: CLOUDFLARE DETECTION
// ============================================

/**
 * Deteksi apakah response terkena Cloudflare challenge.
 *
 * @param body Response body sebagai string
 * @return true jika terkena Cloudflare challenge
 */
fun isCloudflareChallenge(body: String): Boolean {
    return body.contains("cf-browser-verification", ignoreCase = true) ||
           body.contains("cf-challenge", ignoreCase = true) ||
           body.contains("Checking your browser", ignoreCase = true) ||
           body.contains("Just a moment", ignoreCase = true) ||
           body.contains("jschl_answer", ignoreCase = true)
}

// ============================================
// REGION: CLOUDFLARE SOLVER
// ============================================

/**
 * Cloudflare solver menggunakan CloudflareKiller bawaan CloudStream.
 */
object CloudflareSolver {

    private const val TAG = "CloudflareSolver"
    private val cloudflareKiller = CloudflareKiller()

    /**
     * GET request dengan Cloudflare bypass.
     */
    suspend fun cloudflareGet(
        url: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap()
    ): String? {
        return try {
            val response = app.get(url, referer = referer, headers = headers)
            val body = response.text

            if (!isCloudflareChallenge(body)) {
                return body
            }

            Log.d(TAG, "Cloudflare challenge detected, retrying with CloudflareKiller")
            val cfResponse = app.get(
                url,
                referer = referer,
                interceptor = cloudflareKiller,
                headers = headers
            )
            cfResponse.text
        } catch (e: Exception) {
            Log.e(TAG, "cloudflareGet failed: ${e.message}")
            null
        }
    }

    /**
     * POST request dengan Cloudflare bypass.
     */
    suspend fun cloudflarePost(
        url: String,
        data: Map<String, String> = emptyMap(),
        referer: String? = null,
        headers: Map<String, String> = emptyMap()
    ): String? {
        return try {
            val response = app.post(url, data = data, referer = referer, headers = headers)
            val body = response.text

            if (!isCloudflareChallenge(body)) {
                return body
            }

            Log.d(TAG, "Cloudflare challenge on POST, retrying with CloudflareKiller")
            val cfResponse = app.post(
                url,
                data = data,
                referer = referer,
                interceptor = cloudflareKiller,
                headers = headers
            )
            cfResponse.text
        } catch (e: Exception) {
            Log.e(TAG, "cloudflarePost failed: ${e.message}")
            null
        }
    }

    /**
     * Parse HTML string menjadi Document.
     */
    fun parseHtml(html: String?): Document? {
        return html?.let {
            try {
                Jsoup.parse(it)
            } catch (e: Exception) {
                Log.e(TAG, "parseHtml failed: ${e.message}")
                null
            }
        }
    }
}
