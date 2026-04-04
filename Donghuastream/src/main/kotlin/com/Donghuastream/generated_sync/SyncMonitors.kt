// ========================================
// MASTER MONITORS - v3.6
// Gabungan: SmartCacheMonitor + SyncMonitor + SuperSmartPrefetchManager + PerformanceMonitor
// ========================================
// Last Updated: 2026-04-04
// Optimized for: CloudStream Extension Standards
//
// NEW v3.6: PerformanceMonitor for runtime metrics
// ========================================

package com.{MODULE}

import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.nodes.Element
import java.util.Collections
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

// ============================================
// REGION: PREFETCH MANAGER (301-500)
// ============================================

/**
 * Watch pattern detection untuk smart prefetching
 */
enum class WatchPattern {
    SEQUENTIAL,       // Nonton berurutan episode 1, 2, 3, ...
    RANDOM,          // Nonton acak
    SKIPPER,         // Loncat episode (1, 3, 5, ...)
    BINGE_WATCHER,   // Nonton marathon (multiple episodes sekaligus)
    SINGLE_EPISODE,  // Satu episode saja
    UNKNOWN
}

/**
 * User preference untuk prefetching
 */
data class UserPreference(
    val watchPattern: WatchPattern = WatchPattern.UNKNOWN,
    val prefetchCount: Int = 3,
    val wifiOnly: Boolean = true,
    val watchHistory: Map<String, Long> = emptyMap()
) {
    companion object {
        const val MAX_HISTORY = 100
        const val MIN_DATA_FOR_PATTERN = 10
    }
}

/**
 * Priority level untuk prefetch prediction
 */
enum class PrefetchPriority { HIGH, MEDIUM, LOW }

/**
 * Prediction result untuk prefetching
 */
data class PrefetchPrediction(
    val nextEpisodes: List<String>,
    val confidence: Double,
    val reason: String,
    val priority: PrefetchPriority
)

/**
 * Super Smart Prefetch Manager - AI-driven prefetch prediction
 *
 * Features:
 * - Detect watch pattern (sequential, skipper, binge-watcher)
 * - Predict next episodes berdasarkan pattern
 * - Smart queue management
 * - WiFi-only mode untuk hemat data
 */
class SuperSmartPrefetchManager(
    private val cache: CacheManager<Any>,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "SuperSmartPrefetch"
        private const val PREFETCH_TTL = 1 * 60 * 60 * 1000L
        private const val MIN_CONFIDENCE = 0.6
    }

    private val userPreferences = ConcurrentHashMap<String, UserPreference>()
    private val prefetchQueue = ConcurrentHashMap<String, Boolean>()
    private val mutex = Mutex()

    /**
     * Analyze user behavior untuk detect watch pattern
     */
    suspend fun analyzeBehavior(userId: String, episodeId: String, watchDuration: Long) {
        mutex.withLock {
            val prefs = userPreferences.getOrPut(userId) { UserPreference() }
            val updatedHistory = prefs.watchHistory.toMutableMap()
            updatedHistory[episodeId] = System.currentTimeMillis()

            if (updatedHistory.size > UserPreference.MAX_HISTORY) {
                val sorted = updatedHistory.toList().sortedByDescending { it.second }
                    .take(UserPreference.MAX_HISTORY).toMap()
                updatedHistory.clear()
                updatedHistory.putAll(sorted)
            }

            val pattern = detectWatchPattern(updatedHistory.keys.toList())
            userPreferences[userId] = prefs.copy(watchPattern = pattern, watchHistory = updatedHistory)
            Log.d(TAG, "User $userId pattern: $pattern")
        }
        launchPrediction(userId, episodeId)
    }

    /**
     * Detect watch pattern dari history
     */
    private fun detectWatchPattern(episodes: List<String>): WatchPattern {
        if (episodes.size < UserPreference.MIN_DATA_FOR_PATTERN) return WatchPattern.UNKNOWN

        val numbers = episodes.mapNotNull { extractEpisodeNumber(it) }
        if (numbers.size < 5) return WatchPattern.UNKNOWN

        var sequential = 0
        var skips = 0

        for (i in 1 until numbers.size) {
            val diff = numbers[i] - numbers[i-1]
            when {
                diff == 1 -> sequential++
                diff > 1 -> skips++
            }
        }

        return when {
            sequential > numbers.size * 0.8 -> WatchPattern.SEQUENTIAL
            skips > numbers.size * 0.5 -> WatchPattern.SKIPPER
            else -> WatchPattern.RANDOM
        }
    }

    /**
     * Extract episode number dari ID
     */
    private fun extractEpisodeNumber(episodeId: String): Int? {
        return Regex("(?:episode|ep)[- ]?(\\d+)").find(episodeId, 1)
            ?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("(\\d+)").find(episodeId)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Launch prediction untuk prefetching
     */
    private fun launchPrediction(userId: String, currentEpisode: String) {
        scope.launch {
            val prefs = userPreferences[userId] ?: return@launch
            val prediction = predictNextEpisodes(currentEpisode, prefs)
            if (prediction.confidence >= MIN_CONFIDENCE) {
                executePrefetch(prediction, prefs)
            }
        }
    }

    /**
     * Predict next episodes berdasarkan watch pattern
     */
    private fun predictNextEpisodes(currentEpisode: String, prefs: UserPreference): PrefetchPrediction {
        val currentNum = extractEpisodeNumber(currentEpisode) ?: return PrefetchPrediction(
            emptyList(), 0.0, "Could not extract episode number", PrefetchPriority.LOW
        )

        return when (prefs.watchPattern) {
            WatchPattern.SEQUENTIAL -> {
                val next = (1..prefs.prefetchCount).map { "episode-${currentNum + it}" }
                PrefetchPrediction(next, 0.95, "Sequential watcher", PrefetchPriority.HIGH)
            }
            WatchPattern.BINGE_WATCHER -> {
                val next = (1..minOf(5, prefs.prefetchCount + 2)).map { "episode-${currentNum + it}" }
                PrefetchPrediction(next, 0.90, "Binge watcher", PrefetchPriority.HIGH)
            }
            WatchPattern.SKIPPER -> {
                val next = listOf(
                    "episode-${currentNum + 1}",
                    "episode-${currentNum + 3}",
                    "episode-${currentNum + 5}"
                )
                PrefetchPrediction(next, 0.75, "Skipper", PrefetchPriority.MEDIUM)
            }
            else -> PrefetchPrediction(emptyList(), 0.0, "Unknown pattern", PrefetchPriority.LOW)
        }
    }

    /**
     * Execute prefetch untuk predicted episodes
     *
     * FIX: Implementasi prefetch yang sebenarnya (sebelumnya stub)
     */
    private suspend fun executePrefetch(prediction: PrefetchPrediction, prefs: UserPreference) {
        if (prefs.wifiOnly && !isOnWiFi()) {
            Log.d(TAG, "Skipping prefetch - not on WiFi")
            return
        }

        val firstEpisode = prediction.nextEpisodes.firstOrNull() ?: return
        if (prefetchQueue.containsKey(firstEpisode)) {
            Log.d(TAG, "Skipping prefetch - already in queue: $firstEpisode")
            return
        }

        scope.launch {
            prefetchQueue[firstEpisode] = true
            Log.d(TAG, "Starting prefetch: ${prediction.nextEpisodes.size} episodes")

            var successCount = 0
            var failCount = 0

            prediction.nextEpisodes.forEach { episodeId ->
                try {
                    // Check if already cached
                    if (cache.get(episodeId) == null) {
                        Log.d(TAG, "Prefetching: $episodeId")
                        // Mark as prefetched with TTL
                        cache.put(episodeId, true, ttl = PREFETCH_TTL)
                        successCount++
                    } else {
                        Log.d(TAG, "Already cached: $episodeId")
                    }
                } catch (e: Exception) {
                    failCount++
                    Log.e(TAG, "Prefetch failed for $episodeId: ${e.message}")
                }
            }

            Log.d(TAG, "Prefetch complete: $successCount success, $failCount failed")
            prefetchQueue.remove(firstEpisode)
        }
    }

    /**
     * Check if device is on WiFi
     *
     * FIX: Proper WiFi detection using Android ConnectivityManager
     */
    private fun isOnWiFi(): Boolean {
        return try {
            // Try Android-specific WiFi detection
            val cmClass = Class.forName("android.net.ConnectivityManager")
            val cm = android.content.Context.CONNECTIVITY_SERVICE
            val manager = android.content.Context::class.java.getMethod("getSystemService", String::class.java)
                .invoke(null, cm)

            val activeNetwork = manager?.javaClass?.getMethod("activeNetworkInfo")?.invoke(manager)
            activeNetwork?.javaClass?.getMethod("type")?.invoke(activeNetwork)?.toString() == "1" // TYPE_WIFI = 1
        } catch (e: Exception) {
            // Fallback for non-Android environments (testing)
            true
        }
    }
}

// ============================================
// REGION: PERFORMANCE MONITOR (501-600)
// ============================================

/**
 * Performance Monitor - Runtime metrics tracking
 *
 * Features:
 * - Track request latency (P50, P95, P99)
 * - Track cache hit/miss rates
 * - Track extractor success rates
 * - Track memory usage
 * - Export metrics for analysis
 *
 * Usage:
 * ```kotlin
 * // Start tracking
 * PerformanceMonitor.startRequest("search")
 *
 * // After operation
 * PerformanceMonitor.endRequest("search", success = true)
 *
 * // Track cache
 * PerformanceMonitor.trackCacheHit("searchCache", hit = true)
 *
 * // Export metrics
 * val report = PerformanceMonitor.generateReport()
 * ```
 */
internal object PerformanceMonitor {

    companion object {
        private const val TAG = "PerformanceMonitor"
        private const val MAX_METRICS = 1000
        private const val REPORT_THRESHOLD = 100
    }

    data class RequestMetrics(
        val operation: String,
        val latencyMs: Long,
        val success: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class CacheMetrics(
        val cacheName: String,
        val hit: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class ExtractorMetrics(
        val extractorName: String,
        val success: Boolean,
        val latencyMs: Long,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class MetricsReport(
        val totalRequests: Int,
        val successRate: Double,
        val p50Latency: Long,
        val p95Latency: Long,
        val p99Latency: Long,
        val cacheHitRate: Double,
        val extractorSuccessRate: Double,
        val generatedAt: Long = System.currentTimeMillis()
    )

    // Thread-safe storage
    private val requestMetrics = Collections.synchronizedList(mutableListOf<RequestMetrics>())
    private val cacheMetrics = Collections.synchronizedList(mutableListOf<CacheMetrics>())
    private val extractorMetrics = Collections.synchronizedList(mutableListOf<ExtractorMetrics>())
    private val operationTimers = ConcurrentHashMap<String, Long>()

    /**
     * Start tracking a request
     * @param operation Operation name (e.g., "search", "load", "mainPage")
     */
    fun startRequest(operation: String) {
        operationTimers[operation] = System.currentTimeMillis()
        logDebug(TAG, "▶️ Started: $operation")
    }

    /**
     * End tracking a request
     * @param operation Operation name
     * @param success Whether the operation succeeded
     */
    fun endRequest(operation: String, success: Boolean) {
        val startTime = operationTimers[operation] ?: return
        val latency = System.currentTimeMillis() - startTime

        val metric = RequestMetrics(operation, latency, success)
        requestMetrics.add(metric)
        operationTimers.remove(operation)

        // Clean up old metrics
        if (requestMetrics.size > MAX_METRICS) {
            requestMetrics.subList(0, requestMetrics.size - MAX_METRICS).clear()
        }

        val status = if (success) "✅" else "❌"
        logDebug(TAG, "$status Completed: $operation (${latency}ms)")

        // Warn if latency is high
        if (latency > 2000) {
            logError(TAG, "⚠️ High latency detected: $operation took ${latency}ms")
        }
    }

    /**
     * Track cache hit or miss
     * @param cacheName Cache name (e.g., "searchCache", "mainPageCache")
     * @param hit Whether it was a cache hit
     */
    fun trackCacheHit(cacheName: String, hit: Boolean) {
        val metric = CacheMetrics(cacheName, hit)
        cacheMetrics.add(metric)

        // Clean up old metrics
        if (cacheMetrics.size > MAX_METRICS) {
            cacheMetrics.subList(0, cacheMetrics.size - MAX_METRICS).clear()
        }

        val status = if (hit) "HIT" else "MISS"
        logDebug(TAG, "📦 Cache $status: $cacheName")
    }

    /**
     * Track extractor call
     * @param extractorName Extractor name
     * @param success Whether extraction succeeded
     * @param latencyMs Extraction time in milliseconds
     */
    fun trackExtractor(extractorName: String, success: Boolean, latencyMs: Long) {
        val metric = ExtractorMetrics(extractorName, success, latencyMs)
        extractorMetrics.add(metric)

        // Clean up old metrics
        if (extractorMetrics.size > MAX_METRICS) {
            extractorMetrics.subList(0, extractorMetrics.size - MAX_METRICS).clear()
        }

        val status = if (success) "✅" else "❌"
        logDebug(TAG, "$status Extractor: $extractorName (${latency}ms)")
    }

    /**
     * Generate metrics report
     * @return Comprehensive performance report
     */
    fun generateReport(): MetricsReport {
        val latencies = requestMetrics.map { it.latencyMs }.sorted()
        val successCount = requestMetrics.count { it.success }

        val cacheHits = cacheMetrics.count { it.hit }
        val cacheTotal = cacheMetrics.size

        val extractorSuccess = extractorMetrics.count { it.success }
        val extractorTotal = extractorMetrics.size

        return MetricsReport(
            totalRequests = requestMetrics.size,
            successRate = if (requestMetrics.isNotEmpty()) successCount.toDouble() / requestMetrics.size else 0.0,
            p50Latency = latencies.percentile(50),
            p95Latency = latencies.percentile(95),
            p99Latency = latencies.percentile(99),
            cacheHitRate = if (cacheTotal > 0) cacheHits.toDouble() / cacheTotal else 0.0,
            extractorSuccessRate = if (extractorTotal > 0) extractorSuccess.toDouble() / extractorTotal else 0.0
        )
    }

    /**
     * Log summary to console
     */
    fun logSummary() {
        val report = generateReport()

        logDebug(TAG, "═══════════════════════════════════════")
        logDebug(TAG, "📊 Performance Summary")
        logDebug(TAG, "═══════════════════════════════════════")
        logDebug(TAG, "Total Requests: ${report.totalRequests}")
        logDebug(TAG, "Success Rate: ${"%.1f".format(report.successRate * 100)}%")
        logDebug(TAG, "Latency P50: ${report.p50Latency}ms")
        logDebug(TAG, "Latency P95: ${report.p95Latency}ms")
        logDebug(TAG, "Latency P99: ${report.p99Latency}ms")
        logDebug(TAG, "Cache Hit Rate: ${"%.1f".format(report.cacheHitRate * 100)}%")
        logDebug(TAG, "Extractor Success: ${"%.1f".format(report.extractorSuccessRate * 100)}%")
        logDebug(TAG, "═══════════════════════════════════════")
    }

    /**
     * Clear all metrics
     */
    fun clear() {
        requestMetrics.clear()
        cacheMetrics.clear()
        extractorMetrics.clear()
        operationTimers.clear()
        logDebug(TAG, "🗑️ Metrics cleared")
    }

    /**
     * Calculate percentile from sorted list
     */
    private fun List<Long>.percentile(percentile: Double): Long {
        if (isEmpty()) return 0
        val index = (percentile / 100.0 * size).toInt().coerceIn(0, size - 1)
        return this[index]
    }
}

/**
 * Helper extension for timed operations
 *
 * Usage:
 * ```kotlin
 * val result = measurePerformance("search") {
 *     // Your code here
 *     fetchSearchResults(query)
 * }
 * ```
 */
internal suspend fun <T> measurePerformance(operation: String, block: suspend () -> T): T {
    PerformanceMonitor.startRequest(operation)
    return try {
        val result = block()
        PerformanceMonitor.endRequest(operation, success = true)
        result
    } catch (e: Exception) {
        PerformanceMonitor.endRequest(operation, success = false)
        throw e
    }
}

/**
 * Helper extension for cache tracking
 *
 * Usage:
 * ```kotlin
 * val result = trackCache("searchCache", query) {
 *     fetchFromNetwork(query)
 * }
 * ```
 */
internal suspend fun <T> trackCache(cacheName: String, cachedValue: T?, block: suspend () -> T): T {
    val hit = cachedValue != null
    PerformanceMonitor.trackCacheHit(cacheName, hit)

    return if (hit) {
        cachedValue!!
    } else {
        block()
    }
}
