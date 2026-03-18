package com.LayarKaca21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class LayarKaca21Provider: BasePlugin() {
    override fun load() {
        registerMainAPI(LayarKaca21())
        
        // DYNAMIC REGISTER: Auto-register ALL extractors
        // Tidak perlu hardcode satu-satu!
        AllExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
