package com.LayarKaca21

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.LayarKaca21.LayarKaca21
import com.LayarKaca21.generated_sync.SyncExtractors

@CloudstreamPlugin
class LayarKaca21Plugin: BasePlugin() {
    override fun load() {
        registerMainAPI(LayarKaca21())

        // DYNAMIC REGISTER: Auto-register ALL extractors
        // Tidak perlu hardcode satu-satu!
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
