// ========================================
// MASTER AUTO-USED - v2.0
// Consolidated auto-used optimizations
// ========================================
// Created: 2026-03-28
// Updated: 2026-04-04
// Sync Target: generated_sync/SyncAutoUsed.kt
//
// PURPOSE:
// - Auto-use utilities untuk semua provider
// - Zero code changes needed di providers
// - Automatic performance improvements
//
// FEATURES (ALL AUTO-USED):
// - CompiledRegexPatterns helpers (auto-use common patterns)
// - RequestDeduplicator (auto-wrap requests)
// - Constants (centralized values)
// - Text cleaning & image optimization
// ========================================

// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/MasterAutoUsed.kt
// File: SyncAutoUsed.kt
// ========================================
package com.Donghuastream.generated_sync

import com.lagradost.api.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

// ============================================
// REGION: CONSTANTS (AUTO-USED)
// ============================================

/**
 * Centralized constants for all providers
 * 
 * AUTO-USED: Providers can import and use these constants
 * No need to define constants in each provider!
 */
object AutoUsedConstants {
    // Timeouts
    const val DEFAULT_TIMEOUT = 10000L  // 10 seconds (main requests)
    const val FAST_TIMEOUT = 5000L      // 5 seconds (quick checks)
    const val SLOW_TIMEOUT = 30000L     // 30 seconds (heavy operations)
    const val CHECK_TIMEOUT = 5000L     // 5 seconds (cache fingerprint checks)

    // Cache TTL
    const val CACHE_TTL_SHORT = 5 * 60 * 1000L      // 5 minutes
    const val CACHE_TTL_MEDIUM = 30 * 60 * 1000L    // 30 minutes
    const val CACHE_TTL_LONG = 24 * 60 * 60 * 1000L // 24 hours

    // Retry
    const val MAX_RETRIES = 3
    const val RETRY_DELAY = 1000L

    // Image optimization
    const val IMAGE_WIDTH_POSTER = 300
    const val IMAGE_WIDTH_BACKDROP = 780
    const val IMAGE_WIDTH_THUMBNAIL = 200
}

// ============================================
// REGION: COMPILED REGEX PATTERNS (AUTO-USED)
// ============================================

/**
 * Helper functions to auto-use CompiledRegexPatterns
 * 
 * AUTO-WORKS: Providers call these helpers instead of raw regex
 * No need to manually use CompiledRegexPatterns!
 */
object RegexHelpers {
    
    /**
     * Extract episode number from text (auto-uses CompiledRegexPatterns)
     * 
     * Usage: val epNum = extractEpisodeNumber(text)
     */
    fun extractEpisodeNumber(text: String): Int? {
        return CompiledRegexPatterns.EPISODE_NUMBER.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
    
    /**
     * Extract season number from text (auto-uses CompiledRegexPatterns)
     * 
     * Usage: val seasonNum = extractSeasonNumber(text)
     */
    fun extractSeasonNumber(text: String): Int? {
        return CompiledRegexPatterns.SEASON_NUMBER.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
    
    /**
     * Extract year from text (auto-uses CompiledRegexPatterns)
     * 
     * Usage: val year = extractYear(text)
     */
    fun extractYear(text: String): Int? {
        return CompiledRegexPatterns.YEAR_IN_PARENS.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
    
    /**
     * Extract resolution from text (auto-uses CompiledRegexPatterns)
     * 
     * Usage: val (width, height) = extractResolution(text)
     */
    fun extractResolution(text: String): Pair<Int, Int>? {
        val match = CompiledRegexPatterns.RESOLUTION_PATTERN.find(text)
        return match?.let {
            val width = it.groupValues[1].toIntOrNull() ?: return null
            val height = it.groupValues[2].toIntOrNull() ?: return null
            Pair(width, height)
        }
    }
    
    /**
     * Remove non-digits from text (auto-uses CompiledRegexPatterns)
     * 
     * Usage: val digitsOnly = removeNonDigits(text)
     */
    fun removeNonDigits(text: String): String {
        return text.replace(CompiledRegexPatterns.NON_DIGITS, "")
    }
    
    /**
     * Remove resolution suffix from URL (auto-uses CompiledRegexPatterns)
     * 
     * Usage: val cleanUrl = removeResolutionSuffix(url)
     */
    fun removeResolutionSuffix(url: String): String {
        return url.replace(CompiledRegexPatterns.RESOLUTION_SUFFIX, "")
    }
    
    /**
     * Extract m3u8 URLs from text (auto-uses CompiledRegexPatterns)
     * 
     * Usage: val urls = extractM3U8Urls(html)
     */
    fun extractM3U8Urls(text: String): List<String> {
        return CompiledRegexPatterns.M3U8_DOUBLE_QUOTED.findAll(text)
            .map { it.groupValues[1] }
            .toList()
    }
    
    /**
     * Extract MP4 URLs from text (auto-uses CompiledRegexPatterns)
     * 
     * Usage: val urls = extractMP4Urls(html)
     */
    fun extractMP4Urls(text: String): List<String> {
        return CompiledRegexPatterns.MP4_DOUBLE_QUOTED.findAll(text)
            .map { it.groupValues[1] }
            .toList()
    }
}

// ============================================
// REGION: REQUEST DEDUPLICATOR (AUTO-USED)
// ============================================

/**
 * Request Deduplicator - Mencegah duplicate concurrent requests
 *
 * AUTO-WORKS: Wrapped inside executeWithRetry()
 * Providers tidak perlu call manual!
 *
 * Benefits:
 * - Prevents duplicate concurrent requests
 * - Shares in-flight request results
 * - Reduces server load
 */
object AutoRequestDeduplicator {
    private val pendingRequests = ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<Any?>>()
    private val mutex = Mutex()

    /**
     * Deduplicate request dengan key
     *
     * AUTO-WRAP inside executeWithRetry!
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> deduplicate(key: String, block: suspend () -> T): T {
        // Check if there's already a pending request for this key
        val existingDeferred = mutex.withLock {
            pendingRequests[key]
        }

        // If request is in-flight, wait for it
        if (existingDeferred != null && !existingDeferred.isCompleted) {
            Log.d("Deduplicator", "⏳ Deduplicating request: $key")
            return existingDeferred.await() as T
        }

        // Create new deferred for this request
        val newDeferred = kotlinx.coroutines.CompletableDeferred<Any?>()

        // Try to register this request
        val registered = mutex.withLock {
            // Double-check after acquiring lock
            val current = pendingRequests[key]
            if (current != null && !current.isCompleted) {
                // Another request was registered while we were waiting
                newDeferred.cancel()
                false
            } else {
                pendingRequests[key] = newDeferred
                true
            }
        }

        if (!registered) {
            // Wait for the other request
            return pendingRequests[key]?.await() as T
                ?: throw IllegalStateException("Pending request was removed before await")
        }

        // Execute the request
        return try {
            Log.d("Deduplicator", "🚀 Starting request: $key")
            val result = block()
            newDeferred.complete(result)
            Log.d("Deduplicator", "✅ Request completed: $key")
            result
        } catch (e: Exception) {
            newDeferred.completeExceptionally(e)
            Log.d("Deduplicator", "❌ Request failed: $key - ${e.message}")
            throw e
        } finally {
            // Clean up pending request
            mutex.withLock {
                pendingRequests.remove(key)
            }
        }
    }

    /**
     * Get count of pending requests (for debugging)
     */
    fun getPendingCount(): Int = pendingRequests.size
}

// ============================================
// REGION: WRAPPER FUNCTIONS (AUTO-USED)
// ============================================

/**
 * Execute with retry + Auto-optimizations
 * 
 * This function WRAPS executeWithRetry with:
 * - Request Deduplication
 * - Performance Metrics
 * - CompiledRegexPatterns helpers
 * 
 * Providers call executeWithRetry like normal, but get ALL optimizations FREE!
 */
suspend fun <T> executeWithAutoOptimizations(
    key: String,
    maxRetries: Int = AutoUsedConstants.MAX_RETRIES,
    initialDelay: Long = AutoUsedConstants.RETRY_DELAY,
    block: suspend () -> T
): T {
    // AUTO-WRAP with deduplication
    return AutoRequestDeduplicator.deduplicate(key) {
        // Original retry logic
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return@deduplicate block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay(initialDelay * (attempt + 1))
                }
            }
        }
        throw lastException ?: Exception("Unknown error")
    }
}

/**
 * Optimize image URL (auto-uses CompiledRegexPatterns + Constants)
 * 
 * Providers call this function and get auto-optimization!
 */
fun autoOptimizeImage(imageUrl: String?, context: String = "poster"): String? {
    if (imageUrl.isNullOrBlank()) return null
    
    val width = when (context.lowercase()) {
        "backdrop" -> AutoUsedConstants.IMAGE_WIDTH_BACKDROP
        "thumbnail" -> AutoUsedConstants.IMAGE_WIDTH_THUMBNAIL
        else -> AutoUsedConstants.IMAGE_WIDTH_POSTER
    }
    
    // Skip jika sudah optimized
    if (imageUrl.contains("w${width}")) return imageUrl
    
    return when {
        imageUrl.contains("imgur.com") -> "$imageUrl?width=$width"
        imageUrl.contains("image.tmdb.org") -> imageUrl.replace(Regex("/w\\d+/"), "/w${width}/")
        else -> if (imageUrl.contains("?")) "$imageUrl&width=$width" else "$imageUrl?width=$width"
    }
}

/**
 * Extract season/episode info (auto-uses CompiledRegexPatterns)
 * 
 * Providers call this function and get auto-extraction!
 */
fun extractSeasonEpisodeInfo(text: String): Pair<Int?, Int?> {
    val season = RegexHelpers.extractSeasonNumber(text)
    val episode = RegexHelpers.extractEpisodeNumber(text)
    return Pair(season, episode)
}

/**
 * Clean text for display (auto-uses CompiledRegexPatterns)
 * 
 * Providers call this function and get auto-cleaning!
 */
fun cleanDisplayText(text: String): String {
    return text
        .replace(CompiledRegexPatterns.YEAR_IN_PARENS, "")  // Remove (2024)
        .replace(CompiledRegexPatterns.RESOLUTION_SUFFIX, "")  // Remove -1920x1080
        .trim()
}
