package com.Idlix

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.Idlix.IdlixProvider
import com.Idlix.AllExtractors

@CloudstreamPlugin
class IdlixPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Idlix())

        // DYNAMIC REGISTER: Auto-register ALL extractors
        // Tidak perlu hardcode satu-satu!
        AllExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
