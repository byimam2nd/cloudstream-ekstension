package com.layarKacaProvider
import kotlinx.coroutines.withTimeout

import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.zip.CRC32

/**
 * Smart Cache Monitor - Fingerprint-based cache invalidation
 * 
 * Memantau perubahan konten di website dengan membandingkan fingerprint
 * dari judul-judul terbaru. Jika fingerprint berubah, cache di-invalidate.
 * 
 * Keuntungan:
 * - Deteksi update dalam hitungan detik (tidak perlu tunggu TTL)
 * - Minimal network overhead (hanya fetch judul, bukan full content)
 * - Persistent fingerprint (tahan restart aplikasi)
 */

// ============================================
// DATA STRUCTURES
// ============================================

/**
 * Fingerprint untuk identifikasi konten
 */
data class CacheFingerprint(
    val cacheKey: String,
    val contentHash: Long,          // CRC32 hash dari judul
    val itemCount: Int,             // Jumlah item
    val topItemTitle: String,       // Judul item terbaru (untuk debugging)
    val timestamp: Long = System.currentTimeMillis(),
    val ttl: Long = DEFAULT_FINGERPRINT_TTL
) {
    fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttl
    
    companion object {
        const val DEFAULT_FINGERPRINT_TTL = 24 * 60 * 60 * 1000L // 24 jam
    }
}

/**
 * Result dari cache validation
 */
enum class CacheValidationResult {
    CACHE_MISS,           // Tidak ada cache
    CACHE_VALID,          // Cache masih valid (fingerprint sama)
    CACHE_INVALID,        // Cache invalid (fingerprint berbeda)
    CACHE_EXPIRED,        // Cache expired
    CHECK_FAILED          // Gagal check (network error, dll)
}

/**
 * Response dari fingerprint check
 */
data class FingerprintCheckResult(
    val isValid: Boolean,
    val result: CacheValidationResult,
    val oldFingerprint: CacheFingerprint?,
    val newFingerprint: CacheFingerprint?
)

// ============================================
// SMART CACHE MONITOR
// ============================================

abstract class SmartCacheMonitor {
    protected val mutex = Mutex()
    
    // In-memory cache untuk fingerprint
    private val fingerprintCache = mutableMapOf<String, CacheFingerprint>()
    
    companion object {
        private const val TAG = "SmartCacheMonitor"
        
        // Sample size untuk fingerprint
        const val SAMPLE_SIZE = 10  // Ambil 10 judul pertama
        
        // Timeout untuk fingerprint check
        const val CHECK_TIMEOUT = 5000L // 5 detik
    }
    
    /**
     * Check apakah cache masih valid
     * 
     * @param cacheKey Unique key untuk cache
     * @param url URL untuk fetch titles
     * @param cachedFingerprint Fingerprint yang tersimpan
     * @return FingerprintCheckResult
     */
    suspend fun checkCacheValidity(
        cacheKey: String,
        url: String,
        cachedFingerprint: CacheFingerprint?
    ): FingerprintCheckResult {
        return mutex.withLock {
            try {
                // Case 1: No cache
                if (cachedFingerprint == null) {
                    Log.d(TAG, "Cache miss for key: $cacheKey")
                    return@withLock FingerprintCheckResult(
                        isValid = false,
                        result = CacheValidationResult.CACHE_MISS,
                        oldFingerprint = null,
                        newFingerprint = null
                    )
                }
                
                // Case 2: Cache expired
                if (cachedFingerprint.isExpired()) {
                    Log.d(TAG, "Cache expired for key: $cacheKey")
                    fingerprintCache.remove(cacheKey)
                    return@withLock FingerprintCheckResult(
                        isValid = false,
                        result = CacheValidationResult.CACHE_EXPIRED,
                        oldFingerprint = cachedFingerprint,
                        newFingerprint = null
                    )
                }
                
                // Case 3: Fetch current titles for comparison
                val currentTitles = fetchTitlesWithTimeout(url)
                
                if (currentTitles.isEmpty()) {
                    Log.w(TAG, "Failed to fetch titles for key: $cacheKey")
                    return@withLock FingerprintCheckResult(
                        isValid = true, // Fallback: anggap valid jika fetch gagal
                        result = CacheValidationResult.CHECK_FAILED,
                        oldFingerprint = cachedFingerprint,
                        newFingerprint = null
                    )
                }
                
                // Generate new fingerprint
                val newFingerprint = generateFingerprint(cacheKey, currentTitles)
                
                // Compare fingerprints
                val isValid = cachedFingerprint.contentHash == newFingerprint.contentHash
                
                if (isValid) {
                    Log.d(TAG, "Cache valid for key: $cacheKey (hash: ${cachedFingerprint.contentHash})")
                } else {
                    Log.d(TAG, "Cache INVALID for key: $cacheKey")
                    Log.d(TAG, "  Old hash: ${cachedFingerprint.contentHash}, New hash: ${newFingerprint.contentHash}")
                    Log.d(TAG, "  Old top: ${cachedFingerprint.topItemTitle}")
                    Log.d(TAG, "  New top: ${newFingerprint.topItemTitle}")
                    
                    // Update cache dengan fingerprint baru
                    fingerprintCache[cacheKey] = newFingerprint
                }
                
                FingerprintCheckResult(
                    isValid = isValid,
                    result = if (isValid) CacheValidationResult.CACHE_VALID else CacheValidationResult.CACHE_INVALID,
                    oldFingerprint = cachedFingerprint,
                    newFingerprint = if (!isValid) newFingerprint else null
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking cache for key: $cacheKey | Cause: ${e.message}")
                // Fallback: anggap valid jika ada error (return cached data)
                FingerprintCheckResult(
                    isValid = true,
                    result = CacheValidationResult.CHECK_FAILED,
                    oldFingerprint = cachedFingerprint,
                    newFingerprint = null
                )
            }
        }
    }
    
    /**
     * Fetch titles dengan timeout
     */
    private suspend fun fetchTitlesWithTimeout(url: String): List<String> {
        return try {
            withTimeout(CHECK_TIMEOUT) {
                fetchTitles(url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Timeout fetching titles from $url | Cause: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Generate fingerprint dari list judul
     */
    fun generateFingerprint(cacheKey: String, titles: List<String>): CacheFingerprint {
        // Ambil sample judul (10 pertama)
        val sampleTitles = titles.take(SAMPLE_SIZE)
        
        // Generate hash dari judul
        val contentHash = generateContentHash(sampleTitles)
        
        return CacheFingerprint(
            cacheKey = cacheKey,
            contentHash = contentHash,
            itemCount = titles.size,
            topItemTitle = sampleTitles.firstOrNull() ?: "",
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Generate CRC32 hash dari list string
     */
    private fun generateContentHash(titles: List<String>): Long {
        val crc32 = CRC32()
        val signature = titles.joinToString("|") { it.trim() }
        crc32.update(signature.toByteArray(Charsets.UTF_8))
        return crc32.value
    }
    
    /**
     * Simpan fingerprint ke memory cache
     */
    fun saveFingerprint(fingerprint: CacheFingerprint) {
        fingerprintCache[fingerprint.cacheKey] = fingerprint
    }
    
    /**
     * Get fingerprint dari memory cache
     */
    fun getFingerprint(cacheKey: String): CacheFingerprint? {
        return fingerprintCache[cacheKey]
    }
    
    /**
     * Clear fingerprint dari cache
     */
    fun clearFingerprint(cacheKey: String) {
        fingerprintCache.remove(cacheKey)
    }
    
    /**
     * Clear semua fingerprint
     */
    fun clearAll() {
        fingerprintCache.clear()
    }
    
    // ============================================
    // ABSTRACT METHODS (Site-specific implementation)
    // ============================================
    
    /**
     * Fetch titles dari website
     * Implementasi spesifik untuk setiap site
     * 
     * @param url URL untuk fetch
     * @return List judul
     */
    protected abstract suspend fun fetchTitles(url: String): List<String>
}

// ============================================
// HELPER EXTENSIONS
// ============================================

/**
 * Execute dengan fallback ke cached data jika check gagal
 */
suspend fun <T> SmartCacheMonitor.withCacheFallback(
    cacheKey: String,
    url: String,
    cachedFingerprint: CacheFingerprint?,
    fetcher: suspend () -> T,
    titleFetcher: suspend () -> List<String>
): Pair<T, CacheFingerprint?> {
    val checkResult = checkCacheValidity(cacheKey, url, cachedFingerprint)
    
    return when {
        // Cache valid - return cached data (caller harus handle)
        checkResult.isValid && checkResult.result == CacheValidationResult.CACHE_VALID -> {
            throw CacheHitException(cachedFingerprint)
        }
        
        // Cache invalid/miss - fetch new data
        else -> {
            val newData = fetcher()
            val titles = titleFetcher()
            val newFingerprint = generateFingerprint(cacheKey, titles)
            saveFingerprint(newFingerprint)
            
            newData to newFingerprint
        }
    }
}

/**
 * Exception untuk signal cache hit
 */
class CacheHitException(val fingerprint: CacheFingerprint?) : Exception("Cache hit - use cached data")
