// ========================================
// MELOLO PROVIDER
// ========================================
// Site: https://api.tmthreader.com
// Type: Asian Drama (Short-form)
// Language: Indonesian (id)
// Standard: cloudstream-ekstension
// Reference: ExtCloud/Melolo
//
// API-based (no HTML scraping):
// - Catalog API: https://melolo-api-azure.vercel.app/api/melolo/
// - Player API: https://api.tmthreader.com/novel/player/video_model/v1/
// ========================================

package com.Melolo

// ============================================
// GROUP 1: Generated Sync Imports
// ============================================
import com.Melolo.generated_sync.*

// ============================================
// GROUP 2: CloudStream Library
// ============================================
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

// ============================================
// GROUP 3: Java Standard Library
// ============================================
import java.net.URLEncoder

class Melolo : MainAPI() {
    override var mainUrl = Endpoints.apiBase
    override var name = "Melolo😶"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.AsianDrama)

    private val aid = "645713"
    private val catalogBase = Endpoints.catalogBase

    override val mainPage = mainPageOf(
        "latest" to "Terbaru",
        "trending" to "Trending",
        "q:ceo" to "CEO",
        "q:romansa" to "Romansa",
        "q:sistem" to "Sistem",
        "q:keluarga" to "Keluarga",
        "q:mafia" to "Mafia",
        "q:aksi" to "Aksi",
        "q:balas dendam" to "Balas Dendam",
        "q:pernikahan" to "Pernikahan",
        "q:drama periode" to "Drama Periode",
    )

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")
    private fun seriesUrl(bookId: String) = "$mainUrl/series/$bookId"
    private fun bookIdFromUrl(url: String): String = url.substringAfterLast("/").substringBefore("?").trim()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val isSearchCategory = request.data.startsWith("q:", ignoreCase = true)
        if (page > 1 && !isSearchCategory) {
            return newHomePageResponse(HomePageList(request.name, emptyList()), hasNext = false)
        }

        val (books, hasNext) = if (isSearchCategory) {
            val query = request.data.removePrefix("q:").trim()
            val limit = 20
            val offset = (page.coerceAtLeast(1) - 1) * limit
            fetchSearchPage(query, limit = limit, offset = offset)
        } else {
            val b = when (request.data) {
                "trending" -> fetchTrending()
                else -> fetchLatest()
            }
            b to false
        }

        val items = books.mapNotNull { b ->
            val bookId = b.book_id ?: return@mapNotNull null
            val title = b.book_name ?: return@mapNotNull null
            newTvSeriesSearchResponse(title, seriesUrl(bookId), TvType.TvSeries) {
                this.posterUrl = b.thumb_url
            }
        }

        return newHomePageResponse(HomePageList(request.name, items), hasNext = hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val books = fetchSearch(query, limit = 20, offset = 0)
        return books.mapNotNull { b ->
            val bookId = b.book_id ?: return@mapNotNull null
            val title = b.book_name ?: return@mapNotNull null
            newTvSeriesSearchResponse(title, seriesUrl(bookId), TvType.TvSeries) {
                this.posterUrl = b.thumb_url
            }
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val limit = 20
        val offset = (page.coerceAtLeast(1) - 1) * limit
        val (books, _) = fetchSearchPage(query, limit = limit, offset = offset)
        val items = books.mapNotNull { b ->
            val bookId = b.book_id ?: return@mapNotNull null
            val title = b.book_name ?: return@mapNotNull null
            newTvSeriesSearchResponse(title, seriesUrl(bookId), TvType.TvSeries) {
                this.posterUrl = b.thumb_url
            }
        }
        return items.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val bookId = bookIdFromUrl(url)
        if (bookId.isBlank()) throw ErrorLoadingException("Invalid series url")

        val detail = fetchDetail(bookId)
        val seriesId = detail.series_id_str ?: bookId
        val title = detail.series_title ?: "Melolo"

        val episodes = detail.video_list
            .filter { it.disable_play != true }
            .mapNotNull { ep ->
                val vid = ep.vid ?: return@mapNotNull null
                val idx = ep.vid_index ?: return@mapNotNull null
                newEpisode(EpisodeData(bookId = bookId, seriesId = seriesId, vid = vid, episode = idx, videoPlatform = detail.video_platform ?: 3).toJson()) {
                    this.name = "Episode $idx"
                    this.posterUrl = ep.cover
                    this.episode = idx
                }
            }
            .sortedBy { it.episode ?: Int.MAX_VALUE }

        return newTvSeriesLoadResponse(title, seriesUrl(bookId), TvType.TvSeries, episodes) {
            this.plot = detail.series_intro
            this.posterUrl = detail.series_cover
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val ep = tryParseJson<EpisodeData>(data) ?: return false

        val bodyJson = """
            {
                "video_id": "${ep.vid}",
                "biz_param": {
                    "video_id_type": 0,
                    "device_level": 1,
                    "video_platform": ${ep.videoPlatform}
                },
                "NovelCommonParam": {
                    "app_language": "id",
                    "sys_language": "id",
                    "user_language": "id",
                    "ui_language": "id",
                    "language": "id",
                    "region": "ID",
                    "current_region": "ID",
                    "app_region": "ID",
                    "sys_region": "ID",
                    "carrier_region": "ID",
                    "carrier_region_v2": "ID",
                    "fake_priority_region": "ID",
                    "time_zone": "Asia/Jakarta",
                    "mcc_mnc": "51011"
                }
            }
        """.trimIndent()

        val apiUrl = "$mainUrl/novel/player/video_model/v1/?aid=$aid"
        val respText = app.post(
            apiUrl,
            requestBody = bodyJson.toRequestBody("application/json".toMediaType()),
            headers = mapOf(
                "Content-Type" to "application/json",
                "X-Xs-From-Web" to "false",
                "User-Agent" to "okhttp/4.9.3",
                "Referer" to "$mainUrl/"
            )
        ).text

        val resp = tryParseJson<PlayerVideoModelResponse>(respText)
        val main = resp?.data?.main_url
        val backup = resp?.data?.backup_url

        if (!main.isNullOrBlank()) {
            callback(newExtractorLink(name, "Melolo", main, ExtractorLinkType.VIDEO) {
                this.quality = Qualities.Unknown.value
                this.referer = "$mainUrl/"
                this.headers = mapOf("User-Agent" to "okhttp/4.9.3")
            })
        }
        if (!backup.isNullOrBlank() && backup != main) {
            callback(newExtractorLink(name, "Melolo", backup, ExtractorLinkType.VIDEO) {
                this.quality = Qualities.Unknown.value
                this.referer = "$mainUrl/"
                this.headers = mapOf("User-Agent" to "okhttp/4.9.3")
            })
        }

        return !main.isNullOrBlank() || !backup.isNullOrBlank()
    }

    private suspend fun fetchLatest(): List<CatalogBook> {
        val text = app.get("$catalogBase/latest", timeout = 30L).text
        return tryParseJson<CatalogLatestResponse>(text)?.books.orEmpty()
            .filter { it.language.equals("id", ignoreCase = true) }
    }

    private suspend fun fetchTrending(): List<CatalogBook> {
        val text = app.get("$catalogBase/trending", timeout = 30L).text
        return tryParseJson<CatalogTrendingResponse>(text)?.books.orEmpty()
            .filter { it.language.equals("id", ignoreCase = true) }
    }

    private suspend fun fetchSearch(query: String, limit: Int, offset: Int): List<CatalogBook> {
        val url = "$catalogBase/search?query=${query.urlEncode()}&limit=$limit&offset=$offset"
        val text = app.get(url, timeout = 30L).text
        val resp = tryParseJson<CatalogSearchResponse>(text)
        return resp?.data?.search_data?.flatMap { it.books }.orEmpty()
            .filter { it.language.equals("id", ignoreCase = true) }
    }

    private suspend fun fetchSearchPage(query: String, limit: Int, offset: Int): Pair<List<CatalogBook>, Boolean> {
        val url = "$catalogBase/search?query=${query.urlEncode()}&limit=$limit&offset=$offset"
        val text = app.get(url, timeout = 30L).text
        val resp = tryParseJson<CatalogSearchResponse>(text)
        val books = resp?.data?.search_data?.flatMap { it.books }.orEmpty()
            .filter { it.language.equals("id", ignoreCase = true) }
        return books to (resp?.data?.has_more == true)
    }

    private suspend fun fetchDetail(bookId: String): CatalogVideoData {
        val text = app.get("$catalogBase/detail/$bookId", timeout = 30L).text
        val resp = tryParseJson<CatalogDetailResponse>(text)
        return resp?.data?.video_data ?: throw ErrorLoadingException("Empty detail data")
    }

    // ========================================
    // DATA CLASSES
    // ========================================

    data class CatalogLatestResponse(@JsonProperty("books") val books: List<CatalogBook> = emptyList())
    data class CatalogTrendingResponse(@JsonProperty("books") val books: List<CatalogBook> = emptyList())
    data class CatalogSearchResponse(@JsonProperty("code") val code: Int? = null, @JsonProperty("data") val data: CatalogSearchData? = null)
    data class CatalogSearchData(@JsonProperty("has_more") val has_more: Boolean? = null, @JsonProperty("next_offset") val next_offset: Int? = null, @JsonProperty("search_data") val search_data: List<CatalogSearchBlock> = emptyList())
    data class CatalogSearchBlock(@JsonProperty("books") val books: List<CatalogBook> = emptyList())
    data class CatalogBook(@JsonProperty("book_id") val book_id: String? = null, @JsonProperty("book_name") val book_name: String? = null, @JsonProperty("abstract") val abstract: String? = null, @JsonProperty("thumb_url") val thumb_url: String? = null, @JsonProperty("language") val language: String? = null)
    data class CatalogDetailResponse(@JsonProperty("data") val data: CatalogDetailData? = null)
    data class CatalogDetailData(@JsonProperty("video_data") val video_data: CatalogVideoData? = null)
    data class CatalogVideoData(@JsonProperty("series_id_str") val series_id_str: String? = null, @JsonProperty("series_title") val series_title: String? = null, @JsonProperty("series_intro") val series_intro: String? = null, @JsonProperty("series_cover") val series_cover: String? = null, @JsonProperty("episode_cnt") val episode_cnt: Int? = null, @JsonProperty("video_list") val video_list: List<CatalogEpisode> = emptyList(), @JsonProperty("video_platform") val video_platform: Int? = null)
    data class CatalogEpisode(@JsonProperty("vid") val vid: String? = null, @JsonProperty("vid_index") val vid_index: Int? = null, @JsonProperty("cover") val cover: String? = null, @JsonProperty("duration") val duration: Int? = null, @JsonProperty("disable_play") val disable_play: Boolean? = null)
    data class PlayerVideoModelResponse(@JsonProperty("BaseResp") val BaseResp: PlayerBaseResp? = null, @JsonProperty("code") val code: Int? = null, @JsonProperty("data") val data: PlayerVideoModelData? = null, @JsonProperty("message") val message: String? = null)
    data class PlayerBaseResp(@JsonProperty("StatusCode") val StatusCode: Int? = null, @JsonProperty("StatusMessage") val StatusMessage: String? = null)
    data class PlayerVideoModelData(@JsonProperty("main_url") val main_url: String? = null, @JsonProperty("backup_url") val backup_url: String? = null, @JsonProperty("expire_time") val expire_time: Long? = null)
    data class EpisodeData(@JsonProperty("bookId") val bookId: String, @JsonProperty("seriesId") val seriesId: String, @JsonProperty("vid") val vid: String, @JsonProperty("episode") val episode: Int, @JsonProperty("videoPlatform") val videoPlatform: Int = 3)

    private object Endpoints {
        val apiBase: String = "https://api.tmthreader.com"
        val catalogBase: String = "https://melolo-api-azure.vercel.app/api/melolo"
    }
}
