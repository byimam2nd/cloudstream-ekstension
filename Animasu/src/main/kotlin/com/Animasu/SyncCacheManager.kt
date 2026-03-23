// ========================================
package com.Animasu
// AUTO-GENERATED - DO NOT EDIT MANUALLY

// Synced from common/MasterCacheManager.kt
import kotlinx.coroutines.sync.Mutex
// File: SyncCacheManager.kt
import kotlinx.coroutines.sync.withLock
// ========================================

/**
 * Generic thread-safe cache manager with TTL
 * 
 * Usage:
 * ```
 * val cache = CacheManager<List<SearchResponse>>(defaultTtl = 5 * 60 * 1000L)
 * ```
 */
class CacheManager<T>(private val defaultTtl: Long = 300000) {
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

    suspend fun put(key: String, data: T, ttl: Long = defaultTtl) {
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

data class CachedResult<T>(
    val data: T,
    val timestamp: Long,
    val ttl: Long = 300000
) {
    fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttl
}
