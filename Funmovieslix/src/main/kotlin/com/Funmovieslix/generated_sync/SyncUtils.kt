// ========================================
// MASTER UTILS - v3.0 OPTIMIZED
// Utility functions untuk CloudStream Extension
// ========================================
// Last Updated: 2026-03-25
// Optimized for: CloudStream Extension Standards
//
// OPTIMIZATIONS (v3.0):
// - ✅ Object singleton untuk utility functions
// - ✅ Lazy initialization untuk translation map
// - ✅ Constants extraction untuk magic values
// - ✅ Region markers untuk navigation
// - ✅ ConcurrentHashMap untuk thread safety
// ========================================

// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/MasterUtils.kt
// File: SyncUtils.kt
// ========================================
package com.Funmovieslix.generated_sync

import com.lagradost.api.Log
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.nodes.Element
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

// ============================================
// REGION: CONFIGURATION CONSTANTS (1-50)
// ============================================

/**
 * Caching configuration constants
 */
internal const val SEARCH_CACHE_TTL = 30 * 60 * 1000L      // 30 menit
internal const val MAINPAGE_CACHE_TTL = 10 * 60 * 1000L    // 10 menit
internal const val MAX_CACHE_SIZE = 50                     // Max 50 entries

/**
 * Rate limiting configuration constants
 */
internal const val MIN_REQUEST_DELAY = 100L
internal const val MAX_REQUEST_DELAY = 500L

/**
 * Retry configuration constants
 */
internal const val DEFAULT_MAX_RETRIES = 3
internal const val DEFAULT_INITIAL_DELAY = 1000L
internal const val DEFAULT_MAX_DELAY = 10000L
internal const val DEFAULT_BACKOFF_MULTIPLIER = 2.0

/**
 * Debug mode flag
 */
private const val DEBUG_MODE = false

// ============================================
// REGION: RATE LIMITING (51-100)
// ============================================

/**
 * Object singleton untuk rate limiting
 * Thread-safe dengan Mutex
 * Support per-module independent rate limiting
 */
internal object RateLimiter {
    private val rateLimitMutex = Mutex()
    private val requestTimers = ConcurrentHashMap<String, Long>()

    /**
     * Delay untuk rate limiting dengan random jitter
     * Mencegah bot detection dengan request timing yang tidak predictable
     * 
     * @param moduleName Module name for independent rate limiting
     */
    suspend fun delay(moduleName: String = "default") = rateLimitMutex.withLock {
        val now = System.currentTimeMillis()
        val lastRequest = requestTimers[moduleName] ?: 0L
        val elapsed = now - lastRequest

        if (elapsed < MIN_REQUEST_DELAY) {
            val delayNeeded = MIN_REQUEST_DELAY - elapsed + Random.nextLong(0, MAX_REQUEST_DELAY - MIN_REQUEST_DELAY)
            kotlinx.coroutines.delay(delayNeeded)
        }

        requestTimers[moduleName] = System.currentTimeMillis()
    }
}

// Backward compatibility function
internal suspend fun rateLimitDelay(moduleName: String = "default") = RateLimiter.delay(moduleName)

// ============================================
// REGION: USER AGENT ROTATION (101-150)
// ============================================

/**
 * Object singleton untuk User-Agent rotation
 * Lazy initialization untuk better startup performance
 */
internal object UserAgentRotator {
    // Pre-defined User-Agents pool (Chrome, Firefox, Safari)
    private val USER_AGENTS = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0"
    )

    // Random starting index untuk diversity
    private val userAgentIndex = Random.nextInt(USER_AGENTS.size)

    /**
     * Get random User-Agent dari pool
     * Thread-safe dan deterministic per session
     */
    fun getRandom(): String {
        return USER_AGENTS[(userAgentIndex + Random.nextInt(USER_AGENTS.size)) % USER_AGENTS.size]
    }

    /**
     * Get specific User-Agent by index (untuk testing)
     */
    fun getByIndex(index: Int): String {
        return USER_AGENTS[index % USER_AGENTS.size]
    }
}

// Backward compatibility function
internal fun getRandomUserAgent(): String = UserAgentRotator.getRandom()

// ============================================
// REGION: RETRY HELPER (151-200)
// ============================================

/**
 * Object singleton untuk retry logic dengan exponential backoff
 */
internal object RetryHelper {
    /**
     * Execute block dengan retry logic
     * @param maxRetries Jumlah maksimal percobaan
     * @param initialDelay Delay awal (ms)
     * @param maxDelay Delay maksimal (ms)
     * @param backoffMultiplier Multiplier untuk exponential backoff
     * @param block Code yang akan dieksekusi
     * @return Result dari block
     */
    suspend fun <T> execute(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        initialDelay: Long = DEFAULT_INITIAL_DELAY,
        maxDelay: Long = DEFAULT_MAX_DELAY,
        backoffMultiplier: Double = DEFAULT_BACKOFF_MULTIPLIER,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        var delayTime = initialDelay

        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                Log.w("Retry", "Attempt ${attempt + 1}/$maxRetries failed: ${e.message}")

                if (attempt < maxRetries - 1) {
                    delay(delayTime)
                    delayTime = (delayTime * backoffMultiplier).toLong().coerceAtMost(maxDelay)
                }
            }
        }

        throw lastException ?: Exception("Unknown error")
    }
}

// Backward compatibility function
internal suspend fun <T> executeWithRetry(
    maxRetries: Int = 3,
    initialDelay: Long = 1000L,
    maxDelay: Long = 10000L,
    backoffMultiplier: Double = 2.0,
    block: suspend () -> T
): T = RetryHelper.execute(maxRetries, initialDelay, maxDelay, backoffMultiplier, block)

// ============================================
// REGION: LOGGING UTILS (201-250)
// ============================================

/**
 * Object singleton untuk logging
 */
internal object Logger {
    /**
     * Log debug message (hanya jika DEBUG_MODE = true)
     */
    fun debug(tag: String, message: String) {
        if (DEBUG_MODE) {
            Log.d(tag, message)
        }
    }

    /**
     * Log error message dengan optional exception
     */
    fun error(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, message)
        error?.let { Log.e(tag, "Cause: ${it.message}") }
    }

    /**
     * Log warning message
     */
    fun warning(tag: String, message: String) {
        Log.w(tag, message)
    }

    /**
     * Log info message
     */
    fun info(tag: String, message: String) {
        Log.i(tag, message)
    }
}

// Backward compatibility functions
internal fun logDebug(tag: String, message: String) = Logger.debug(tag, message)
internal fun logError(tag: String, message: String, error: Throwable? = null) = Logger.error(tag, message, error)

// ============================================
// REGION: TRANSLATION UTILS (251-400)
// ============================================

/**
 * Object singleton untuk auto-translation English → Indonesian
 * Lazy initialization untuk translation map
 */
internal object Translator {
    // Lazy initialization untuk menghemat memory dan startup time
    private val translationMap by lazy {
        mapOf(
            "watch" to "nonton",
            "streaming" to "streaming",
            "download" to "unduh",
            "free" to "gratis",
            "full" to "lengkap",
            "episode" to "episode",
            "season" to "musim",
            "movie" to "film",
            "series" to "series",
            "show" to "acara",
            "plot" to "alur cerita",
            "story" to "cerita",
            "about" to "tentang",
            "with" to "dengan",
            "and" to "dan",
            "the" to "",
            "in" to "di",
            "on" to "pada",
            "for" to "untuk",
            "from" to "dari",
            "quality" to "kualitas",
            "subtitle" to "subtitle",
            "indonesia" to "Indonesia",
            "english" to "Inggris",
            "chinese" to "China",
            "japanese" to "Jepang",
            "korean" to "Korea",
            "thriller" to "thriller",
            "action" to "aksi",
            "adventure" to "petualangan",
            "drama" to "drama",
            "comedy" to "komedi",
            "romance" to "romantis",
            "fantasy" to "fantasi",
            "sci-fi" to "fiksi ilmiah",
            "horror" to "horor",
            "mystery" to "misteri",
            "crime" to "kriminal",
            "suspense" to "tegang",
            "exciting" to "menegangkan",
            "amazing" to "luar biasa",
            "best" to "terbaik",
            "new" to "baru",
            "latest" to "terbaru",
            "popular" to "populer",
            "follows" to "mengikuti",
            "story of" to "cerita tentang",
            "life of" to "kehidupan",
            "journey of" to "perjalanan",
            "adventure of" to "petualangan",
            "must watch" to "wajib tonton",
            "don't miss" to "jangan lewatkan",
            "high quality" to "kualitas tinggi",
            "hd quality" to "kualitas HD",
            "full hd" to "full HD",
            "bluray" to "BluRay",
            "completed" to "selesai",
            "ongoing" to "berlangsung",
            "upcoming" to "akan datang"
        )
    }

    // Indonesian words untuk detection (lazy initialization)
    private val indonesianWords by lazy {
        listOf(
            "dengan", "dan", "yang", "untuk", "dari", "pada", "ini", "itu",
            "adalah", "merupakan", "telah", "sudah", "akan", "sedang",
            "nonton", "streaming", "unduh", "lengkap", "gratis",
            "film", "series", "episode", "musim", "alur", "cerita",
            "tentang", "mengikuti", "kehidupan", "perjalanan", "petualangan"
        )
    }

    /**
     * Check if text is likely in Indonesian
     */
    private fun isIndonesian(text: String): Boolean {
        val lowerText = text.lowercase()
        return indonesianWords.any { lowerText.contains(it) }
    }

    /**
     * Auto-translate English text to Indonesian
     * Uses smart translation with context awareness
     */
    fun translateToIndonesian(text: String?): String? {
        if (text.isNullOrBlank()) return null

        // Check if text is already in Indonesian (avoid double translation)
        if (isIndonesian(text)) return text

        // Translate using mapping
        var translated = text.lowercase()

        // Apply translations (lazy map access)
        translationMap.forEach { (english, indonesian) ->
            translated = translated.replace(english, indonesian, ignoreCase = true)
        }

        // Capitalize first letter
        return translated.replaceFirstChar { it.uppercase() }
    }

    /**
     * Clean and format description
     * Removes "Lihat selengkapnya" and similar truncation markers
     */
    fun cleanDescription(text: String?): String? {
        if (text.isNullOrBlank()) return null

        return text
            .replace(Regex("\\s*\\.\\.\\.$"), "") // Remove trailing ...
            .replace(Regex("\\s*Lihat selengkapnya.*", RegexOption.IGNORE_CASE), "") // Remove "Lihat selengkapnya"
            .replace(Regex("\\s*Read more.*", RegexOption.IGNORE_CASE), "") // Remove "Read more"
            .replace(Regex("\\s*View more.*", RegexOption.IGNORE_CASE), "") // Remove "View more"
            .replace(Regex("\\s*Continue reading.*", RegexOption.IGNORE_CASE), "") // Remove "Continue reading"
            .trim()
    }
}

// Backward compatibility functions
fun translateToIndonesian(text: String?): String? = Translator.translateToIndonesian(text)
fun cleanDescription(text: String?): String? = Translator.cleanDescription(text)

// ============================================
// REGION: ELEMENT EXTENSIONS (401-450)
// ============================================

/**
 * Extension function object untuk Element operations
 */
internal object ElementUtils {
    /**
     * Extension function to get image URL from Element
     * Handles various image attribute patterns used by anime streaming sites
     * Fallback chain: src → data-src → data-original → data-srcset → etc
     */
    fun getImageAttr(element: Element?): String? {
        return element?.attr("src")
            ?: element?.attr("data-src")
            ?: element?.attr("data-original")
            ?: element?.attr("data-srcset")?.split(" ")?.firstOrNull()
            ?: element?.attr("data-lazy-src")
            ?: element?.attr("data-permalink")
            ?: element?.attr("data-img")
            ?: element?.attr("data-image")
            ?: element?.attr("data-url")
            ?: element?.attr("href")
            ?: element?.text()
    }
}

// Extension function syntax untuk backward compatibility
fun Element.getImageAttr(): String? = ElementUtils.getImageAttr(this)

// ============================================
// REGION: PERFORMANCE METRICS (451-500)
// ============================================

/**
 * Performance metrics tracker untuk monitoring
 * Thread-safe dengan ConcurrentHashMap
 */
internal object PerformanceMetrics {
    private val metrics = ConcurrentHashMap<String, Long>()
    private val enabled = DEBUG_MODE

    /**
     * Start timer untuk operation tertentu
     */
    fun startTimer(name: String) {
        if (enabled) {
            metrics[name] = System.currentTimeMillis()
        }
    }

    /**
     * End timer dan log duration
     * @return Duration dalam milliseconds
     */
    fun endTimer(name: String): Long {
        if (!enabled) return 0

        val start = metrics[name] ?: return 0
        val duration = System.currentTimeMillis() - start
        Log.d("Performance", "$name: ${duration}ms")
        return duration
    }

    /**
     * Clear semua metrics
     */
    fun clear() {
        metrics.clear()
    }
}
