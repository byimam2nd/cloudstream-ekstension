// ========================================
// MASTER RATE LIMITER - v3.7
// Centralized rate limiting untuk semua modul
// ========================================
// Last Updated: 2026-03-27
// Sync Target: generated_sync/SyncRateLimiter.kt
//
// PURPOSE:
// - Centralized rate limiting untuk semua modul
// - Module-specific delay configuration
// - Thread-safe dengan Mutex
// - Reusable untuk future modules
//
// USAGE:
// ```kotlin
// // Module-specific rate limiter
// private val animasuRateLimiter = ModuleRateLimiter.create("Animasu", 500L)
// 
// // In loadLinks or other functions
// animasuRateLimiter.delay()
// ```
// ========================================

// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/MasterRateLimiter.kt
// File: SyncRateLimiter.kt
// ========================================
package com.Pencurimovie.generated_sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

// ============================================
// REGION: MODULE RATE LIMITER
// ============================================

/**
 * Centralized rate limiter untuk semua modul
 * 
 * Features:
 * - Module-specific delay configuration
 * - Thread-safe dengan Mutex
 * - Auto-create limiter per module
 * - Reusable untuk future modules
 * 
 * Example usage:
 * ```kotlin
 * // Create module-specific limiter
 * private val animasuLimiter = ModuleRateLimiter.create("Animasu", 500L)
 * private val samehadakuLimiter = ModuleRateLimiter.create("Samehadaku", 500L)
 * 
 * // Use in functions
 * suspend fun loadLinks(...) {
 *     animasuLimiter.delay()
 *     // ... rest of code
 * }
 * ```
 */
object ModuleRateLimiter {
    private val limiters = ConcurrentHashMap<String, RateLimiter>()
    
    /**
     * Create or get rate limiter untuk module tertentu
     * 
     * @param moduleName Nama module (contoh: "Animasu", "Samehadaku")
     * @param delayMs Delay minimal antar request dalam milliseconds
     * @return RateLimiter instance untuk module tersebut
     */
    fun create(moduleName: String, delayMs: Long = 500L): RateLimiter {
        return limiters.getOrPut(moduleName) { 
            RateLimiter(moduleName, delayMs) 
        }
    }
    
    /**
     * Get existing limiter atau create dengan default delay
     */
    fun getOrCreate(moduleName: String): RateLimiter {
        return create(moduleName, 500L)
    }
    
    /**
     * Clear semua limiters (untuk testing)
     */
    fun clearAll() {
        limiters.clear()
    }
    
    /**
     * Get count of active limiters (untuk monitoring)
     */
    fun getActiveCount(): Int {
        return limiters.size
    }
    
    // ============================================
    // REGION: RATE LIMITER CLASS
    // ============================================
    
    /**
     * Rate limiter instance untuk satu module
     * 
     * Internal class yang handle actual rate limiting logic
     */
    class RateLimiter(
        private val name: String,
        private val delayMs: Long
    ) {
        private val mutex = Mutex()
        private var lastRequest = 0L
        
        /**
         * Delay untuk rate limiting
         * 
         * Akan delay jika waktu sejak last request < delayMs
         * Thread-safe dengan Mutex
         */
        suspend fun delay() = mutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequest
            
            if (elapsed < delayMs) {
                val delayNeeded = delayMs - elapsed
                delay(delayNeeded)
            }
            
            lastRequest = System.currentTimeMillis()
        }
        
        /**
         * Get last request timestamp (untuk monitoring)
         */
        fun getLastRequest(): Long {
            return lastRequest
        }
        
        /**
         * Get delay configuration (untuk monitoring)
         */
        fun getDelayMs(): Long {
            return delayMs
        }
        
        /**
         * Get module name (untuk monitoring)
         */
        fun getName(): String {
            return name
        }
        
        /**
         * Reset last request timestamp (untuk testing)
         */
        fun reset() {
            lastRequest = 0L
        }
    }
}

// ============================================
// REGION: BACKWARD COMPATIBILITY
// ============================================

/**
 * Backward compatibility untuk module yang sudah punya rate limiter
 * 
 * Usage:
 * ```kotlin
 * // Old code (still works)
 * animasuRateLimitDelay()
 * 
 * // New code (recommended)
 * ModuleRateLimiter.create("Animasu", 500L).delay()
 * ```
 */

/**
 * Animasu-specific rate limiter (backward compatibility)
 */
internal object AnimasuRateLimiter {
    private val limiter = ModuleRateLimiter.create("Animasu", 500L)
    suspend fun delay() = limiter.delay()
}

/**
 * Samehadaku-specific rate limiter (backward compatibility)
 */
internal object SamehadakuRateLimiter {
    private val limiter = ModuleRateLimiter.create("Samehadaku", 500L)
    suspend fun delay() = limiter.delay()
}

/**
 * Backward compatible function untuk Animasu
 */
internal suspend fun animasuRateLimitDelay() = AnimasuRateLimiter.delay()

/**
 * Backward compatible function untuk Samehadaku
 */
internal suspend fun samehadakuRateLimitDelay() = SamehadakuRateLimiter.delay()
