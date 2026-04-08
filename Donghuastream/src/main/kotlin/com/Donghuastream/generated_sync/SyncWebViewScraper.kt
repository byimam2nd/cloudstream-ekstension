// ========================================
// MASTER WEBVIEW SCRAPER
// ========================================
// Scraper untuk website SPA (Single Page Application)
// yang konten-nya di-render via JavaScript.
//
// Cara kerja:
// 1. Buka halaman di WebView
// 2. Tunggu JavaScript selesai render
// 3. Eksekusi JS untuk extract data dari DOM
// 4. Parse hasil sebagai JSON → return structured data
//
// Usage di provider:
// ```kotlin
// val items = WebViewScraper.scrapeMainPage(
//     url = "https://example.com/movies",
//     jsExtract = """
//         Array.from(document.querySelectorAll('.movie-card')).map(el => ({
//             title: el.querySelector('.title')?.textContent || '',
//             poster: el.querySelector('img')?.src || '',
//             href: el.querySelector('a')?.href || ''
//         }))
//     """,
//     timeout = 15000L
// )
// ```
// ========================================
// Last Updated: 2026-04-08
// ========================================

// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/MasterWebViewScraper.kt
// File: SyncWebViewScraper.kt
// ========================================
package com.Donghuastream.generated_sync

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.*
import com.lagradost.api.Log
import kotlinx.coroutines.*
import org.json.JSONArray

// ============================================
// REGION: WEBVIEW SCRAPER
// ============================================

/**
 * WebView-based scraper untuk website SPA/CSR.
 *
 * Menggunakan WebView untuk render halaman (JS execution),
 * lalu extract data dari DOM menggunakan custom JavaScript.
 */
object WebViewScraper {

    private const val TAG = "WebViewScraper"
    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var cachedContext: android.content.Context? = null

    /**
     * Get Android context secara otomatis via reflection.
     * CloudStream app menyimpan Application instance di static field.
     */
    private fun getContext(): android.content.Context? {
        cachedContext?.let { return it }

        try {
            // Try to get context from ActivityThread
            val activityThread = Class.forName("android.app.ActivityThread")
                .getMethod("currentActivityThread").invoke(null)

            val app = activityThread::class.java
                .getMethod("getApplication").invoke(activityThread) as? android.app.Application

            if (app != null) {
                cachedContext = app
                return app
            }
        } catch (_: Exception) {}

        try {
            // Alternative: get from LoadedApk
            val activityThread = Class.forName("android.app.ActivityThread")
                .getMethod("currentActivityThread").invoke(null)

            val context = activityThread::class.java
                .getMethod("getSystemContext").invoke(activityThread) as? android.content.Context

            if (context != null) {
                cachedContext = context
                return context
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Scrape halaman main page / kategori.
     */
    suspend fun scrapeMainPage(
        url: String,
        jsExtract: String,
        context: android.content.Context? = null,
        timeout: Long = 20000L
    ): List<ScrapeItem> {
        val ctx = context ?: getContext() ?: return emptyList()
        return scrapeWithWebView(ctx, url, jsExtract, timeout)
    }

    /**
     * Scrape halaman search results.
     *
     * @param url URL search page
     * @param jsExtract JavaScript yang return array of objects
     * @param context Android context
     * @param timeout Timeout dalam ms
     * @return List of ScrapeItem
     */
    suspend fun scrapeSearch(
        url: String,
        jsExtract: String,
        context: Context,
        timeout: Long = 15000L
    ): List<ScrapeItem> {
        return scrapeWithWebView(context, url, jsExtract, timeout)
    }

    /**
     * Scrape video links dari episode page.
     *
     * @param url URL episode page
     * @param jsExtract JavaScript yang return array of objects dengan keys: server, url
     * @param context Android context
     * @param timeout Timeout dalam ms
     * @return List of ScrapeItem (dengan server dan url di field yang sesuai)
     */
    suspend fun scrapeVideoLinks(
        url: String,
        jsExtract: String,
        context: Context,
        timeout: Long = 15000L
    ): List<ScrapeItem> {
        return scrapeWithWebView(context, url, jsExtract, timeout)
    }

    /**
     * Core WebView scraping logic.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun scrapeWithWebView(
        context: Context,
        url: String,
        jsExtract: String,
        timeout: Long,
        isDetail: Boolean = false
    ): List<ScrapeItem> {
        return withContext(Dispatchers.Main) {
            try {
                val result = CompletableDeferred<List<ScrapeItem>>()

                // Create WebView
                val wv = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            // Tunggu sebentar setelah page load untuk JS render
                            Handler(Looper.getMainLooper()).postDelayed({
                                executeExtraction(this@apply, jsExtract, result, isDetail)
                            }, 2000)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            Log.e(TAG, "WebView error: ${error?.description}")
                            if (!result.isCompleted) {
                                result.complete(emptyList())
                            }
                        }
                    }
                }

                webView = wv

                // Timeout handler
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!result.isCompleted) {
                        Log.w(TAG, "WebView timeout after ${timeout}ms for $url")
                        result.complete(emptyList())
                        cleanupWebView(wv)
                    }
                }, timeout)

                // Load page
                wv.loadUrl(url)

                // Wait for result
                result.await()
            } catch (e: Exception) {
                Log.e(TAG, "scrapeWithWebView failed: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Execute extraction JavaScript.
     */
    private fun executeExtraction(
        webView: WebView,
        jsExtract: String,
        result: CompletableDeferred<List<ScrapeItem>>,
        isDetail: Boolean
    ) {
        val wrapperJs = """
            (function() {
                try {
                    var data = $jsExtract;
                    return JSON.stringify(data);
                } catch(e) {
                    return JSON.stringify({error: e.message});
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(wrapperJs) { jsonResult ->
            try {
                if (jsonResult == "null" || jsonResult.contains("\"error\"")) {
                    Log.w(TAG, "JS extraction returned error or null")
                    result.complete(emptyList())
                } else {
                    val parsed = parseListJson(jsonResult)
                    result.complete(parsed)
                }
            } catch (e: Exception) {
                Log.e(TAG, "JSON parse failed: ${e.message}")
                result.complete(emptyList())
            } finally {
                cleanupWebView(webView)
            }
        }
    }

    /**
     * Parse JSON array ke List<ScrapeItem>.
     */
    private fun parseListJson(json: String): List<ScrapeItem> {
        val items = mutableListOf<ScrapeItem>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                items.add(
                    ScrapeItem(
                        title = obj.optString("title", ""),
                        posterUrl = obj.optString("poster", obj.optString("posterUrl", "")),
                        href = obj.optString("href", obj.optString("url", "")),
                        quality = obj.optString("quality", ""),
                        year = obj.optString("year", "").toIntOrNull(),
                        episode = obj.optString("episode", "").toIntOrNull()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseListJson failed: ${e.message}")
        }
        return items
    }

    /**
     * Cleanup WebView.
     */
    private fun cleanupWebView(webView: WebView) {
        try {
            mainHandler.post {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.destroy()
            }
        } catch (_: Exception) {
            // Ignore cleanup errors
        }
        if (WebViewScraper.webView === webView) {
            WebViewScraper.webView = null
        }
    }
}

// ============================================
// REGION: DATA CLASSES
// ============================================

/**
 * Hasil scrape untuk item (main page / search).
 */
data class ScrapeItem(
    val title: String = "",
    val posterUrl: String = "",
    val href: String = "",
    val quality: String = "",
    val year: Int? = null,
    val episode: Int? = null
)

/**
 * Hasil scrape untuk detail page.
 */
data class ScrapeDetail(
    val title: String = "",
    val posterUrl: String = "",
    val description: String = "",
    val year: Int? = null,
    val rating: Double? = null,
    val episodes: List<EpisodeData> = emptyList()
)

/**
 * Data episode dari detail page.
 */
data class EpisodeData(
    val name: String = "",
    val href: String = "",
    val episode: Int? = null,
    val season: Int? = null
)

/**
 * Link video server.
 */
data class ServerLink(
    val server: String = "",
    val url: String = ""
)
