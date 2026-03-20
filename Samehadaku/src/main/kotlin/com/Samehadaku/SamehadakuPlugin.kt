// ========================================
// SAMEHADAKU PLUGIN - Entry Point
// ========================================
// Dynamic Extractor Registration
// Standard: cloudstream-ekstension
// ========================================

package com.Samehadaku

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class SamehadakuPlugin: BasePlugin() {
    override fun load() {
        // Register main scraping API
        registerMainAPI(Samehadaku())
        
        // DYNAMIC REGISTER: Auto-register ALL extractors
        // Tidak perlu hardcode satu-satu!
        // Semua extractor dari MasterExtractors.kt akan di-register
        AllExtractors.list.forEach { extractor ->
            registerExtractorAPI(extractor)
        }
    }
}
