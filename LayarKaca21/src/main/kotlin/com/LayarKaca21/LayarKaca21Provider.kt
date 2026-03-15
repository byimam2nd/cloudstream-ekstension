package com.layarkaca21

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class LayarKaca21Provider: BasePlugin() {
    override fun load() {
        registerMainAPI(LayarKaca21())
    }
}
