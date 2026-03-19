package com.Pencurimovie

import com.lagradost.api.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

internal const val MIN_REQUEST_DELAY = 100L
internal const val MAX_REQUEST_DELAY = 500L

private val rateLimitMutex = Mutex()
private var lastRequestTime = 0L

internal suspend fun rateLimitDelay() = rateLimitMutex.withLock {
    val elapsed = System.currentTimeMillis() - lastRequestTime
    if (elapsed < MIN_REQUEST_DELAY) {
        delay(MIN_REQUEST_DELAY - elapsed + Random.nextLong(0, MAX_REQUEST_DELAY - MIN_REQUEST_DELAY))
    }
    lastRequestTime = System.currentTimeMillis()
}

private val USER_AGENTS = listOf(
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)

internal fun getRandomUserAgent(): String = USER_AGENTS.random()

internal suspend fun <T> executeWithRetry(maxRetries: Int = 3, block: suspend () -> T): T {
    var lastException: Exception? = null
    repeat(maxRetries) { attempt ->
        try { return block() }
        catch (e: Exception) { lastException = e; if (attempt < maxRetries - 1) delay(1000) }
    }
    throw lastException ?: Exception("Unknown error")
}

// ============================================
// AUTO-TRANSLATE UTILITY
// ============================================

private val translationMap = mapOf(
    "watch" to "nonton", "streaming" to "streaming", "download" to "unduh",
    "free" to "gratis", "full" to "lengkap", "movie" to "film", "series" to "series",
    "story" to "cerita", "about" to "tentang", "with" to "dengan", "and" to "dan",
    "action" to "aksi", "adventure" to "petualangan", "drama" to "drama",
    "thriller" to "thriller", "comedy" to "komedi", "romance" to "romantis",
    "exciting" to "menegangkan", "best" to "terbaik", "new" to "baru",
    "latest" to "terbaru", "follows" to "mengikuti", "life" to "kehidupan"
)

private fun isIndonesian(text: String): Boolean {
    val indonesianWords = listOf("dengan", "dan", "yang", "untuk", "nonton", "film", "cerita")
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
