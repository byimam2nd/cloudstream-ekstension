package com.Donghuastream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.Donghuastream.generated_sync.SyncExtractors

@CloudstreamPlugin
class DonghuastreamPlugin: BasePlugin() {
    override fun load() {
        // Register both Donghuastream and SeaTV as separate providers
        registerMainAPI(Donghuastream())
        registerMainAPI(SeaTV())

        // DYNAMIC REGISTER: Auto-register ALL extractors
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
