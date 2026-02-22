package com.Anichin

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

// Add custom extractors here if Anichin uses specific video hosts
// For now, using generic loadExtractor which handles most common hosts

class AnichinExtractor : ExtractorApi() {
    override val name = "Anichin"
    override val mainUrl = "https://anichin.cafe"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Generic extractor - loadExtractor will handle most video hosts
        loadExtractor(url, referer, subtitleCallback, callback)
    }
}
