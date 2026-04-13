package com.Melolo

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.Melolo.generated_sync.SyncExtractors

@CloudstreamPlugin
class MeloloPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Melolo())
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
