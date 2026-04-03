// ========================================
// MASTER CIRCUIT BREAKER - v3.2 SAFE
// Failure isolation pattern - NO CONFLICTS
// ========================================
// Last Updated: 2026-03-25
// Sync Target: generated_sync/SyncCircuitBreaker.kt
//
// DESIGN PRINCIPLES:
// - All types are PUBLIC (no private enum)
// - No conflicting constants with MasterUtils
// - Self-contained functionality
// ========================================

// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/MasterCircuitBreaker.kt
// File: SyncCircuitBreaker.kt
// ========================================
package com.Idlix.generated_sync

import com.lagradost.api.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

// ============================================
// REGION: PUBLIC ENUM (Must be public!)
// ============================================

/**
 * Circuit Breaker states - PUBLIC enum
 */
enum class CircuitState { 
    CLOSED,      // Normal operation
    OPEN,        // Failure threshold exceeded
    HALF_OPEN    // Testing recovery
}

// ============================================
// REGION: CONFIGURATION CONSTANTS
// ============================================

// Using CB_ prefix to avoid conflicts with MasterUtils
private const val CB_DEFAULT_FAILURE_THRESHOLD = 5
private const val CB_DEFAULT_SUCCESS_THRESHOLD = 2
private const val CB_DEFAULT_RESET_TIMEOUT_MS = 60_000L // 1 minute

// ============================================
// REGION: CIRCUIT BREAKER CLASS
// ============================================

/**
 * Circuit Breaker pattern untuk isolate failures
 * 
 * Usage:
 * ```
 * val breaker = CircuitBreaker("VidStack")
 * val result = breaker.execute { extractor.getUrl() }
 * ```
 */
class CircuitBreaker(
    private val name: String,
    private val failureThreshold: Int = CB_DEFAULT_FAILURE_THRESHOLD,
    private val successThreshold: Int = CB_DEFAULT_SUCCESS_THRESHOLD,
    private val resetTimeoutMs: Long = CB_DEFAULT_RESET_TIMEOUT_MS
) {
    private var state: CircuitState = CircuitState.CLOSED
    private var failureCount = 0
    private var successCount = 0
    private var lastFailureTime = 0L
    private val mutex = Mutex()
    
    /**
     * Execute block dengan circuit breaker protection
     * Returns null jika circuit OPEN
     *
     * FIX: Mutex hanya untuk state transition, bukan selama eksekusi block
     */
    suspend fun <T> execute(block: suspend () -> T): T? {
        // Step 1: Check state (mutex only for state check)
        val shouldExecute = mutex.withLock {
            when (state) {
                CircuitState.OPEN -> {
                    // Check if timeout exceeded
                    if (System.currentTimeMillis() - lastFailureTime > resetTimeoutMs) {
                        Log.d("CircuitBreaker", "🟡 $name: OPEN → HALF_OPEN")
                        state = CircuitState.HALF_OPEN
                        successCount = 0
                        true  // Allow execution
                    } else {
                        Log.w("CircuitBreaker", "🔴 $name: Circuit OPEN, skipping")
                        false  // Block execution
                    }
                }

                CircuitState.HALF_OPEN, CircuitState.CLOSED -> {
                    true  // Allow execution
                }
            }
        }

        if (!shouldExecute) {
            return null
        }

        // Step 2: Execute block (NON-BLOCKING - no mutex held)
        return try {
            val result = block()
            // Step 3: Update state on success (mutex only for state update)
            mutex.withLock { onSuccess() }
            result

        } catch (e: Exception) {
            Log.e("CircuitBreaker", "❌ $name: ${e.message}")
            // Step 3: Update state on failure (mutex only for state update)
            mutex.withLock { onFailure() }
            null
        }
    }
    
    private fun onSuccess() {
        failureCount = 0
        
        if (state == CircuitState.HALF_OPEN) {
            successCount++
            if (successCount >= successThreshold) {
                Log.d("CircuitBreaker", "🟢 $name: HALF_OPEN → CLOSED")
                state = CircuitState.CLOSED
                successCount = 0
            }
        }
    }
    
    private fun onFailure() {
        failureCount++
        lastFailureTime = System.currentTimeMillis()
        
        when (state) {
            CircuitState.CLOSED -> {
                if (failureCount >= failureThreshold) {
                    Log.e("CircuitBreaker", "🔴 $name: CLOSED → OPEN ($failureCount failures)")
                    state = CircuitState.OPEN
                }
            }
            
            CircuitState.HALF_OPEN -> {
                Log.e("CircuitBreaker", "🔴 $name: HALF_OPEN → OPEN (test failed)")
                state = CircuitState.OPEN
            }
            
            CircuitState.OPEN -> {}
        }
    }
    
    /**
     * Get current state - PUBLIC
     */
    fun getState(): CircuitState = state
    
    /**
     * Get failure count - PUBLIC
     */
    fun getFailureCount(): Int = failureCount
}

// ============================================
// REGION: CIRCUIT BREAKER REGISTRY
// ============================================

/**
 * Registry untuk manage multiple circuit breakers
 */
object CircuitBreakerRegistry {
    private val breakers = ConcurrentHashMap<String, CircuitBreaker>()
    
    /**
     * Get or create circuit breaker untuk extractor tertentu
     */
    fun getOrCreate(
        name: String, 
        failureThreshold: Int = CB_DEFAULT_FAILURE_THRESHOLD
    ): CircuitBreaker {
        return breakers.getOrPut(name) { 
            CircuitBreaker(name = name, failureThreshold = failureThreshold) 
        }
    }
    
    /**
     * Get semua circuit breaker states
     */
    fun getAllStates(): Map<String, CircuitState> {
        return breakers.mapValues { it.value.getState() }
    }
    
    /**
     * Reset circuit breaker tertentu
     */
    fun reset(name: String) {
        breakers.remove(name)
    }
    
    /**
     * Reset semua circuit breakers
     */
    fun resetAll() {
        breakers.clear()
    }
}
