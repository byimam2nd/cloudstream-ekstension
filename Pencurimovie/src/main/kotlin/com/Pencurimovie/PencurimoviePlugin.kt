package com.Pencurimovie

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.Pencurimovie.AllExtractors

@CloudstreamPlugin
class PencurimoviePlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Pencurimovie())

        // DYNAMIC REGISTER: Auto-register ALL extractors
        // Tidak perlu hardcode satu-satu!
        AllExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
