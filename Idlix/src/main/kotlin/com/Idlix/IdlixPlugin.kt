package com.Idlix

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.Idlix.Idlix
import com.Idlix.generated-sync.SyncExtractors

@CloudstreamPlugin
class IdlixPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Idlix())

        // DYNAMIC REGISTER: Auto-register ALL extractors
        // Tidak perlu hardcode satu-satu!
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
