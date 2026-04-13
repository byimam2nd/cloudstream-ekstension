package com.Dramabox

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.Dramabox.generated_sync.SyncExtractors

@CloudstreamPlugin
class DramaboxPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Dramabox())
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
