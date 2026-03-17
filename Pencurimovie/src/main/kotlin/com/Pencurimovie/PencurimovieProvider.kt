package com.Pencurimovie

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class PencurimovieProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Pencurimovie())
        // Register extractor untuk server-server yang tidak didukung bawaan CloudStream
        registerExtractorAPI(Do7go())
        registerExtractorAPI(Dhcplay())
        registerExtractorAPI(Listeamed())
        registerExtractorAPI(Voe())
    }
}