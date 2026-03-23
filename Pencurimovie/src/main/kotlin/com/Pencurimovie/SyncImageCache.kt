// ========================================
package com.Pencurimovie
// AUTO-GENERATED - DO NOT EDIT MANUALLY

// Synced from common/MasterImageCache.kt
import android.graphics.Bitmap
// File: SyncImageCache.kt
import android.graphics.BitmapFactory
// ========================================
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Advanced Image Cache with:
 * - Disk-only storage (persistent)
 * - 200MB shared limit across all sites
 * - Smart compression (WebP, 85% quality)
 * - Optimized for Android/Google TV (600x900)
 * - Auto-cleanup: Delete 2x old before adding new
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

    private val siteCacheDir: File = File(cacheDir, "image_cache_anichin").apply { mkdirs() }
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

fun String.md5(): String {
    return MessageDigest.getInstance("MD5").digest(this.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
