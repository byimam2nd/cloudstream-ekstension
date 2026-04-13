package com.Donghub

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.Donghub.generated_sync.SyncExtractors

@CloudstreamPlugin
class DonghubPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Donghub())
        SyncExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
