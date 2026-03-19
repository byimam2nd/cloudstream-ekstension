package com.layarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Shared utilities untuk provider ini
 * - Caching dengan TTL configurable
 * - Rate limiting untuk mencegah IP ban
 * - Memory management dengan max cache size
 */

// ============================================
// CACHING CONFIGURATION
// ============================================

// TTL berbeda untuk setiap tipe data
internal const val SEARCH_CACHE_TTL = 30 * 60 * 1000L      // 30 menit
internal const val MAINPAGE_CACHE_TTL = 10 * 60 * 1000L    // 10 menit
internal const val LOAD_CACHE_TTL = 5 * 60 * 1000L         // 5 menit

// Memory limits untuk mencegah OOM
internal const val MAX_CACHE_SIZE = 100
internal const val CLEANUP_THRESHOLD = 80  // Mulai cleanup saat 80% full

// Rate limiting configuration
internal const val MIN_REQUEST_DELAY = 100L  // Minimal 100ms antar request

// Debug mode
private const val DEBUG_MODE = false
internal const val MAX_REQUEST_DELAY = 500L  // Maksimal 500ms (random untuk natural behavior)

// ============================================
// RATE LIMITING
// ============================================

private val rateLimitMutex = Mutex()
private var lastRequestTime = 0L

/**
 * Delay acak untuk rate limiting (mencegah IP ban)
 * Harus dipanggil sebelum setiap HTTP request
 */
internal suspend fun rateLimitDelay() = rateLimitMutex.withLock {
    val now = System.currentTimeMillis()
    val elapsed = now - lastRequestTime

    if (elapsed < MIN_REQUEST_DELAY) {
        val delayNeeded = MIN_REQUEST_DELAY - elapsed + Random.nextLong(0, MAX_REQUEST_DELAY - MIN_REQUEST_DELAY)
        delay(delayNeeded)
    }

    lastRequestTime = System.currentTimeMillis()
}

// ============================================
// USER AGENT ROTATION
// ============================================

/**
 * List user-agent yang umum digunakan
 * Rotate untuk menghindari detection
 */
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

private val userAgentIndex = Random.nextInt(USER_AGENTS.size)

/**
 * Get random user-agent untuk request
 */
internal fun getRandomUserAgent(): String {
    return USER_AGENTS[(userAgentIndex + Random.nextInt(USER_AGENTS.size)) % USER_AGENTS.size]
}

// ============================================
// HELPER FUNCTIONS
// ============================================

/**
 * Execute dengan retry logic dan exponential backoff
 * @param maxRetries Jumlah maksimal retry (default: 3)
 * @param initialDelay Delay awal dalam ms (default: 1000)
 * @param maxDelay Delay maksimal dalam ms (default: 10000)
 * @param backoffMultiplier Multiplier untuk exponential backoff (default: 2.0)
 */
internal suspend fun <T> executeWithRetry(
    maxRetries: Int = 3,
    initialDelay: Long = 1000L,
    maxDelay: Long = 10000L,
    backoffMultiplier: Double = 2.0,
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

/**
 * Logging conditional - hanya aktif saat debug mode
 */
internal fun logDebug(tag: String, message: String) {
    if (DEBUG_MODE) {
        Log.d(tag, message)
    }
}

internal fun logError(tag: String, message: String, error: Throwable? = null) {
    Log.e(tag, message)
    error?.let { Log.e(tag, "Cause: ${it.message}") }
}

// ============================================
// AUTO-TRANSLATE UTILITY
// ============================================
// Auto-translate English descriptions to Indonesian
// Handle "Lihat selengkapnya" truncated descriptions
// ============================================

private val translationMap = mapOf(
    "watch" to "nonton", "streaming" to "streaming", "download" to "unduh",
    "free" to "gratis", "full" to "lengkap", "episode" to "episode",
    "season" to "musim", "movie" to "film", "series" to "series",
    "show" to "acara", "plot" to "alur cerita", "story" to "cerita",
    "about" to "tentang", "with" to "dengan", "and" to "dan",
    "the" to "", "in" to "di", "on" to "pada", "for" to "untuk",
    "from" to "dari", "quality" to "kualitas", "subtitle" to "subtitle",
    "indonesia" to "Indonesia", "english" to "Inggris",
    "chinese" to "China", "japanese" to "Jepang", "korean" to "Korea",
    "thriller" to "thriller", "action" to "aksi", "adventure" to "petualangan",
    "drama" to "drama", "comedy" to "komedi", "romance" to "romantis",
    "fantasy" to "fantasi", "sci-fi" to "fiksi ilmiah", "horror" to "horor",
    "mystery" to "misteri", "crime" to "kriminal", "suspense" to "tegang",
    "exciting" to "menegangkan", "amazing" to "luar biasa", "best" to "terbaik",
    "new" to "baru", "latest" to "terbaru", "popular" to "populer",
    "follows" to "mengikuti", "story of" to "cerita tentang",
    "life of" to "kehidupan", "journey of" to "perjalanan",
    "must watch" to "wajib tonton", "don't miss" to "jangan lewatkan",
    "high quality" to "kualitas tinggi", "completed" to "selesai",
    "ongoing" to "berlangsung"
)

private fun isIndonesian(text: String): Boolean {
    val indonesianWords = listOf(
        "dengan", "dan", "yang", "untuk", "dari", "pada", "ini", "itu",
        "adalah", "merupakan", "telah", "sudah", "akan", "sedang",
        "nonton", "streaming", "unduh", "lengkap", "gratis",
        "film", "series", "episode", "musim", "alur", "cerita",
        "tentang", "mengikuti", "kehidupan", "perjalanan"
    )
    return text.lowercase().let { lowerText -> indonesianWords.any { lowerText.contains(it) } }
}

fun translateToIndonesian(text: String?): String? {
    if (text.isNullOrBlank()) return null
    if (isIndonesian(text)) return text
    
    var translated = text.lowercase()
    translationMap.forEach { (eng, ind) ->
        translated = translated.replace(eng, ind, ignoreCase = true)
    }
    return translated.replaceFirstChar { it.uppercase() }
}

fun cleanDescription(text: String?): String? {
    if (text.isNullOrBlank()) return null
    return text
        .replace(Regex("\\s*\\.\\.\\.$"), "")
        .replace(Regex("\\s*Lihat selengkapnya.*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*Read more.*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*View more.*", RegexOption.IGNORE_CASE), "")
        .trim()
}
