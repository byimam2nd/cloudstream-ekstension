package com.HiAnime

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class HiAnimeProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(HiAnime())
        
        // DYNAMIC REGISTER: Auto-register ALL extractors
        // Tidak perlu hardcode satu-satu!
        AllExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
