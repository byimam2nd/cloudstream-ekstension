package com.Idlix

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.Idlix.Idlix
import com.Idlix.generated_sync.SyncExtractors
import com.Idlix.generated_sync.WebViewScraper

@CloudstreamPlugin
class IdlixPlugin: BasePlugin() {
    override fun load() {
        // Set WebViewScraper context untuk SPA scraping
        WebViewScraper.appContext = ctx
        Idlix.appContext = ctx

        registerMainAPI(Idlix())

        // DYNAMIC REGISTER: Auto-register ALL extractors
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
