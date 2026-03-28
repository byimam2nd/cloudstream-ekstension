package com.Pencurimovie

import com.Pencurimovie.generated_sync.CacheManager
import com.Pencurimovie.generated_sync.EpisodePreFetcher
import com.Pencurimovie.generated_sync.CircuitBreaker
import com.Pencurimovie.generated_sync.CircuitBreakerRegistry
import com.Pencurimovie.generated_sync.SmartCacheMonitor
import com.Pencurimovie.generated_sync.HttpClientFactory
import com.Pencurimovie.generated_sync.CompiledRegexPatterns
import com.Pencurimovie.generated_sync.CircuitBreaker
import com.Pencurimovie.generated_sync.CircuitBreakerRegistry
import com.Pencurimovie.generated_sync.loadExtractorWithFallback
import com.Pencurimovie.generated_sync.rateLimitDelay
import com.Pencurimovie.generated_sync.getRandomUserAgent
import com.Pencurimovie.generated_sync.executeWithRetry
import com.Pencurimovie.generated_sync.logError
import com.Pencurimovie.generated_sync.logDebug

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import java.util.concurrent.ConcurrentHashMap
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec


class Pencurimovie : MainAPI() {
    override var mainUrl = "https://ww73.pencurimovie.bond"
    override var name = "Pencurimovie"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.Cartoon)
    
    // =========================
    // CONFIG (PRODUCTION SETTINGS)
    // =========================
    companion object {
        private const val MAX_LINKS = 15
        private const val MAX_FOUND = 8
        private const val MAX_DEPTH = 2
    }
    
    // Dynamic domain whitelist (anti-noise)
    private val allowedDomains = listOf(
        "voe", "do7go", "dhcplay", "listeamed",
        "hglink", "dsvplay", "streamwish", "dood",
        "filemoon", "mixdrop", "vidhide"
    )
    
    // Dynamic domain learning (auto-detect new hosts) - THREAD-SAFE
    private val dynamicDomains = ConcurrentHashMap.newKeySet<String>()
    
    private fun learnDomain(url: String) {
        try {
            val host = URI(url).host ?: return
            
            // Filter basic (anti-spam) - MORE STRICT
            if (host.contains(".") &&
                !host.contains("google") &&
                !host.contains("facebook") &&
                !host.contains("doubleclick") &&
                !host.contains("cloudflare") &&
                !host.contains("analytics")
            ) {
                dynamicDomains.add(host)
            }
        } catch (_: Exception) {}
    }
    
    private fun isValidVideoHost(url: String): Boolean {
        val host = try { URI(url).host } catch (e: Exception) { return false }
        
        return allowedDomains.any { host.contains(it) } ||
               dynamicDomains.any { host.contains(it) }
    }
    
    private fun normalizeUrl(url: String): String {
        // Don't remove query params (needed for tokens!)
        return url.substringBefore("#")
    }
    
    // =========================
    // CACHING LAYER (suspend-aware, THREAD-SAFE)
    // =========================
    data class CacheEntry(
        val data: List<String>,
        val timestamp: Long
    )
    
    // THREAD-SAFE cache
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    
    private suspend fun getCachedOrFetch(
        url: String,
        ttl: Long,
        referer: String?
    ): List<String> {
        val now = System.currentTimeMillis()
        val cached = cache[url]
        
        // Return cached if not expired
        if (cached != null && now - cached.timestamp < ttl) {
            return cached.data
        }
        
        // Fetch fresh data
        val fresh = deepResolve(url, referer)
        
        // Cache the result
        cache[url] = CacheEntry(fresh, now)
        
        return fresh
    }
    
    // =========================
    // AES DECRYPTION (for listeamed etc)
    // =========================
    private fun decryptAES(
        encrypted: String,
        key: String,
        iv: String
    ): String? {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(key.toByteArray(), "AES")
            val ivSpec = IvParameterSpec(iv.toByteArray())
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            
            // Use Java Base64 decoder (cross-platform)
            val decoded = java.util.Base64.getDecoder().decode(encrypted)
            val decrypted = cipher.doFinal(decoded)
            
            String(decrypted)
        } catch (e: Exception) {
            Log.e("Pencurimovie", "AES decrypt error: ${e.message}")
            null
        }
    }
    
    private fun isVideoUrl(url: String): Boolean {
        // More precise - only video extensions
        return url.contains(".m3u8") ||
               url.contains(".mp4") ||
               url.contains(".mkv") ||
               url.contains(".webm")
    }


    override val mainPage = mainPageOf(
        "movies" to "Latest Movies",
        "series" to "TV Series",
        "most-rating" to "Most Rating Movies",
        "top-imdb" to "Top IMDB Movies",
        "country/malaysia" to "Malaysia Movies",
        "country/indonesia" to "Indonesia Movies",
        "country/india" to "India Movies",
        "country/japan" to "Japan Movies",
        "country/thailand" to "Thailand Movies",
        "country/china" to "China Movies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val cacheKey = "${request.data}${page}"

        // Check cache with fingerprint validation
        val cached = mainPageCache.get(cacheKey)
        val storedFingerprint = cacheFingerprints[cacheKey]
        
        if (cached != null) {
            if (storedFingerprint != null) {
                val validity = monitor.checkCacheValidity(mainUrl, storedFingerprint)
                when (validity) {
                    SmartCacheMonitor.CacheValidationResult.CACHE_VALID -> {
                        logDebug("Pencurimovie", "Cache HIT (validated) for $cacheKey")
                        return cached
                    }
                    SmartCacheMonitor.CacheValidationResult.CACHE_INVALID -> {
                        logDebug("Pencurimovie", "Cache INVALID - refetching for $cacheKey")
                        cacheFingerprints.remove(cacheKey)
                    }
                    else -> {
                        logDebug("Pencurimovie", "Cache validation failed, using cached for $cacheKey")
                        return cached
                    }
                }
            } else {
                logDebug("Pencurimovie", "Cache HIT (no fingerprint) for $cacheKey")
                return cached
            }
        }

        logDebug("Pencurimovie", "Cache MISS for $cacheKey")

        val document = executeWithRetry {
            rateLimitDelay(moduleName = "Pencurimovie")
            app.get(
                "$mainUrl/${request.data}/page/$page",
                timeout = 5000L,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).document
        }
        val home = document.select("div.ml-item").mapNotNull { it.toSearchResult() }

        val response = newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )

        // Generate and store fingerprint
        val fingerprint = monitor.generateFingerprint(mainUrl)
        if (fingerprint != null) {
            cacheFingerprints[cacheKey] = fingerprint
        }
        
        // Cache the result
        mainPageCache.put(cacheKey, response)

        return response
    }

    private fun Element.toSearchResult(): SearchResponse {
        // FIXED: Fallback strategy untuk title (2-layer)
        val title = this.select("a").attr("oldtitle").substringBefore("(")
            .ifEmpty { this.select("a").attr("title").substringBefore("(") }
        
        val href = fixUrl(this.select("a").attr("href"))
        
        // FIXED: Fallback strategy untuk poster (3-layer)
        val posterUrl = fixUrlNull(
            this.select("a img").attr("data-original")
                .ifEmpty { this.select("a img").attr("data-src") }
                .ifEmpty { this.select("a img").attr("src") }
        )
        
        val quality = getQualityFromString(this.select("span.mli-quality").text())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = quality
        }
    }


    // Standard timeout (10 detik)
    private val requestTimeout = 10000L

    override suspend fun search(query: String): List<SearchResponse> {
        val document = executeWithRetry {
            rateLimitDelay(moduleName = "Pencurimovie")
            app.get(
                "${mainUrl}?s=$query",
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).document
        }
        val results = document.select("div.ml-item").mapNotNull { it.toSearchResult() }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = executeWithRetry {
            rateLimitDelay(moduleName = "Pencurimovie")
            app.get(
                url,
                timeout = requestTimeout,
                headers = mapOf("User-Agent" to getRandomUserAgent())
            ).document
        }
        
        // FIXED: Fallback strategy untuk title (3-layer)
        val title = document.selectFirst("div.mvic-desc h3")?.text()?.trim()
            ?.substringBefore("(")
            ?: document.selectFirst("h1.title")?.text()?.trim()?.substringBefore("(")
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: ""
        
        // FIXED: Fallback strategy untuk poster (4-layer)
        val poster = document.select("meta[property=og:image]").attr("content")
            .ifEmpty { document.selectFirst("div.mvic-thumb img")?.attr("src").orEmpty() }
            .ifEmpty { document.selectFirst("img[data-original]")?.attr("data-original").orEmpty() }
            .ifEmpty { document.selectFirst("img[data-src]")?.attr("data-src").orEmpty() }
        
        // FIXED: Fallback strategy untuk description (3-layer)
        val description = document.selectFirst("div.desc p.f-desc")?.text()?.trim()
            ?: document.selectFirst("div.description")?.text()?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content").orEmpty()
        
        val tvtag = if (url.contains("series")) TvType.TvSeries else TvType.Movie
        val trailer = document.select("meta[itemprop=embedUrl]").attr("content") ?: ""
        val genre = document.select("div.mvic-info p:contains(Genre)").select("a").map { it.text() }
        val rating = document.selectFirst("span.imdb-r[itemprop=ratingValue]")
            ?.text()
            ?.toDoubleOrNull()
        val duration = document.selectFirst("span[itemprop=duration]")
            ?.text()
            ?.replace(Regex("\\D"), "")
            ?.toIntOrNull()

        val actors =
            document.select("div.mvic-info p:contains(Actors)").select("a").map { it.text() }
        val year =
            document.select("div.mvic-info p:contains(Release)").select("a").text().toIntOrNull()
        val recommendation=document.select("div.ml-item").mapNotNull {
            it.toSearchResult()
        }
        return if (tvtag == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            document.select("div.tvseason").amap { info ->
                val season = info.select("strong").text().substringAfter("Season").trim().toIntOrNull()
                info.select("div.les-content a").forEach { it ->
                    Log.d("Phis","$it")
                    val name = it.select("a").text().substringAfter("-").trim()
                    val href = it.select("a").attr("href") ?: ""
                    val Rawepisode = it.select("a").text().substringAfter("Episode")
                            .substringBefore("-")
                            .trim().toIntOrNull()
                    episodes.add(
                        newEpisode(href)
                        {
                            this.episode=Rawepisode
                            this.name=name
                            this.season=season
                        }
                    )
                }
            }

            // 🎯 PRE-FETCH: Start fetching links in background for first 10 episodes
            EpisodePreFetcher.preFetchEpisodes(episodes, mainUrl)


            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genre
                this.year = year
                addTrailer(trailer)
                addActors(actors)
                this.recommendations=recommendation
                this.duration = duration ?: 0
                if (rating != null) addScore(rating.toString(), 10)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genre
                this.year = year
                addTrailer(trailer)
                addActors(actors)
                this.recommendations=recommendation
                this.duration = duration ?: 0
                if (rating != null) addScore(rating.toString(), 10)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 🎯 CHECK CACHE FIRST (from pre-fetch)
        if (EpisodePreFetcher.loadCached(data, callback, subtitleCallback)) {
            return true
        }

        // No cache → extract normally
        try {
            val document = executeWithRetry {
                rateLimitDelay(moduleName = "Pencurimovie")
                app.get(
                    data,
                    timeout = requestTimeout,
                    headers = mapOf("User-Agent" to getRandomUserAgent())
                ).document
            }
            val links = mutableSetOf<String>()
            
            // =========================
            // STEP 1: Collect links with DOMAIN FILTER (anti-noise)
            // =========================
            // iframe with data-src or src
            document.select("iframe").forEach {
                val src = it.attr("data-src").ifEmpty { it.attr("src") }
                if (src.isNotEmpty() && src.startsWith("http") && isValidVideoHost(src)) {
                    links.add(normalizeUrl(fixUrl(src)))
                }
            }
            
            // All attributes that might contain video URLs
            document.select("[src], [data-src], [data-link], a[href]").forEach {
                val link = it.attr("data-src")
                    .ifEmpty { it.attr("data-link") }
                    .ifEmpty { it.attr("src") }
                    .ifEmpty { it.attr("href") }
                
                if (link.isNotEmpty() && link.startsWith("http") && isValidVideoHost(link)) {
                    links.add(normalizeUrl(link))
                }
            }
            
            // Fallback: regex for JS-embedded URLs (with domain filter)
            Regex("""https?://[^\s'"]+""")
                .findAll(document.html())
                .map { it.value }
                .filter { url ->
                    isValidVideoHost(url)
                }
                .forEach { links.add(normalizeUrl(it)) }
            
            // =========================
            // STEP 2: Priority sorting (smart ordering)
            // =========================
            val sortedLinks = links.sortedByDescending {
                when {
                    it.contains(".m3u8") -> 5
                    it.contains("embed") -> 4
                    it.contains("stream") -> 3
                    else -> 1
                }
            }.take(MAX_LINKS) // LIMIT: max 15 links
            
            // =========================
            // STEP 3: Parallel resolve (THREAD-SAFE + CACHED)
            // =========================
            val found = AtomicInteger(0)
            
            sortedLinks.amap { link ->
                if (found.get() >= MAX_FOUND) return@amap
                
                // Auto-learn domain
                learnDomain(link)
                
                try {
                    // Use cache (5 min TTL) - with proper referer
                    val resolved = getCachedOrFetch(link, 5 * 60 * 1000, link).distinct()
                    
                    resolved.forEach { realUrl ->
                        if (found.get() >= MAX_FOUND) return@forEach

                        // DIRECTLY call extractors (lebih reliable)
                        val success = extractVideo(realUrl, data, subtitleCallback, callback)

                        if (success) {
                            found.incrementAndGet()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Pencurimovie", "Error resolving: $link")
                }
            }
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    // =========================
    // DEEP RESOLVER (with depth limit)
    // =========================
    private suspend fun deepResolve(
        url: String,
        referer: String?,
        depth: Int = 0
    ): List<String> {
        // DEPTH LIMIT: prevent infinite recursion
        if (depth > MAX_DEPTH) return emptyList()
        
        val results = mutableSetOf<String>()
        
        try {
            // Build session with proper headers
            val headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to (referer ?: url),
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.9"
            )
            
            // Step 1: Initial request (get cookies) - WITH TIMEOUT
            val res = app.get(url, headers = headers, allowRedirects = true, timeout = requestTimeout)
            val text = res.text
            val finalUrl = res.url
            
            // Add final URL
            results.add(finalUrl)
            
            // Step 2: Extract m3u8 directly (FILTER video only)
            Regex("""https?://[^\s'"]+\.m3u8[^\s'"]*""")
                .findAll(text)
                .forEach { 
                    val url = it.value
                    if (isVideoUrl(url)) results.add(url)
                }
            
            // Step 3: Extract file:/src: patterns (FILTER video only)
            Regex("""file["']?\s*:\s*["']([^"']+)["']""")
                .findAll(text)
                .forEach { 
                    val url = it.groupValues[1]
                    if (isVideoUrl(url)) results.add(url)
                }
            
            Regex("""src["']?\s*:\s*["']([^"']+)["']""")
                .findAll(text)
                .forEach { 
                    val url = it.groupValues[1]
                    if (isVideoUrl(url)) results.add(url)
                }
            
            // Step 4: Extract iframes (nested) - with depth limit
            Regex("""<iframe[^>]*src=["']([^"']+)["']""")
                .findAll(text)
                .forEach { 
                    val iframeUrl = fixUrl(it.groupValues[1])
                    if (iframeUrl.startsWith("http") && isValidVideoHost(iframeUrl)) {
                        // RECURSIVE with depth+1
                        val nested = deepResolve(iframeUrl, url, depth + 1)
                        results.addAll(nested)
                    }
                }
            
            // Step 5: Detect API endpoints (FILTER video only)
            val apiMatch = Regex("""fetch\(["']([^"']+)["']""").find(text)
            if (apiMatch != null) {
                val apiUrl = fixUrl(apiMatch.groupValues[1])
                try {
                    val json = app.get(apiUrl, headers = headers).text
                    Regex("""https?://[^\s'"]+\.m3u8[^\s'"]*""")
                        .find(json)?.value?.let { 
                            if (isVideoUrl(it)) results.add(it)
                        }
                } catch (e: Exception) {
                    // Ignore API errors
                }
            }
            
            // Step 6: Unpack JavaScript (if packed) (FILTER video only)
            val unpacked = getAndUnpack(text)
            if (unpacked != null) {
                Regex("""https?://[^\s'"]+\.m3u8[^\s'"]*""")
                    .findAll(unpacked)
                    .forEach { 
                        val url = it.value
                        if (isVideoUrl(url)) results.add(url)
                    }
            }
            
        } catch (e: Exception) {
            Log.e("Pencurimovie", "Deep resolve error: $url")
        }
        
        // Dedup + normalize
        return results.map { normalizeUrl(it) }.distinct()
    }
    
    // =========================
    // DIRECT EXTRACTOR (call extractors explicitly)
    // =========================
    private suspend fun extractVideo(
        url: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            // DYNAMIC: Use loadExtractorWithFallback for ALL extractors
            // Auto-routes to correct extractor based on URL
            val loaded = com.Pencurimovie.generated_sync.loadExtractorWithFallback(
                url = url,
                referer = referer,
                subtitleCallback = subtitleCallback,
                callback = callback
            )
            if (!loaded) {
                Log.e("Pencurimovie", "loadExtractorWithFallback failed for $url")
            }
            return loaded
        } catch (e: Exception) {
            Log.e("Pencurimovie", "Extract error: $url - ${e.message}")
            return false
        }
    }
    
    // =========================
    // UNIVERSAL EXTRACTOR (Fallback)
    // =========================
    private suspend fun universalExtract(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val res = app.get(url, headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to (referer ?: url)
            ))
            
            val text = getAndUnpack(res.text) ?: res.text
            
            // Priority 1: M3U8 (use generateM3u8 for HLS)
            val m3u8 = Regex("""https?://[^\s'"]+\.m3u8[^\s'"]*""")
                .find(text)?.value
            
            if (m3u8 != null) {
                callback.invoke(
                    newExtractorLink(
                        "Universal",
                        "Universal",
                        m3u8,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = referer ?: url
                        this.quality = Qualities.Unknown.value
                    }
                )
                return true
            }
            
            // Priority 2: MP4 fallback
            val mp4 = Regex("""https?://[^\s'"]+\.mp4[^\s'"]*""")
                .find(text)?.value
            
            if (mp4 != null) {
                callback.invoke(
                    newExtractorLink(
                        "Universal",
                        "Universal",
                        mp4,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.referer = referer ?: url
                        this.quality = Qualities.Unknown.value
                    }
                )
                return true
            }
            
            false
        } catch (e: Exception) {
            Log.e("Pencurimovie", "Universal extract error: $url")
            false
        }
    }
}

// Smart Cache Monitor for fingerprint-based cache validation
class PencurimovieMonitor : SmartCacheMonitor() {
    override suspend fun fetchTitles(url: String): List<String> {
        val document = executeWithRetry { rateLimitDelay(moduleName = "Pencurimovie"); app.get(url, timeout = CHECK_TIMEOUT, headers = mapOf("User-Agent" to getRandomUserAgent())).documentLarge }
        return document.select("div.listupd article div.bsx a").mapNotNull { it.attr("title").trim() }.filter { it.isNotEmpty() }
    }
}
private val monitor = PencurimovieMonitor()
private val cacheFingerprints = ConcurrentHashMap<String, SmartCacheMonitor.CacheFingerprint>()

// Cache instances
private val searchCache = CacheManager<List<SearchResponse>>()
private val mainPageCache = CacheManager<HomePageResponse>()
