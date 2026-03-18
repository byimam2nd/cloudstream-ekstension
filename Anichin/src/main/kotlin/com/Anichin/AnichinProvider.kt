package com.Anichin

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class AnichinProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Anichin())
        
        // DYNAMIC REGISTER: Auto-register ALL extractors
        // Tidak perlu hardcode satu-satu!
        AllExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
