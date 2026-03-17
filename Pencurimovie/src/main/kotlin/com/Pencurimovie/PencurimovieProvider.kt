package com.Pencurimovie

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class PencurimovieProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Pencurimovie())
        registerExtractorAPI(Do7go())
        registerExtractorAPI(Dhcplay())
        registerExtractorAPI(Listeamed())
        registerExtractorAPI(Voe())
    }
}