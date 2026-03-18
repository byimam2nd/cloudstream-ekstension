package com.IdlixProvider

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class IdlixProviderPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(com.hexated.IdlixProvider())

        // DYNAMIC REGISTER: Auto-register ALL extractors
        // Tidak perlu hardcode satu-satu!
        com.IdlixProvider.AllExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
