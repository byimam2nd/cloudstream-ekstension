package com.Funmovieslix

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.Funmovieslix.generated-sync.SyncExtractors

@CloudstreamPlugin
class FunmovieslixPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Funmovieslix())

        // DYNAMIC REGISTER: Auto-register ALL extractors
        // Tidak perlu hardcode satu-satu!
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
