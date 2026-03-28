// ========================================
// MASTER CACHES - v3.0 OPTIMIZED
// Gabungan: CacheManager + ImageCache
// ========================================
// Last Updated: 2026-03-25
// Optimized for: CloudStream Extension Standards
// ========================================

// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/MasterCaches.kt
// File: SyncCaches.kt
// ========================================
package com.Pencurimovie.generated_sync

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.ObjectOutputStream
import java.io.ObjectInputStream
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

// ============================================
// REGION: GENERIC CACHE MANAGER (1-150)
// ============================================

/**
 * Generic thread-safe cache manager with TTL + Disk Cache
 *
 * Features:
 * - In-memory cache for fast access (TTL: 30 minutes)
 * - Disk cache for persistence (TTL: 24 hours)
 * - Total disk cache limit: 200MB (shared across all providers)
 * - Auto-cleanup when limit exceeded
 * - Lazy initialization for better startup performance
 *
 * Usage:
 * ```
 * val cache = CacheManager<List<SearchResponse>>(defaultTtl = 30 * 60 * 1000L)
 * ```
 */
class CacheManager<T>(
    private val defaultTtl: Long = 30 * 60 * 1000L, // 30 minutes for memory
    private val diskTtl: Long = 24 * 60 * 60 * 1000L, // 24 hours for disk
    private val maxDiskSize: Long = 200 * 1024 * 1024 // 200MB total limit
) {
    private val cache = ConcurrentHashMap<String, CachedResult<T>>()
    private val mutex = Mutex()

    // Disk cache directory (shared across all providers)
    private val diskCacheDir: File by lazy {
        val baseDir = System.getProperty("java.io.tmpdir") ?: "/tmp"
        File("$baseDir/cloudstream-cache").apply { mkdirs() }
    }

    suspend fun get(key: String): T? {
        return mutex.withLock {
            // Try memory cache first (fastest)
            val memoryCached = cache[key]
            if (memoryCached != null && !memoryCached.isExpired()) {
                return@withLock memoryCached.data
            }

            // Try disk cache (slower but persistent)
            val diskFile = getDiskCacheFile(key)
            if (diskFile.exists()) {
                try {
                    val diskCached = readFromDisk(diskFile)
                    if (diskCached != null && !diskCached.isExpired(diskTtl)) {
                        // Restore to memory cache for faster next access
                        cache[key] = diskCached
                        return@withLock diskCached.data
                    } else {
                        // Expired, delete from disk
                        diskFile.delete()
                    }
                } catch (e: Exception) {
                    // Corrupted cache file, delete it
                    diskFile.delete()
                }
            }

            // Cache miss
            cache.remove(key)
            null
        }
    }

    suspend fun put(key: String, data: T, ttl: Long = defaultTtl) {
        mutex.withLock {
            // Store in memory cache
            val cachedResult = CachedResult(data, System.currentTimeMillis(), ttl)
            cache[key] = cachedResult

            // Store in disk cache (for persistence)
            try {
                val diskFile = getDiskCacheFile(key)

                // Check disk space before writing
                if (getTotalDiskSize() + estimateSize(data) > maxDiskSize) {
                    cleanupDiskCache()
                }

                writeToDisk(diskFile, cachedResult)
            } catch (e: Exception) {
                // Disk cache failed, continue with memory cache only
            }

            // Cleanup expired entries periodically (not on every put to avoid performance hit)
            // cleanupExpiredEntries() - disabled for performance
        }
    }

    suspend fun clear() {
        mutex.withLock {
            cache.clear()
            diskCacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    suspend fun size(): Int = cache.size

    suspend fun diskSize(): Long = mutex.withLock { getTotalDiskSize() }

    // ========================================
    // HELPER FUNCTIONS
    // ========================================

    private fun getDiskCacheFile(key: String): File {
        // Use MD5 hash for filename to avoid special characters
        val hash = key.md5()
        return File(diskCacheDir, "cache_$hash.dat")
    }

    @Suppress("UNCHECKED_CAST")
    private fun readFromDisk(file: File): CachedResult<T>? {
        return try {
            ObjectInputStream(ByteArrayInputStream(file.readBytes())).use {
                it.readObject() as? CachedResult<T>
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun writeToDisk(file: File, data: CachedResult<T>) {
        ByteArrayOutputStream().use { baos ->
            ObjectOutputStream(baos).use { oos ->
                oos.writeObject(data)
                file.writeBytes(baos.toByteArray())
            }
        }
    }

    private fun estimateSize(data: T): Long {
        // Rough estimate: 1KB base + serialized size
        return try {
            ByteArrayOutputStream().use { baos ->
                ObjectOutputStream(baos).use { oos ->
                    oos.writeObject(data)
                    baos.size().toLong()
                }
            }
        } catch (e: Exception) {
            1024 // Default 1KB estimate
        }
    }

    private fun getTotalDiskSize(): Long {
        return diskCacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun cleanupDiskCache() {
        // Delete oldest files until under limit
        val files = diskCacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var currentSize = getTotalDiskSize()

        for (file in files) {
            if (currentSize <= maxDiskSize * 0.8) break // Stop at 80% of limit
            currentSize -= file.length()
            file.delete()
        }
    }

    private fun cleanupExpiredEntries() {
        cache.entries.removeAll { it.value.isExpired() }
    }
}

data class CachedResult<T>(
    val data: T,
    val timestamp: Long,
    val ttl: Long = 30 * 60 * 1000L
) {
    fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttl
    fun isExpired(customTtl: Long): Boolean = System.currentTimeMillis() - timestamp > customTtl
}

// ============================================
// REGION: IMAGE CACHE (151-400)
// ============================================

/**
 * Advanced Image Cache with:
 * - Disk-only storage (persistent)
 * - 200MB shared limit across all sites
 * - Smart compression (WebP, 85% quality)
 * - Optimized for Android/Google TV (600x900)
 * - Auto-cleanup: Delete 2x old before adding new
 * - Lazy initialization for better performance
 */
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
        private const val CACHE_TTL = 7 * 24 * 60 * 60 * 1000L // 7 days
        private val globalMutex = Mutex()
        private var lastGlobalCleanup = 0L
        private const val CLEANUP_INTERVAL = 5 * 60 * 1000L
    }

    private val siteCacheDir: File by lazy {
        File(cacheDir, "image_cache").apply { mkdirs() }
    }

    private val mutex = Mutex()

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
                    Log.e(TAG, "Failed to decode cached bitmap: ${e.message}")
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
                Log.e(TAG, "Failed to cache image: ${e.message}")
                -1
            }
        }
    }

    private fun estimateWebPSize(bitmap: Bitmap, quality: Int): Long {
        return (bitmap.byteCount.toLong() * when {
            quality >= 90 -> 0.35
            quality >= 80 -> 0.30
            else -> 0.25
        }).toLong()
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
            Log.e(TAG, "Failed to cleanup old files: ${e.message}")
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
            Log.e(TAG, "Failed to fetch image: ${e.message}")
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
            Log.e(TAG, "Failed to cleanup site cache: ${e.message}")
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
}

// ============================================
// REGION: UTILITY EXTENSIONS (401-450)
// ============================================

/**
 * MD5 hash extension for String
 */
fun String.md5(): String {
    return MessageDigest.getInstance("MD5").digest(this.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

// ============================================
// REGION: PERSISTENT CACHE MANAGER (451-550)
// ============================================

/**
 * Persistent Cache Manager - DISK-BASED cache untuk semua modul
 *
 * Features:
 * - Survives app restart (unlike memory cache)
 * - TTL: 24 hours (longer than 30 min memory cache)
 * - Auto-cleanup when size exceeds limit
 * - Thread-safe dengan Mutex
 *
 * Usage:
 * ```kotlin
 * val cache = PersistentCacheManager<List<SearchResponse>>(
 *     ttl = 24 * 60 * 60 * 1000L,  // 24 hours
 *     maxSize = 50 * 1024 * 1024L  // 50MB
 * )
 *
 * cache.put("search:anime", searchResults)
 * val results = cache.get("search:anime")
 * ```
 */
class PersistentCacheManager<T>(
    private val ttl: Long = 24 * 60 * 60 * 1000L,  // 24 hours default
    private val maxSize: Long = 50 * 1024 * 1024L  // 50MB default
) {
    private val cache = ConcurrentHashMap<String, PersistentCachedResult<T>>()
    private val mutex = Mutex()

    // Disk cache directory (UNIQUE NAME to avoid conflicts!)
    private val persistentDiskCacheDir: File by lazy {
        val baseDir = System.getProperty("java.io.tmpdir") ?: "/tmp"
        File("$baseDir/cloudstream-persistent-cache").apply { mkdirs() }
    }

    /**
     * Get data dari cache (memory or disk)
     */
    suspend fun get(key: String): T? {
        return mutex.withLock {
            // Try memory first
            val memoryCached = cache[key]
            if (memoryCached != null && !memoryCached.isExpired()) {
                Log.d("PersistentCache", "✅ Memory HIT: $key")
                return@withLock memoryCached.data
            }

            // Try disk cache
            val diskFile = getPersistentCacheFile(key)
            if (diskFile.exists()) {
                try {
                    val diskCached = readPersistentFromDisk(diskFile)
                    if (diskCached != null && !diskCached.isExpired()) {
                        // Restore to memory
                        cache[key] = diskCached
                        Log.d("PersistentCache", "✅ Disk HIT: $key")
                        return@withLock diskCached.data
                    } else {
                        // Expired, delete
                        diskFile.delete()
                    }
                } catch (e: Exception) {
                    Log.e("PersistentCache", "Disk read failed: ${e.message}")
                    diskFile.delete()
                }
            }

            // Cache miss
            cache.remove(key)
            Log.d("PersistentCache", "❌ MISS: $key")
            null
        }
    }

    /**
     * Put data ke cache (memory + disk)
     */
    suspend fun put(key: String, data: T, customTtl: Long = ttl) {
        mutex.withLock {
            // Store in memory
            val cachedResult = PersistentCachedResult(data, System.currentTimeMillis(), customTtl)
            cache[key] = cachedResult

            // Store in disk
            try {
                val diskFile = getPersistentCacheFile(key)

                // Check disk space
                if (getPersistentTotalDiskSize() + estimatePersistentSize(data) > maxSize) {
                    cleanupPersistentDiskCache()
                }

                writePersistentToDisk(diskFile, cachedResult)
                Log.d("PersistentCache", "💾 Saved: $key (${estimatePersistentSize(data) / 1024}KB)")
            } catch (e: Exception) {
                Log.e("PersistentCache", "Disk write failed: ${e.message}")
            }
        }
    }

    /**
     * Remove data dari cache
     */
    suspend fun remove(key: String) {
        mutex.withLock {
            cache.remove(key)
            getPersistentCacheFile(key).delete()
            Log.d("PersistentCache", "🗑️ Removed: $key")
        }
    }

    /**
     * Clear semua cache
     */
    suspend fun clear() {
        mutex.withLock {
            cache.clear()
            persistentDiskCacheDir.listFiles()?.forEach { it.delete() }
            Log.d("PersistentCache", "🗑️ Cache cleared")
        }
    }

    /**
     * Get cache size (memory entries)
     */
    suspend fun size(): Int = cache.size

    /**
     * Get disk cache size (bytes)
     */
    suspend fun diskSize(): Long = mutex.withLock { getPersistentTotalDiskSize() }

    // ============================================
    // HELPER FUNCTIONS (UNIQUE NAMES!)
    // ============================================

    private fun getPersistentCacheFile(key: String): File {
        val hash = key.md5()
        return File(persistentDiskCacheDir, "persistent_$hash.dat")
    }

    @Suppress("UNCHECKED_CAST")
    private fun readPersistentFromDisk(file: File): PersistentCachedResult<T>? {
        return try {
            ObjectInputStream(ByteArrayInputStream(file.readBytes())).use {
                it.readObject() as? PersistentCachedResult<T>
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun writePersistentToDisk(file: File, data: PersistentCachedResult<T>) {
        ByteArrayOutputStream().use { baos ->
            ObjectOutputStream(baos).use { oos ->
                oos.writeObject(data)
                file.writeBytes(baos.toByteArray())
            }
        }
    }

    private fun estimatePersistentSize(data: T): Long {
        return try {
            ByteArrayOutputStream().use { baos ->
                ObjectOutputStream(baos).use { oos ->
                    oos.writeObject(data)
                    baos.size().toLong()
                }
            }
        } catch (e: Exception) {
            1024  // Default 1KB estimate
        }
    }

    private fun getPersistentTotalDiskSize(): Long {
        return persistentDiskCacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun cleanupPersistentDiskCache() {
        // Delete oldest files until under 80% capacity
        val files = persistentDiskCacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var freed = 0L
        val targetFree = (getPersistentTotalDiskSize() * 0.2).toLong()

        for (file in files) {
            if (freed >= targetFree) break
            file.delete()
            freed += file.length()
            Log.d("PersistentCache", "🗑️ Deleted old: ${file.name}")
        }
    }
}

/**
 * Cached result dengan TTL
 */
data class PersistentCachedResult<T>(
    val data: T,
    val timestamp: Long,
    val ttl: Long
) {
    fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttl
}
