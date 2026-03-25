// ========================================
// MASTER REQUEST DEDUPLICATOR - v3.3
// Prevent duplicate concurrent requests
// ========================================
// Last Updated: 2026-03-25
// Sync Target: generated_sync/SyncRequestDeduplicator.kt
//
// PURPOSE:
// - Prevent multiple simultaneous requests to same URL
// - Share in-flight request results across coroutines
// - Reduce server load and improve response time
//
// USAGE:
// ```kotlin
// val result = RequestDeduplicator.deduplicate(url) {
//     app.get(url).text
// }
// ```
// ========================================

// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/MasterRequestDeduplicator.kt
// File: SyncRequestDeduplicator.kt
// ========================================
package com.Anichin.generated_sync

import com.lagradost.api.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

// ============================================
// REGION: REQUEST DEDUPLICATOR
// ============================================

/**
 * Request Deduplicator - Mencegah duplicate concurrent requests
 * 
 * Problem yang diselesaikan:
 * - Multiple coroutines request URL yang sama bersamaan
 * - Server mendapat beban berlebihan
 * - Response time lebih lambat
 * 
 * Solution:
 * - Track in-flight requests dengan ConcurrentHashMap
 * - Coroutine kedua yang request URL sama akan tunggu yang pertama selesai
 * - Result di-share ke semua yang tunggu
 * 
 * Example usage:
 * ```kotlin
 * // Di extractor
 * suspend fun fetchUrl(url: String): String {
 *     return RequestDeduplicator.deduplicate(url) {
 *         app.get(url).text
 *     }
 * }
 * 
 * // Jika 3 coroutines call fetchUrl("https://example.com") bersamaan:
 * // - Coroutine 1: Execute actual request
 * // - Coroutine 2: Wait for coroutine 1
 * // - Coroutine 3: Wait for coroutine 1
 * // - Semua dapat result yang sama
 * ```
 */
object RequestDeduplicator {
    
    private val mutex = Mutex()
    
    // Track pending requests: key = request key, value = Job
    private val pendingRequests = ConcurrentHashMap<String, Job>()
    
    // Cache results dengan TTL pendek (5 detik) untuk immediate reuse
    private val resultCache = ConcurrentHashMap<String, CachedResult<Any?>>()
    
    private const val CACHE_TTL_MS = 5000L // 5 seconds
    
    private data class CachedResult<T>(
        val data: T,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_TTL_MS
    }
    
    /**
     * Execute block dengan deduplication
     * 
     * Jika ada request dengan key yang sama sedang berjalan:
     * - Tunggu request yang sedang berjalan selesai
     * - Return result yang sama
     * 
     * Jika tidak ada request yang sedang berjalan:
     * - Execute block
     * - Store result untuk request berikutnya
     * 
     * @param key Unique key untuk request (biasanya URL)
     * @param block Code yang akan dieksekusi
     * @return Result dari block
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> deduplicate(key: String, block: suspend () -> T): T {
        // Check cache first (very recent results)
        val cached = resultCache[key] as? CachedResult<T>
        if (cached != null && !cached.isExpired()) {
            Log.d("RequestDedup", "✅ Cache HIT: $key")
            return cached.data
        }
        
        // Remove expired cache
        resultCache.remove(key)
        
        // Check if there's already a pending request for this key
        val existingJob = pendingRequests[key]
        if (existingJob != null && !existingJob.isCompleted) {
            Log.d("RequestDedup", "⏳ Waiting for pending: $key")
            
            // Wait for the existing request to complete
            try {
                existingJob.join()
                
                // Get result from cache after completion
                val result = resultCache[key] as? CachedResult<T>
                if (result != null && !result.isExpired()) {
                    Log.d("RequestDedup", "✅ Shared result: $key")
                    return result.data
                }
                
                // If cache missed, throw to trigger new request
                throw DeduplicationException("Result not available after job completion")
                
            } catch (e: Exception) {
                // If waiting failed, fallback to execute new request
                Log.w("RequestDedup", "⚠️ Wait failed, executing new: $key - ${e.message}")
            }
        }
        
        // No pending request or wait failed - execute new request
        return mutex.withLock {
            // Double-check inside mutex
            val doubleCheckJob = pendingRequests[key]
            if (doubleCheckJob != null && !doubleCheckJob.isCompleted) {
                // Another coroutine registered while we were waiting for mutex
                Log.d("RequestDedup", "⏳ Double-check wait: $key")
                doubleCheckJob.join()
                
                val result = resultCache[key] as? CachedResult<T>
                if (result != null && !result.isExpired()) {
                    return@withLock result.data
                }
            }
            
            // Execute the actual request
            Log.d("RequestDedup", "🔄 Executing new: $key")
            
            // Create a Job to track this request
            val scope = CoroutineScope(Dispatchers.IO)
            val job = scope.launch {
                try {
                    val result = block()
                    
                    // Cache the result
                    resultCache[key] = CachedResult(result)
                    
                    Log.d("RequestDedup", "✅ Completed: $key")
                    
                } catch (e: Exception) {
                    Log.e("RequestDedup", "❌ Failed: $key - ${e.message}")
                    
                    // Remove from cache on failure
                    resultCache.remove(key)
                } finally {
                    // Remove from pending requests
                    pendingRequests.remove(key)
                }
            }
            
            // Store the job
            pendingRequests[key] = job
            
            // Wait for completion
            job.join()
            
            // Get result from cache
            val result = resultCache[key] as? CachedResult<T>
            if (result != null && !result.isExpired()) {
                return@withLock result.data
            }
            
            // If still no result, execute block directly (fallback)
            Log.w("RequestDedup", "⚠️ Cache miss after join, executing fallback: $key")
            block()
        }
    }
    
    /**
     * Execute block dengan deduplication dan custom TTL
     */
    suspend fun <T> deduplicate(key: String, ttlMs: Long, block: suspend () -> T): T {
        // Override TTL for this request
        val result = deduplicate(key, block)
        
        // Update cache with custom TTL
        mutex.withLock {
            resultCache[key] = CachedResult(result, System.currentTimeMillis() + (ttlMs - CACHE_TTL_MS))
        }
        
        return result
    }
    
    /**
     * Get number of pending requests (for monitoring)
     */
    fun getPendingCount(): Int = pendingRequests.size
    
    /**
     * Get number of cached results (for monitoring)
     */
    fun getCacheSize(): Int = resultCache.size
    
    /**
     * Clear all pending requests (use with caution)
     */
    fun clearPending() {
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
    }
    
    /**
     * Clear all cached results
     */
    fun clearCache() {
        resultCache.clear()
    }
    
    /**
     * Get statistics for monitoring
     */
    fun getStats(): DedupStats {
        return DedupStats(
            pendingCount = pendingRequests.size,
            cacheSize = resultCache.size
        )
    }
}

// ============================================
// REGION: SUPPORTING TYPES
// ============================================

/**
 * Statistics for monitoring
 */
data class DedupStats(
    val pendingCount: Int,
    val cacheSize: Int
)

/**
 * Exception for deduplication failures
 */
class DeduplicationException(message: String) : Exception(message)

// ============================================
// REGION: USAGE EXAMPLES
// ============================================

/*
// EXAMPLE 1: Basic usage in extractor
object MyExtractor {
    suspend fun fetchVideo(url: String): String {
        return RequestDeduplicator.deduplicate(url) {
            app.get(url).text
        }
    }
}

// EXAMPLE 2: With custom TTL
suspend fun fetchWithCache(url: String): String {
    return RequestDeduplicator.deduplicate(url, ttlMs = 60000L) { // 1 minute
        app.get(url).text
    }
}

// EXAMPLE 3: Multiple coroutines sharing same request
coroutineScope {
    (1..10).map { i ->
        async {
            val result = RequestDeduplicator.deduplicate("https://example.com") {
                delay(1000) // Simulate network
                "Result"
            }
            println("Coroutine $i got: $result")
        }
    }.awaitAll()
}
// Output: Only 1 actual request, 10 coroutines share the result
*/
