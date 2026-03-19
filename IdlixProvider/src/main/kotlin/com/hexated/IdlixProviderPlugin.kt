package com.hexated

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class IdlixProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(com.hexated.IdlixProvider())

        // DYNAMIC REGISTER: Auto-register ALL extractors
        // Tidak perlu hardcode satu-satu!
        com.hexated.AllExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
