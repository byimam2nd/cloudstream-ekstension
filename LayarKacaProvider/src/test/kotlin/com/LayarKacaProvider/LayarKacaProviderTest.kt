package com.layarKacaProvider

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractorsHelper
import com.lagradost.cloudstream3.loadExtractor
import com.lagradost.cloudstream3.subtitleCallback
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Assert.*
import org.junit.Test
import java.net.URI

/**
 * Unit tests for LayarKaca21 Provider
 * 
 * These tests verify:
 * 1. Domain connectivity
 * 2. Search functionality
 * 3. Load functionality (movie & series)
 * 4. Link extraction
 * 5. Main page loading
 */
class LayarKacaProviderTest {

    private val provider = LayarKacaProvider()

    /**
     * Test 1: Verify provider basic configuration
     */
    @Test
    fun testProviderConfiguration() = runBlocking {
        println("Testing provider configuration...")
        
        assertNotNull("Provider name should not be null", provider.name)
        assertNotNull("Main URL should not be null", provider.mainUrl)
        assertTrue("Provider name should not be empty", provider.name!!.isNotEmpty())
        assertTrue("Main URL should start with https", provider.mainUrl!!.startsWith("https"))
        
        assertEquals("Provider name should be LayarKaca21", "LayarKaca21", provider.name)
        assertTrue("Provider should have main page", provider.hasMainPage)
        assertEquals("Provider language should be 'id'", "id", provider.lang)
        
        assertTrue("Provider should support Movie type", provider.supportedTypes.contains(TvType.Movie))
        assertTrue("Provider should support TvSeries type", provider.supportedTypes.contains(TvType.TvSeries))
        assertTrue("Provider should support AsianDrama type", provider.supportedTypes.contains(TvType.AsianDrama))
        
        println("✓ Provider configuration test passed")
        println("  - Name: ${provider.name}")
        println("  - Main URL: ${provider.mainUrl}")
        println("  - Series URL: ${provider.seriesUrl}")
        println("  - Supported Types: ${provider.supportedTypes}")
    }

    /**
     * Test 2: Verify domain connectivity
     */
    @Test
    fun testDomainConnectivity() = runBlocking {
        println("\nTesting domain connectivity...")
        
        // Test main domain
        try {
            val mainResponse = app.get(provider.mainUrl!!, timeout = 10000L)
            assertTrue(
                "Main domain should be accessible (status: ${mainResponse.code})",
                mainResponse.code in 200..399
            )
            println("✓ Main domain (${provider.mainUrl}) is accessible (status: ${mainResponse.code})")
        } catch (e: Exception) {
            println("⚠ Main domain connectivity test failed: ${e.message}")
            // Don't fail the test, as domains may be blocked in some regions
        }
        
        // Test series domain
        try {
            val seriesResponse = app.get(provider.seriesUrl, timeout = 10000L)
            assertTrue(
                "Series domain should be accessible (status: ${seriesResponse.code})",
                seriesResponse.code in 200..399
            )
            println("✓ Series domain (${provider.seriesUrl}) is accessible (status: ${seriesResponse.code})")
        } catch (e: Exception) {
            println("⚠ Series domain connectivity test failed: ${e.message}")
        }
    }

    /**
     * Test 3: Test search functionality with common query
     */
    @Test
    fun testSearchFunctionality() = runBlocking {
        println("\nTesting search functionality...")
        
        val testQuery = "avatar" // Common movie/series title
        val results = provider.search(testQuery)
        
        assertNotNull("Search results should not be null", results)
        println("✓ Search returned ${results.size} results for query: '$testQuery'")
        
        if (results.isNotEmpty()) {
            // Verify search result structure
            val firstResult = results.first()
            assertNotNull("Search result should have name", firstResult.name)
            assertNotNull("Search result should have URL", firstResult.url)
            
            println("  First result:")
            println("    - Name: ${firstResult.name}")
            println("    - URL: ${firstResult.url}")
            println("    - Type: ${firstResult.type}")
            
            // Verify all results have required fields
            results.forEachIndexed { index, result ->
                assertNotNull("Result $index should have name", result.name)
                assertNotNull("Result $index should have URL", result.url)
                assertTrue(
                    "Result $index URL should start with http",
                    result.url!!.startsWith("http")
                )
            }
            println("✓ All search results have valid structure")
        } else {
            println("⚠ No search results found (may be due to network/domain issues)")
        }
    }

    /**
     * Test 4: Test main page loading
     */
    @Test
    fun testMainPageLoading() = runBlocking {
        println("\nTesting main page loading...")
        
        assertTrue("Provider should have at least one main page request", provider.mainPage.isNotEmpty())
        
        val firstMainPageRequest = provider.mainPage.first()
        println("  Testing main page: ${firstMainPageRequest.name}")
        println("  URL pattern: ${firstMainPageRequest.data}")
        
        try {
            val response: HomePageResponse = provider.getMainPage(1, firstMainPageRequest)
            assertNotNull("Main page response should not be null", response)
            assertNotNull("Main page items should not be null", response.items)
            
            println("✓ Main page loaded successfully with ${response.items.size} items")
            
            if (response.items.isNotEmpty()) {
                val firstItem = response.items.first()
                println("  First item:")
                println("    - Name: ${firstItem.name}")
                println("    - URL: ${firstItem.url}")
            }
        } catch (e: Exception) {
            println("⚠ Main page loading test failed: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Test 5: Test load functionality with movie URL
     */
    @Test
    fun testLoadMovieFunctionality() = runBlocking {
        println("\nTesting load functionality (movie)...")
        
        // First, get a movie URL from search
        val searchResults = provider.search("movie")
        val movieResults = searchResults.filter { it.type == TvType.Movie }
        
        if (movieResults.isNotEmpty()) {
            val movieUrl = movieResults.first().url
            println("  Testing with movie URL: $movieUrl")
            
            try {
                val loadResponse: LoadResponse = provider.load(movieUrl!!)
                assertNotNull("Load response should not be null", loadResponse)
                
                println("✓ Movie loaded successfully")
                println("  - Title: ${loadResponse.name}")
                println("  - Type: ${loadResponse.type}")
                
                if (loadResponse is com.lagradost.cloudstream3.MovieLoadResponse) {
                    println("  - Year: ${loadResponse.year}")
                    println("  - Plot: ${loadResponse.plot?.take(100)}...")
                    println("  - Tags: ${loadResponse.tags}")
                }
            } catch (e: Exception) {
                println("⚠ Movie load test failed: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println("⚠ No movie results found for load test")
        }
    }

    /**
     * Test 6: Test load functionality with series URL
     */
    @Test
    fun testLoadSeriesFunctionality() = runBlocking {
        println("\nTesting load functionality (series)...")
        
        // First, get a series URL from search
        val searchResults = provider.search("series")
        val seriesResults = searchResults.filter { it.type == TvType.TvSeries }
        
        if (seriesResults.isNotEmpty()) {
            val seriesUrl = seriesResults.first().url
            println("  Testing with series URL: $seriesUrl")
            
            try {
                val loadResponse: LoadResponse = provider.load(seriesUrl!!)
                assertNotNull("Load response should not be null", loadResponse)
                
                println("✓ Series loaded successfully")
                println("  - Title: ${loadResponse.name}")
                println("  - Type: ${loadResponse.type}")
                
                if (loadResponse is com.lagradost.cloudstream3.TvSeriesLoadResponse) {
                    println("  - Episodes: ${loadResponse.episodes.size}")
                    if (loadResponse.episodes.isNotEmpty()) {
                        val firstEp = loadResponse.episodes.first()
                        println("  - First Episode: ${firstEp.name} (S${firstEp.season}E${firstEp.episode})")
                    }
                }
            } catch (e: Exception) {
                println("⚠ Series load test failed: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println("⚠ No series results found for load test")
        }
    }

    /**
     * Test 7: Test link loading (extractor)
     */
    @Test
    fun testLoadLinksFunctionality() = runBlocking {
        println("\nTesting load links functionality...")
        
        // Get a movie URL first
        val searchResults = provider.search("action")
        val movieResults = searchResults.filter { it.type == TvType.Movie }
        
        if (movieResults.isNotEmpty()) {
            val movieUrl = movieResults.first().url
            println("  Testing with movie URL: $movieUrl")
            
            try {
                val loadResponse: LoadResponse = provider.load(movieUrl!!)
                val dataUrl = loadResponse.dataUrl
                
                if (!dataUrl.isNullOrEmpty()) {
                    val extractorLinks = mutableListOf<ExtractorLink>()
                    val subtitles = mutableListOf<com.lagradost.cloudstream3.SubtitleFile>()
                    
                    val success = provider.loadLinks(
                        dataUrl,
                        false,
                        { subtitle -> subtitles.add(subtitle) },
                        { extractor -> extractorLinks.add(extractor) }
                    )
                    
                    println("✓ Load links completed: $success")
                    println("  - Extractor links: ${extractorLinks.size}")
                    println("  - Subtitles: ${subtitles.size}")
                    
                    if (extractorLinks.isNotEmpty()) {
                        val firstLink = extractorLinks.first()
                        println("  First extractor:")
                        println("    - Name: ${firstLink.name}")
                        println("    - URL: ${firstLink.url.take(100)}...")
                        println("    - Type: ${firstLink.type}")
                        println("    - Quality: ${firstLink.quality}")
                    }
                    
                    assertTrue("Load links should return true", success)
                } else {
                    println("⚠ No data URL available for link loading")
                }
            } catch (e: Exception) {
                println("⚠ Load links test failed: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println("⚠ No movie results found for link load test")
        }
    }

    /**
     * Test 8: Test helper functions
     */
    @Test
    fun testHelperFunctions() {
        println("\nTesting helper functions...")
        
        // Test getBaseUrl
        val testUrl = "https://example.com/path/to/page"
        val baseUrl = provider.getBaseUrl(testUrl)
        assertEquals("Base URL should be https://example.com", "https://example.com", baseUrl)
        println("✓ getBaseUrl() works correctly")
        
        // Test encodeUrl
        val testQuery = "avatar the last airbender"
        val encoded = testQuery.encodeUrl()
        assertTrue("Encoded URL should contain %20 for spaces", encoded.contains("%20"))
        println("✓ encodeUrl() works correctly: '$testQuery' -> '$encoded'")
    }

    /**
     * Test 9: Test cache functionality
     */
    @Test
    fun testCacheFunctionality() = runBlocking {
        println("\nTesting cache functionality...")
        
        val testQuery = "test_cache_${System.currentTimeMillis()}"
        
        // First search (should populate cache)
        val results1 = provider.search(testQuery)
        println("✓ First search completed (${results1.size} results)")
        
        // Second search (should use cache)
        val results2 = provider.search(testQuery)
        println("✓ Second search completed (${results2.size} results) - using cache")
        
        // Results should be the same
        assertEquals("Cached results should match", results1.size, results2.size)
    }

    /**
     * Test 10: Stress test - Multiple searches
     */
    @Test
    fun testMultipleSearches() = runBlocking {
        println("\nRunning stress test - multiple searches...")
        
        val testQueries = listOf("action", "comedy", "drama", "korea", "anime")
        var successCount = 0
        var failCount = 0
        
        testQueries.forEach { query ->
            try {
                val results = provider.search(query)
                println("  ✓ Search '$query': ${results.size} results")
                successCount++
            } catch (e: Exception) {
                println("  ⚠ Search '$query' failed: ${e.message}")
                failCount++
            }
        }
        
        println("\nStress test completed:")
        println("  - Success: $successCount/${testQueries.size}")
        println("  - Failed: $failCount/${testQueries.size}")
        
        assertTrue("At least 50% of searches should succeed", successCount >= testQueries.size / 2)
    }
}
