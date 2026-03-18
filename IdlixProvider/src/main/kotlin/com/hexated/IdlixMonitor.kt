package com.hexated

import com.hexated.SmartCacheMonitor
import com.lagradost.api.Log
import kotlinx.coroutines.withTimeout
import com.lagradost.cloudstream3.app

/**
 * IdlixProvider-specific cache monitor
 */
class IdlixMonitor : SmartCacheMonitor() {
    
    companion object {
        private const val TAG = "IdlixMonitor"
    }
    
    /**
     * Fetch titles dari Idlix homepage
     * Selector: "div.items.full article h3 > a" atau "div.items.featured article h3 > a"
     */
    override suspend fun fetchTitles(url: String): List<String> {
        return try {
            val document = app.get(
                url,
                timeout = CHECK_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
            
            // Selector untuk title - remove year from title
            document.select("div.items article h3 > a")
                .mapNotNull { 
                    it.text().replace(Regex("\\(\\d{4}\\)"), "").trim()
                }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch titles from $url")
            emptyList()
        }
    }
    
    private fun getRandomUserAgent(): String {
        val userAgents = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        return userAgents.random()
    }
}
