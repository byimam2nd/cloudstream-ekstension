// ========================================
// SMART CACHE MONITOR - Fingerprint-based Invalidation
// ========================================
// Standard: cloudstream-ekstension
// Fitur:
// - Generate fingerprint dari homepage content
// - Auto-invalidate cache saat konten berubah
// - Monitoring berbasis hash
// ========================================

package com.Animasu

import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Smart Cache Monitor untuk fingerprint-based cache invalidation
 * 
 * Cara Kerja:
 * 1. Fetch titles dari homepage
 * 2. Generate fingerprint (hash) dari titles
 * 3. Compare dengan cached fingerprint
 * 4. Invalidate cache jika berbeda
 */
class AnimasuMonitor {
    companion object {
        private const val TAG = "AnimasuMonitor"
    }
    
    // Mutex untuk thread-safety
    private val mutex = Mutex()
    
    // Cached fingerprint
    private var cachedFingerprint: String? = null
    
    // Last check timestamp
    private var lastCheckTime = 0L
    
    // Check interval (5 menit)
    private val checkInterval = 5 * 60 * 1000L
    
    /**
     * Generate fingerprint dari homepage content
     * @param mainUrl Base URL site
     * @return Fingerprint (hash) dari content
     */
    private suspend fun generateFingerprint(mainUrl: String): String {
        try {
            // Fetch homepage
            val document = app.get("$mainUrl/?urutan=update&halaman=1").document
            
            // Extract titles (ambil 10 pertama untuk fingerprint)
            val titles = document.select("div.listupd div.bs div.tt")
                .take(10)
                .map { it.text().trim() }
                .joinToString("|")
            
            // Generate simple hash
            val fingerprint = titles.hashCode().toString(16)
            
            Log.d(TAG, "Generated fingerprint: $fingerprint")
            return fingerprint
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate fingerprint: ${e.message}")
            return ""
        }
    }
    
    /**
     * Check apakah cache perlu di-invalidate
     * @param mainUrl Base URL site
     * @return true jika cache perlu di-invalidate, false jika tidak
     */
    suspend fun shouldInvalidate(mainUrl: String): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        
        // Skip jika belum saatnya check
        if (now - lastCheckTime < checkInterval) {
            return@withLock false
        }
        
        lastCheckTime = now
        
        // Generate fingerprint baru
        val newFingerprint = generateFingerprint(mainUrl)
        
        // Compare dengan cached fingerprint
        val shouldInvalidate = cachedFingerprint != null && 
                               newFingerprint.isNotEmpty() && 
                               cachedFingerprint != newFingerprint
        
        if (shouldInvalidate) {
            Log.w(TAG, "Content changed! Cache invalidation required")
            Log.w(TAG, "Old fingerprint: $cachedFingerprint")
            Log.w(TAG, "New fingerprint: $newFingerprint")
        }
        
        // Update cached fingerprint
        cachedFingerprint = newFingerprint.takeIf { it.isNotEmpty() }
        
        return@withLock shouldInvalidate
    }
    
    /**
     * Reset monitor (clear cached fingerprint)
     */
    suspend fun reset() = mutex.withLock {
        cachedFingerprint = null
        lastCheckTime = 0L
        Log.d(TAG, "Monitor reset")
    }
    
    /**
     * Get current fingerprint
     * @return Current fingerprint atau null jika belum ada
     */
    suspend fun getFingerprint(): String? = mutex.withLock {
        return@withLock cachedFingerprint
    }
}
