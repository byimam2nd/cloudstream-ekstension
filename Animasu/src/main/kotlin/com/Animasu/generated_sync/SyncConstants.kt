// ========================================
// MASTER CONSTANTS - v3.2 SAFE
// Centralized constants - NO CONFLICTS
// ========================================
// Last Updated: 2026-03-25
// Sync Target: generated_sync/SyncConstants.kt
//
// DESIGN PRINCIPLES:
// - Only constants NOT in MasterUtils.kt
// - Unique names with NET_ prefix for network
// - Unique names with CACHE_ prefix for cache
// - Unique names with EXT_ prefix for extractor
// ========================================

// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/MasterConstants.kt
// File: SyncConstants.kt
// ========================================
package com.Animasu.generated_sync

// ============================================
// REGION: NETWORK CONFIGURATION
// ============================================

/**
 * Network timeout configuration
 * These are NOT in MasterUtils.kt
 */
const val NET_CONNECT_TIMEOUT_MS = 15_000L
const val NET_READ_TIMEOUT_MS = 30_000L
const val NET_WRITE_TIMEOUT_MS = 15_000L

/**
 * Connection pool configuration
 */
const val NET_CONNECTION_POOL_SIZE = 20
const val NET_CONNECTION_KEEP_ALIVE_MINUTES = 10L

/**
 * DNS cache TTL (minutes)
 */
const val NET_DNS_CACHE_TTL_MINUTES = 5L

// ============================================
// REGION: CACHE CONFIGURATION
// ============================================

/**
 * Cache TTL configuration
 * Different from MasterUtils internal constants
 */
const val CACHE_SEARCH_TTL_MS = 30 * 60 * 1000L      // 30 minutes
const val CACHE_MAIN_PAGE_TTL_MS = 10 * 60 * 1000L   // 10 minutes
const val CACHE_DETAIL_TTL_MS = 15 * 60 * 1000L      // 15 minutes
const val CACHE_EPISODE_TTL_MS = 60 * 60 * 1000L     // 1 hour
const val CACHE_IMAGE_TTL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days

/**
 * Cache size limits
 */
const val CACHE_MAX_ENTRIES = 100
const val CACHE_MAX_DISK_SIZE_MB = 200L
const val CACHE_MAX_IMAGE_SIZE_MB = 200L

// ============================================
// REGION: EXTRACTOR CONFIGURATION
// ============================================

/**
 * Extractor-specific configuration
 */
const val EXT_TIMEOUT_MS = 10_000L
const val EXT_MAX_RETRIES = 2
const val EXT_CONCURRENT_LIMIT = 3

// ============================================
// REGION: PERFORMANCE CONFIGURATION
// ============================================

/**
 * Performance monitoring thresholds
 */
const val PERF_SLOW_THRESHOLD_MS = 1000L
const val PERF_VERY_SLOW_THRESHOLD_MS = 3000L

// ============================================
// REGION: IMAGE CONFIGURATION
// ============================================

/**
 * Image optimization configuration
 */
const val IMG_TARGET_WIDTH = 600
const val IMG_TARGET_HEIGHT = 900
const val IMG_COMPRESSION_QUALITY = 85

// ============================================
// REGION: API CONFIGURATION
// ============================================

/**
 * API request configuration
 */
const val API_TIMEOUT_MS = 15_000L
const val API_MAX_RETRIES = 3
const val API_RETRY_DELAY_MS = 1000L

// ============================================
// REGION: DEBUG CONFIGURATION
// ============================================

/**
 * Debug mode flag
 * Using different name from MasterUtils DEBUG_MODE
 */
const val GLOBAL_DEBUG_MODE = false
