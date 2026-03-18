package com.Donghuastream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class DonghuastreamProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Donghuastream())
        
        // DYNAMIC REGISTER: Auto-register ALL extractors
        // Tidak perlu hardcode satu-satu!
        AllExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
