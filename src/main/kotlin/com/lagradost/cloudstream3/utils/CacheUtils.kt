// Shared caching utility for all extensions
// Provides instant search & main page results (5 minute TTL)

package com lagradost cloudstream3 utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Cache entry with TTL (Time To Live)
data class CachedResult<T>(
    val data: T,
    val timestamp: Long,
    val ttl: Long = 300000 // 5 minutes default
) {
    fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttl
}

// Thread-safe cache manager
class CacheManager<T> {
    private val cache = mutableMapOf<String, CachedResult<T>>()
    private val mutex = Mutex()
    
    // Get from cache (returns null if not found or expired)
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
    
    // Put to cache
    suspend fun put(key: String, data: T, ttl: Long = 300000) {
        mutex.withLock {
            cache[key] = CachedResult(data, System.currentTimeMillis(), ttl)
            // Clean old entries
            cache.entries.removeAll { it.value.isExpired() }
        }
    }
    
    // Clear all cache
    suspend fun clear() {
        mutex.withLock {
            cache.clear()
        }
    }
    
    // Get cache size
    suspend fun size(): Int {
        return mutex.withLock { cache.size }
    }
}

// Global cache instances for search and main page
val searchCache = CacheManager<List<SearchResponse>>()
val mainPageCache = CacheManager<HomePageResponse>()
