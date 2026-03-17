package com.Pencurimovie

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class PencurimovieProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Pencurimovie())
        // Register extractor khusus untuk server-server Pencurimovie
        registerExtractorAPI(Do7go())
        registerExtractorAPI(Dhcplay())
        registerExtractorAPI(Listeamed())
        registerExtractorAPI(Voe())
        // Existing extractors
        registerExtractorAPI(Dsvplay())
        registerExtractorAPI(Hglink())
    }
}
