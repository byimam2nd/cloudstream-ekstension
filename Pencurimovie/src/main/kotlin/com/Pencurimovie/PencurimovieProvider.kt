package com.Pencurimovie

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class PencurimovieProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Pencurimovie())
        
        // Register ALL extractors (dari file Extractors.kt yang di-auto-copy)
        // StreamWish based
        registerExtractorAPI(Do7go())
        registerExtractorAPI(Dhcplay())
        registerExtractorAPI(Hglink())
        registerExtractorAPI(Ghbrisk())
        registerExtractorAPI(Movearnpre())
        registerExtractorAPI(Minochinos())
        registerExtractorAPI(Mivalyo())
        registerExtractorAPI(Ryderjet())
        registerExtractorAPI(Bingezove())
        registerExtractorAPI(Dingtezuni())
        
        // VidStack based
        registerExtractorAPI(Listeamed())
        registerExtractorAPI(Streamcasthub())
        registerExtractorAPI(Dm21embed())
        registerExtractorAPI(Dm21upns())
        registerExtractorAPI(Pm21p2p())
        registerExtractorAPI(Dm21())
        registerExtractorAPI(Meplayer())
        
        // Custom extractors
        registerExtractorAPI(Voe())
        registerExtractorAPI(Veev())
        registerExtractorAPI(Dintezuvio())
        
        // Other extractors
        registerExtractorAPI(Dailymotion())
        registerExtractorAPI(Okru())
        registerExtractorAPI(Rumble())
        registerExtractorAPI(StreamRuby())
        registerExtractorAPI(VidGuard())
        registerExtractorAPI(Archivd())
        registerExtractorAPI(Newuservideo())
        registerExtractorAPI(Vidhidepro())
    }
}
