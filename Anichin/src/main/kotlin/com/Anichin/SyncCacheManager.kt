// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/MasterCacheManager.kt
// File: SyncCacheManager.kt
// ========================================
package com.Anichin

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.ObjectOutputStream
import java.io.ObjectInputStream
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream

/**
 * Generic thread-safe cache manager with TTL + Disk Cache
 * 
 * Features:
 * - In-memory cache for fast access (TTL: 30 minutes)
 * - Disk cache for persistence (TTL: 24 hours)
 * - Total disk cache limit: 200MB (shared across all providers)
 * - Auto-cleanup when limit exceeded
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
    private val cache = mutableMapOf<String, CachedResult<T>>()
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
            
            // Cleanup expired entries
            cache.entries.removeAll { it.value.isExpired() }
        }
    }

    suspend fun clear() {
        mutex.withLock { 
            cache.clear()
            diskCacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    suspend fun size(): Int {
        return mutex.withLock { cache.size }
    }
    
    suspend fun diskSize(): Long {
        return mutex.withLock { getTotalDiskSize() }
    }

    // Helper functions
    private fun getDiskCacheFile(key: String): File {
        // Use MD5 hash for filename to avoid special characters
        val hash = java.security.MessageDigest
            .getInstance("MD5")
            .digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
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
}

data class CachedResult<T>(
    val data: T,
    val timestamp: Long,
    val ttl: Long = 30 * 60 * 1000L
) {
    fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttl
    fun isExpired(customTtl: Long): Boolean = System.currentTimeMillis() - timestamp > customTtl
}
