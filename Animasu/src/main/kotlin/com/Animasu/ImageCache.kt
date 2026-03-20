// ========================================
// IMAGE CACHE - Disk-based Image Caching
// ========================================
// Standard: cloudstream-ekstension
// Fitur:
// - 200MB disk limit
// - Site-specific cache folders
// - Auto global cleanup
// - WEBP compression
// ========================================

package com.Animasu

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Disk-based Image Cache untuk caching poster/background
 * 
 * Fitur:
 * - Cache di disk (bukan memory)
 * - Max 200MB
 * - Auto cleanup saat limit tercapai
 * - Site-specific folders
 */
class ImageCache {
    companion object {
        private const val TAG = "AnimasuImageCache"
        private const val MAX_CACHE_SIZE_MB = 200
        private const val MAX_CACHE_SIZE_BYTES = MAX_CACHE_SIZE_MB * 1024 * 1024L
    }
    
    // Cache folder (site-specific)
    private val cacheDir: File by lazy {
        // Gunakan temp directory untuk cache
        val baseDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        val cacheDir = File(baseDir, "animasu_image_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        cacheDir
    }
    
    /**
     * Fetch image dari URL dan cache ke disk
     * @param imageUrl URL image
     * @return Bitmap image atau null jika gagal
     */
    suspend fun fetchAndCache(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Generate filename dari URL
            val fileName = imageUrl.hashCode().toString() + ".webp"
            val cacheFile = File(cacheDir, fileName)
            
            // Check apakah sudah ada di cache
            if (cacheFile.exists()) {
                Log.d(TAG, "Cache HIT for image: $imageUrl")
                
                // Check apakah file masih valid (belum expired)
                val age = System.currentTimeMillis() - cacheFile.lastModified()
                val maxAge = 7 * 24 * 60 * 60 * 1000L // 7 days
                
                if (age < maxAge) {
                    return@withContext BitmapFactory.decodeFile(cacheFile.absolutePath)
                } else {
                    Log.d(TAG, "Cache expired, refetching...")
                    cacheFile.delete()
                }
            }
            
            Log.d(TAG, "Cache MISS for image: $imageUrl")
            
            // Fetch image dari URL
            val response = app.get(imageUrl)
            val bytes = response.bytes()
            
            if (bytes.isEmpty()) {
                Log.e(TAG, "Empty response for image: $imageUrl")
                return@withContext null
            }
            
            // Decode bitmap
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext null
            
            // Save ke cache
            try {
                FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 80, out)
                }
                Log.d(TAG, "Image cached: $imageUrl (${bytes.size / 1024}KB)")
                
                // Check size dan cleanup jika perlu
                cleanupIfNeeded()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save image to cache: ${e.message}")
            }
            
            return@withContext bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch image: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Get image dari cache tanpa fetch
     * @param imageUrl URL image
     * @return Bitmap dari cache atau null
     */
    suspend fun getFromCache(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val fileName = imageUrl.hashCode().toString() + ".webp"
            val cacheFile = File(cacheDir, fileName)
            
            if (cacheFile.exists()) {
                Log.d(TAG, "Cache HIT for image: $imageUrl")
                return@withContext BitmapFactory.decodeFile(cacheFile.absolutePath)
            }
            
            Log.d(TAG, "Cache MISS for image: $imageUrl")
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get image from cache: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Remove image dari cache
     * @param imageUrl URL image yang akan di-remove
     */
    suspend fun remove(imageUrl: String) = withContext(Dispatchers.IO) {
        try {
            val fileName = imageUrl.hashCode().toString() + ".webp"
            val cacheFile = File(cacheDir, fileName)
            
            if (cacheFile.exists()) {
                cacheFile.delete()
                Log.d(TAG, "Image removed from cache: $imageUrl")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove image from cache: ${e.message}")
        }
    }
    
    /**
     * Clear semua cache
     */
    suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "All images cleared from cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache: ${e.message}")
        }
    }
    
    /**
     * Cleanup cache jika ukuran melebihi limit
     */
    private suspend fun cleanupIfNeeded() = withContext(Dispatchers.IO) {
        try {
            val files = cacheDir.listFiles() ?: return@withContext
            val totalSize = files.sumOf { it.length() }
            
            Log.d(TAG, "Current cache size: ${totalSize / 1024 / 1024}MB / ${MAX_CACHE_SIZE_MB}MB")
            
            if (totalSize > MAX_CACHE_SIZE_BYTES) {
                Log.d(TAG, "Cache size exceeded, cleaning up...")
                
                // Sort by last modified (oldest first)
                val sortedFiles = files.sortedBy { it.lastModified() }
                
                // Delete oldest files hingga ukuran < 80% limit
                val targetSize = MAX_CACHE_SIZE_BYTES * 0.8
                var currentSize = totalSize
                
                for (file in sortedFiles) {
                    if (currentSize <= targetSize) break
                    
                    file.delete()
                    currentSize -= file.length()
                    Log.d(TAG, "Deleted old cache file: ${file.name}")
                }
                
                Log.d(TAG, "Cleanup completed. New size: ${currentSize / 1024 / 1024}MB")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup cache: ${e.message}")
        }
    }
    
    /**
     * Get cache size dalam bytes
     */
    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        try {
            return@withContext cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get cache size: ${e.message}")
            return@withContext 0L
        }
    }
    
    /**
     * Get jumlah file di cache
     */
    suspend fun getFileCount(): Int = withContext(Dispatchers.IO) {
        try {
            return@withContext cacheDir.listFiles()?.size ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file count: ${e.message}")
            return@withContext 0
        }
    }
}
