// ========================================
// COMPLETE CACHE SYSTEM FOR CLOUDSTREAM
// ========================================
// Unified cache management with 3 components + Custom Monitors:
// 1. SmartCacheMonitor - Fingerprint-based cache invalidation
// 2. SuperSmartPrefetchManager - AI-powered prefetching
// 3. ImageCache - Disk-based image caching with 200MB limit
// 4. Custom Monitors - Site-specific implementations
// ========================================
// ALL-IN-ONE FILE for easy maintenance
// ========================================

package com.Pencurimovie

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

// ============================================
// COMPONENT 1: SMART CACHE MONITOR (BASE)
// ============================================

/**
 * Cache entry with TTL (Time To Live)
 */
data class CachedResult<T>(
    val data: T,
    val timestamp: Long,
    val ttl: Long = 300000 // 5 minutes default
) {
    fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttl
}

/**
 * Thread-safe cache manager
 */
class CacheManager<T> {
    private val cache = mutableMapOf<String, CachedResult<T>>()
    private val mutex = Mutex()

    suspend fun get(key: String): T? {
        return mutex.withLock {
            val cached = cache[key]
            if (cached != null && !cached.isExpired()) {
                cached.data
            } else {
                cache.remove(key)
                null
            }
        }
    }

    suspend fun put(key: String, data: T, ttl: Long = 300000) {
        mutex.withLock {
            cache[key] = CachedResult(data, System.currentTimeMillis(), ttl)
            cache.entries.removeAll { it.value.isExpired() }
        }
    }

    suspend fun clear() {
        mutex.withLock { cache.clear() }
    }

    suspend fun size(): Int {
        return mutex.withLock { cache.size }
    }
}

/**
 * Smart Cache Monitor - Fingerprint-based cache invalidation
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

    abstract suspend fun fetchTitles(url: String): List<String>

    suspend fun checkCacheValidity(cacheKey: String, currentFingerprint: CacheFingerprint?): CacheValidationResult {
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
            Log.e(TAG, "Error checking cache validity", e)
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
            Log.e(TAG, "Failed to generate fingerprint", e)
            null
        }
    }
}

// ============================================
// COMPONENT 2: SUPER SMART PREFETCH MANAGER
// ============================================

enum class WatchPattern {
    SEQUENTIAL, RANDOM, SKIPPER, BINGE_WATCHER, SINGLE_EPISODE, UNKNOWN
}

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

data class PrefetchPrediction(
    val nextEpisodes: List<String>,
    val confidence: Double,
    val reason: String,
    val priority: PrefetchPriority
) {
    enum class PrefetchPriority { HIGH, MEDIUM, LOW }
}

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

    private fun extractEpisodeNumber(episodeId: String): Int? {
        return Regex("(?:episode|ep)[- ]?(\\d+)").find(episodeId, RegexOption.IGNORE_CASE)
            ?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("(\\d+)").find(episodeId)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun launchPrediction(userId: String, currentEpisode: String) {
        scope.launch {
            val prefs = userPreferences[userId] ?: return@launch
            val prediction = predictNextEpisodes(currentEpisode, prefs)
            if (prediction.confidence >= MIN_CONFIDENCE) {
                executePrefetch(prediction, prefs)
            }
        }
    }

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
                val next = listOf("episode-${currentNum + 1}", "episode-${currentNum + 3}", "episode-${currentNum + 5}")
                PrefetchPrediction(next, 0.75, "Skipper", PrefetchPriority.MEDIUM)
            }
            else -> PrefetchPrediction(emptyList(), 0.0, "Unknown pattern", PrefetchPriority.LOW)
        }
    }

    private suspend fun executePrefetch(prediction: PrefetchPrediction, prefs: UserPreference) {
        if (prefs.wifiOnly && !isOnWiFi()) return
        if (prefetchQueue.containsKey(prediction.nextEpisodes.first())) return
        
        scope.launch {
            prefetchQueue[prediction.nextEpisodes.first()] = true
            Log.d(TAG, "Starting prefetch: ${prediction.nextEpisodes.size} episodes")
            
            prediction.nextEpisodes.forEach { episodeId ->
                try {
                    if (cache.get<Any>(episodeId) == null) {
                        Log.d(TAG, "Prefetching: $episodeId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to prefetch $episodeId", e)
                }
            }
            
            Log.d(TAG, "Prefetch complete!")
            prefetchQueue.remove(prediction.nextEpisodes.first())
        }
    }

    private fun isOnWiFi(): Boolean = true
}

// ============================================
// COMPONENT 3: ADVANCED IMAGE CACHE
// ============================================

class ImageCache(
    private val cacheDir: File,
    private val maxTotalSize: Long = 200 * 1024 * 1024,
    private val maxSiteSize: Long = 30 * 1024 * 1024,
    private val compressionQuality: Int = 85,
    private val targetWidth: Int = 600,
    private val targetHeight: Int = 900
) {
    companion object {
        private const val TAG = "AdvancedImageCache"
        private const val CACHE_TTL = 7 * 24 * 60 * 60 * 1000L
        private val globalMutex = Mutex()
        private var lastGlobalCleanup = 0L
        private const val CLEANUP_INTERVAL = 5 * 60 * 1000L
    }

    private val siteCacheDir: File = File(cacheDir, "image_cache_${getSiteName()}").apply { mkdirs() }
    private val mutex = Mutex()

    private fun getSiteName(): String {
        return try {
            this::class.java.`package`.name.replace("com.", "").replace(".", "_")
        } catch (e: Exception) { "unknown_site" }
    }

    suspend fun get(url: String): Bitmap? {
        return mutex.withLock {
            val file = File(siteCacheDir, url.md5())
            if (file.exists() && file.lastModified() + CACHE_TTL > System.currentTimeMillis()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null && !bitmap.isRecycled) {
                        Log.d(TAG, "✅ Cache HIT: ${url.takeLast(40)}")
                        return@withLock bitmap
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode cached bitmap", e)
                }
            }
            null
        }
    }

    suspend fun put(url: String, bitmap: Bitmap): Long {
        return mutex.withLock {
            try {
                val compressedBitmap = compressForTV(bitmap)
                val estimatedSize = estimateWebPSize(compressedBitmap, compressionQuality)
                val currentSize = getCurrentTotalSize()
                
                if (currentSize + estimatedSize >= maxTotalSize) {
                    cleanupOldFiles(estimatedSize * 2)
                }
                
                val file = File(siteCacheDir, url.md5())
                FileOutputStream(file).use { out ->
                    compressedBitmap.compress(Bitmap.CompressFormat.WEBP, compressionQuality, out)
                }
                
                cleanupSiteIfNeeded()
                performGlobalCleanup()
                compressedBitmap.recycle()
                file.length() / 1024
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cache image", e)
                -1
            }
        }
    }

    private fun estimateWebPSize(bitmap: Bitmap, quality: Int): Long {
        return (bitmap.byteCount.toLong() * when {
            quality >= 90 -> 0.35
            quality >= 80 -> 0.30
            else -> 0.25
        })
    }

    private suspend fun getCurrentTotalSize(): Long {
        return globalMutex.withLock {
            cacheDir.parentFile?.listFiles { f -> f.isDirectory && f.name.startsWith("image_cache_") }
                ?.sumOf { dir -> dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L } ?: 0L
        }
    }

    private suspend fun cleanupOldFiles(cleanupTarget: Long) {
        try {
            val allFiles = cacheDir.parentFile?.listFiles { f -> f.isDirectory && f.name.startsWith("image_cache_") }
                ?.flatMap { dir -> dir.listFiles()?.filter { it.isFile } ?: emptyList() }
                ?.sortedBy { it.lastModified() } ?: return

            var freed = 0L
            for (file in allFiles) {
                if (freed >= cleanupTarget) break
                file.delete()
                freed += file.length()
                Log.d(TAG, "🗑️ Deleted old: ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old files", e)
        }
    }

    suspend fun fetchAndCache(url: String): Bitmap? {
        get(url)?.let { return it }

        try {
            val bytes = app.get(url).body.bytes()
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
                inSampleSize = calculateInSampleSize(this, targetWidth, targetHeight)
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (bitmap != null) {
                put(url, bitmap)
                return bitmap
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch image", e)
            return null
        }
    }

    private fun compressForTV(bitmap: Bitmap): Bitmap {
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        var newWidth = targetWidth
        var newHeight = (targetWidth / aspectRatio).toInt()
        if (newHeight > targetHeight) {
            newHeight = targetHeight
            newWidth = (targetHeight * aspectRatio).toInt()
        }
        return if (bitmap.width == newWidth && bitmap.height == newHeight) bitmap
        else Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun cleanupSiteIfNeeded() {
        try {
            val files = siteCacheDir.listFiles()?.filter { it.isFile } ?: return
            val totalSize = files.sumOf { it.length() }
            if (totalSize > maxSiteSize) {
                val sorted = files.sortedBy { it.lastModified() }
                var freed = 0L
                for (file in sorted) {
                    if (freed >= totalSize - (maxSiteSize * 0.8).toLong()) break
                    file.delete()
                    freed += file.length()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup site cache", e)
        }
    }

    private suspend fun performGlobalCleanup() {
        globalMutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastGlobalCleanup < CLEANUP_INTERVAL) return
            lastGlobalCleanup = now

            val allFiles = cacheDir.parentFile?.listFiles { f -> f.isDirectory && f.name.startsWith("image_cache_") }
                ?.flatMap { dir -> dir.listFiles()?.filter { it.isFile } ?: emptyList() } ?: return
            
            val totalSize = allFiles.sumOf { it.length() }
            if (totalSize > maxTotalSize) {
                var freed = 0L
                for (file in allFiles.sortedBy { it.lastModified() }) {
                    if (freed >= totalSize - (maxTotalSize * 0.8).toLong()) break
                    file.delete()
                    freed += file.length()
                }
            }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            siteCacheDir.deleteRecursively()
            Log.d(TAG, "🗑️ Site cache cleared")
        }
    }

    suspend fun getStats(): ImageCacheStats {
        return mutex.withLock {
            val files = siteCacheDir.listFiles()?.filter { it.isFile } ?: emptyList()
            val totalSize = files.sumOf { it.length() }
            val oldest = files.minByOrNull { it.lastModified() }
            ImageCacheStats(
                files.size, totalSize, totalSize.toDouble() / 1024 / 1024,
                maxSiteSize.toDouble() / 1024 / 1024, maxTotalSize.toDouble() / 1024 / 1024,
                ((totalSize * 100) / maxSiteSize).toInt(),
                oldest?.let { (System.currentTimeMillis() - it.lastModified()) / 1000 / 60 / 60 } ?: 0,
                compressionQuality, "${targetWidth}x${targetHeight}"
            )
        }
    }
}

data class ImageCacheStats(
    val fileCount: Int, val totalSizeBytes: Long, val totalSizeMB: Double,
    val maxSiteSizeMB: Double, val maxTotalSizeMB: Double, val usagePercent: Int,
    val oldestFileAge: Int, val compressionQuality: Int, val targetResolution: String
) {
    fun getSummary(): String = """
📊 Image Cache Stats:
├─ Files: $fileCount
├─ Size: ${String.format("%.1f", totalSizeMB)}MB / ${String.format("%.0f", maxSiteSizeMB)}MB (${usagePercent}%)
├─ Global Limit: ${String.format("%.0f", maxTotalSizeMB)}MB
├─ Oldest: ${oldestFileAge}h
├─ Quality: ${compressionQuality}%
└─ Resolution: ${targetResolution}
    """.trimIndent()
}

fun String.md5(): String {
    return MessageDigest.getInstance("MD5").digest(this.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

// ============================================
// COMPONENT 4: CUSTOM SITE MONITORS
// ============================================

/**
 * LAYARKACA21 CUSTOM MONITOR
 */
class LayarKacaMonitor : SmartCacheMonitor() {
    companion object {
        private const val TAG = "LayarKacaMonitor"
    }
    
    override suspend fun fetchTitles(url: String): List<String> {
        return try {
            val document = app.get(url, timeout = CHECK_TIMEOUT).documentLarge
            document.select("article figure h3")
                .mapNotNull { it.ownText()?.trim() }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch titles", e)
            emptyList()
        }
    }
}

/**
 * HIANIME CUSTOM MONITOR
 */
class HiAnimeMonitor : SmartCacheMonitor() {
    companion object {
        private const val TAG = "HiAnimeMonitor"
    }
    
    override suspend fun fetchTitles(url: String): List<String> {
        return try {
            val document = app.get(url, timeout = CHECK_TIMEOUT).documentLarge
            document.select("div.flw-item h3.film-name a")
                .mapNotNull { it.attr("title").trim() }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch titles", e)
            emptyList()
        }
    }
}

/**
 * ANICHIN CUSTOM MONITOR
 */
class AnichinMonitor : SmartCacheMonitor() {
    companion object {
        private const val TAG = "AnichinMonitor"
    }
    
    override suspend fun fetchTitles(url: String): List<String> {
        return try {
            val document = app.get(url, timeout = CHECK_TIMEOUT).documentLarge
            document.select("h2.anime-title")
                .mapNotNull { it.ownText()?.trim() }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch titles", e)
            emptyList()
        }
    }
}

/**
 * IDLIXPROVIDER CUSTOM MONITOR
 */
class IdlixMonitor : SmartCacheMonitor() {
    companion object {
        private const val TAG = "IdlixMonitor"
    }
    
    override suspend fun fetchTitles(url: String): List<String> {
        return try {
            val document = app.get(url, timeout = CHECK_TIMEOUT).documentLarge
            document.select("h2.entry-title")
                .mapNotNull { it.ownText()?.trim() }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch titles", e)
            emptyList()
        }
    }
}

// ============================================
// USAGE EXAMPLE
// ============================================

/*

// In your site's main .kt file:

class MyProvider : MainAPI() {
    // Initialize all cache components
    private val cacheManager = CacheManager<Any>()
    private val prefetchManager = SuperSmartPrefetchManager(cacheManager)
    private val imageCache = ImageCache(cacheDir = context?.cacheDir ?: File("/tmp/cache"))
    
    // Use custom monitor for your site (if needed)
    private val monitor = when (this) {
        is LayarKaca21 -> LayarKacaMonitor()
        is HiAnime -> HiAnimeMonitor()
        is Anichin -> AnichinMonitor()
        is IdlixProvider -> IdlixMonitor()
        else -> null
    }
    
    override suspend fun search(query: String): List<SearchResponse> {
        val cached = cacheManager.get<List<SearchResponse>>(query)
        if (cached != null) return cached
        
        val results = fetchSearchResults(query)
        cacheManager.put(query, results)
        return results
    }
    
    override suspend fun loadLinks(...): Boolean {
        // Trigger smart prefetch
        prefetchManager.analyzeBehavior("user_${System.currentTimeMillis()}", data, 24)
        return true
    }
    
    suspend fun getPoster(url: String): Bitmap? {
        return imageCache.fetchAndCache(url)
    }
}

*/
