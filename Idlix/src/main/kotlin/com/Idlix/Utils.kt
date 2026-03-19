package com.Idlix

import com.lagradost.api.Log
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

// ============================================
// CACHING CONFIGURATION
// ============================================
internal const val SEARCH_CACHE_TTL = 30 * 60 * 1000L      // 30 menit
internal const val MAINPAGE_CACHE_TTL = 10 * 60 * 1000L    // 10 menit
internal const val MAX_CACHE_SIZE = 50                     // Max 50 entries

// Rate limiting configuration
internal const val MIN_REQUEST_DELAY = 100L

// Debug mode
private const val DEBUG_MODE = false
internal const val MAX_REQUEST_DELAY = 500L

// ============================================
// RATE LIMITING
// ============================================

private val rateLimitMutex = Mutex()
private var lastRequestTime = 0L

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

internal fun getRandomUserAgent(): String {
    return USER_AGENTS[(userAgentIndex + Random.nextInt(USER_AGENTS.size)) % USER_AGENTS.size]
}

// ============================================
// HELPER FUNCTIONS
// ============================================

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

private val translationMap = mapOf(
    "watch" to "nonton", "streaming" to "streaming", "download" to "unduh",
    "free" to "gratis", "full" to "lengkap", "episode" to "episode",
    "season" to "musim", "movie" to "film", "series" to "series",
    "story" to "cerita", "about" to "tentang", "with" to "dengan",
    "and" to "dan", "the" to "", "in" to "di", "for" to "untuk",
    "from" to "dari", "quality" to "kualitas", "subtitle" to "subtitle",
    "action" to "aksi", "adventure" to "petualangan", "drama" to "drama",
    "thriller" to "thriller", "comedy" to "komedi", "romance" to "romantis",
    "fantasy" to "fantasi", "horror" to "horor", "mystery" to "misteri",
    "exciting" to "menegangkan", "amazing" to "luar biasa", "best" to "terbaik",
    "new" to "baru", "latest" to "terbaru", "popular" to "populer",
    "follows" to "mengikuti", "journey" to "perjalanan", "life" to "kehidupan",
    "completed" to "selesai", "ongoing" to "berlangsung"
)

private fun isIndonesian(text: String): Boolean {
    val indonesianWords = listOf("dengan", "dan", "yang", "untuk", "dari", "nonton", "film", "cerita", "tentang")
    return text.lowercase().let { t -> indonesianWords.any { t.contains(it) } }
}

fun translateToIndonesian(text: String?): String? {
    if (text.isNullOrBlank()) return null
    if (isIndonesian(text)) return text
    var translated = text.lowercase()
    translationMap.forEach { (e, i) -> translated = translated.replace(e, i, ignoreCase = true) }
    return translated.replaceFirstChar { it.uppercase() }
}

fun cleanDescription(text: String?): String? {
    if (text.isNullOrBlank()) return null
    return text.replace(Regex("\\s*Lihat selengkapnya.*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*Read more.*", RegexOption.IGNORE_CASE), "").trim()
}
