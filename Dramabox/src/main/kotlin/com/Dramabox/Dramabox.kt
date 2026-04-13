// ========================================
// DRAMABOX PROVIDER
// ========================================
// Site: https://www.dramabox.com/in
// Type: Asian Drama (Short-form)
// Language: Indonesian (id)
// Standard: cloudstream-ekstension
// Reference: ExtCloud/Dramabox
//
// API-based (no HTML scraping):
// - API: https://db.hafizhibnusyam.my.id (obfuscated)
// - Video links directly from API (multiple qualities)
// ========================================

package com.Dramabox

// ============================================
// GROUP 1: Generated Sync Imports
// ============================================
import com.Dramabox.generated_sync.*

// ============================================
// GROUP 2: CloudStream Library
// ============================================
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

// ============================================
// GROUP 3: Java Standard Library
// ============================================
import java.net.URLEncoder

class Dramabox : MainAPI() {
    override var mainUrl = "https://www.dramabox.com/in"
    private val apiUrl = "https://db.hafizhibnusyam.my.id"
    override var name = "DramaBox👌"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "/api/dramas/indo" to "Drama Dub Indo",
        "/api/dramas/trending" to "Trending",
        "/api/dramas/must-sees" to "Must Sees",
        "/api/dramas/hidden-gems" to "Hidden Gems",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val safePage = if (page < 1) 1 else page
        val response = fetchDramaList(request.data, safePage)
        val items = response?.data.orEmpty()
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        val hasMore = response?.meta?.pagination?.hasMore ?: items.isNotEmpty()
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false
            ),
            hasNext = hasMore
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val keyword = query.trim()
        if (keyword.isBlank()) return emptyList()

        val url = "$apiUrl/api/search?keyword=${encodeQuery(keyword)}&page=1&size=50"
        val response = tryParseJson<DramaListResponse>(app.get(url).text)
        return response?.data.orEmpty()
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val dramaId = extractDramaId(url)
        if (dramaId.isBlank()) throw ErrorLoadingException("ID tidak ditemukan")

        val localTitle = getQueryParam(url, "title")
        val localPoster = getQueryParam(url, "poster")
        val localIntro = getQueryParam(url, "intro")
        val localTags = getQueryParam(url, "tags")
            ?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
        val localEpisodeCount = getQueryParam(url, "ep")?.toIntOrNull()

        val needDetail = localEpisodeCount == null || localTitle.isNullOrBlank() || localPoster.isNullOrBlank() || localIntro.isNullOrBlank()
        val detail = if (needDetail) fetchDramaDetail(dramaId) else null
        val episodeCount = localEpisodeCount ?: detail?.data?.episodeCount ?: inferEpisodeCount(dramaId)
        if (episodeCount <= 0) throw ErrorLoadingException("Episode tidak ditemukan")

        val rawTitle = localTitle?.takeIf { it.isNotBlank() }
            ?: detail?.data?.title?.takeIf { it.isNotBlank() }
            ?: "DramaBox"
        val title = cleanTitle(rawTitle)
        val poster = cleanPosterUrl(localPoster) ?: cleanPosterUrl(detail?.data?.coverImage)
        val description = cleanText(localIntro) ?: cleanText(detail?.data?.introduction)

        val episodes = (1..episodeCount).map { episodeNo ->
            newEpisode(LoadData(bookId = dramaId, episodeNo = episodeNo).toJson()) {
                this.name = "Episode $episodeNo"
                this.episode = episodeNo
                this.posterUrl = poster
            }
        }
        val safeUrl = buildDramaUrl(dramaId)

        return newTvSeriesLoadResponse(title, safeUrl, TvType.AsianDrama, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.tags = localTags ?: detail?.data?.tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parsed = parseJson<LoadData>(data)
        val dramaId = parsed.bookId ?: return false
        val episodeNo = parsed.episodeNo ?: return false

        val chapter = fetchChapterForEpisode(dramaId, episodeNo) ?: return false
        val streams = chapter.streamUrl
            .orEmpty()
            .mapNotNull { stream ->
                val streamUrl = stream.url?.trim().orEmpty()
                if (streamUrl.isBlank()) return@mapNotNull null
                stream.copy(url = streamUrl)
            }
            .distinctBy { it.url }
            .sortedByDescending { it.quality ?: 0 }

        if (streams.isEmpty()) return false

        streams.forEach { stream ->
            val qualityValue = stream.quality ?: Qualities.Unknown.value
            val qualityLabel = stream.quality?.let { "${it}p" } ?: "Auto"

            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "DramaBox $qualityLabel",
                    url = stream.url!!,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.quality = qualityValue
                    this.referer = "$mainUrl/"
                }
            )
        }

        return true
    }

    private suspend fun fetchDramaList(path: String, page: Int): DramaListResponse? {
        val prefix = if (path.startsWith("http", true)) path else "$apiUrl$path"
        val join = if (prefix.contains("?")) "&" else "?"
        val url = "$prefix${join}page=$page"
        val body = runCatching { app.get(url).text }.getOrNull() ?: return null
        return tryParseJson<DramaListResponse>(body)
    }

    private suspend fun fetchDramaDetail(dramaId: String): DramaDetailResponse? {
        val url = "$apiUrl/api/dramas/$dramaId"
        val body = runCatching { app.get(url).text }.getOrNull() ?: return null
        return tryParseJson<DramaDetailResponse>(body)
    }

    private suspend fun fetchChapterForEpisode(dramaId: String, episodeNo: Int): ChapterContent? {
        val url = "$apiUrl/api/chapters/video?book_id=$dramaId&episode=$episodeNo"
        val body = runCatching { app.post(url).text }.getOrNull() ?: return null
        val response = tryParseJson<ChapterResponse>(body) ?: return null
        return (response.data.orEmpty() + response.extras.orEmpty())
            .firstOrNull { it.chapterIndex?.toIntOrNull() == episodeNo }
    }

    private suspend fun inferEpisodeCount(dramaId: String): Int {
        val url = "$apiUrl/api/chapters/video?book_id=$dramaId&episode=1"
        val body = runCatching { app.post(url).text }.getOrNull() ?: return 0
        val response = tryParseJson<ChapterResponse>(body) ?: return 0
        return (response.data.orEmpty() + response.extras.orEmpty())
            .mapNotNull { it.chapterIndex?.toIntOrNull() }
            .maxOrNull() ?: 0
    }

    private fun DramaItem.toSearchResult(): SearchResponse? {
        val id = getPreferredDramaId(this)
        val rawTitle = title?.trim().orEmpty()
        if (id.isBlank() || rawTitle.isBlank()) return null

        val cleanTitle = cleanTitle(rawTitle)
        val cleanPoster = cleanPosterUrl(coverImage)

        return newTvSeriesSearchResponse(cleanTitle, buildDramaUrl(dramaId = id, title = cleanTitle, coverImage = coverImage, introduction = introduction, tags = tags, episodeCount = episodeCount), TvType.AsianDrama) {
            this.posterUrl = cleanPoster
        }
    }

    private fun cleanPosterUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        // Remove query parameters that may break image loading
        return url.split("?", "@").firstOrNull()
            ?.takeIf { it.startsWith("http") }
    }

    private fun cleanTitle(raw: String): String {
        return raw
            .replace(Regex("\\(Sulih Suara\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Dub Indo\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Indonesian Sub\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Sub Indo\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Bahasa Indonesia\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .trim('(', ')', '-', '_')
            .trim()
    }

    private fun cleanText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return text
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun getPreferredDramaId(item: DramaItem): String {
        val fromCover = extractIdFromCover(item.coverImage)
        if (!fromCover.isNullOrBlank()) return fromCover
        return item.id?.trim().orEmpty()
    }

    private fun extractIdFromCover(coverUrl: String?): String? {
        if (coverUrl.isNullOrBlank()) return null
        val pattern = Regex("/(\\d+)/\\d+\\.jpg")
        return pattern.find(coverUrl)?.groupValues?.getOrNull(1)
    }

    private fun encodeQuery(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun extractDramaId(url: String): String {
        return getQueryParam(url, "id")
            ?: url.substringAfterLast("/").substringBefore("?").substringAfter("_").trim()
    }

    private fun getQueryParam(url: String, key: String): String? {
        return url.toRegex().find(url)?.groupValues?.getOrNull(1)
            ?: runCatching {
                val uri = java.net.URI(url)
                uri.rawQuery?.split("&")?.find { it.startsWith("$key=") }?.substringAfter("=")
            }.getOrNull()
    }

    private fun buildDramaUrl(
        dramaId: String,
        title: String? = null,
        coverImage: String? = null,
        introduction: String? = null,
        tags: List<String>? = null,
        episodeCount: Int? = null,
    ): String {
        val params = mutableListOf<String>()
        title?.let { params += "title=${encodeQuery(it)}" }
        coverImage?.let { params += "poster=${encodeQuery(it)}" }
        introduction?.let { params += "intro=${encodeQuery(it)}" }
        tags?.joinToString("|")?.let { params += "tags=${encodeQuery(it)}" }
        episodeCount?.let { params += "ep=$it" }
        return "$mainUrl/drama/_${dramaId}${if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""}"
    }



    // ========================================
    // DATA CLASSES (JSON responses)
    // ========================================

    data class DramaListResponse(
        @JsonProperty("data") val data: List<DramaItem>? = null,
        @JsonProperty("meta") val meta: ResponseMeta? = null,
        @JsonProperty("success") val success: Boolean? = null,
        @JsonProperty("message") val message: String? = null,
    )

    data class DramaDetailResponse(
        @JsonProperty("data") val data: DramaItem? = null,
        @JsonProperty("meta") val meta: ResponseMeta? = null,
        @JsonProperty("success") val success: Boolean? = null,
        @JsonProperty("message") val message: String? = null,
    )

    data class DramaItem(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("cover_image") val coverImage: String? = null,
        @JsonProperty("introduction") val introduction: String? = null,
        @JsonProperty("tags") val tags: List<String>? = null,
        @JsonProperty("episode_count") val episodeCount: Int? = null,
    )

    data class ResponseMeta(
        @JsonProperty("pagination") val pagination: Pagination? = null,
    )

    data class Pagination(
        @JsonProperty("page") val page: Int? = null,
        @JsonProperty("size") val size: Int? = null,
        @JsonProperty("total") val total: Int? = null,
        @JsonProperty("has_more") val hasMore: Boolean? = null,
    )

    data class ChapterResponse(
        @JsonProperty("data") val data: List<ChapterContent>? = null,
        @JsonProperty("extras") val extras: List<ChapterContent>? = null,
        @JsonProperty("success") val success: Boolean? = null,
        @JsonProperty("message") val message: String? = null,
    )

    data class ChapterContent(
        @JsonProperty("chapter_index") val chapterIndex: String? = null,
        @JsonProperty("stream_url") val streamUrl: List<StreamItem>? = null,
    )

    data class StreamItem(
        @JsonProperty("quality") val quality: Int? = null,
        @JsonProperty("url") val url: String? = null,
    )

    data class LoadData(
        @JsonProperty("bookId") val bookId: String? = null,
        @JsonProperty("episodeNo") val episodeNo: Int? = null,
    )
}
