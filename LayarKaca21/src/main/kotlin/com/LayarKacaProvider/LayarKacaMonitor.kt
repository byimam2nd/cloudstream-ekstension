package com.layarKacaProvider

import com.layarKacaProvider.SmartCacheMonitor
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.withTimeout

/**
 * LayarKaca21-specific cache monitor
 * 
 * Mengambil judul-judul dari LayarKaca21 untuk fingerprint comparison
 */
class LayarKacaMonitor : SmartCacheMonitor() {
    
    companion object {
        private const val TAG = "LayarKacaMonitor"
    }
    
    /**
     * Fetch titles dari LayarKaca21 homepage
     * Selector: "article figure h3" - sama dengan yang digunakan di toSearchResult()
     */
    override suspend fun fetchTitles(url: String): List<String> {
        return try {
            val document = app.get(
                url,
                timeout = CHECK_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
            
            // Selector yang sama dengan mainPage scraping
            document.select("article figure h3")
                .mapNotNull { it.ownText()?.trim() }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch titles from $url")
            emptyList()
        }
    }
    
    /**
     * Get random user-agent (copy dari Utils.kt untuk standalone usage)
     */
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
