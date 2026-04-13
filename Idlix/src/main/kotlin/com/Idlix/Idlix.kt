// ========================================
// IDLIX PROVIDER — API-Based
// ========================================
// Site: https://z1.idlixku.com (API server)
// Type: Movie/TV Series/Anime/Asian Drama
// Language: Indonesian (id)
// Standard: cloudstream-ekstension
//
// Architecture: REST API + Proof-of-Work challenge
// - Uses JSON API endpoints (not HTML scraping)
// - SHA-256 POW challenge for embed URL
// - References: Phisher/IdlixProvider
// ========================================

package com.Idlix

import com.Idlix.generated_sync.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import java.security.MessageDigest

// ========================================
// DATA CLASSES (JSON responses)
// ========================================

data class IdlixApiResponse(
    val data: List<IdlixApiItem> = emptyList(),
    val pagination: IdlixPagination? = null
)

data class IdlixApiItem(
    val id: String? = null,
    val title: String? = null,
    val slug: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val voteAverage: String? = null,
    val quality: String? = null,
    val contentType: String? = null
)

data class IdlixPagination(
    val page: Int? = null,
    val limit: Int? = null,
    val totalPages: Int? = null
)

data class IdlixDetailResponse(
    val id: String? = null,
    val title: String? = null,
    val slug: String? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val logoPath: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val voteAverage: Any? = null,
    val quality: String? = null,
    val trailerUrl: String? = null,
    val genres: List<IdlixGenre>? = null,
    val cast: List<IdlixCast>? = null,
    val seasons: List<IdlixSeason>? = null,
    val firstSeason: IdlixSeason? = null
)

data class IdlixGenre(
    val id: String? = null,
    val name: String? = null
)

data class IdlixCast(
    val id: String? = null,
    val name: String? = null,
    val character: String? = null,
    val profilePath: String? = null
)

data class IdlixSeason(
    val id: String? = null,
    val seasonNumber: Int? = null,
    val name: String? = null,
    val posterPath: String? = null,
    val episodes: List<IdlixEpisode>? = null
)

data class IdlixEpisode(
    val id: String? = null,
    val episodeNumber: Int? = null,
    val name: String? = null,
    val overview: String? = null,
    val stillPath: String? = null,
    val airDate: String? = null,
    val runtime: Int? = null,
    val voteAverage: Any? = null
)

data class IdlixSearchResponse(
    val results: List<IdlixSearchResult> = emptyList(),
    val total: Long = 0
)

data class IdlixSearchResult(
    val id: String = "",
    val contentType: String = "",
    val title: String = "",
    val posterPath: String = "",
    val slug: String = "",
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val voteAverage: Double = 0.0,
    val quality: String? = null
)

data class IdlixChallengeResponse(
    val challenge: String = "",
    val signature: String = "",
    val difficulty: Int = 0
)

data class IdlixSolveResponse(
    val embedUrl: String? = null
)

data class IdlixLoadData(
    val id: String,
    val type: String // "movie" or "episode"
)

// ========================================
// MAIN PROVIDER
// ========================================

class Idlix : MainAPI() {
    // API server (base64 encoded to prevent hotlinking)
    override var mainUrl = "https://z1.idlixku.com"
    override var name = "Idlix"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/api/movies?page=%d&limit=36&sort=createdAt" to "Movie Terbaru",
        "$mainUrl/api/series?page=%d&limit=36&sort=createdAt" to "TV Series Terbaru",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&network=prime-video" to "Amazon Prime",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&network=apple-tv-plus" to "Apple TV+",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&network=disney-plus" to "Disney+",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&network=hbo" to "HBO",
        "$mainUrl/api/browse?page=%d&limit=36&sort=latest&network=netflix" to "Netflix",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.contains("%d")) request.data.format(page) else request.data
        val res = app.get(url, timeout = 10000L).parsedSafe<IdlixApiResponse>()
            ?: return newHomePageResponse(request.name, emptyList())

        val home = res.data.mapNotNull { item ->
            val title = item.title ?: return@mapNotNull null
            val poster = item.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }

            if (item.contentType == "movie") {
                val movieUrl = "$mainUrl/api/movies/${item.slug}"
                newMovieSearchResponse(title, movieUrl, TvType.Movie) {
                    this.posterUrl = poster
                    this.year = item.releaseDate?.substringBefore("-")?.toIntOrNull()
                    this.quality = getQualityFromString(item.quality)
                    this.score = Score.from10(item.voteAverage)
                }
            } else {
                val seriesUrl = "$mainUrl/api/series/${item.slug}"
                newTvSeriesSearchResponse(title, seriesUrl, TvType.TvSeries) {
                    this.posterUrl = poster
                    this.year = item.releaseDate?.substringBefore("-")?.toIntOrNull()
                    this.score = Score.from10(item.voteAverage)
                    this.quality = getQualityFromString(item.quality)
                }
            }
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api/search?q=$query&page=1&limit=20"
        val res = app.get(url).parsedSafe<IdlixSearchResponse>() ?: return emptyList()

        return res.results.mapNotNull { item ->
            val title = item.title.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val poster = "https://image.tmdb.org/t/p/w342${item.posterPath}"
            val year = (item.releaseDate ?: item.firstAirDate)?.substringBefore("-")?.toIntOrNull()

            val link = if (item.contentType == "movie") {
                "$mainUrl/api/movies/${item.slug}"
            } else {
                "$mainUrl/api/series/${item.slug}"
            }

            if (item.contentType == "movie") {
                newMovieSearchResponse(title, link, TvType.Movie) {
                    this.posterUrl = poster
                    this.year = year
                    this.quality = getQualityFromString(item.quality)
                    this.score = Score.from10(item.voteAverage)
                }
            } else {
                newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                    this.posterUrl = poster
                    this.year = year
                    this.score = Score.from10(item.voteAverage)
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url, timeout = 10000L)
        val data = response.parsedSafe<IdlixDetailResponse>()
            ?: throw ErrorLoadingException("Invalid JSON response")

        val title = data.title ?: "Unknown"
        val poster = data.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
        val backdrop = data.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
        val year = (data.releaseDate ?: data.firstAirDate)?.substringBefore("-")?.toIntOrNull()
        val tags = data.genres?.mapNotNull { it.name } ?: emptyList()
        val actors = data.cast?.mapNotNull {
            it.name?.let { name ->
                Actor(name, it.profilePath?.let { p -> "https://image.tmdb.org/t/p/w185$p" })
            }
        } ?: emptyList()

        if (data.seasons != null) {
            // TV Series
            val episodes = mutableListOf<Episode>()

            // First season (pre-loaded)
            data.firstSeason?.episodes?.forEach { ep ->
                episodes.add(newEpisode(IdlixLoadData(id = ep.id ?: return@forEach, type = "episode").toJson()) {
                    this.name = ep.name
                    this.season = data.firstSeason.seasonNumber
                    this.episode = ep.episodeNumber
                    this.description = ep.overview
                    this.runTime = ep.runtime
                    this.score = Score.from10(ep.voteAverage?.toString())
                    this.posterUrl = ep.stillPath?.let { "https://image.tmdb.org/t/p/w300$it" }
                })
            }

            // Other seasons (load on demand)
            data.seasons.forEach { season ->
                val seasonNum = season.seasonNumber ?: return@forEach
                if (seasonNum == data.firstSeason?.seasonNumber) return@forEach

                val seasonUrl = "$mainUrl/api/series/${data.slug}/season/$seasonNum"
                val seasonData = try {
                    app.get(seasonUrl).parsedSafe<IdlixSeasonWrapper>()?.season
                } catch (_: Exception) { null }

                seasonData?.episodes?.forEach { ep ->
                    episodes.add(newEpisode(IdlixLoadData(id = ep.id ?: return@forEach, type = "episode").toJson()) {
                        this.name = ep.name
                        this.season = seasonNum
                        this.episode = ep.episodeNumber
                        this.description = ep.overview
                        this.runTime = ep.runtime
                        this.score = Score.from10(ep.voteAverage?.toString())
                        this.posterUrl = ep.stillPath?.let { "https://image.tmdb.org/t/p/w300$it" }
                    })
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.logoUrl = data.logoPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                this.year = year
                this.plot = data.overview
                this.tags = tags
                this.score = Score.from10(data.voteAverage?.toString())
                addActors(actors)
                addTrailer(data.trailerUrl)
                addTMDbId(data.tmdbId)
                addImdbId(data.imdbId)
            }
        } else {
            // Movie
            return newMovieLoadResponse(title, url, TvType.Movie,
                IdlixLoadData(id = data.id ?: "", type = "movie").toJson()) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.logoUrl = data.logoPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                this.year = year
                this.plot = data.overview
                this.tags = tags
                this.score = Score.from10(data.voteAverage?.toString())
                addActors(actors)
                addTrailer(data.trailerUrl)
                addTMDbId(data.tmdbId)
                addImdbId(data.imdbId)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parsed = try {
            parseJson<IdlixLoadData>(data)
        } catch (_: Exception) { null } ?: return false

        val contentId = parsed.id
        val contentType = parsed.type

        // Step 1: Get clearance token from ad frame
        val ts = System.currentTimeMillis()
        val aclrRes = try {
            app.get("$mainUrl/pagead/ad_frame.js?_=$ts").text
        } catch (_: Exception) { null }
        val aclr = aclrRes?.let {
            Regex("""__aclr\s*=\s*"([a-f0-9]+)""").find(it)?.groupValues?.getOrNull(1)
        }

        // Step 2: Get challenge
        val challengeJson = """
            {"contentType":"$contentType","contentId":"$contentId"${aclr?.let { ",\"clearance\":\"$it\"" } ?: ""}}
        """.trimIndent()

        val headers = mapOf(
            "accept" to "*/*",
            "content-type" to "application/json",
            "origin" to mainUrl,
            "referer" to mainUrl,
            "user-agent" to USER_AGENT
        )

        val challengeRes = try {
            app.post("$mainUrl/api/watch/challenge",
                data = mapOf(
                    "contentType" to contentType,
                    "contentId" to contentId,
                    "clearance" to (aclr ?: "")
                ),
                headers = headers
            ).parsedSafe<IdlixChallengeResponse>()
        } catch (e: Exception) {
            logError("Idlix", "Challenge request failed: ${e.message}")
            null
        } ?: return false

        // Step 3: Solve proof-of-work
        val nonce = solvePow(challengeRes.challenge, challengeRes.difficulty)
        logDebug("Idlix", "POW solved: nonce=$nonce, difficulty=${challengeRes.difficulty}")

        // Step 4: Submit solution
        val solveRes = try {
            app.post("$mainUrl/api/watch/solve",
                data = mapOf(
                    "challenge" to challengeRes.challenge,
                    "signature" to challengeRes.signature,
                    "nonce" to nonce.toString()
                ),
                headers = headers
            ).parsedSafe<IdlixSolveResponse>()
        } catch (e: Exception) {
            logError("Idlix", "Solve request failed: ${e.message}")
            null
        } ?: return false

        // Step 5: Get embed URL
        val embedUrl = solveRes.embedUrl ?: return false
        val finalUrl = app.get("$mainUrl$embedUrl").document.selectFirst("iframe")?.attr("src")
            ?: return false

        logDebug("Idlix", "Found embed URL: $finalUrl")

        // Step 6: Load extractors
        return loadExtractorWithFallback(finalUrl, mainUrl, subtitleCallback, callback)
    }

    /**
     * Solve SHA-256 proof-of-work challenge.
     * Finds nonce such that sha256(challenge + nonce) starts with difficulty zeros.
     */
    private fun solvePow(challenge: String, difficulty: Int): Int {
        val target = "0".repeat(difficulty)
        var nonce = 0
        while (true) {
            val hash = sha256(challenge + nonce)
            if (hash.startsWith(target)) return nonce
            nonce++
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

// Wrapper for season API response
data class IdlixSeasonWrapper(val season: IdlixSeason? = null)
