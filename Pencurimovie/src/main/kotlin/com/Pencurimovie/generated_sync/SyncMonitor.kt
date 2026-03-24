// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/MasterSyncMonitor.kt
// File: SyncMonitor.kt
// ========================================
// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/MasterSyncMonitor.kt
// File: SyncMonitor.kt
// ========================================
package com.Pencurimovie.generated_sync

import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.withTimeout
import org.jsoup.nodes.Element

/**
 * SyncMonitor - Generic Monitor dengan AUTO-DISCOVERY
 * 
 * Monitor ini OTOMATIS detect CSS selector dari Provider class
 * menggunakan reflection. Tidak perlu interface, config, atau passing manual.
 * 
 * Usage:
 * ```
 * val monitor = SyncMonitor()
 * val anichin = Anichin()
 * val titles = monitor.fetchTitles(anichin, url)
 * ```
 * 
 * Requirements:
 * - Provider harus punya variable dengan nama: selector, mainPageSelector, searchSelector, dll
 * - Provider bisa punya method: extractTitle, getTitle, parseTitle (optional, ada default)
 */
abstract class SyncMonitor {
    
    companion object {
        private const val TAG = "SyncMonitor"
        const val CHECK_TIMEOUT = 5000L
        
        // Nama-nama variable yang akan di-cari (bisa custom di Provider)
        val SELECTOR_FIELD_NAMES = listOf(
            "selector",
            "mainPageSelector",
            "searchSelector",
            "animeSelector",
            "movieSelector",
            "mainPageSelector"
        )
        
        // Nama-nama function extract yang akan di-cari
        val EXTRACT_METHOD_NAMES = listOf(
            "extractTitle",
            "getTitle",
            "parseTitle"
        )
    }

    /**
     * Fetch titles dengan AUTO-DISCOVERY selector dari Provider
     * 
     * @param provider Provider instance (Anichin, Idlix, dll)
     * @param url URL untuk fetch
     * @return List of titles
     */
    suspend fun fetchTitles(
        provider: MainAPI,
        url: String
    ): List<String> {
        return try {
            val document = app.get(
                url,
                timeout = CHECK_TIMEOUT,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).documentLarge
            
            // AUTO-DISCOVER selector dari Provider
            val selector = discoverSelector(provider)
            
            // AUTO-DISCOVER extract function dari Provider (atau pakai default)
            val extractTitle = discoverExtractMethod(provider)
            
            // Pakai selector yang di-detect
            document.select(selector)
                .mapNotNull { extractTitle(it) }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "fetchTitles failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * AUTO-DISCOVER: Cari selector field di Provider class
     */
    private fun discoverSelector(provider: MainAPI): String {
        val providerClass = provider::class.java
        
        // Cari field dengan nama yang match
        val selectorField = SELECTOR_FIELD_NAMES
            .firstNotNullOfOrNull { fieldName ->
                providerClass.declaredFields
                    .find { it.name == fieldName && it.type == String::class.java }
            }
        
        if (selectorField == null) {
            throw Exception(
                "No selector field found in ${providerClass.simpleName}. " +
                "Expected one of: ${SELECTOR_FIELD_NAMES.joinToString(", ")}"
            )
        }
        
        // Ambil value dari field
        selectorField.isAccessible = true
        return selectorField.get(provider) as String
    }

    /**
     * AUTO-DISCOVER: Cari extractTitle method di Provider class
     */
    private fun discoverExtractMethod(provider: MainAPI): (Element) -> String? {
        val providerClass = provider::class.java
        
        // Cari method dengan nama yang match
        val extractMethod = EXTRACT_METHOD_NAMES
            .firstNotNullOfOrNull { methodName ->
                providerClass.declaredMethods
                    .find { 
                        it.name == methodName &&
                        it.parameterCount == 1 &&
                        it.parameterTypes[0] == Element::class.java
                    }
            }
        
        if (extractMethod == null) {
            // Default extraction jika tidak ada custom method
            return { element ->
                element.attr("title").trim().ifEmpty { null }
            }
        }
        
        // Bind method ke provider instance
        extractMethod.isAccessible = true
        return { element ->
            extractMethod.invoke(provider, element) as String?
        }
    }

    /**
     * Check cache validity dengan auto-discovery
     */
    suspend fun checkCacheValidity(
        provider: MainAPI,
        cacheKey: String,
        currentFingerprint: Long?
    ): Boolean {
        return try {
            val titles = withTimeout(CHECK_TIMEOUT) { 
                fetchTitles(provider, cacheKey)
            }
            
            if (titles.isEmpty()) return false
            
            val newFingerprint = titles.hashCode().toLong()
            newFingerprint == currentFingerprint
        } catch (e: Exception) {
            Log.e(TAG, "checkCacheValidity failed: ${e.message}")
            false
        }
    }

    /**
     * Generate fingerprint dari titles
     */
    suspend fun generateFingerprint(
        provider: MainAPI,
        url: String
    ): Long {
        val titles = withTimeout(CHECK_TIMEOUT) { 
            fetchTitles(provider, url)
        }
        return titles.hashCode().toLong()
    }

    /**
     * Get random user-agent (SHARED)
     */
    protected fun getRandomUserAgent(): String {
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
