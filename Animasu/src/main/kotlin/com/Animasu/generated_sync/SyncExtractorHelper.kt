// ========================================
// MASTER EXTRACTOR HELPER - v3.4
// Helper untuk loadExtractor dengan fallback
// ========================================
// Last Updated: 2026-03-26
// Sync Target: generated_sync/SyncExtractorHelper.kt
//
// PURPOSE:
// - Helper function untuk semua modul
// - Try loadExtractor first
// - If failed, try direct extractor call from SyncExtractors
// - Works for ALL modules (Anichin, Idlix, LayarKaca21, etc.)
//
// USAGE:
// ```kotlin
// val loaded = loadExtractorWithFallback(
//     url = iframeUrl,
//     referer = data,
//     subtitleCallback = subtitleCallback,
//     callback = callback
// )
// ```
// ========================================

// ========================================
// AUTO-GENERATED - DO NOT EDIT MANUALLY
// Synced from common/MasterExtractorHelper.kt
// File: SyncExtractorHelper.kt
// ========================================
package com.Animasu.generated_sync

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

// ============================================
// REGION: EXTRACTOR HELPER
// ============================================

/**
 * Load extractor dengan fallback ke direct extractor call
 * 
 * Flow:
 * 1. Try loadExtractor (CloudStream API)
 * 2. If failed (returns false), find matching extractor from SyncExtractors
 * 3. Try ALL matching extractors until one works
 * 4. Return true if at least 1 extractor worked
 * 
 * @param url Video URL to extract
 * @param referer Referer URL
 * @param subtitleCallback Subtitle callback
 * @param callback ExtractorLink callback
 * @return true if at least 1 extractor worked, false otherwise
 */
suspend fun loadExtractorWithFallback(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    var loaded = false
    
    // Step 1: Try loadExtractor (CloudStream API)
    try {
        loaded = loadExtractor(url, referer, subtitleCallback, callback)
        Log.d("ExtractorHelper", "loadExtractor result: $loaded")
    } catch (e: Exception) {
        Log.e("ExtractorHelper", "loadExtractor exception: ${e.message}")
    }
    
    // Step 2: If failed, try direct extractor call from SyncExtractors
    if (!loaded) {
        Log.d("ExtractorHelper", "loadExtractor failed, trying direct extractors...")
        Log.d("ExtractorHelper", "URL: $url")
        
        val urlDomain = url.removePrefix("http://").removePrefix("https://").split("/").first().lowercase()
        Log.d("ExtractorHelper", "URL Domain: $urlDomain")
        
        // Get SyncExtractors list dynamically
        val syncExtractorsClass = Class.forName("com.master.generated_sync.SyncExtractors")
        val listField = syncExtractorsClass.getDeclaredField("list")
        @Suppress("UNCHECKED_CAST")
        val extractors = listField.get(null) as List<com.lagradost.cloudstream3.utils.ExtractorApi>
        
        // Find ALL matching extractors
        val matchingExtractors = extractors.filter { extractor ->
            val extractorDomain = extractor.mainUrl.removePrefix("http://").removePrefix("https://").split("/").first().lowercase()
            val domainMatch = urlDomain.contains(extractorDomain) || extractorDomain.contains(urlDomain)
            val nameMatch = url.contains(extractor.name, ignoreCase = true)
            domainMatch || nameMatch
        }
        
        Log.d("ExtractorHelper", "Found ${matchingExtractors.size} matching extractors: ${matchingExtractors.joinToString { it.name }}")
        
        // Try ALL matching extractors
        matchingExtractors.forEach { extractor ->
            try {
                Log.d("ExtractorHelper", "Trying extractor: ${extractor.name} (${extractor.mainUrl})")
                extractor.getUrl(url, referer, subtitleCallback, callback)
                Log.d("ExtractorHelper", "SUCCESS: Extractor ${extractor.name} worked!")
                loaded = true
            } catch (e: Exception) {
                Log.e("ExtractorHelper", "Extractor ${extractor.name} failed: ${e.message}")
            }
        }
    }
    
    return loaded
}

// ============================================
// REGION: LEGACY COMPATIBILITY
// ============================================

/**
 * Legacy function name for backward compatibility
 */
suspend fun loadExtractorDirect(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    return loadExtractorWithFallback(url, referer, subtitleCallback, callback)
}
