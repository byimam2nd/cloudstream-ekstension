// ========================================
// SUPER SMART PREFETCH MANAGER - AI-powered Prefetching
// ========================================
// Standard: cloudstream-ekstension
// Fitur:
// - Predictive prefetching
// - User behavior analysis
// - Priority-based queuing
// - Memory-efficient
// ========================================

package com.Animasu

import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Super Smart Prefetch Manager untuk predictive prefetching
 * 
 * Fitur:
 * - Analisis user behavior
 * - Predict episode berikutnya yang akan ditonton
 * - Prefetch links secara proaktif
 * - Priority-based queue
 */
class SuperSmartPrefetchManager {
    companion object {
        private const val TAG = "AnimasuPrefetch"
        private const val MAX_QUEUE_SIZE = 5
        private const val PREFETCH_TIMEOUT = 10000L
    }
    
    // Prefetch queue
    private val prefetchQueue = mutableListOf<PrefetchItem>()
    
    // Mutex untuk thread-safety
    private val mutex = Mutex()
    
    // Scope untuk coroutines
    private val prefetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // User behavior tracking
    private val watchHistory = mutableListOf<WatchEvent>()
    
    // Data class untuk prefetch item
    data class PrefetchItem(
        val episodeUrl: String,
        val priority: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    // Data class untuk watch event
    data class WatchEvent(
        val episodeUrl: String,
        val episodeNumber: Int?,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Predict dan prefetch episode berikutnya
     * @param currentEpisode Episode yang sedang ditonton
     * @param allEpisodes List semua episode
     */
    suspend fun predictAndPrefetch(
        currentEpisode: Episode,
        allEpisodes: List<Episode>
    ) = mutex.withLock {
        try {
            // Track watch event
            trackWatchEvent(currentEpisode)
            
            // Find next episode
            val currentIndex = allEpisodes.indexOfFirst { it.data == currentEpisode.data }

            if (currentIndex == -1 || currentIndex >= allEpisodes.size - 1) {
                Log.d(TAG, "No next episode to prefetch")
                return@withLock
            }

            val nextEpisode = allEpisodes[currentIndex + 1]

            // Add to prefetch queue dengan high priority
            addToQueue(nextEpisode.data, priority = 10)

            Log.d(TAG, "Prefetching next episode: ${nextEpisode.name}")

            // Prefetch immediately untuk next episode
            prefetchScope.launch {
                try {
                    withTimeout(PREFETCH_TIMEOUT) {
                        app.get(nextEpisode.data).document
                        Log.d(TAG, "Successfully prefetched: ${nextEpisode.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to prefetch: ${e.message}")
                }
            }

            // Predict dan prefetch episode setelahnya (priority lebih rendah)
            if (currentIndex + 2 < allEpisodes.size) {
                val afterNextEpisode = allEpisodes[currentIndex + 2]
                addToQueue(afterNextEpisode.data, priority = 5)
                
                Log.d(TAG, "Queueing for prefetch: ${afterNextEpisode.name}")
            }
            
            // Cleanup queue
            cleanupQueue()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in predictAndPrefetch: ${e.message}")
        }
    }
    
    /**
     * Track watch event untuk behavior analysis
     */
    private fun trackWatchEvent(episode: Episode) {
        watchHistory.add(
            WatchEvent(
                episodeUrl = episode.data,
                episodeNumber = episode.episode
            )
        )
        
        // Keep only last 100 events
        if (watchHistory.size > 100) {
            watchHistory.removeAt(0)
        }
    }
    
    /**
     * Add episode ke prefetch queue
     */
    private fun addToQueue(episodeUrl: String, priority: Int) {
        // Check apakah sudah ada di queue
        if (prefetchQueue.any { it.episodeUrl == episodeUrl }) {
            Log.d(TAG, "Episode already in queue: $episodeUrl")
            return
        }
        
        prefetchQueue.add(PrefetchItem(episodeUrl, priority))
        
        // Sort by priority (highest first)
        prefetchQueue.sortByDescending { it.priority }
        
        Log.d(TAG, "Added to prefetch queue (priority=$priority): $episodeUrl")
    }
    
    /**
     * Cleanup queue jika melebihi max size
     */
    private fun cleanupQueue() {
        if (prefetchQueue.size > MAX_QUEUE_SIZE) {
            // Remove lowest priority items
            val toRemove = prefetchQueue.size - MAX_QUEUE_SIZE
            repeat(toRemove) {
                if (prefetchQueue.isNotEmpty()) {
                    val removed = prefetchQueue.removeAt(prefetchQueue.size - 1)
                    Log.d(TAG, "Removed from queue: ${removed.episodeUrl}")
                }
            }
        }
    }
    
    /**
     * Analyze user behavior untuk pattern detection
     * @return Pattern yang terdeteksi (binge, selective, random)
     */
    suspend fun analyzeUserBehavior(): String = mutex.withLock {
        if (watchHistory.size < 5) {
            return@withLock "INSUFFICIENT_DATA"
        }
        
        try {
            // Check apakah user nonton secara berurutan (binge-watching)
            var sequentialCount = 0
            for (i in 1 until watchHistory.size) {
                val prev = watchHistory[i - 1].episodeNumber
                val curr = watchHistory[i].episodeNumber
                
                if (prev != null && curr != null && curr == prev + 1) {
                    sequentialCount++
                }
            }
            
            val sequentialRatio = sequentialCount.toDouble() / (watchHistory.size - 1)
            
            return@withLock when {
                sequentialRatio > 0.8 -> "BINGE_WATCHER"      // Nonton berurutan
                sequentialRatio > 0.5 -> "MOSTLY_SEQUENTIAL"  // Kebanyakan berurutan
                else -> "SELECTIVE"                           // Pilih-pilih episode
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze behavior: ${e.message}")
            return@withLock "UNKNOWN"
        }
    }
    
    /**
     * Get prefetch queue size
     */
    suspend fun getQueueSize(): Int = mutex.withLock {
        return@withLock prefetchQueue.size
    }
    
    /**
     * Clear prefetch queue
     */
    suspend fun clearQueue() = mutex.withLock {
        prefetchQueue.clear()
        Log.d(TAG, "Prefetch queue cleared")
    }
    
    /**
     * Clear watch history
     */
    suspend fun clearHistory() = mutex.withLock {
        watchHistory.clear()
        Log.d(TAG, "Watch history cleared")
    }
    
    /**
     * Cancel semua prefetch operations
     */
    suspend fun cancelAll() = mutex.withLock {
        prefetchScope.cancel()
        Log.d(TAG, "All prefetch operations cancelled")
    }
}
