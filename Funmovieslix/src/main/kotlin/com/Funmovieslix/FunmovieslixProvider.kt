package com.Funmovieslix

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class FunmovieslixProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Funmovieslix())
        
        // DYNAMIC REGISTER: Auto-register ALL extractors
        // Tidak perlu hardcode satu-satu!
        AllExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
