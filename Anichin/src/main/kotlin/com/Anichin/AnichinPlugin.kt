package com.Anichin

import android.content.Context
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

enum class ServerList(val link: Pair<String, Boolean>) {
    ANICHIN_CAFE("https://anichin.cafe" to true),
    ANICHIN_TEAM("https://anichin.team" to true),
    ANICHIN_WATCH("https://anichin.watch" to true),
    ANICHIN_TOP("https://anichin.top" to true),
}

@CloudstreamPlugin
class AnichinPlugin : Plugin() {

    override fun load(context: Context) {
        registerMainAPI(Anichin())
        // Register extractors if needed
        // registerExtractorAPI(YourExtractor())
    }

    companion object {
        var currentAnichinServer: String
            get() = getKey("ANICHIN_CURRENT_SERVER") ?: ServerList.ANICHIN_CAFE.link.first
            set(value) {
                setKey("ANICHIN_CURRENT_SERVER", value)
            }
    }
}
