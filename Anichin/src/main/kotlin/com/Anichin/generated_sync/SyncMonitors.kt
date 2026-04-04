// ========================================
// MASTER MONITORS - v4.0 OPTIMIZED
// Gabungan: SmartCacheMonitor + SyncMonitor
// (SuperSmartPrefetchManager dihapus - tidak dipakai)
// ========================================
// Last Updated: 2026-04-04
// Optimized for: Runtime Performance
// ========================================

// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/MasterMonitors.kt
// File: SyncMonitors.kt
// ========================================
package com.Anichin.generated_sync

import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.jsoup.nodes.Element
import java.util.concurrent.ConcurrentHashMap

// ============================================
// REGION: SMART CACHE MONITOR (1-100)
// ============================================

/**
 * Smart Cache Monitor - Fingerprint-based cache invalidation
 * Uses content hashing to detect when cached data is stale
 */
abstract class SmartCacheMonitor {
    companion object {
        private const val TAG = "SmartCacheMonitor"
        const val SAMPLE_SIZE = 10
        const val CHECK_TIMEOUT = 5000L
        const val DEFAULT_FINGERPRINT_TTL = 24 * 60 * 60 * 1000L
    }

    data class CacheFingerprint(
        val cacheKey: String,
        val contentHash: Long,
        val itemCount: Int,
        val topItemTitle: String,
        val timestamp: Long = System.currentTimeMillis(),
        val ttl: Long = DEFAULT_FINGERPRINT_TTL
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttl

        companion object {
            fun calculateContentHash(titles: List<String>): Long {
                val combined = titles.joinToString("|")
                val crc = java.util.zip.CRC32()
                crc.update(combined.toByteArray())
                return crc.value
            }
        }
    }

    enum class CacheValidationResult {
        CACHE_MISS, CACHE_VALID, CACHE_INVALID, FETCH_ERROR
    }

    /**
     * Performance Mode Flag
     * Set to true untuk disable fingerprint validation (faster)
     * Set to false untuk enable fingerprint validation (more accurate)
     *
     * Default: true (performance first)
     */
    var ENABLE_FINGERPRINT_VALIDATION = false

    abstract suspend fun fetchTitles(url: String): List<String>

    suspend fun checkCacheValidity(cacheKey: String, currentFingerprint: CacheFingerprint?): CacheValidationResult {
        // PERFORMANCE MODE: Skip fingerprint validation for faster response
        if (!ENABLE_FINGERPRINT_VALIDATION) {
            return if (currentFingerprint == null) {
                CacheValidationResult.CACHE_MISS
            } else {
                CacheValidationResult.CACHE_VALID  // Assume valid
            }
        }

        if (currentFingerprint == null) return CacheValidationResult.CACHE_MISS

        try {
            val titles = withTimeout(CHECK_TIMEOUT) { fetchTitles(cacheKey) }
            if (titles.isEmpty()) return CacheValidationResult.FETCH_ERROR

            val newFingerprint = CacheFingerprint(
                cacheKey = cacheKey,
                contentHash = CacheFingerprint.calculateContentHash(titles.take(SAMPLE_SIZE)),
                itemCount = titles.size,
                topItemTitle = titles.firstOrNull() ?: ""
            )

            return if (newFingerprint.contentHash == currentFingerprint.contentHash) {
                CacheValidationResult.CACHE_VALID
            } else {
                CacheValidationResult.CACHE_INVALID
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: $e")
            return CacheValidationResult.FETCH_ERROR
        }
    }

    suspend fun generateFingerprint(url: String): CacheFingerprint? {
        return try {
            val titles = withTimeout(CHECK_TIMEOUT) { fetchTitles(url) }
            if (titles.isEmpty()) null
            else CacheFingerprint(
                cacheKey = url,
                contentHash = CacheFingerprint.calculateContentHash(titles.take(SAMPLE_SIZE)),
                itemCount = titles.size,
                topItemTitle = titles.firstOrNull() ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fingerprint failed")
            null
        }
    }
}

// ============================================
// REGION: SYNC MONITOR (101-300)
// ============================================

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
            "movieSelector"
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
