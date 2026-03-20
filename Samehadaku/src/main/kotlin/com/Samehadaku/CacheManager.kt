// ========================================
// CACHE MANAGER - Thread-safe Cache dengan TTL
// ========================================
// Standard: cloudstream-ekstension
// Fitur:
// - TTL (Time To Live) otomatis
// - Thread-safe dengan coroutines
// - Auto cleanup expired entries
// - Max size limiting
// ========================================

package com.Animasu

import com.lagradost.api.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe cache manager dengan TTL (Time To Live)
 * @param T Type data yang di-cache
 * @param ttl Time To Live dalam milliseconds (default: 5 menit)
 * @param maxSize Maximum jumlah entries (default: 100)
 */
class CacheManager<T>(
    private val ttl: Long = 5 * 60 * 1000L,  // 5 minutes
    private val maxSize: Int = 100
) {
    // Data class untuk cache entry
    data class CacheEntry<T>(
        val value: T,
        val timestamp: Long
    )
    
    // Cache storage
    private val cache = mutableMapOf<String, CacheEntry<T>>()
    
    // Mutex untuk thread-safety
    private val mutex = Mutex()
    
    /**
     * Get data dari cache
     * @param key Key cache
     * @return Data jika ada dan belum expired, null jika tidak
     */
    suspend fun get(key: String): T? = mutex.withLock {
        val entry = cache[key] ?: return@withLock null
        
        // Check apakah sudah expired
        val now = System.currentTimeMillis()
        val elapsed = now - entry.timestamp
        
        if (elapsed > ttl) {
            // Expired, remove dari cache
            cache.remove(key)
            Log.d("AnimasuCache", "Cache expired for key: $key")
            return@withLock null
        }
        
        Log.d("AnimasuCache", "Cache HIT for key: $key")
        return@withLock entry.value
    }
    
    /**
     * Simpan data ke cache
     * @param key Key cache
     * @param value Data yang akan di-cache
     */
    suspend fun put(key: String, value: T) = mutex.withLock {
        // Check size, cleanup jika perlu
        if (cache.size >= maxSize) {
            cleanup()
        }
        
        cache[key] = CacheEntry(value, System.currentTimeMillis())
        Log.d("AnimasuCache", "Cache PUT for key: $key (size: ${cache.size})")
    }
    
    /**
     * Remove data dari cache
     * @param key Key cache yang akan di-remove
     */
    suspend fun remove(key: String) = mutex.withLock {
        cache.remove(key)
        Log.d("AnimasuCache", "Cache REMOVE for key: $key")
    }
    
    /**
     * Clear semua cache
     */
    suspend fun clear() = mutex.withLock {
        cache.clear()
        Log.d("AnimasuCache", "Cache CLEAR all")
    }
    
    /**
     * Cleanup expired entries
     */
    private fun cleanup() {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()
        
        // Find expired entries
        cache.forEach { (key, entry) ->
            if (now - entry.timestamp > ttl) {
                toRemove.add(key)
            }
        }
        
        // Remove expired entries
        toRemove.forEach { key ->
            cache.remove(key)
        }
        
        if (toRemove.isNotEmpty()) {
            Log.d("AnimasuCache", "Cleanup removed ${toRemove.size} expired entries")
        }
    }
    
    /**
     * Get cache size
     * @return Jumlah entries di cache
     */
    suspend fun size(): Int = mutex.withLock {
        return@withLock cache.size
    }
    
    /**
     * Check apakah key ada di cache (tanpa check expiry)
     * @param key Key cache
     * @return true jika ada, false jika tidak
     */
    suspend fun containsKey(key: String): Boolean = mutex.withLock {
        return@withLock cache.containsKey(key)
    }
}
