package com.Anichin

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.Anichin.generated_sync.SyncExtractors

@CloudstreamPlugin
class AnichinPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Anichin())

        // DYNAMIC REGISTER: Auto-register ALL extractors
        // Tidak perlu hardcode satu-satu!
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
