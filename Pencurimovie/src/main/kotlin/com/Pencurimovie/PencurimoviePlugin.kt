package com.Pencurimovie

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.Pencurimovie.SyncExtractors

@CloudstreamPlugin
class PencurimoviePlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Pencurimovie())

        // DYNAMIC REGISTER: Auto-register ALL extractors
        // Tidak perlu hardcode satu-satu!
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
