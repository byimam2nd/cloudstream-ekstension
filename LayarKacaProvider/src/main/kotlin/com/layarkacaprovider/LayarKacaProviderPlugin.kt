package com.layarkacaprovider

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class LayarKacaProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(LayarKaca21())
    }
}
